package app.ritualhabits.data.day

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import app.ritualhabits.core.time.AppClock
import app.ritualhabits.core.time.HabitDay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlin.time.Duration.Companion.milliseconds

/**
 * Publishes the current habit day. Nothing is written at midnight; screens simply re-render
 * for the new date. Refreshes on foreground, on system date/time/zone changes, and at the
 * next local midnight while the app is visible.
 */
class HabitDayTracker(
    private val context: Context,
    private val clock: AppClock,
    private val scope: CoroutineScope,
) : DefaultLifecycleObserver {

    private val _today = MutableStateFlow(clock.today())
    val today: StateFlow<LocalDate> = _today.asStateFlow()

    private var ticker: Job? = null
    private var receiverRegistered = false

    private val timeChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            refresh()
            restartTicker()
        }
    }

    /** Call once from Application.onCreate (main thread). */
    fun install() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    fun refresh() {
        _today.value = clock.today()
    }

    override fun onStart(owner: LifecycleOwner) {
        refresh()
        if (!receiverRegistered) {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_DATE_CHANGED)
                addAction(Intent.ACTION_TIME_CHANGED)
                addAction(Intent.ACTION_TIMEZONE_CHANGED)
            }
            // System broadcasts are still delivered to non-exported receivers.
            ContextCompat.registerReceiver(context, timeChangeReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
            receiverRegistered = true
        }
        restartTicker()
    }

    override fun onStop(owner: LifecycleOwner) {
        ticker?.cancel()
        if (receiverRegistered) {
            context.unregisterReceiver(timeChangeReceiver)
            receiverRegistered = false
        }
    }

    private fun restartTicker() {
        ticker?.cancel()
        ticker = scope.launch {
            while (isActive) {
                val now = clock.now()
                delay(HabitDay.nextBoundary(now, clock.zone()) - now + 250.milliseconds)
                refresh()
            }
        }
    }
}
