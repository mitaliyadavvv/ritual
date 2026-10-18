package app.ritualhabits.ui.progress

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.ritualhabits.core.rules.CalendarMonth
import app.ritualhabits.core.rules.DayStatus
import app.ritualhabits.core.rules.HabitDayStatus
import app.ritualhabits.ui.RitualViewModels
import app.ritualhabits.ui.components.CalendarCell
import app.ritualhabits.ui.components.CalendarGrid
import app.ritualhabits.ui.components.CellStyle
import app.ritualhabits.ui.components.CompletionCheck
import app.ritualhabits.ui.components.DelayedLoadingIndicator
import app.ritualhabits.ui.components.EmptyState
import app.ritualhabits.ui.components.Hairline
import app.ritualhabits.ui.components.LegendItem
import app.ritualhabits.ui.components.Module
import app.ritualhabits.ui.components.PrimaryButton
import app.ritualhabits.ui.components.RitualIconButton
import app.ritualhabits.ui.components.RitualIcons
import app.ritualhabits.ui.components.ScreenColumn
import app.ritualhabits.ui.components.ScreenHeader
import app.ritualhabits.ui.components.SecondaryButton
import app.ritualhabits.ui.components.SegmentedChoice
import app.ritualhabits.ui.navigation.SettingsButton
import app.ritualhabits.ui.theme.Ritual
import app.ritualhabits.ui.theme.RitualType
import app.ritualhabits.ui.theme.Spacing
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toJavaLocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields
import java.util.Locale
import kotlin.math.roundToInt

// --- Copy (presentation only) -----------------------------------------------------------------

fun monthTitle(month: CalendarMonth, locale: Locale = Locale.getDefault()): String =
    DateTimeFormatter.ofPattern("LLLL yyyy", locale).format(month.firstDay.toJavaLocalDate())

fun dayTitle(date: LocalDate, locale: Locale = Locale.getDefault()): String =
    DateTimeFormatter.ofPattern("EEEE, d MMMM", locale).format(date.toJavaLocalDate())

fun dayStatusText(status: DayStatus, required: Int, completed: Int): String = when (status) {
    DayStatus.COMPLETE -> if (required == 1) "Habit done" else "All $required habits done"
    DayStatus.PARTIAL -> "$completed of $required habits done"
    DayStatus.MISSED -> if (required == 1) "Habit not done" else "None of $required habits done"
    DayStatus.IN_PROGRESS -> "Today · $completed of $required done so far"
    DayStatus.NOTHING_DUE -> "No habits were due"
    DayStatus.FUTURE -> "Upcoming"
}

fun habitDayStatusText(status: HabitDayStatus): String = when (status) {
    HabitDayStatus.DONE -> "done"
    HabitDayStatus.MISSED -> "not done"
    HabitDayStatus.PENDING -> "due today, not done yet"
    HabitDayStatus.NOT_DUE -> "not due"
    HabitDayStatus.FUTURE -> "upcoming"
}

fun percent(completed: Int, required: Int): String =
    if (required == 0) "—" else "${(completed * 100.0 / required).roundToInt()}%"

private fun localFirstDayOfWeek(): DayOfWeek = WeekFields.of(Locale.getDefault()).firstDayOfWeek

private fun DayStatus.cellStyle(): CellStyle = when (this) {
    DayStatus.COMPLETE -> CellStyle.FILLED
    DayStatus.PARTIAL -> CellStyle.HALF
    DayStatus.MISSED, DayStatus.IN_PROGRESS -> CellStyle.OUTLINED
    DayStatus.NOTHING_DUE -> CellStyle.FAINT
    DayStatus.FUTURE -> CellStyle.UPCOMING
}

private fun HabitDayStatus.cellStyle(): CellStyle = when (this) {
    HabitDayStatus.DONE -> CellStyle.FILLED
    HabitDayStatus.MISSED, HabitDayStatus.PENDING -> CellStyle.OUTLINED
    HabitDayStatus.NOT_DUE -> CellStyle.FAINT
    HabitDayStatus.FUTURE -> CellStyle.UPCOMING
}

