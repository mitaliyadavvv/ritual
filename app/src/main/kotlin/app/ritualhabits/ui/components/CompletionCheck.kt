package app.ritualhabits.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.ritualhabits.ui.theme.Ritual

/**
 * Decorative completion mark: an outlined circle that fills, then draws its check stroke, with a
 * brief scale settle. Purely visual; the row that hosts it owns the toggle semantics.
 * Pending vs done never relies on colour: outline vs filled disc plus a check mark.
 */
@Composable
fun CompletionCheck(checked: Boolean, modifier: Modifier = Modifier, size: Dp = 26.dp) {
    val c = Ritual.colors
    val motion = Ritual.motion
    val fill by animateFloatAsState(if (checked) 1f else 0f, motion.quick(), label = "fill")
    val stroke by animateFloatAsState(if (checked) 1f else 0f, motion.standard(), label = "check")
    val settle = remember { Animatable(1f) }
    LaunchedEffect(checked) {
        if (checked && !motion.reduced) {
            settle.snapTo(0.86f)
            settle.animateTo(1f, motion.standard())
        }
    }

    Canvas(modifier.size(size).scale(settle.value)) {
        val radius = this.size.minDimension / 2
        val ring = 1.5.dp.toPx()
        // Outline ring, faded out as the fill takes over.
        drawCircle(c.borderStrong.copy(alpha = 1f - fill), radius - ring / 2, style = Stroke(ring))
        // Fill grows from the centre.
        if (fill > 0f) drawCircle(c.accent, radius * fill)
        if (stroke > 0f) {
            val s = this.size.width
            val path = Path().apply {
                moveTo(s * 0.29f, s * 0.52f)
                lineTo(s * 0.44f, s * 0.66f)
                lineTo(s * 0.72f, s * 0.36f)
            }
            val measure = PathMeasure().apply { setPath(path, false) }
            val partial = Path()
            measure.getSegment(0f, measure.length * stroke, partial, true)
            drawPath(partial, c.onAccent, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

/** Thin daily progress track; the fill animates its width (snaps when animations are off). */
@Composable
fun ProgressTrack(progress: Float, modifier: Modifier = Modifier) {
    val c = Ritual.colors
    val animated by animateFloatAsState(progress.coerceIn(0f, 1f), Ritual.motion.standard(), label = "progress")
    Box(
        modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(CircleShape)
            .background(c.border),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(4.dp)
                .layout { measurable, constraints ->
                    val width = (constraints.maxWidth * animated).toInt()
                    val placeable = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
                    layout(constraints.maxWidth, placeable.height) { placeable.place(0, 0) }
                }
                .clip(CircleShape)
                .background(c.accent),
        )
    }
}

