package app.ritualhabits.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ritualhabits.core.rules.CalendarMonth
import app.ritualhabits.ui.theme.Ritual
import app.ritualhabits.ui.theme.RitualType
import app.ritualhabits.ui.theme.Spacing
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/** Visual treatment of one calendar cell. Each is distinguishable by shape/fill, not shade alone. */
enum class CellStyle {
    /** Solid ink: complete / done. */
    FILLED,
    /** Mid-grey fill: partially complete. */
    HALF,
    /** Outline only: due but missed (or today, still open). */
    OUTLINED,
    /** Faint tonal fill, muted number: nothing was due / habit not due. */
    FAINT,
    /** No fill, no outline, muted number: hasn't happened yet. */
    UPCOMING,
}

private val CellGap = 6.dp

/**
 * A month as a seven-column calendar: weekday initials, blanks so day 1 sits under its weekday, then
 * one cell per day (28–31). Cells keep a square aspect and simply get smaller on narrow screens;
 * nothing scrolls horizontally.
 */
@Composable
fun CalendarGrid(
    month: CalendarMonth,
    firstDayOfWeek: DayOfWeek,
    modifier: Modifier = Modifier,
    showWeekdays: Boolean = true,
    cell: @Composable (LocalDate) -> Unit,
) {
    val locale = Locale.getDefault()
    val weekdays = remember(firstDayOfWeek) { (0 until 7).map { firstDayOfWeek.plusDays(it) } }
    Column(modifier.fillMaxWidth()) {
        if (showWeekdays) {
            Row(Modifier.fillMaxWidth().clearAndSetSemantics { }, horizontalArrangement = Arrangement.spacedBy(CellGap)) {
                weekdays.forEach { day ->
                    Text(
                        day.getDisplayName(TextStyle.NARROW, locale),
                        style = RitualType.caption,
                        color = Ritual.colors.textSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Spacer(Modifier.height(Spacing.sm))
        }
        val blanks = month.leadingBlanks(firstDayOfWeek)
        val slots: List<LocalDate?> = List(blanks) { null } + month.days
        slots.chunked(7).forEachIndexed { index, week ->
            if (index > 0) Spacer(Modifier.height(CellGap))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(CellGap)) {
                week.forEach { date ->
                    Box(Modifier.weight(1f).aspectRatio(1f)) { if (date != null) cell(date) }
                }
                repeat(7 - week.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}

private fun DayOfWeek.plusDays(n: Int): DayOfWeek = DayOfWeek.values()[(ordinal + n) % 7]

/**
 * One day square. [description] is the full spoken label (the visible number is decorative and
 * shrinks to fit at large font sizes). [onClick] null = not interactive (future days).
 */
@Composable
fun CalendarCell(
    day: Int,
    style: CellStyle,
    isToday: Boolean,
    description: String,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    showNumber: Boolean = true,
) {
    val c = Ritual.colors
    val shape = RoundedCornerShape(24)
    val (fill, numberColor) = when (style) {
        CellStyle.FILLED -> c.accent to c.onAccent
        CellStyle.HALF -> c.borderStrong to c.textPrimary
        CellStyle.OUTLINED -> Color.Transparent to c.textPrimary
        CellStyle.FAINT -> c.fill to c.textTertiary
        CellStyle.UPCOMING -> Color.Transparent to c.textTertiary
    }
    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .then(if (isToday) Modifier.border(2.dp, c.accent, RoundedCornerShape(26)).padding(3.dp) else Modifier)
            .clip(shape)
            .background(fill)
            .then(if (style == CellStyle.OUTLINED) Modifier.border(1.5.dp, c.borderStrong, shape) else Modifier)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(color = c.textPrimary),
                        onClick = onClick,
                    )
                } else {
                    Modifier
                },
            )
            .clearAndSetSemantics {
                contentDescription = description
                if (onClick != null) {
                    role = Role.Button
                    onClick(label = "Show day") { onClick(); true }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        if (showNumber) {
            BasicText(
                day.toString(),
                style = RitualType.caption.copy(
                    color = numberColor,
                    fontWeight = if (isToday) FontWeight.SemiBold else FontWeight.Normal,
                    textAlign = TextAlign.Center,
                ),
                maxLines = 1,
                softWrap = false,
                autoSize = TextAutoSize.StepBased(minFontSize = 7.sp, maxFontSize = RitualType.caption.fontSize, stepSize = 1.sp),
                modifier = Modifier.padding(2.dp),
            )
        }
    }
}

/** One legend entry: a miniature cell and its meaning. */
@Composable
fun LegendItem(style: CellStyle, label: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(14.dp)) {
            CalendarCell(day = 0, style = style, isToday = false, description = "", onClick = null, showNumber = false)
        }
        Spacer(Modifier.size(Spacing.xs + 2.dp))
        Text(label, style = RitualType.caption, color = Ritual.colors.textSecondary)
    }
}
