package app.ritualhabits.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.ritualhabits.R

/**
 * Outfit, bundled as one variable font (res/font/outfit_variable.ttf, SIL OFL 1.1, licence in
 * assets/licenses). Each weight is an instance of the "wght" axis. Regular is the lightest weight
 * used anywhere.
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
)

/** Tabular figures so counters and timers don't shift as digits change. */
private const val TABULAR = "tnum"

/** Trims the extra space Outfit adds above caps, so text aligns optically with icons. */
private val Trimmed = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both)

private fun style(size: Int, line: Int, weight: FontWeight, tracking: Double = 0.0, tabular: Boolean = false) = TextStyle(
    fontFamily = Outfit,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = tracking.em,
    fontFeatureSettings = if (tabular) TABULAR else null,
    lineHeightStyle = Trimmed,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

/**
 * Type scale. Every size is sp and follows the user's font-size setting with no caps.
 *
 * | Role        | Size / weight     | Use                                  |
 * |-------------|-------------------|--------------------------------------|
 * | display     | 64 Medium, tnum   | focus countdown                      |
 * | title       | 30 SemiBold       | screen titles                        |
 * | numeral     | 28 SemiBold, tnum | summary counts, streak               |
 * | heading     | 20 SemiBold       | section titles, sheet titles         |
 * | value       | 17 SemiBold, tnum | inline numbers                       |
 * | rowTitle    | 17 Regular        | habit names                          |
 * | body        | 16 Regular        | copy                                 |
 * | bodySmall   | 15 Regular        | descriptions, supporting text        |
 * | label       | 16 SemiBold       | buttons                              |
 * | overline    | 14 Medium         | eyebrow (date), small section labels |
 * | caption     | 13 Regular, tnum  | metadata, tab labels, axes           |
 */
@Immutable
object RitualType {
    val display = style(64, 70, FontWeight.Medium, tracking = -0.02, tabular = true)
    val title = style(30, 36, FontWeight.SemiBold, tracking = -0.015)
    val numeral = style(28, 32, FontWeight.SemiBold, tracking = -0.01, tabular = true)
    val heading = style(20, 26, FontWeight.SemiBold, tracking = -0.005)
    val value = style(17, 22, FontWeight.SemiBold, tabular = true)
    val rowTitle = style(17, 23, FontWeight.Normal)
    val body = style(16, 23, FontWeight.Normal)
    val bodySmall = style(15, 21, FontWeight.Normal)
    val label = style(16, 21, FontWeight.SemiBold)
    val overline = style(14, 19, FontWeight.Medium)
    val caption = style(13, 18, FontWeight.Normal, tabular = true)
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
