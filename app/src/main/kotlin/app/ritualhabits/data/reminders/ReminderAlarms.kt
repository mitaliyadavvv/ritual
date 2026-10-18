package app.ritualhabits.data.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import kotlinx.datetime.Instant

/** One pending alarm per habit. Scheduling again replaces the previous one; cancel removes it. */
interface ReminderAlarms {
    fun schedule(habitId: String, at: Instant)
    fun cancel(habitId: String)
}

/**
 * AlarmManager implementation. Each habit's alarm is identified by a data URI containing its id
 * (`ritual://reminder/<id>`), so PendingIntents for different habits never collide and the same
 * habit always maps to the same alarm (no duplicates).
 *
 * Timing: exact (`setExactAndAllowWhileIdle`) only if the user has already allowed exact alarms for
 * Ritual (e.g. via Focus's "Allow precise alerts"); otherwise `setAndAllowWhileIdle`, which still
 * fires in Doze but may be a few minutes late. Reminders never request exact-alarm access.
 */
class AndroidReminderAlarms(private val context: Context) : ReminderAlarms {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    override fun schedule(habitId: String, at: Instant) {
        val intent = pendingIntent(habitId, at) ?: return
        val millis = at.toEpochMilliseconds()
        if (Build.VERSION.SDK_INT < 31 || alarmManager.canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, intent)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, intent)
        }
    }

    override fun cancel(habitId: String) {
        val existing = PendingIntent.getBroadcast(
            context, 0, baseIntent(habitId), PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        ) ?: return
        alarmManager.cancel(existing)
        existing.cancel()
    }

    private fun pendingIntent(habitId: String, at: Instant): PendingIntent? = PendingIntent.getBroadcast(
        context,
        0,
        baseIntent(habitId).putExtra(EXTRA_SCHEDULED_AT, at.toEpochMilliseconds()),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun baseIntent(habitId: String) = Intent(context, HabitReminderReceiver::class.java)
        .setAction(ACTION_REMIND)
        .setData(Uri.Builder().scheme("ritual").authority("reminder").appendPath(habitId).build())

    companion object {
        const val ACTION_REMIND = "app.ritualhabits.action.HABIT_REMINDER"
        const val EXTRA_SCHEDULED_AT = "scheduled_at"

        fun habitIdFrom(intent: Intent): String? = intent.data?.lastPathSegment
    }
}
