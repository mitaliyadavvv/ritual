package app.ritualhabits.ui.theme

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

/** Semantic haptic events. Screens ask for meaning, not vibration patterns. */
enum class HapticEvent {
    /** Selection change: tab switch, segmented choice, preset. */
    Selection,
    /** A habit or switch turned on. */
    ToggleOn,
    /** A habit or switch turned off. */
    ToggleOff,
    /** Success: all habits done, focus session finished. */
    Confirm,
    /** Rejected action: validation error. */
    Reject,
}

fun interface HapticPerformer {
    fun perform(event: HapticEvent)
}

/** Drops everything when the user has turned haptics off in Ritual; otherwise hands the event on. */
class RitualHaptics(
    private val enabled: Boolean,
    private val performer: HapticPerformer,
) {
    fun perform(event: HapticEvent) {
        if (!enabled) return
        performer.perform(event)
    }

    companion object {
        val Disabled = RitualHaptics(enabled = false, performer = { })
    }
}

/**
 * Plays haptics through [Vibrator] with the platform's predefined effects (click, tick, heavy
 * click, double click), which device makers implement across the board. View-based haptic
 * constants (CONFIRM, TOGGLE_ON, SEGMENT_TICK, CLOCK_TICK…) are optional for manufacturers and do
 * nothing on many phones.
 *
 * Respects the system: nothing plays without a vibrator or when "Touch feedback" is off, and every
 * vibration is tagged as touch feedback so the phone's touch-vibration intensity applies.
 * Each effect is a single short pulse; nothing repeats.
 */
class SystemHaptics(
    context: Context,
    private val sdkInt: Int = Build.VERSION.SDK_INT,
) : HapticPerformer {
    private val resolver = context.applicationContext.contentResolver
    private val vibrator: Vibrator? = if (sdkInt >= 31) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    override fun perform(event: HapticEvent) {
        val vibrator = vibrator?.takeIf { it.hasVibrator() } ?: return
        if (!touchFeedbackEnabled()) return
        val effect = effectFor(event, vibrator.hasAmplitudeControl())
        runCatching {
            if (sdkInt >= 33) {
                vibrator.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(effect, TOUCH_AUDIO_ATTRIBUTES)
            }
        }
    }

    /** The system "Touch feedback" switch (on unless the user turned it off). */
    private fun touchFeedbackEnabled(): Boolean =
        Settings.System.getInt(resolver, Settings.System.HAPTIC_FEEDBACK_ENABLED, 1) != 0

    internal fun effectFor(event: HapticEvent, amplitudeControl: Boolean): VibrationEffect = if (sdkInt >= 29) {
        VibrationEffect.createPredefined(predefinedFor(event))
    } else {
        // Android 8–9: no predefined effects; short, gentle one-shot pulses instead.
        fun amp(level: Int) = if (amplitudeControl) level else VibrationEffect.DEFAULT_AMPLITUDE
        when (event) {
            HapticEvent.Selection -> VibrationEffect.createOneShot(10, amp(80))
            HapticEvent.ToggleOn -> VibrationEffect.createOneShot(18, amp(160))
            HapticEvent.ToggleOff -> VibrationEffect.createOneShot(12, amp(100))
            HapticEvent.Confirm -> VibrationEffect.createOneShot(28, amp(220))
            HapticEvent.Reject -> VibrationEffect.createWaveform(longArrayOf(0, 18, 70, 18), intArrayOf(0, amp(160), 0, amp(160)), -1)
        }
    }

    companion object {
        internal fun predefinedFor(event: HapticEvent): Int = when (event) {
            HapticEvent.Selection -> VibrationEffect.EFFECT_TICK
            HapticEvent.ToggleOn -> VibrationEffect.EFFECT_CLICK
            HapticEvent.ToggleOff -> VibrationEffect.EFFECT_TICK
            HapticEvent.Confirm -> VibrationEffect.EFFECT_HEAVY_CLICK
            HapticEvent.Reject -> VibrationEffect.EFFECT_DOUBLE_CLICK
        }

        /** Android 8–12: the attributes the system treats as touch feedback. */
        private val TOUCH_AUDIO_ATTRIBUTES: AudioAttributes by lazy {
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        }
    }
}

val LocalRitualHaptics = staticCompositionLocalOf { RitualHaptics.Disabled }

@Composable
internal fun rememberRitualHaptics(enabled: Boolean): RitualHaptics {
    val context = LocalContext.current
    val system = remember(context) { SystemHaptics(context) }
    return remember(enabled, system) { RitualHaptics(enabled, system) }
}
