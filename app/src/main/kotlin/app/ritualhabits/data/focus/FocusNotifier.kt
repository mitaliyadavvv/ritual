package app.ritualhabits.data.focus

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.ritualhabits.MainActivity
import app.ritualhabits.R
import app.ritualhabits.data.repository.FocusOutcome

/** Posts the "focus session ended" notification. */
interface FocusNotifier {
    fun notifyEnded(habitName: String, outcome: FocusOutcome)
    fun clear()
}

class AndroidFocusNotifier(private val context: Context) : FocusNotifier {

    fun createChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "Focus sessions", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Tells you when a focus session has finished."
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    override fun notifyEnded(habitName: String, outcome: FocusOutcome) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return // The session is still recorded; the user sees the result when they open Ritual.
        }
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_OPEN_TAB, MainActivity.TAB_FOCUS)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val text = when (outcome) {
            FocusOutcome.MARKED_DONE -> "“$habitName” is marked done for today."
            FocusOutcome.NOT_DUE -> "“$habitName” wasn't due today, so nothing was recorded."
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_focus)
            .setContentTitle("Focus session complete")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    override fun clear() = NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)

    companion object {
        const val CHANNEL_ID = "focus_complete"
        const val NOTIFICATION_ID = 4102
    }
}
