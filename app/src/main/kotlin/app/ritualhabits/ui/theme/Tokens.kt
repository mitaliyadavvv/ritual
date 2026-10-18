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
    val scrim: Color,
    val isDark: Boolean,
)

val LightColors = RitualColors(
    background = Color(0xFFFAFAFA),
    surface = Color(0xFFFFFFFF),
    surfaceRaised = Color(0xFFF2F2F2),
    glass = Color(0xEBFFFFFF),
    textPrimary = Color(0xFF0A0A0A),
    textSecondary = Color(0xFF616161),
    textTertiary = Color(0xFF8F8F8F),
    border = Color(0xFFE5E5E5),
    borderStrong = Color(0xFFC7C7C7),
    accent = Color(0xFF0A0A0A),
    onAccent = Color(0xFFFAFAFA),
    scrim = Color(0x66000000),
    isDark = false,
)

val DarkColors = RitualColors(
    background = Color(0xFF0A0A0A),
    surface = Color(0xFF141414),
    surfaceRaised = Color(0xFF1F1F1F),
    glass = Color(0xE6141414),
    textPrimary = Color(0xFFF5F5F5),
    textSecondary = Color(0xFFA3A3A3),
    textTertiary = Color(0xFF6E6E6E),
    border = Color(0xFF262626),
    borderStrong = Color(0xFF3D3D3D),
    accent = Color(0xFFF5F5F5),
    onAccent = Color(0xFF0A0A0A),
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
    val sm: Dp = 8.dp
    val md: Dp = 14.dp
    val lg: Dp = 20.dp
    val xl: Dp = 28.dp
}

@Immutable
object Sizes {
    /** Minimum touch target for anything tappable (Material / WCAG 2.5.5). */
    val minTouchTarget: Dp = 48.dp
    val hairline: Dp = 1.dp
    val icon: Dp = 22.dp
    val iconSmall: Dp = 18.dp
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
