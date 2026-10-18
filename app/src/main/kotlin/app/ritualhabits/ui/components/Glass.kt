package app.ritualhabits.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.ritualhabits.ui.theme.Ritual
import app.ritualhabits.ui.theme.Sizes

/**
 * Ritual's restrained "liquid glass" material, for floating chrome only (the tab bar).
 *
 * - A neutral surface at ~92 % opacity: content scrolling beneath reads as a soft tonal shift, while
 *   labels on the glass keep full contrast (tested against worst-case content behind it).
 * - A delicate edge: a light top highlight that fades out, plus a faint lower hairline, which gives
 *   the surface its lifted, polished rim in both themes.
 * - A very soft, wide shadow (light theme) for separation from the page.
 *
 * No backdrop blur: Compose has no supported API for blurring content *behind* a surface without a
 * third-party library, and the design must look right without blur anyway. Everything here renders
 * the same on every Android version and costs no extra GPU passes.
 */
fun Modifier.glassSurface(shape: Shape, elevation: Dp = 18.dp): Modifier = composed {
    val c = Ritual.colors
    val shadowAlpha = if (c.isDark) 0.0f else 0.12f
    this
        .shadow(
            elevation = if (c.isDark) 0.dp else elevation,
            shape = shape,
            clip = false,
            ambientColor = c.shadow.copy(alpha = shadowAlpha),
            spotColor = c.shadow.copy(alpha = shadowAlpha),
        )
        .clip(shape)
        .background(c.glass)
        .border(
            width = Sizes.hairline,
            brush = Brush.verticalGradient(
                0f to c.glassEdge,
                0.45f to c.glassEdge.copy(alpha = 0f),
                1f to c.border.copy(alpha = if (c.isDark) 0.9f else 0.7f),
            ),
            shape = shape,
        )
}
