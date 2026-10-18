package app.ritualhabits.ui.theme

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Motion guidelines
 * - Quick (120 ms): state feedback — press, toggle, check fill.
 * - Standard (220 ms): content changes — tab cross-fade, expand/collapse.
 * - Emphasized (320 ms): spatial moves — sheets, pushed screens.
 * - Never animate purely for decoration; never exceed 400 ms.
 * - When the system "Remove animations" setting is on (animator scale 0), every spec snaps.
 */
@Immutable
data class RitualMotion(val reduced: Boolean) {
    val standardEasing: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val exitEasing: Easing = CubicBezierEasing(0.3f, 0f, 1f, 1f)

    fun <T> quick(): FiniteAnimationSpec<T> = spec(QUICK_MS, standardEasing)
    fun <T> standard(): FiniteAnimationSpec<T> = spec(STANDARD_MS, standardEasing)
    fun <T> emphasized(): FiniteAnimationSpec<T> = spec(EMPHASIZED_MS, standardEasing)
    fun <T> exit(): FiniteAnimationSpec<T> = spec(QUICK_MS, exitEasing)

    private fun <T> spec(ms: Int, easing: Easing): FiniteAnimationSpec<T> =
        if (reduced) snap() else tween(ms, easing = easing)

    companion object {
        const val QUICK_MS = 120
        const val STANDARD_MS = 220
        const val EMPHASIZED_MS = 320

        fun fromSystem(context: Context): RitualMotion {
            val scale = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
            return RitualMotion(reduced = scale == 0f)
        }
    }
}

val LocalRitualMotion = staticCompositionLocalOf { RitualMotion(reduced = false) }
