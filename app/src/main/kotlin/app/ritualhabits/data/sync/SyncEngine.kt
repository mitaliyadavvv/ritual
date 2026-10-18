package app.ritualhabits.data.sync

import androidx.room.withTransaction
import app.ritualhabits.core.model.CompletionSource
import app.ritualhabits.core.model.ThemeMode
import app.ritualhabits.data.local.CompletionEntity
import app.ritualhabits.data.local.HabitEntity
import app.ritualhabits.data.local.HabitPeriodEntity
import app.ritualhabits.data.local.RitualDatabase
import app.ritualhabits.data.local.SyncEntity
import app.ritualhabits.data.local.SyncMetaEntity
import app.ritualhabits.data.local.SyncTriggers
import app.ritualhabits.data.repository.PreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

/** What signing in as an account means for the data already on this phone. */
sealed interface Attach {
    /** First sign-in on this phone: its data now belongs to the account and will be uploaded. */
    data object Claimed : Attach
    /** Same account as before: carry on syncing. */
    data object Resumed : Attach
    /** The phone holds another account's data. Nothing is merged unless the user chooses to switch. */
    data class OtherAccount(val previousEmail: String?, val unsyncedChanges: Int) : Attach
}

/**
 * Two-way sync between the local database (always the source of truth for the UI) and [CloudStore].
 *
 * - Local writes are queued by SQLite triggers ([SyncTriggers]) into `sync_outbox`.
 * - [sync] pulls what changed remotely since the last cursor, applies each record unless this phone
 *   has a newer pending change to it (last writer wins, per record), then uploads the outbox.
 * - Records have stable ids, so a retried upload overwrites rather than duplicates.
 * - Streaks aren't stored: they're recalculated from habits, periods and completions, which sync
 *   as-is (dates are calendar dates, so time zones never shift them).
 */
