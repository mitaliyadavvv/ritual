package app.ritualhabits.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Monochrome colour tokens. Only neutral greys: every value has R == G == B.
 * Contrast (WCAG): textPrimary ≥ 15:1, textSecondary ≥ 4.5:1 on background and surface in both themes.
 * textTertiary is for non-essential text and disabled states only (≥ 3:1).
 */
@Immutable
data class RitualColors(
    val background: Color,
    val surface: Color,
    val surfaceRaised: Color,
    /** Translucent fill for floating chrome (bottom bar). Opaque enough that text behind never competes. */
    val glass: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val border: Color,
    val borderStrong: Color,
    /** Filled controls: black in light, white in dark. */
    val accent: Color,
    val onAccent: Color,
    /** Secondary text on an accent (inverted) surface, e.g. unselected tabs, captions in the streak block. */
    val onAccentSecondary: Color,
    val scrim: Color,
    val isDark: Boolean,
)

val LightColors = RitualColors(
    background = Color(0xFFF3F3F3),
    surface = Color(0xFFFFFFFF),
    surfaceRaised = Color(0xFFE6E6E6),
    glass = Color(0xEBFFFFFF),
    textPrimary = Color(0xFF0A0A0A),
    textSecondary = Color(0xFF575757),
    textTertiary = Color(0xFF8A8A8A),
    border = Color(0xFFDADADA),
    borderStrong = Color(0xFFABABAB),
    accent = Color(0xFF0A0A0A),
    onAccent = Color(0xFFF3F3F3),
    onAccentSecondary = Color(0xFFA8A8A8),
    scrim = Color(0x66000000),
    isDark = false,
)

val DarkColors = RitualColors(
    background = Color(0xFF0B0B0B),
    surface = Color(0xFF161616),
    surfaceRaised = Color(0xFF242424),
    glass = Color(0xE6161616),
    textPrimary = Color(0xFFF3F3F3),
    textSecondary = Color(0xFFA8A8A8),
    textTertiary = Color(0xFF6E6E6E),
    border = Color(0xFF2A2A2A),
    borderStrong = Color(0xFF555555),
    accent = Color(0xFFF3F3F3),
    onAccent = Color(0xFF0B0B0B),
    onAccentSecondary = Color(0xFF575757),
    scrim = Color(0x99000000),
    isDark = true,
)

/** 4-pt spacing scale. Use these instead of literal dp values. */
@Immutable
object Spacing {
    val xxs: Dp = 2.dp
    val xs: Dp = 4.dp
    val sm: Dp = 8.dp
    val md: Dp = 12.dp
    val lg: Dp = 16.dp
    val xl: Dp = 24.dp
    val xxl: Dp = 32.dp
    val xxxl: Dp = 48.dp
}

@Immutable
object Radii {
    val sm: Dp = 10.dp
    val md: Dp = 16.dp
    val lg: Dp = 24.dp
    val xl: Dp = 32.dp
}

@Immutable
object Sizes {
    /** Minimum touch target for anything tappable (Material / WCAG 2.5.5). */
    val minTouchTarget: Dp = 48.dp
    val hairline: Dp = 1.dp
    /** Heavy editorial rule that opens a section. */
    val rule: Dp = 2.dp
    /** Stroke for outlined graphic marks (pending checks, empty segments). */
    val stroke: Dp = 2.dp
    val icon: Dp = 24.dp
    val iconSmall: Dp = 20.dp
    /** Minimum height; the bar grows if its labels need more room. */
    val bottomBarHeight: Dp = 64.dp
}

/** Responsive layout tokens derived from the available width. */
@Immutable
data class LayoutMetrics(
    val gutter: Dp,
    val contentMaxWidth: Dp,
    val bottomBarMaxWidth: Dp,
) {
    companion object {
        fun forWidth(width: Dp): LayoutMetrics = when {
            width < 600.dp -> LayoutMetrics(gutter = 20.dp, contentMaxWidth = width, bottomBarMaxWidth = width)
            width < 840.dp -> LayoutMetrics(gutter = 32.dp, contentMaxWidth = 600.dp, bottomBarMaxWidth = 440.dp)
            else -> LayoutMetrics(gutter = 40.dp, contentMaxWidth = 680.dp, bottomBarMaxWidth = 440.dp)
        }
    }
}

val LocalRitualColors = staticCompositionLocalOf { LightColors }
val LocalLayoutMetrics = staticCompositionLocalOf { LayoutMetrics.forWidth(400.dp) }
