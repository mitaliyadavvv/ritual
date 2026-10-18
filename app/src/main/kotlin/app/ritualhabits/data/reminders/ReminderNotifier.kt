package app.ritualhabits.data.reminders

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

/** Shows and clears a habit's reminder notification. */
interface ReminderNotifier {
    fun show(habitId: String, habitName: String)
    fun clear(habitId: String)
    /** False when Android won't show notifications for Ritual (permission denied or channel off). */
    val canNotify: Boolean
}

class AndroidReminderNotifier(private val context: Context) : ReminderNotifier {

    fun createChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "Habit reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "A daily nudge at the time you choose for a habit."
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    override val canNotify: Boolean
        get() {
            val granted = Build.VERSION.SDK_INT < 33 ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            val manager = NotificationManagerCompat.from(context)
            val channelOn = manager.getNotificationChannel(CHANNEL_ID)?.importance != NotificationManager.IMPORTANCE_NONE
            return granted && manager.areNotificationsEnabled() && channelOn
        }

    override fun show(habitId: String, habitName: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val open = PendingIntent.getActivity(
            context,
            habitId.hashCode(),
            Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_OPEN_TAB, MainActivity.TAB_TODAY)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_reminder)
            .setContentTitle("A little nudge from Ritual")
            .setContentText("Time for $habitName")
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        // Tagged by habit: a second reminder for the same habit replaces the first, never stacks.
        NotificationManagerCompat.from(context).notify(habitId, NOTIFICATION_ID, notification)
    }

    override fun clear(habitId: String) = NotificationManagerCompat.from(context).cancel(habitId, NOTIFICATION_ID)

    companion object {
        const val CHANNEL_ID = "habit_reminders"
        const val NOTIFICATION_ID = 5100
    }
}
