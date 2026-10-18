package app.ritualhabits.data.repository

import androidx.room.withTransaction
import app.ritualhabits.core.model.CompletionSource
import app.ritualhabits.core.model.FocusSession
import app.ritualhabits.core.model.FocusStatus
import app.ritualhabits.core.rules.FocusTimer
import app.ritualhabits.core.time.AppClock
import app.ritualhabits.data.local.CompletionEntity
import app.ritualhabits.data.local.FocusSessionEntity
import app.ritualhabits.data.local.RitualDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import kotlin.time.Duration

sealed interface FocusStartResult {
    data class Started(val session: FocusSession) : FocusStartResult
    data object AlreadyActive : FocusStartResult
    data object NotEligible : FocusStartResult
}

/**
 * Persists focus sessions. Every action first reconciles, so a session whose end time has passed
 * is completed exactly once, no matter which caller (UI, alarm, boot) notices first.
 */
class FocusRepository(
    private val db: RitualDatabase,
    private val clock: AppClock,
) {
    private val focusDao = db.focusSessionDao()
    private val completionDao = db.completionDao()

    fun observeCurrent(): Flow<FocusSession?> = focusDao.observeCurrent().map { it?.toModel() }

    suspend fun start(habitId: String, duration: Duration): FocusStartResult = db.withTransaction {
        reconcileInTransaction()
        if (focusDao.active().isNotEmpty()) return@withTransaction FocusStartResult.AlreadyActive
        if (!db.isEligible(habitId, clock.today())) return@withTransaction FocusStartResult.NotEligible
        val session = FocusTimer.start(UUID.randomUUID().toString(), habitId, duration, clock.now())
        focusDao.insert(FocusSessionEntity.from(session))
        FocusStartResult.Started(session)
    }

    suspend fun pause(id: String): FocusSession? = transition(id, FocusStatus.RUNNING) { FocusTimer.pause(it, clock.now()) }

    suspend fun resume(id: String): FocusSession? = transition(id, FocusStatus.PAUSED) { FocusTimer.resume(it, clock.now()) }

    /** Never writes a completion. If the end time already passed, the session completes instead. */
    suspend fun cancel(id: String): FocusSession? = db.withTransaction {
        reconcileInTransaction()
        val session = focusDao.get(id)?.toModel() ?: return@withTransaction null
        if (!session.isActive) return@withTransaction session
        FocusTimer.cancel(session, clock.now()).also { focusDao.update(FocusSessionEntity.from(it)) }
    }

    /** User dismissed the "Session complete" state. */
    suspend fun acknowledge(id: String) = focusDao.acknowledge(id)

    /** Completes every RUNNING session whose end time has passed. Returns the ones this call completed. */
    suspend fun reconcile(): List<FocusSession> = db.withTransaction { reconcileInTransaction() }

    private suspend fun transition(
        id: String,
        requiredStatus: FocusStatus,
        change: (FocusSession) -> FocusSession,
    ): FocusSession? = db.withTransaction {
        reconcileInTransaction()
        val session = focusDao.get(id)?.toModel() ?: return@withTransaction null
        if (session.status != requiredStatus) return@withTransaction session
        change(session).also { focusDao.update(FocusSessionEntity.from(it)) }
    }

    private suspend fun reconcileInTransaction(): List<FocusSession> {
        val zone = clock.zone()
        return focusDao.due(clock.now()).mapNotNull { entity ->
            val done = FocusTimer.complete(entity.toModel(), zone)
            val date = done.completionDate!!
            // Compare-and-set: if another caller already completed it, do nothing.
            if (focusDao.markCompleted(done.id, done.finishedAt!!, date) == 0) return@mapNotNull null
            if (db.isEligible(done.habitId, date)) {
                // IGNORE keeps an existing (e.g. manual) completion; counts never inflate.
                completionDao.insertIgnore(
                    CompletionEntity(done.habitId, date, done.finishedAt!!, CompletionSource.FOCUS, done.id),
                )
            }
            done
        }
    }
}
