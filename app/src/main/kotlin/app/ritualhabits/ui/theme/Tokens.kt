package app.ritualhabits.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Monochrome colour tokens: every value is a neutral grey (R == G == B), enforced by tests.
 * Light and dark are designed separately rather than inverted:
 * - Light: white grouped surfaces on a soft neutral page; near-black ink.
 * - Dark: true-black page (OLED), charcoal grouped surfaces, softened white ink.
 * Contrast (WCAG): textPrimary ≥ 7:1 and textSecondary ≥ 4.5:1 on every opaque surface, and
 * secondary text stays ≥ 4.5:1 on the glass bar even over worst-case content behind it.
 */
@Immutable
data class RitualColors(
    /** The page behind everything. */
    val background: Color,
    /** Grouped content surfaces (lists, summary, sheets' fields). */
    val surface: Color,
    /** Selected thumbs and raised elements inside a surface. */
    val surfaceRaised: Color,
    /** Tonal fill for quiet controls: icon buttons, segmented tracks, secondary buttons. */
    val fill: Color,
    /** Floating "glass" surfaces (bottom bar). Mostly opaque so text over it stays legible. */
    val glass: Color,
    /** Delicate top-edge highlight on glass. */
    val glassEdge: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    /** Decorative and disabled only; never for essential text. */
    val textTertiary: Color,
    /** Hairline separators. */
    val border: Color,
    /** Outlines of pending marks and inputs. */
    val borderStrong: Color,
    /** Filled controls and completion marks: near-black in light, near-white in dark. */
    val accent: Color,
    val onAccent: Color,
    /** Secondary text on an accent surface (snackbar actions, etc.). */
    val onAccentSecondary: Color,
    val scrim: Color,
    /** Shadow tint for floating elements. */
    val shadow: Color,
    val isDark: Boolean,
)

val LightColors = RitualColors(
    background = Color(0xFFF4F4F4),
    surface = Color(0xFFFFFFFF),
    surfaceRaised = Color(0xFFFFFFFF),
    fill = Color(0xFFE9E9E9),
    glass = Color(0xEBFFFFFF),
    glassEdge = Color(0xFFFFFFFF),
    textPrimary = Color(0xFF111111),
    textSecondary = Color(0xFF636363),
    textTertiary = Color(0xFFA3A3A3),
    border = Color(0xFFE3E3E3),
    borderStrong = Color(0xFFC2C2C2),
    accent = Color(0xFF111111),
    onAccent = Color(0xFFFFFFFF),
    onAccentSecondary = Color(0xFFADADAD),
    scrim = Color(0x52000000),
    shadow = Color(0xFF000000),
    isDark = false,
)

val DarkColors = RitualColors(
    background = Color(0xFF000000),
    surface = Color(0xFF1B1B1B),
    surfaceRaised = Color(0xFF363636),
    fill = Color(0xFF2B2B2B),
    glass = Color(0xEB1E1E1E),
    glassEdge = Color(0x2EFFFFFF),
    textPrimary = Color(0xFFF4F4F4),
    textSecondary = Color(0xFFA3A3A3),
    textTertiary = Color(0xFF6A6A6A),
    border = Color(0xFF2E2E2E),
    borderStrong = Color(0xFF555555),
    accent = Color(0xFFF4F4F4),
    onAccent = Color(0xFF000000),
    onAccentSecondary = Color(0xFF5C5C5C),
    scrim = Color(0x99000000),
    shadow = Color(0xFF000000),
    isDark = true,
)

/** Spacing scale. Use these instead of literal dp values. */
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
    /** Small controls, segmented thumbs. */
    val sm: Dp = 10.dp
    /** Menus, fields, snackbars. */
    val md: Dp = 14.dp
    /** Grouped surfaces. */
    val lg: Dp = 22.dp
    /** Sheets, dialogs, floating bar. */
    val xl: Dp = 30.dp
}

@Immutable
object Sizes {
    /** Minimum touch target for anything tappable (Material / WCAG 2.5.5). */
    val minTouchTarget: Dp = 48.dp
    val hairline: Dp = 1.dp
    /** Stroke of pending completion rings and outlines. */
    val stroke: Dp = 1.75.dp
    val icon: Dp = 24.dp
    val iconSmall: Dp = 20.dp
    /** Visible diameter of tonal icon buttons (touch target stays 48 dp). */
    val iconButton: Dp = 40.dp
    /** Minimum height of the floating bar; it grows with large font sizes. */
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
            width < 360.dp -> LayoutMetrics(gutter = 16.dp, contentMaxWidth = width, bottomBarMaxWidth = width)
            width < 600.dp -> LayoutMetrics(gutter = 20.dp, contentMaxWidth = width, bottomBarMaxWidth = width)
            width < 840.dp -> LayoutMetrics(gutter = 32.dp, contentMaxWidth = 600.dp, bottomBarMaxWidth = 440.dp)
            else -> LayoutMetrics(gutter = 40.dp, contentMaxWidth = 680.dp, bottomBarMaxWidth = 440.dp)
        }
    }
}

val LocalRitualColors = staticCompositionLocalOf { LightColors }
val LocalLayoutMetrics = staticCompositionLocalOf { LayoutMetrics.forWidth(400.dp) }
