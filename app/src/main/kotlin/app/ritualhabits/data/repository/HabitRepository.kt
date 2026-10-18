package app.ritualhabits.data.repository

import androidx.room.withTransaction
import app.ritualhabits.core.model.CompletionKey
import app.ritualhabits.core.rules.EligibilityRules
import app.ritualhabits.core.rules.FocusTimer
import app.ritualhabits.core.rules.HabitHistory
import app.ritualhabits.core.rules.HabitInput
import app.ritualhabits.core.rules.HabitInputError
import app.ritualhabits.core.rules.HabitValidation
import app.ritualhabits.core.rules.PeriodChange
import app.ritualhabits.core.time.AppClock
import app.ritualhabits.data.local.FocusSessionEntity
import app.ritualhabits.data.local.HabitEntity
import app.ritualhabits.data.local.HabitPeriodEntity
import app.ritualhabits.data.local.RitualDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import java.util.UUID

/** A habit's daily reminder setting as saved from the editor. */
data class ReminderSetting(val enabled: Boolean, val time: LocalTime?)

sealed interface HabitWriteResult {
    data class Success(val habitId: String) : HabitWriteResult
    data class Invalid(val error: HabitInputError) : HabitWriteResult
    data object NotFound : HabitWriteResult
}

class HabitRepository(
    private val db: RitualDatabase,
    private val clock: AppClock,
) {
    private val habitDao = db.habitDao()
    private val periodDao = db.periodDao()
    private val completionDao = db.completionDao()
    private val focusDao = db.focusSessionDao()

    fun observeActive(): Flow<List<HabitEntity>> = habitDao.observeActive()
    fun observeArchived(): Flow<List<HabitEntity>> = habitDao.observeArchived()
    fun observeAll(): Flow<List<HabitEntity>> = habitDao.observeAll()

    /** Any habit by id, including archived and deleted ones (for labelling history). */
    suspend fun get(id: String): HabitEntity? = habitDao.get(id)

    /** Everything streaks and analytics are derived from. */
    fun observeHistory(): Flow<HabitHistory> =
        combine(periodDao.observeAll(), completionDao.observeAll()) { periods, completions ->
            HabitHistory(periods.map { it.toModel() }, completions.map { CompletionKey(it.habitId, it.date) })
        }

    suspend fun create(name: String, description: String?, reminder: ReminderSetting? = null): HabitWriteResult = db.withTransaction {
        when (val input = HabitValidation.validate(name, description, habitDao.liveNames())) {
            is HabitInput.Invalid -> HabitWriteResult.Invalid(input.error)
            is HabitInput.Valid -> {
                val habit = HabitEntity(
                    id = UUID.randomUUID().toString(),
                    name = input.name,
                    description = input.description,
                    sortOrder = habitDao.maxSortOrder() + 1,
                    createdAt = clock.now(),
                    archivedAt = null,
                    deletedAt = null,
                    reminderEnabled = reminder?.enabled ?: false,
                    reminderTime = reminder?.time,
                )
                habitDao.insert(habit)
                apply(EligibilityRules.onCreate(habit.id, clock.today()))
                HabitWriteResult.Success(habit.id)
            }
        }
    }

    /** Rename / edit description. Never touches eligibility or completions. */
    suspend fun update(id: String, name: String, description: String?, reminder: ReminderSetting? = null): HabitWriteResult = db.withTransaction {
        val habit = habitDao.get(id)?.takeUnless { it.isDeleted } ?: return@withTransaction HabitWriteResult.NotFound
        when (val input = HabitValidation.validate(name, description, habitDao.liveNames(excludingId = id))) {
            is HabitInput.Invalid -> HabitWriteResult.Invalid(input.error)
            is HabitInput.Valid -> {
                habitDao.update(
                    habit.copy(
                        name = input.name,
                        description = input.description,
                        // null = leave the reminder as it is (callers that don't edit reminders).
                        reminderEnabled = reminder?.enabled ?: habit.reminderEnabled,
                        reminderTime = if (reminder != null) reminder.time ?: habit.reminderTime else habit.reminderTime,
                    ),
                )
                HabitWriteResult.Success(id)
            }
        }
    }

    suspend fun reorder(idsInOrder: List<String>) = db.withTransaction {
        idsInOrder.forEachIndexed { index, id -> habitDao.setSortOrder(id, index) }
    }

    /**
     * Move an active habit [by] positions (negative = up) within the active order. Reads the order
     * inside the transaction, so rapid repeated moves are applied to the latest order.
     * Returns false at either end of the list.
     */
    suspend fun move(id: String, by: Int): Boolean = db.withTransaction {
        val order = habitDao.activeOrdered().map { it.id }.toMutableList()
        val from = order.indexOf(id)
        if (from < 0) return@withTransaction false
        val to = (from + by).coerceIn(0, order.lastIndex)
        if (to == from) return@withTransaction false
        order.add(to, order.removeAt(from))
        order.forEachIndexed { index, habitId -> habitDao.setSortOrder(habitId, index) }
        true
    }

    suspend fun archive(id: String): HabitWriteResult = deactivate(id) { it.copy(archivedAt = clock.now()) }

    /** Soft delete: hidden everywhere, history kept so past results never change. */
    suspend fun delete(id: String): HabitWriteResult = deactivate(id) { it.copy(deletedAt = clock.now()) }

    /** Restore an archived habit, or undo a delete. Required again from today. */
    suspend fun restore(id: String): HabitWriteResult = db.withTransaction {
        val habit = habitDao.get(id) ?: return@withTransaction HabitWriteResult.NotFound
        if (habit.isActive) return@withTransaction HabitWriteResult.Success(id)
        if (habitDao.liveNames(excludingId = id).any { it.equals(habit.name, ignoreCase = true) }) {
            return@withTransaction HabitWriteResult.Invalid(HabitInputError.NAME_DUPLICATE)
        }
        habitDao.update(habit.copy(archivedAt = null, deletedAt = null))
        val periods = periodDao.forHabit(id).map { it.toModel() }
        apply(EligibilityRules.onReactivate(id, periods, clock.today()))
        HabitWriteResult.Success(id)
    }

    /**
     * Undo a delete: return the habit to the state it had before (active or archived).
     * Only an active habit becomes required again, from today.
     */
    suspend fun undoDelete(id: String): HabitWriteResult = db.withTransaction {
        val habit = habitDao.get(id) ?: return@withTransaction HabitWriteResult.NotFound
        if (!habit.isDeleted) return@withTransaction HabitWriteResult.Success(id)
        if (habitDao.liveNames(excludingId = id).any { it.equals(habit.name, ignoreCase = true) }) {
            return@withTransaction HabitWriteResult.Invalid(HabitInputError.NAME_DUPLICATE)
        }
        habitDao.update(habit.copy(deletedAt = null))
        if (habit.archivedAt == null) {
            val periods = periodDao.forHabit(id).map { it.toModel() }
            apply(EligibilityRules.onReactivate(id, periods, clock.today()))
        }
        HabitWriteResult.Success(id)
    }

    private suspend fun deactivate(id: String, mark: (HabitEntity) -> HabitEntity): HabitWriteResult =
        db.withTransaction {
            val habit = habitDao.get(id) ?: return@withTransaction HabitWriteResult.NotFound
            if (habit.isDeleted) return@withTransaction HabitWriteResult.Success(id)
            habitDao.update(mark(habit))
            val periods = periodDao.forHabit(id).map { it.toModel() }
            apply(EligibilityRules.onDeactivate(periods, clock.today()))
            cancelActiveSessions(id)
            HabitWriteResult.Success(id)
        }

    private suspend fun cancelActiveSessions(habitId: String) {
        val now = clock.now()
        focusDao.active().filter { it.habitId == habitId }.forEach {
            focusDao.update(FocusSessionEntity.from(FocusTimer.cancel(it.toModel(), now)))
        }
    }

    private suspend fun apply(change: PeriodChange) {
        when (change) {
            PeriodChange.None -> Unit
            is PeriodChange.Insert -> periodDao.insert(HabitPeriodEntity.from(change.period))
            is PeriodChange.Close ->
                periodDao.update(HabitPeriodEntity.from(change.period).copy(endDate = change.endDateExclusive))
            is PeriodChange.Remove -> periodDao.delete(HabitPeriodEntity.from(change.period))
            is PeriodChange.Reopen -> periodDao.update(HabitPeriodEntity.from(change.period).copy(endDate = null))
        }
    }
}

/** Shared check: is [habitId] required on [date] according to stored periods? */
internal suspend fun RitualDatabase.isEligible(habitId: String, date: LocalDate): Boolean =
    periodDao().forHabit(habitId).any { it.toModel().covers(date) }
