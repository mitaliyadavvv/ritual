package app.ritualhabits.data.focus

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.ritualhabits.RitualApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Fired by [AndroidFocusAlarms] at a session's end time. */
class FocusAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val handler = (context.applicationContext as RitualApp).container.focusCompletion
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                handler.onAlarm()
            } finally {
                pending.finish()
            }
        }
    }
}

/** Alarms don't survive a reboot or app update: reconcile and re-arm. */
class FocusBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val handler = (context.applicationContext as RitualApp).container.focusCompletion
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                handler.onBoot()
            } finally {
                pending.finish()
            }
        }
    }
}
