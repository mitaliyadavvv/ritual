package app.ritualhabits.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ritualhabits.ui.theme.Ritual
import app.ritualhabits.ui.theme.Sizes

/**
 * Completion "stamp": a heavy outlined circle that fills, draws its check and lands with a small
 * overshoot. Decorative; the hosting row owns the checkbox semantics. Done vs pending is fill +
 * check mark, never colour alone. Snaps when system animations are off.
 */
@Composable
fun CompletionCheck(checked: Boolean, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    val c = Ritual.colors
    val motion = Ritual.motion
    val fill by animateFloatAsState(if (checked) 1f else 0f, motion.quick(), label = "fill")
    val tick by animateFloatAsState(if (checked) 1f else 0f, motion.standard(), label = "tick")
    val stamp = remember { Animatable(1f) }
    LaunchedEffect(checked) {
        if (checked && !motion.reduced) {
            stamp.snapTo(0.72f)
            stamp.animateTo(1f, motion.playful())
        }
    }

    Canvas(modifier.size(size).scale(stamp.value)) {
        val radius = this.size.minDimension / 2
        val ring = Sizes.stroke.toPx()
        drawCircle(c.textPrimary, radius - ring / 2, style = Stroke(ring))
        if (fill > 0f) drawCircle(c.textPrimary, radius * fill.coerceIn(0f, 1f))
        if (tick > 0f) {
            val s = this.size.width
            val path = Path().apply {
                moveTo(s * 0.28f, s * 0.52f)
                lineTo(s * 0.44f, s * 0.67f)
                lineTo(s * 0.73f, s * 0.36f)
            }
            val measure = PathMeasure().apply { setPath(path, false) }
            val partial = Path()
            measure.getSegment(0f, measure.length * tick.coerceIn(0f, 1f), partial, true)
            drawPath(partial, c.onAccent, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

/**
 * Countable progress: one block per habit, filled left to right as habits are done. When every
 * block is filled the gaps close and the bar becomes one solid slab. Decorative; callers attach
 * progress semantics.
 */
@Composable
fun SegmentedProgress(total: Int, completed: Int, modifier: Modifier = Modifier, height: Dp = 22.dp) {
    if (total <= 0) return
    val c = Ritual.colors
    val motion = Ritual.motion
    val allDone = completed >= total
    val openGap = if (total > 12) 3.dp else 6.dp
    val gap by animateDpAsState(if (allDone) 0.dp else openGap, motion.playful(), label = "gap")
    Row(
        modifier.fillMaxWidth().height(height),
        horizontalArrangement = Arrangement.spacedBy(gap.coerceAtLeast(0.dp)),
    ) {
        repeat(total) { index ->
            val fill by animateFloatAsState(if (index < completed) 1f else 0f, motion.playful(), label = "segment")
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .drawBehind {
                        val stroke = Sizes.stroke.toPx()
                        val f = fill.coerceIn(0f, 1f)
                        if (f < 1f) {
                            drawRect(
                                c.textPrimary,
                                topLeft = Offset(stroke / 2, stroke / 2),
                                size = Size(size.width - stroke, size.height - stroke),
                                style = Stroke(stroke),
                            )
                        }
                        if (f > 0f) drawRect(c.textPrimary, size = Size(size.width * f, size.height))
                    },
            )
        }
    }
}

/**
 * Decorative large text that shrinks (never grows) to fit its width on one line, so oversized
 * numerals stay intact at large font sizes and on narrow screens. Not for reading text.
 */
@Composable
fun FitText(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    minFontSize: TextUnit = 28.sp,
    outlined: Boolean = false,
) {
    val strokePx = with(LocalDensity.current) { 2.5.dp.toPx() }
    val max = style.fontSize
    BasicText(
        text = text,
        modifier = modifier,
        style = style.copy(color = color, drawStyle = if (outlined) Stroke(width = strokePx) else null),
        maxLines = 1,
        softWrap = false,
        autoSize = TextAutoSize.StepBased(minFontSize = minOf(minFontSize, max), maxFontSize = max, stepSize = 2.sp),
    )
}

private fun minOf(a: TextUnit, b: TextUnit): TextUnit = if (a.value <= b.value) a else b
