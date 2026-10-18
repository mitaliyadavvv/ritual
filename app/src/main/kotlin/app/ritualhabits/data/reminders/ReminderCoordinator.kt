package app.ritualhabits.data.reminders

import app.ritualhabits.core.rules.ReminderSchedule
import app.ritualhabits.core.time.AppClock
import app.ritualhabits.data.local.HabitEntity
import app.ritualhabits.data.repository.HabitRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Instant

/** What happened when a reminder alarm was delivered. */
enum class ReminderOutcome { SHOWN, ALREADY_DONE, NOT_DUE, INACTIVE, STALE, DUPLICATE }

/**
 * Keeps one alarm per habit in step with the habits table, and decides at delivery time whether a
 * reminder is still wanted. Lifecycle changes (create, edit, archive, restore, delete, undo) all go
 * through the existing repository; this observes the result, so their code is untouched.
 *
 * A habit has an alarm only while it is active with its reminder on. Delivery never writes a
 * completion; it only shows a notification.
 */
class ReminderCoordinator(
    private val habits: HabitRepository,
    private val alarms: ReminderAlarms,
    private val notifier: ReminderNotifier,
    private val clock: AppClock,
) {
    private val mutex = Mutex()
    /** habitId -> instant currently scheduled, to avoid redundant AlarmManager calls. */
    private val scheduled = mutableMapOf<String, Instant>()
    /** habitId -> the scheduled time last shown, so a repeated delivery can't notify twice. */
    private val lastShown = mutableMapOf<String, Instant>()

    /** Follow the habits table for the life of the process. The first pass is a full resync. */
    fun start(scope: CoroutineScope) {
        scope.launch {
            var first = true
            habits.observeAll().collect {
                sync(it, force = first)
                first = false
            }
        }
    }

    /** Recompute every alarm from scratch: after boot, app update, clock or time-zone change. */
    suspend fun resyncAll() = sync(habits.observeAll().first(), force = true)

    /**
     * Make alarms match [all]: active habits with a reminder get their next occurrence; every other
     * habit has none (and any reminder notification it showed is cleared). Unchanged alarms are left
     * alone unless [force] (system events can silently drop or shift alarms).
     */
    suspend fun sync(all: List<HabitEntity>, force: Boolean = false) = mutex.withLock {
        val now = clock.now()
        val zone = clock.zone()
        if (force) scheduled.clear()
        for (habit in all) {
            val at = habit.nextReminder(now, zone)
            when {
                at == null -> {
                    if (force || habit.id in scheduled) alarms.cancel(habit.id)
                    scheduled.remove(habit.id)
                    notifier.clear(habit.id)
                }
                scheduled[habit.id] != at -> {
                    alarms.schedule(habit.id, at)
                    scheduled[habit.id] = at
                }
            }
        }
        // Rows that disappeared entirely (local data cleared): cancel what was scheduled.
        val present = all.mapTo(HashSet()) { it.id }
        (scheduled.keys - present).toList().forEach { id ->
            alarms.cancel(id)
            notifier.clear(id)
            scheduled.remove(id)
        }
    }

    /** An alarm fired for [habitId], originally scheduled for [scheduledAt]. */
    suspend fun onReminder(habitId: String, scheduledAt: Instant): ReminderOutcome = mutex.withLock {
        val now = clock.now()
        val zone = clock.zone()
        val habit = habits.get(habitId)
        if (habit == null || !habit.isActive || !habit.reminderEnabled || habit.reminderTime == null) {
            alarms.cancel(habitId)
            scheduled.remove(habitId)
            return@withLock ReminderOutcome.INACTIVE
        }
        // Whatever happens today, tomorrow's reminder is armed (replacing this one).
        val next = ReminderSchedule.next(habit.reminderTime, now, zone)
        alarms.schedule(habitId, next)
        scheduled[habitId] = next

        if (!ReminderSchedule.isForToday(scheduledAt, now, zone)) return@withLock ReminderOutcome.STALE
        val today = clock.today()
        val history = habits.observeHistory().first()
        when {
            !history.isEligible(habitId, today) -> ReminderOutcome.NOT_DUE
            history.isCompleted(habitId, today) -> ReminderOutcome.ALREADY_DONE
            lastShown[habitId] == scheduledAt -> ReminderOutcome.DUPLICATE
            else -> {
                notifier.show(habitId, habit.name)
                lastShown[habitId] = scheduledAt
                ReminderOutcome.SHOWN
            }
        }
    }

    private fun HabitEntity.nextReminder(now: Instant, zone: kotlinx.datetime.TimeZone): Instant? {
        val time = reminderTime ?: return null
        if (!isActive || !reminderEnabled) return null
        return ReminderSchedule.next(time, now, zone)
    }
}
