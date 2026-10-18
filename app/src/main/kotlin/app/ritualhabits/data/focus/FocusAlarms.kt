package app.ritualhabits.data.focus

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import app.ritualhabits.core.model.FocusSession
import app.ritualhabits.core.model.FocusStatus

/** Schedules the wake-up that tells the user a focus session has ended. */
interface FocusAlarms {
    /** Arms (or re-arms) the alarm for a RUNNING session's end time. Other states are ignored. */
    fun schedule(session: FocusSession)
    fun cancel()

    /** False when Android only allows inexact alarms (the end-of-session alert may be minutes late). */
    val canBeExact: Boolean get() = true
}

/**
 * AlarmManager-backed implementation. The alarm only *notifies*: correctness never depends on it,
 * because the repository reconciles from the stored end time whenever the app or a receiver runs.
 *
 * - Exact (`setExactAndAllowWhileIdle`) when the user/system allows exact alarms.
 * - Otherwise `setAndAllowWhileIdle`: still fires in Doze, possibly a few minutes late.
 * Only one session can be active, so a single request code is used.
 */
class AndroidFocusAlarms(private val context: Context) : FocusAlarms {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    override fun schedule(session: FocusSession) {
        val endsAt = session.endsAt ?: return
        if (session.status != FocusStatus.RUNNING) return
        val at = endsAt.toEpochMilliseconds()
        val intent = pendingIntent()
        if (canScheduleExact()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent)
        }
    }

    override fun cancel() = alarmManager.cancel(pendingIntent())

    fun canScheduleExact(): Boolean = Build.VERSION.SDK_INT < 31 || alarmManager.canScheduleExactAlarms()

    override val canBeExact: Boolean get() = canScheduleExact()

    private fun pendingIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        Intent(context, FocusAlarmReceiver::class.java).setAction(ACTION_FOCUS_ENDED),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        const val ACTION_FOCUS_ENDED = "app.ritualhabits.action.FOCUS_ENDED"
        private const val REQUEST_CODE = 4101
    }
}
