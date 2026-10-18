package app.ritualhabits.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.ritualhabits.ui.theme.Ritual
import app.ritualhabits.ui.theme.RitualType
import app.ritualhabits.ui.theme.Sizes
import app.ritualhabits.ui.theme.Spacing

/**
 * Completion mark: a light ring that fills from the centre and draws its tick, with a near
 * bounce-free settle. [emphasis] (the day's final habit) gets a slightly livelier settle.
 * Decorative: the hosting row owns the checkbox semantics. Done vs pending is fill + tick, never
 * colour alone. Snaps when system animations are off.
 */
@Composable
fun CompletionCheck(checked: Boolean, modifier: Modifier = Modifier, size: Dp = 28.dp, emphasis: Boolean = false) {
    val c = Ritual.colors
    val motion = Ritual.motion
    val fill by animateFloatAsState(if (checked) 1f else 0f, motion.quick(), label = "fill")
    val tick by animateFloatAsState(if (checked) 1f else 0f, motion.standard(), label = "tick")
    val settle = remember { Animatable(1f) }
    LaunchedEffect(checked) {
        if (checked && !motion.reduced) {
            settle.snapTo(if (emphasis) 0.78f else 0.86f)
            settle.animateTo(1f, if (emphasis) motion.emphasis() else motion.settle())
        }
    }

    Canvas(modifier.size(size).scale(settle.value)) {
        val radius = this.size.minDimension / 2
        val ring = Sizes.stroke.toPx()
        if (fill < 1f) drawCircle(c.borderStrong, radius - ring / 2, style = Stroke(ring))
        if (fill > 0f) drawCircle(c.accent, radius * fill.coerceIn(0f, 1f))
        if (tick > 0f) {
            val s = this.size.width
            val path = Path().apply {
                moveTo(s * 0.30f, s * 0.52f)
                lineTo(s * 0.44f, s * 0.65f)
                lineTo(s * 0.71f, s * 0.37f)
            }
            val measure = PathMeasure().apply { setPath(path, false) }
            val partial = Path()
            measure.getSegment(0f, measure.length * tick.coerceIn(0f, 1f), partial, true)
            drawPath(partial, c.onAccent, style = Stroke(s * 0.085f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

/**
 * Monochrome progress ring with rounded ends: a tonal track and an ink arc that eases to its new
 * value. [content] is centred inside. Decorative; callers provide progress semantics.
 */
@Composable
fun ProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    strokeWidth: Dp = 7.dp,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val c = Ritual.colors
    val animated by animateFloatAsState(progress.coerceIn(0f, 1f), Ritual.motion.settle(), label = "ring")
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = strokeWidth.toPx()
            val inset = stroke / 2
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            drawArc(c.fill, 0f, 360f, useCenter = false, topLeft = Offset(inset, inset), size = arcSize, style = Stroke(stroke))
            val sweep = 360f * animated.coerceIn(0f, 1f)
            if (sweep > 0f) {
                drawArc(c.accent, -90f, sweep, useCenter = false, topLeft = Offset(inset, inset), size = arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            }
        }
        content()
    }
}

enum class DotState {
    /** Day counted (all required habits done). */
    FILLED,
    /** Habits were due and not all done. */
    MISSED,
    /** Today, still open. */
    TODAY,
    /** Nothing was due. */
    EMPTY,
}

data class DotDay(val label: String, val state: DotState, val isToday: Boolean)

/** Seven quiet dots under day initials: filled = counted, ring = missed, dark ring = today, tiny dot = nothing due. */
@Composable
fun WeekDots(days: List<DotDay>, modifier: Modifier = Modifier) {
    val c = Ritual.colors
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        days.forEach { day ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    day.label,
                    style = RitualType.caption.copy(fontWeight = if (day.isToday) FontWeight.SemiBold else FontWeight.Normal),
                    color = if (day.isToday) c.textPrimary else c.textSecondary,
                )
                Spacer(Modifier.height(Spacing.sm))
                Canvas(Modifier.size(12.dp)) {
                    val r = size.minDimension / 2
                    val stroke = 1.5.dp.toPx()
                    when (day.state) {
                        DotState.FILLED -> drawCircle(c.accent, r)
                        DotState.MISSED -> drawCircle(c.borderStrong, r - stroke / 2, style = Stroke(stroke))
                        DotState.TODAY -> drawCircle(c.accent, r - stroke / 2, style = Stroke(stroke))
                        DotState.EMPTY -> drawCircle(c.border, r * 0.35f)
                    }
                }
            }
        }
    }
}

/**
 * Focus timer dial: a thin ring with the time set in the display face. Used by the Focus screen;
 * establishes how the countdown will look once timed sessions are built.
 */
@Composable
fun TimerDial(progress: Float, time: String, modifier: Modifier = Modifier, caption: String? = null, size: Dp = 260.dp) {
    val c = Ritual.colors
    ProgressRing(progress, modifier, size = size, strokeWidth = 6.dp) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(time, style = RitualType.display, color = c.textPrimary, maxLines = 1)
            if (caption != null) {
                Spacer(Modifier.height(Spacing.xs))
                Text(caption, style = RitualType.bodySmall, color = c.textSecondary)
            }
        }
    }
}
