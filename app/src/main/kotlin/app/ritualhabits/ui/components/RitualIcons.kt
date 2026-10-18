package app.ritualhabits.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Hand-drawn 24×24 line icons in one consistent style (1.6 stroke, round caps).
 * Tinted at draw time, so they follow the theme.
 */
object RitualIcons {
    val Today: ImageVector by lazy { icon("today") { circle(12f, 12f, 8.5f); moveTo(8.4f, 12.2f); lineTo(10.9f, 14.6f); lineTo(15.7f, 9.6f) } }
    val Progress: ImageVector by lazy {
        icon("progress") {
            moveTo(6f, 19f); lineTo(6f, 13f)
            moveTo(12f, 19f); lineTo(12f, 6f)
            moveTo(18f, 19f); lineTo(18f, 10f)
        }
    }
    val Focus: ImageVector by lazy {
        icon("focus") {
            circle(12f, 13.5f, 7.5f)
            moveTo(12f, 13.5f); lineTo(12f, 9.5f)
            moveTo(10f, 3.5f); lineTo(14f, 3.5f)
            moveTo(18.2f, 6.6f); lineTo(19.4f, 5.4f)
        }
    }
    val Manage: ImageVector by lazy {
        icon("manage") {
            moveTo(9f, 7f); lineTo(20f, 7f)
            moveTo(9f, 12f); lineTo(20f, 12f)
            moveTo(9f, 17f); lineTo(20f, 17f)
            circle(4.75f, 7f, 0.75f); circle(4.75f, 12f, 0.75f); circle(4.75f, 17f, 0.75f)
        }
    }
    /** Settings / profile entry point: two sliders. */
    val Settings: ImageVector by lazy {
        icon("settings") {
            moveTo(4f, 8f); lineTo(20f, 8f)
            moveTo(4f, 16f); lineTo(20f, 16f)
            circle(9f, 8f, 2.25f)
            circle(15f, 16f, 2.25f)
        }
    }
    val Back: ImageVector by lazy { icon("back") { moveTo(15f, 5f); lineTo(8f, 12f); lineTo(15f, 19f) } }
    val Add: ImageVector by lazy { icon("add") { moveTo(12f, 5f); lineTo(12f, 19f); moveTo(5f, 12f); lineTo(19f, 12f) } }
    val More: ImageVector by lazy { icon("more") { circle(5.5f, 12f, 0.9f); circle(12f, 12f, 0.9f); circle(18.5f, 12f, 0.9f) } }
    val ArrowUp: ImageVector by lazy { icon("arrow_up") { moveTo(12f, 19f); lineTo(12f, 5f); moveTo(6f, 11f); lineTo(12f, 5f); lineTo(18f, 11f) } }
    val ArrowDown: ImageVector by lazy { icon("arrow_down") { moveTo(12f, 5f); lineTo(12f, 19f); moveTo(6f, 13f); lineTo(12f, 19f); lineTo(18f, 13f) } }
    val Check: ImageVector by lazy { icon("check") { moveTo(5.5f, 12.5f); lineTo(10f, 17f); lineTo(18.5f, 7.5f) } }

    private fun icon(name: String, block: PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.6f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                pathBuilder = block,
            )
        }.build()
}

private fun PathBuilder.circle(cx: Float, cy: Float, r: Float) {
    moveTo(cx - r, cy)
    arcToRelative(r, r, 0f, true, true, r * 2, 0f)
    arcToRelative(r, r, 0f, true, true, -r * 2, 0f)
    close()
}