// --- Route ------------------------------------------------------------------------------------

@Composable
fun ProgressRoute(
    bottomInset: PaddingValues,
    onOpenSettings: () -> Unit,
    onOpenManage: () -> Unit,
    viewModel: ProgressViewModel = viewModel(factory = RitualViewModels.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val view by viewModel.view.collectAsStateWithLifecycle()
    val detail by viewModel.dayDetail.collectAsStateWithLifecycle()

    ProgressScreen(
        state = state,
        view = view,
        bottomInset = bottomInset,
        onView = viewModel::setView,
        onPrevious = viewModel::previousMonth,
        onNext = viewModel::nextMonth,
        onDay = viewModel::openDay,
        onRetry = viewModel::retry,
        onOpenSettings = onOpenSettings,
        onOpenManage = onOpenManage,
    )
    detail?.let { DayDetailSheet(it, onDismiss = viewModel::closeDay) }
}

@Composable
fun ProgressScreen(
    state: ProgressUiState,
    view: ProgressView,
    bottomInset: PaddingValues,
    onView: (ProgressView) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onDay: (LocalDate, String?) -> Unit,
    onRetry: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenManage: () -> Unit,
) {
    ScreenColumn(bottomInset) {
        ScreenHeader(title = "Progress", actions = { SettingsButton(onOpenSettings) })
        Spacer(Modifier.height(Spacing.xl))
        when (state) {
            ProgressUiState.Loading -> DelayedLoadingIndicator("Loading progress")
            ProgressUiState.Error -> EmptyState(
                icon = RitualIcons.Progress,
                title = "Couldn't load your progress",
                body = "Your data is still safe on this device.",
                action = { SecondaryButton("Try again", onClick = onRetry) },
            )
            ProgressUiState.Empty -> EmptyState(
                icon = RitualIcons.Progress,
                title = "No history yet",
                body = "Your month and each habit's consistency appear here once you've added a habit.",
                action = { PrimaryButton("Add a habit", onClick = onOpenManage) },
            )
            is ProgressUiState.Ready -> {
                MonthSelector(state, onPrevious, onNext)
                Spacer(Modifier.height(Spacing.lg))
                SegmentedChoice(
                    options = ProgressView.entries,
                    selected = view,
                    label = { if (it == ProgressView.MONTH) "Month" else "Habits" },
                    onSelect = onView,
                )
                Spacer(Modifier.height(Spacing.xl))
                val motion = Ritual.motion
                AnimatedContent(
                    targetState = Triple(view, state.month, state),
                    contentKey = { (v, m, _) -> v to m },
                    transitionSpec = {
                        if (motion.reduced) {
                            ContentTransform(EnterTransition.None, ExitTransition.None)
                        } else {
                            val forward = targetState.second > initialState.second ||
                                (targetState.second == initialState.second && targetState.first.ordinal > initialState.first.ordinal)
                            val shift = if (forward) 1 else -1
                            (fadeIn(motion.standard()) + slideInHorizontally(motion.standard()) { shift * it / 12 })
                                .togetherWith(fadeOut(motion.exit()) + slideOutHorizontally(motion.exit()) { -shift * it / 12 })
                        }
                    },
                    label = "progressContent",
                ) { (v, _, s) ->
                    Column {
                        if (v == ProgressView.MONTH) MonthView(s, onDay) else HabitsView(s, onDay)
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthSelector(state: ProgressUiState.Ready, onPrevious: () -> Unit, onNext: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        RitualIconButton(RitualIcons.Back, contentDescription = "Previous month", onClick = onPrevious, enabled = state.canGoBack)
        Text(
            monthTitle(state.month),
            style = RitualType.heading,
            color = Ritual.colors.textPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .weight(1f)
                .semantics {
                    heading()
                    liveRegion = LiveRegionMode.Polite
                },
        )
        RitualIconButton(RitualIcons.Forward, contentDescription = "Next month", onClick = onNext, enabled = state.canGoForward)
    }
}

// --- Month view -------------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MonthView(state: ProgressUiState.Ready, onDay: (LocalDate, String?) -> Unit) {
    val c = Ritual.colors
    val cells = state.overview.days.associateBy { it.date }
    Module(contentPadding = PaddingValues(Spacing.lg)) {
        CalendarGrid(state.month, localFirstDayOfWeek()) { date ->
            val cell = cells.getValue(date)
            CalendarCell(
                day = date.dayOfMonth,
                style = cell.status.cellStyle(),
                isToday = cell.isToday,
                description = "${dayTitle(date)}: ${dayStatusText(cell.status, cell.required, cell.completed)}",
                onClick = if (cell.status == DayStatus.FUTURE) null else ({ onDay(date, null) }),
            )
        }
        Spacer(Modifier.height(Spacing.lg))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            LegendItem(CellStyle.FILLED, "All done")
            LegendItem(CellStyle.HALF, "Partly")
            LegendItem(CellStyle.OUTLINED, "Not done")
            LegendItem(CellStyle.FAINT, "Nothing due")
        }
    }
    Spacer(Modifier.height(Spacing.lg))
    Module(contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.lg)) {
        val stats = listOf(
            state.overview.completeDays.toString() to "Complete days",
            state.overview.partialDays.toString() to "Partial days",
            percent(state.overview.rate.completed, state.overview.rate.required) to "Completion",
        )
        if (LocalDensity.current.fontScale > 1.3f) {
            // Large text: one stat per line so labels never break mid-word.
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                stats.forEach { (value, label) ->
                    Row(Modifier.semantics(mergeDescendants = true) { }, verticalAlignment = Alignment.CenterVertically) {
                        Text(value, style = RitualType.numeral, color = c.textPrimary)
                        Spacer(Modifier.width(Spacing.md))
                        Text(label, style = RitualType.bodySmall, color = c.textSecondary)
                    }
                }
            }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                stats.forEach { (value, label) -> Stat(value, label, Modifier.weight(1f)) }
            }
        }
        Spacer(Modifier.height(Spacing.md))
        Hairline()
        Spacer(Modifier.height(Spacing.md))
        Text(
            "Current streak ${state.currentStreak} · Best ${state.longestStreak}",
            style = RitualType.bodySmall,
            color = c.textSecondary,
        )
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier.semantics(mergeDescendants = true) { }) {
        Text(value, style = RitualType.numeral, color = Ritual.colors.textPrimary)
        Spacer(Modifier.height(Spacing.xxs))
        Text(label, style = RitualType.caption.copy(fontFeatureSettings = null), color = Ritual.colors.textSecondary)
    }
}

// --- Habits view ------------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HabitsView(state: ProgressUiState.Ready, onDay: (LocalDate, String?) -> Unit) {
    val c = Ritual.colors
    if (state.habits.isEmpty()) {
        EmptyState(
            icon = RitualIcons.Manage,
            title = "No habits this month",
            body = "None of your habits were due in ${monthTitle(state.month)}.",
        )
        return
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.padding(start = Spacing.xs)) {
        LegendItem(CellStyle.FILLED, "Done")
        LegendItem(CellStyle.OUTLINED, "Not done")
        LegendItem(CellStyle.FAINT, "Not due")
    }
    Spacer(Modifier.height(Spacing.md))
    val firstDay = localFirstDayOfWeek()
    state.habits.forEachIndexed { index, habit ->
        if (index > 0) Spacer(Modifier.height(Spacing.md))
        Module(contentPadding = PaddingValues(Spacing.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    habit.name,
                    style = RitualType.rowTitle.copy(fontWeight = FontWeight.Medium),
                    color = if (habit.state == HabitState.ACTIVE) c.textPrimary else c.textSecondary,
                    modifier = Modifier.weight(1f, fill = false).semantics { heading() },
                )
                if (habit.state != HabitState.ACTIVE) {
                    Spacer(Modifier.width(Spacing.sm))
                    Text(
                        if (habit.state == HabitState.ARCHIVED) "Archived" else "Deleted",
                        style = RitualType.caption.copy(fontFeatureSettings = null),
                        color = c.textSecondary,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(c.fill)
                            .padding(horizontal = Spacing.sm, vertical = Spacing.xxs),
                    )
                }
                }
                Spacer(Modifier.width(Spacing.sm))
                Text(percent(habit.month.rate.completed, habit.month.rate.required), style = RitualType.value, color = c.textPrimary)
            }
            Spacer(Modifier.height(Spacing.xs))
            Text(
                "Current streak ${habit.currentStreak} · Best ${habit.longestStreak}",
                style = RitualType.bodySmall,
                color = c.textSecondary,
            )
            Spacer(Modifier.height(Spacing.md))
            CalendarGrid(state.month, firstDay, showWeekdays = index == 0) { date ->
                val status = habit.month.days[date.dayOfMonth - 1]
                CalendarCell(
                    day = date.dayOfMonth,
                    style = status.cellStyle(),
                    isToday = date == state.today,
                    description = "${habit.name}, ${dayTitle(date)}: ${habitDayStatusText(status)}",
                    onClick = if (status == HabitDayStatus.FUTURE) null else ({ onDay(date, habit.id) }),
                )
            }
        }
    }
}

