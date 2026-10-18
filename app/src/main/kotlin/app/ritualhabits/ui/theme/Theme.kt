package app.ritualhabits.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import app.ritualhabits.core.model.ThemeMode

/** Pure so it can be unit-tested: which palette does a preference resolve to? */
fun ThemeMode.isDark(systemIsDark: Boolean): Boolean = when (this) {
    ThemeMode.SYSTEM -> systemIsDark
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun RitualTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    hapticsEnabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colors = if (themeMode.isDark(isSystemInDarkTheme())) DarkColors else LightColors
    val context = LocalContext.current
    val motion = remember(context) { RitualMotion.fromSystem(context) }

    MaterialTheme(
        colorScheme = colors.toMaterial(),
        typography = MaterialTypography,
        shapes = RitualShapes,
    ) {
        BoxWithConstraints {
            CompositionLocalProvider(
                LocalRitualColors provides colors,
                LocalRitualMotion provides motion,
                LocalRitualHaptics provides rememberRitualHaptics(hapticsEnabled),
                LocalLayoutMetrics provides LayoutMetrics.forWidth(maxWidth),
                content = content,
            )
        }
    }
}

/** Shortcut accessors: `Ritual.colors.textPrimary`, `Ritual.haptics.perform(…)`. */
object Ritual {
    val colors: RitualColors @Composable get() = LocalRitualColors.current
    val motion: RitualMotion @Composable get() = LocalRitualMotion.current
    val haptics: RitualHaptics @Composable get() = LocalRitualHaptics.current
    val layout: LayoutMetrics @Composable get() = LocalLayoutMetrics.current
}

private val RitualShapes = Shapes(
    extraSmall = RoundedCornerShape(Radii.sm),
    small = RoundedCornerShape(Radii.sm),
    medium = RoundedCornerShape(Radii.md),
    large = RoundedCornerShape(Radii.lg),
    extraLarge = RoundedCornerShape(Radii.xl),
)

/**
 * Every Material role is mapped to a neutral token, so no stock component can introduce colour.
 * Errors stay monochrome; they are communicated with text and icons, never colour alone.
 */
private fun RitualColors.toMaterial(): ColorScheme {
    val base = if (isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = accent, onPrimary = onAccent,
        primaryContainer = fill, onPrimaryContainer = textPrimary,
        inversePrimary = onAccent,
        secondary = textSecondary, onSecondary = onAccent,
        secondaryContainer = surfaceRaised, onSecondaryContainer = textPrimary,
        tertiary = textSecondary, onTertiary = onAccent,
        tertiaryContainer = surfaceRaised, onTertiaryContainer = textPrimary,
        background = background, onBackground = textPrimary,
        surface = surface, onSurface = textPrimary,
        surfaceVariant = surfaceRaised, onSurfaceVariant = textSecondary,
        surfaceTint = background,
        surfaceBright = surface, surfaceDim = background,
        surfaceContainerLowest = surface, surfaceContainerLow = surface,
        surfaceContainer = surface, surfaceContainerHigh = surface,
        surfaceContainerHighest = fill,
        inverseSurface = accent, inverseOnSurface = onAccent,
        outline = borderStrong, outlineVariant = border,
        error = textPrimary, onError = onAccent,
        errorContainer = surfaceRaised, onErrorContainer = textPrimary,
        scrim = scrim,
    )
}
