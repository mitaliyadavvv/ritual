package app.ritualhabits.data.focus

import app.ritualhabits.core.model.FocusStatus
import app.ritualhabits.data.repository.FocusOutcome
import app.ritualhabits.data.repository.FocusRepository
import app.ritualhabits.data.repository.HabitRepository

/**
 * Runs when the alarm fires or the device boots. Reconciles through the repository, which completes
 * each due session exactly once (compare-and-set) and records a completion only for the session's
 * own habit, on the day the session ended, and only if that habit was due. Notifies only when the
 * app isn't in the foreground (the Focus screen shows the result itself).
 */
class FocusCompletionHandler(
    private val focus: FocusRepository,
    private val habits: HabitRepository,
    private val alarms: FocusAlarms,
    private val notifier: FocusNotifier,
    private val isAppInForeground: () -> Boolean,
) {
    /** Alarm fired: complete what's due and tell the user. */
    suspend fun onAlarm() {
        val completed = focus.reconcile()
        if (isAppInForeground()) return
        for (session in completed) {
            val name = habits.get(session.habitId)?.name ?: "Your habit"
            notifier.notifyEnded(name, focus.outcome(session) ?: FocusOutcome.NOT_DUE)
        }
    }

    /** Device rebooted (alarms are cleared on reboot): finish anything that ended, re-arm the rest. */
    suspend fun onBoot() {
        onAlarm()
        focus.activeSessions().filter { it.status == FocusStatus.RUNNING }.forEach(alarms::schedule)
    }
}
