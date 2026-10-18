package app.ritualhabits.ui.components

import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.ritualhabits.ui.theme.Ritual

/**
 * Ritual's restrained "liquid glass" material, for floating chrome only (the tab bar and its
 * selection bubble).
 *
 * - A translucent neutral [fill]: content behind reads as a soft tonal shift. Only primary-strength
 *   text sits on glass, tested at ≥ 7:1 even over worst-case content behind it.
 * - A luminous rim: a fine outer edge that separates the glass from the page, plus an inner
 *   highlight that is brightest along the top, fades through the middle and returns as a faint
 *   glint at the bottom, the way light catches the edge of a glass capsule.
 * - A very soft shadow (light theme) for lift. Dark theme relies on the rim instead.
 *
 * No backdrop blur: Compose has no supported API for blurring content *behind* a surface without a
 * third-party library, and the material must look right without blur anyway. It renders the same
 * on every Android version and costs no extra GPU passes.
 */
fun Modifier.glassSurface(
    shape: Shape,
    elevation: Dp = 18.dp,
    fill: Color? = null,
    edgeStrength: Float = 1f,
): Modifier = composed {
    val c = Ritual.colors
    val shadowAlpha = if (c.isDark) 0f else 0.12f
    val edge = c.glassEdge.copy(alpha = (c.glassEdge.alpha * edgeStrength).coerceIn(0f, 1f))
    this
        .shadow(
            elevation = if (c.isDark) 0.dp else elevation,
            shape = shape,
            clip = false,
            ambientColor = c.shadow.copy(alpha = shadowAlpha),
            spotColor = c.shadow.copy(alpha = shadowAlpha),
        )
        .clip(shape)
        .background(fill ?: c.glass)
        .drawWithContent {
            drawContent()
            val hairline = 1.dp.toPx()
            // Outer edge: drawn twice as wide because the clip hides its outer half.
            drawOutline(shape.createOutline(size, layoutDirection, this), color = c.glassRim, style = Stroke(hairline * 2))
            // Inner rim highlight, inset by one hairline.
            val inner = Size(size.width - hairline * 2, size.height - hairline * 2)
            translate(hairline, hairline) {
                drawOutline(
                    shape.createOutline(inner, layoutDirection, this),
                    brush = Brush.verticalGradient(
                        0f to edge,
                        0.42f to edge.copy(alpha = 0f),
                        0.75f to edge.copy(alpha = 0f),
                        1f to edge.copy(alpha = edge.alpha * 0.45f),
                    ),
                    style = Stroke(hairline),
                )
            }
        }
}
