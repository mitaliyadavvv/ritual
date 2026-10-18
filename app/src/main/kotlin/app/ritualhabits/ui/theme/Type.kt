package app.ritualhabits.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.ritualhabits.R

/**
 * Outfit, bundled as one variable font (res/font/outfit_variable.ttf, SIL OFL 1.1).
 * Each weight is an instance of the variable "wght" axis. Nothing below Regular is used:
 * the identity comes from heavy display weights against plain reading text.
 */
@OptIn(ExperimentalTextApi::class)
private fun outfit(weight: FontWeight) = Font(
    R.font.outfit_variable,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

val Outfit = FontFamily(
    outfit(FontWeight.Normal),
    outfit(FontWeight.Medium),
    outfit(FontWeight.SemiBold),
    outfit(FontWeight.Bold),
    outfit(FontWeight.ExtraBold),
    outfit(FontWeight.Black),
)

/** Tabular figures so counters and timers don't jitter as digits change. */
private const val TABULAR = "tnum"

/**
 * Type scale. All sizes are sp and scale with the user's font size setting; nothing is capped.
 * Hero/numeral styles are decorative maxima: components may shrink them to fit the space
 * available (see FitText), never below what is readable.
 */
@Immutable
object RitualType {
    /** Giant decorative numerals: today's count. Shrinks to fit. */
    val hero = TextStyle(fontFamily = Outfit, fontWeight = FontWeight.Black, fontSize = 152.sp, lineHeight = 136.sp, letterSpacing = (-0.05).em, fontFeatureSettings = TABULAR)
    /** Large secondary numerals: streak length, analytics. */
    val numeral = TextStyle(fontFamily = Outfit, fontWeight = FontWeight.ExtraBold, fontSize = 72.sp, lineHeight = 68.sp, letterSpacing = (-0.04).em, fontFeatureSettings = TABULAR)
    /** Focus countdown. */
    val display = TextStyle(fontFamily = Outfit, fontWeight = FontWeight.SemiBold, fontSize = 88.sp, lineHeight = 88.sp, letterSpacing = (-0.03).em, fontFeatureSettings = TABULAR)
    /** Screen titles and editorial statements ("Today", "Day complete."). */
    val title = TextStyle(fontFamily = Outfit, fontWeight = FontWeight.Bold, fontSize = 44.sp, lineHeight = 44.sp, letterSpacing = (-0.035).em)
    /** Sheet titles, sub-statements. */
    val heading = TextStyle(fontFamily = Outfit, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 28.sp, letterSpacing = (-0.02).em)
    /** Analytics values in dense modules. */
    val value = TextStyle(fontFamily = Outfit, fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 34.sp, letterSpacing = (-0.03).em, fontFeatureSettings = TABULAR)
    /** Habit names in lists. */
    val rowTitle = TextStyle(fontFamily = Outfit, fontWeight = FontWeight.Medium, fontSize = 20.sp, lineHeight = 25.sp, letterSpacing = (-0.01).em)
    /** Body copy. */
    val body = TextStyle(fontFamily = Outfit, fontWeight = FontWeight.Normal, fontSize = 18.sp, lineHeight = 25.sp)
    /** Supporting lines, descriptions. */
    val bodySmall = TextStyle(fontFamily = Outfit, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 21.sp)
    /** Buttons, tab labels. */
    val label = TextStyle(fontFamily = Outfit, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 20.sp, letterSpacing = 0.005.em)
    /** Small uppercase metadata and section markers ("01 — HABITS"). */
    val overline = TextStyle(fontFamily = Outfit, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.14.em, fontFeatureSettings = TABULAR)
    /** Captions, chart axes. */
    val caption = TextStyle(fontFamily = Outfit, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp, fontFeatureSettings = TABULAR)
}

/** Material slots mapped onto the Ritual scale so stock M3 components inherit Outfit. */
internal val MaterialTypography = Typography(
    displayLarge = RitualType.display,
    headlineLarge = RitualType.title,
    headlineMedium = RitualType.title,
    headlineSmall = RitualType.heading,
    titleLarge = RitualType.heading,
    titleMedium = RitualType.rowTitle,
    titleSmall = RitualType.label,
    bodyLarge = RitualType.body,
    bodyMedium = RitualType.bodySmall,
    bodySmall = RitualType.caption,
    labelLarge = RitualType.label,
    labelMedium = RitualType.overline,
    labelSmall = RitualType.caption,
)
