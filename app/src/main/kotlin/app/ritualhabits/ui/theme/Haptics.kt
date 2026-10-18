package app.ritualhabits.ui.theme

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalView

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
    fun perform(constant: Int)
}

/**
 * Maps events to platform constants, newest first with fallbacks, and drops everything
 * when the user has turned haptics off in Ritual. System haptic settings are respected
 * by [View.performHapticFeedback] itself.
 */
class RitualHaptics(
    private val enabled: Boolean,
    private val performer: HapticPerformer,
    private val sdkInt: Int = Build.VERSION.SDK_INT,
) {
    fun perform(event: HapticEvent) {
        if (!enabled) return
        performer.perform(constantFor(event))
    }

    internal fun constantFor(event: HapticEvent): Int = when (event) {
        HapticEvent.Selection ->
            if (sdkInt >= 34) HapticFeedbackConstants.SEGMENT_TICK else HapticFeedbackConstants.CLOCK_TICK
        HapticEvent.ToggleOn ->
            if (sdkInt >= 34) HapticFeedbackConstants.TOGGLE_ON else HapticFeedbackConstants.VIRTUAL_KEY
        HapticEvent.ToggleOff ->
            if (sdkInt >= 34) HapticFeedbackConstants.TOGGLE_OFF else HapticFeedbackConstants.CLOCK_TICK
        HapticEvent.Confirm ->
            if (sdkInt >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY
        HapticEvent.Reject ->
            if (sdkInt >= 30) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS
    }

    companion object {
        val Disabled = RitualHaptics(enabled = false, performer = { })
    }
}

val LocalRitualHaptics = staticCompositionLocalOf { RitualHaptics.Disabled }

@Composable
internal fun rememberRitualHaptics(enabled: Boolean): RitualHaptics {
    val view: View = LocalView.current
    return remember(enabled, view) { RitualHaptics(enabled, { view.performHapticFeedback(it) }) }
}