// --- Day sheet --------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DayDetailSheet(detail: DayDetail, onDismiss: () -> Unit) {
    val c = Ritual.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.background,
        contentColor = c.textPrimary,
        scrimColor = c.scrim,
        shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
        tonalElevation = 0.dp,
        dragHandle = {
            Box(
                Modifier
                    .padding(top = Spacing.sm, bottom = Spacing.xs)
                    .size(width = 36.dp, height = 5.dp)
                    .background(c.borderStrong, CircleShape),
            )
        },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = Ritual.layout.gutter)
                .padding(top = Spacing.sm, bottom = Spacing.xl),
        ) {
            Text(dayTitle(detail.date), style = RitualType.heading, color = c.textPrimary, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.height(Spacing.xs))
            val done = detail.items.count { it.done }
            Text(dayStatusText(detail.status, detail.items.size, done), style = RitualType.bodySmall, color = c.textSecondary)
            Spacer(Modifier.height(Spacing.lg))
            if (detail.items.isEmpty()) {
                Text(
                    "Nothing was due on this day, so it doesn't count toward or against your streak.",
                    style = RitualType.bodySmall,
                    color = c.textSecondary,
                )
            } else {
                Module(contentPadding = PaddingValues(0.dp)) {
                    detail.items.forEachIndexed { index, item ->
                        if (index > 0) Hairline(startIndent = Spacing.lg + 24.dp + Spacing.md)
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 56.dp)
                                .semantics(mergeDescendants = true) { }
                                .padding(horizontal = Spacing.lg, vertical = Spacing.md),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CompletionCheck(item.done, size = 24.dp)
                            Spacer(Modifier.width(Spacing.md))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    item.name,
                                    style = RitualType.rowTitle.copy(
                                        fontWeight = if (item.habitId == detail.focusHabitId) FontWeight.SemiBold else FontWeight.Normal,
                                    ),
                                    color = c.textPrimary,
                                )
                                val tag = listOfNotNull(
                                    if (item.done) "Done" else "Not done",
                                    when (item.state) {
                                        HabitState.ARCHIVED -> "now archived"
                                        HabitState.DELETED -> "now deleted"
                                        HabitState.ACTIVE -> null
                                    },
                                ).joinToString(" · ")
                                Text(tag, style = RitualType.caption.copy(fontFeatureSettings = null), color = c.textSecondary)
                            }
                        }
                    }
                }
            }
        }
    }
}

