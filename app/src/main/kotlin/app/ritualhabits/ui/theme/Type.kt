package app.ritualhabits.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * Single typeface for the whole app. System sans-serif (Roboto / Google Sans on recent Pixels):
 * offline, highly legible, respects user font scaling. Swap here to bundle a font later.
 */
private val Sans = FontFamily.SansSerif

/** Tabular figures so counters and timers don't jitter as digits change. */
private const val TABULAR = "tnum"

@Immutable
object RitualType {
    /** Focus countdown. */
    val display = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Light, fontSize = 72.sp, lineHeight = 76.sp, letterSpacing = (-0.02).em, fontFeatureSettings = TABULAR)
    /** Screen titles: "Today", "Progress". */
    val title = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 30.sp, lineHeight = 36.sp, letterSpacing = (-0.02).em)
    /** Section and sheet headings. */
    val heading = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 22.sp, letterSpacing = (-0.01).em)
    /** Primary values in analytics modules (streak counts, percentages). */
    val value = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 28.sp, lineHeight = 32.sp, letterSpacing = (-0.02).em, fontFeatureSettings = TABULAR)
    /** List rows, body copy. */
    val body = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 22.sp)
    /** Secondary lines, descriptions. */
    val bodySmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp)
    /** Buttons, tab labels. */
    val label = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 18.sp, letterSpacing = 0.01.em)
    /** Overlines and section labels: small caps-style metadata ("SATURDAY, 18 OCTOBER"). */
    val overline = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.08.em)
    /** Chart axes, captions. */
    val caption = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp, fontFeatureSettings = TABULAR)
}

/** Material slots mapped onto the Ritual scale so stock M3 components inherit it. */
internal val MaterialTypography = Typography(
    displayLarge = RitualType.display,
    headlineLarge = RitualType.title,
    headlineMedium = RitualType.title,
    headlineSmall = RitualType.heading,
    titleLarge = RitualType.heading,
    titleMedium = RitualType.body.copy(fontWeight = FontWeight.Medium),
    titleSmall = RitualType.label,
    bodyLarge = RitualType.body,
    bodyMedium = RitualType.bodySmall,
    bodySmall = RitualType.caption,
    labelLarge = RitualType.label,
    labelMedium = RitualType.overline,
    labelSmall = RitualType.caption,
)
