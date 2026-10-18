package app.ritualhabits.data.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.ritualhabits.RitualApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant

/** Delivered at a habit's reminder time. */
class HabitReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val habitId = AndroidReminderAlarms.habitIdFrom(intent) ?: return
        val scheduledAt = Instant.fromEpochMilliseconds(intent.getLongExtra(AndroidReminderAlarms.EXTRA_SCHEDULED_AT, 0L))
        val coordinator = (context.applicationContext as RitualApp).container.reminders
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                coordinator.onReminder(habitId, scheduledAt)
            } finally {
                pending.finish()
            }
        }
    }
}

/**
 * Alarms are cleared on reboot and app update, and wall-clock reminders must move when the clock
 * or time zone changes: recompute every habit's next reminder.
 */
class ReminderResyncReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in RESYNC_ACTIONS) return
        val coordinator = (context.applicationContext as RitualApp).container.reminders
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                coordinator.resyncAll()
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        val RESYNC_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
        )
    }
}