class SyncEngine(
    private val db: RitualDatabase,
    private val cloud: CloudStore,
    private val preferences: PreferencesRepository,
) {
    private val dao = db.syncDao()
    private val habitDao = db.habitDao()
    private val periodDao = db.periodDao()
    private val mutex = Mutex()

    fun observePendingCount(): Flow<Int> = dao.observeOutboxCount()

    suspend fun pendingCount(): Int = dao.observeOutboxCount().first()

    suspend fun ownerEmail(): String? = dao.meta(META_OWNER_EMAIL)

    suspend fun ownerUid(): String? = dao.meta(SyncTriggers.META_OWNER_UID)

    suspend fun lastSyncedAt(): Instant? = dao.meta(META_LAST_SYNC)?.toLongOrNull()?.let(Instant::fromEpochMilliseconds)

    /** Called after every successful sign-in. Never moves data between accounts by itself. */
    suspend fun attach(uid: String, email: String?): Attach = mutex.withLock {
        db.withTransaction {
            when (dao.meta(SyncTriggers.META_OWNER_UID)) {
                null -> {
                    dao.setMeta(SyncMetaEntity(SyncTriggers.META_OWNER_UID, uid))
                    email?.let { dao.setMeta(SyncMetaEntity(META_OWNER_EMAIL, it)) }
                    // Keep everything already on the phone: queue it for upload (cloud wins exact ties).
                    dao.queueAllHabits()
                    dao.queueAllCompletions()
                    dao.deleteMeta(META_CURSOR_HABITS)
                    dao.deleteMeta(META_CURSOR_COMPLETIONS)
                    Attach.Claimed
                }
                uid -> {
                    email?.let { dao.setMeta(SyncMetaEntity(META_OWNER_EMAIL, it)) }
                    Attach.Resumed
                }
                else -> Attach.OtherAccount(dao.meta(META_OWNER_EMAIL), dao.outbox().size)
            }
        }
    }

    /**
     * The user chose to switch accounts: remove the previous account's records from this phone
     * (they remain in that account's cloud copy) and take on [uid]. The next [sync] downloads its data.
     */
    suspend fun replaceLocalDataWith(uid: String, email: String?) = mutex.withLock {
        db.withTransaction {
            dao.setMeta(SyncMetaEntity(SyncTriggers.META_APPLYING, "1"))
            dao.deleteAllCompletions()
            dao.deleteAllFocusSessions()
            dao.deleteAllPeriods()
            dao.deleteAllHabits()
            dao.clearOutbox()
            dao.clearMeta() // also clears the applying flag and cursors
            dao.setMeta(SyncMetaEntity(SyncTriggers.META_OWNER_UID, uid))
            email?.let { dao.setMeta(SyncMetaEntity(META_OWNER_EMAIL, it)) }
        }
        // Let the new account's appearance settings win.
        preferences.resetSyncedPreferencesClock()
    }

    /** One full round: download, merge, upload. Throws [CloudUnavailableException] when offline. */
    suspend fun sync(uid: String) = mutex.withLock {
        check(dao.meta(SyncTriggers.META_OWNER_UID) == uid) { "Local data belongs to another account" }
        pull(uid)
        push(uid)
        syncPreferences(uid)
        dao.setMeta(SyncMetaEntity(META_LAST_SYNC, System.currentTimeMillis().toString()))
    }

    private suspend fun pull(uid: String) {
        val habits = cloud.habitsSince(uid, dao.meta(META_CURSOR_HABITS)?.toLongOrNull())
        val completions = cloud.completionsSince(uid, dao.meta(META_CURSOR_COMPLETIONS)?.toLongOrNull())
        db.withTransaction {
            dao.setMeta(SyncMetaEntity(SyncTriggers.META_APPLYING, "1"))
            habits.items.forEach { applyHabit(it) }
            var skippedFrom: Long? = null
            for (remote in completions.items) {
                if (!applyCompletion(remote)) skippedFrom = minOf(skippedFrom ?: remote.serverTime, remote.serverTime)
            }
            dao.deleteMeta(SyncTriggers.META_APPLYING)
            habits.newestServerTime?.let { dao.setMeta(SyncMetaEntity(META_CURSOR_HABITS, it.toString())) }
            // A completion whose habit hasn't arrived yet is fetched again next time.
            (skippedFrom ?: completions.newestServerTime)?.let { dao.setMeta(SyncMetaEntity(META_CURSOR_COMPLETIONS, it.toString())) }
        }
    }

    private suspend fun applyHabit(remote: CloudHabit) {
        val pending = dao.outboxEntry(SyncEntity.HABIT, remote.id)
        if (pending != null && pending.changedAt >= remote.updatedAt) return // this phone's change is newer
        val incoming = remote.toEntity()
        val local = habitDao.get(remote.id)
        when {
            local == null -> habitDao.insert(incoming)
            local != incoming -> habitDao.update(incoming)
        }
        val localPeriods = periodDao.forHabit(remote.id).map { CloudPeriod(it.startDate.toString(), it.endDate?.toString()) }
        if (localPeriods.toSet() != remote.periods.toSet()) {
            dao.deletePeriods(remote.id)
            remote.periods.forEach {
                periodDao.insert(HabitPeriodEntity(habitId = remote.id, startDate = LocalDate.parse(it.start), endDate = it.end?.let(LocalDate::parse)))
            }
        }
        if (pending != null) dao.dropOutboxEntry(SyncEntity.HABIT, remote.id)
    }

    /** Returns false if the completion's habit isn't on this phone yet. */
    private suspend fun applyCompletion(remote: CloudCompletion): Boolean {
        val key = completionKey(remote.habitId, remote.date)
        val pending = dao.outboxEntry(SyncEntity.COMPLETION, key)
        if (pending != null && pending.changedAt >= remote.updatedAt) return true
        val date = LocalDate.parse(remote.date)
        if (remote.done) {
            if (habitDao.get(remote.habitId) == null) return false
            val incoming = CompletionEntity(
                habitId = remote.habitId,
                date = date,
                completedAt = Instant.fromEpochMilliseconds(remote.completedAt ?: remote.updatedAt),
                source = remote.source?.let { s -> CompletionSource.entries.firstOrNull { it.name == s } } ?: CompletionSource.MANUAL,
                focusSessionId = remote.focusSessionId,
            )
            if (dao.completion(remote.habitId, date) != incoming) dao.upsertCompletion(incoming)
        } else {
            db.completionDao().delete(remote.habitId, date)
        }
        if (pending != null) dao.dropOutboxEntry(SyncEntity.COMPLETION, key)
        return true
    }

    private suspend fun push(uid: String) {
        val entries = dao.outbox()
        if (entries.isEmpty()) return
        val habits = mutableListOf<CloudHabit>()
        val completions = mutableListOf<CloudCompletion>()
        for (entry in entries) {
            when (entry.entity) {
                SyncEntity.HABIT -> {
                    val habit = habitDao.get(entry.key)
                    if (habit == null) dao.markUploaded(entry.entity, entry.key, entry.changedAt)
                    else habits += habit.toCloud(periodDao.forHabit(habit.id), entry.changedAt)
                }
                SyncEntity.COMPLETION -> {
                    val (habitId, dateText) = entry.key.split('|', limit = 2).let { it[0] to it.getOrElse(1) { "" } }
                    val date = runCatching { LocalDate.parse(dateText) }.getOrNull()
                    if (date == null) {
                        dao.markUploaded(entry.entity, entry.key, entry.changedAt)
                        continue
                    }
                    val row = dao.completion(habitId, date)
                    completions += CloudCompletion(
                        habitId = habitId,
                        date = dateText,
                        done = row != null,
                        completedAt = row?.completedAt?.toEpochMilliseconds(),
                        source = row?.source?.name,
                        focusSessionId = row?.focusSessionId,
                        updatedAt = entry.changedAt,
                    )
                }
                else -> dao.markUploaded(entry.entity, entry.key, entry.changedAt)
            }
        }
        cloud.write(uid, habits, completions)
        // Only clear what was uploaded; anything changed meanwhile has a newer timestamp and stays queued.
        db.withTransaction { entries.forEach { dao.markUploaded(it.entity, it.key, it.changedAt) } }
    }

    private suspend fun syncPreferences(uid: String) {
        val local = preferences.syncedPreferences()
        val remote = cloud.preferences(uid)
        when {
            remote != null && remote.updatedAt > local.changedAt -> preferences.applySyncedPreferences(
                themeMode = ThemeMode.entries.firstOrNull { it.name == remote.themeMode } ?: local.themeMode,
                hapticsEnabled = remote.hapticsEnabled,
                changedAt = remote.updatedAt,
            )
            remote == null || local.changedAt > remote.updatedAt -> cloud.writePreferences(
                uid,
                CloudPreferences(local.themeMode.name, local.hapticsEnabled, local.changedAt),
            )
        }
    }

    companion object {
        const val META_OWNER_EMAIL = "owner_email"
        const val META_CURSOR_HABITS = "cursor_habits"
        const val META_CURSOR_COMPLETIONS = "cursor_completions"
        const val META_LAST_SYNC = "last_sync"

        fun completionKey(habitId: String, date: String) = "$habitId|$date"
    }
}

internal fun HabitEntity.toCloud(periods: List<HabitPeriodEntity>, updatedAt: Long) = CloudHabit(
    id = id,
    name = name,
    description = description,
    sortOrder = sortOrder,
    createdAt = createdAt.toEpochMilliseconds(),
    archivedAt = archivedAt?.toEpochMilliseconds(),
    deletedAt = deletedAt?.toEpochMilliseconds(),
    reminderEnabled = reminderEnabled,
    reminderMinutes = reminderTime?.let { it.hour * 60 + it.minute },
    periods = periods.sortedBy { it.startDate }.map { CloudPeriod(it.startDate.toString(), it.endDate?.toString()) },
    updatedAt = updatedAt,
)

internal fun CloudHabit.toEntity() = HabitEntity(
    id = id,
    name = name,
    description = description,
    sortOrder = sortOrder,
    createdAt = Instant.fromEpochMilliseconds(createdAt),
    archivedAt = archivedAt?.let(Instant::fromEpochMilliseconds),
    deletedAt = deletedAt?.let(Instant::fromEpochMilliseconds),
    reminderEnabled = reminderEnabled,
    reminderTime = reminderMinutes?.takeIf { it in 0 until 24 * 60 }?.let { LocalTime(it / 60, it % 60) },
)
