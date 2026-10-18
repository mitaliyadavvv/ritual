package app.ritualhabits.ui.today

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.progressSemantics
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.ritualhabits.ui.RitualViewModels
import app.ritualhabits.ui.components.CompletionCheck
import app.ritualhabits.ui.components.DelayedLoadingIndicator
import app.ritualhabits.ui.components.DotDay
import app.ritualhabits.ui.components.DotState
import app.ritualhabits.ui.components.EmptyState
import app.ritualhabits.ui.components.Hairline
import app.ritualhabits.ui.components.Module
import app.ritualhabits.ui.components.PrimaryButton
import app.ritualhabits.ui.components.ProgressRing
import app.ritualhabits.ui.components.RitualIconButton
import app.ritualhabits.ui.components.RitualIcons
import app.ritualhabits.ui.components.RitualSnackbarHost
import app.ritualhabits.ui.components.ScreenColumn
import app.ritualhabits.ui.components.ScreenHeader
import app.ritualhabits.ui.components.SecondaryButton
import app.ritualhabits.ui.components.SectionHeader
import app.ritualhabits.ui.components.WeekDots
import app.ritualhabits.ui.habits.HabitEditorSheet
import app.ritualhabits.ui.navigation.SettingsButton
import app.ritualhabits.ui.theme.Ritual
import app.ritualhabits.ui.theme.RitualType
import app.ritualhabits.ui.theme.Spacing
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toJavaLocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale

// --- Copy (presentation only; every number comes from TodayUiState) ----------------------------

/** "Sunday, 18 October", localised day and month names. */
fun formatHabitDay(date: LocalDate, locale: Locale = Locale.getDefault()): String =
    DateTimeFormatter.ofPattern("EEEE, d MMMM", locale).format(date.toJavaLocalDate())

fun streakLabel(days: Int): String = when (days) {
    0 -> "No streak yet"
    1 -> "1-day streak"
    else -> "$days-day streak"
}

/** Summary headline: what's left today, in plain words. */
fun todayTitle(state: TodayUiState.Ready): String = when {
    state.allDone -> "All done for today"
    state.completed == 0 -> if (state.total == 1) "1 habit today" else "${state.total} habits today"
    else -> "${state.remaining} left today"
}

/** Summary detail: the streak and the all-habits rule, briefly. */
fun todaySubtitle(state: TodayUiState.Ready): String = when {
    state.allDone && state.currentStreak > 0 -> streakLabel(state.currentStreak)
    state.allDone -> "Today counts toward your streak"
    state.currentStreak > 0 -> "${streakLabel(state.currentStreak)} · finish all to continue"
    else -> "Finish all to start a streak"
}

// --- Route ------------------------------------------------------------------------------------

@Composable
fun TodayRoute(
    bottomInset: PaddingValues,
    onOpenSettings: () -> Unit,
    viewModel: TodayViewModel = viewModel(factory = RitualViewModels.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val form by viewModel.addHabit.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val haptics by rememberUpdatedState(Ritual.haptics)

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is TodayEvent.Haptic -> haptics.perform(event.event)
                is TodayEvent.Message -> snackbar.showSnackbar(event.text)
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        TodayScreen(
            state = state,
            bottomInset = bottomInset,
            onToggle = viewModel::toggle,
            onAddHabit = viewModel::openAddHabit,
            onRetry = viewModel::retry,
            onOpenSettings = onOpenSettings,
        )
        RitualSnackbarHost(snackbar, bottomInset, Modifier.align(Alignment.BottomCenter))
    }

    form?.let {
        HabitEditorSheet(
            title = "New Habit",
            confirmLabel = "Add habit",
            state = it,
            onNameChange = viewModel::onNameChange,
            onDescriptionChange = viewModel::onDescriptionChange,
            onSave = viewModel::saveHabit,
            onDismiss = viewModel::dismissAddHabit,
        )
    }
}

// --- Screen -----------------------------------------------------------------------------------

@Composable
fun TodayScreen(
    state: TodayUiState,
    bottomInset: PaddingValues,
    onToggle: (String) -> Unit,
    onAddHabit: () -> Unit,
    onRetry: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    ScreenColumn(bottomInset) {
        ScreenHeader(
            title = "Today",
            overline = (state as? TodayUiState.Ready)?.date?.let(::formatHabitDay),
            actions = {
                if (state is TodayUiState.Ready) {
                    RitualIconButton(RitualIcons.Add, contentDescription = "Add habit", onClick = onAddHabit)
                }
                SettingsButton(onOpenSettings)
            },
        )
        Spacer(Modifier.height(Spacing.xl))

        when (state) {
            TodayUiState.Loading -> DelayedLoadingIndicator("Loading habits")
            TodayUiState.Error -> EmptyState(
                icon = RitualIcons.Today,
                title = "Couldn't load your habits",
                body = "Your data is still safe on this device.",
                action = { SecondaryButton("Try again", onClick = onRetry) },
            )
            is TodayUiState.Ready -> when {
                state.habits.isEmpty() && !state.hasActiveHabits -> EmptyState(
                    icon = RitualIcons.Today,
                    title = "Start with one habit",
                    body = "Choose something small you'd like to do every day. Ritual counts it from today.",
                    action = { PrimaryButton("Add your first habit", onClick = onAddHabit) },
                )
                state.habits.isEmpty() -> EmptyState(
                    icon = RitualIcons.Today,
                    title = "Nothing due today",
                    body = "Habits count from the day they were created.",
                )
                else -> {
                    SummaryCard(state)
                    Spacer(Modifier.height(Spacing.xxl))
                    SectionHeader("Habits", trailing = "${state.completed} of ${state.total}")
                    Spacer(Modifier.height(Spacing.md))
                    HabitList(state, onToggle)
                }
            }
        }
    }
}

/**
 * One calm card: a progress ring with the count inside, a plain-language status and streak, and
 * the last seven days as quiet dots.
 */
@Composable
private fun SummaryCard(state: TodayUiState.Ready) {
    val c = Ritual.colors
    Module(contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.lg)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProgressRing(
                progress = state.progress,
                size = 68.dp,
                strokeWidth = 7.dp,
                modifier = Modifier
                    .clearAndSetSemantics { contentDescription = "${state.completed} of ${state.total} habits done today" }
                    .progressSemantics(state.progress),
            ) { RingCenter(state) }
            Spacer(Modifier.width(Spacing.lg))
            Column(Modifier.weight(1f)) {
                Text(todayTitle(state), style = RitualType.heading, color = c.textPrimary)
                Spacer(Modifier.height(Spacing.xs))
                Text(todaySubtitle(state), style = RitualType.bodySmall, color = c.textSecondary)
            }
        }
        Spacer(Modifier.height(Spacing.lg))
        Hairline()
        Spacer(Modifier.height(Spacing.md))
        val locale = Locale.getDefault()
        val completeDays = state.week.count { it.mark == DayMark.COMPLETE }
        WeekDots(
            days = state.week.map { day ->
                DotDay(
                    label = day.date.dayOfWeek.getDisplayName(JavaTextStyle.NARROW, locale),
                    state = when (day.mark) {
                        DayMark.COMPLETE -> DotState.FILLED
                        DayMark.MISSED -> DotState.MISSED
                        DayMark.PENDING -> DotState.TODAY
                        DayMark.NONE -> DotState.EMPTY
                    },
                    isToday = day.isToday,
                )
            },
            modifier = Modifier.clearAndSetSemantics { contentDescription = "$completeDays of the last 7 days complete" },
        )
    }
}

/** Count inside the ring; rolls gently when it changes and becomes a check when the day is done. */
@Composable
private fun RingCenter(state: TodayUiState.Ready) {
    val c = Ritual.colors
    val motion = Ritual.motion
    AnimatedContent(
        targetState = if (state.allDone) -1 else state.completed,
        transitionSpec = {
            if (motion.reduced) {
                ContentTransform(EnterTransition.None, ExitTransition.None)
            } else {
                val up = targetState > initialState || targetState == -1
                (slideInVertically(motion.settle()) { if (up) it / 3 else -it / 3 } + fadeIn(motion.quick()))
                    .togetherWith(slideOutVertically(motion.quick()) { if (up) -it / 3 else it / 3 } + fadeOut(motion.exit()))
            }
        },
        label = "ringCenter",
    ) { value ->
        if (value == -1) {
            Icon(RitualIcons.Check, contentDescription = null, tint = c.textPrimary, modifier = Modifier.width(26.dp).height(26.dp))
        } else {
            // Decorative (the count is also in the section header and the ring's label), so it may
            // shrink to stay inside the ring at large font sizes.
            BasicText(
                "$value/${state.total}",
                style = RitualType.value.copy(color = c.textPrimary),
                maxLines = 1,
                softWrap = false,
                autoSize = TextAutoSize.StepBased(minFontSize = 9.sp, maxFontSize = RitualType.value.fontSize, stepSize = 1.sp),
                modifier = Modifier.widthIn(max = 50.dp),
            )
        }
    }
}

/** All habits in one grouped list; separators align with the habit names. */
@Composable
private fun HabitList(state: TodayUiState.Ready, onToggle: (String) -> Unit) {
    val lastPending = state.remaining == 1
    Module(contentPadding = PaddingValues(0.dp)) {
        state.habits.forEachIndexed { index, habit ->
            key(habit.id) {
                if (index > 0) Hairline(startIndent = RowTextIndent)
                HabitRow(habit, emphasis = lastPending && !habit.completed, onToggle = { onToggle(habit.id) })
            }
        }
    }
}

private val CheckSize = 28.dp
private val RowTextIndent = Spacing.lg + CheckSize + Spacing.md

/**
 * The whole row is one checkbox target (≥ 60 dp). Done vs pending: filled check mark and a quieter
 * name — never colour alone. [emphasis] marks the habit that would complete the day.
 */
@Composable
private fun HabitRow(habit: TodayHabit, emphasis: Boolean, onToggle: () -> Unit) {
    val c = Ritual.colors
    val interaction = remember { MutableInteractionSource() }
    val nameColor by animateColorAsState(if (habit.completed) c.textSecondary else c.textPrimary, Ritual.motion.standard(), label = "name")
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .toggleable(
                value = habit.completed,
                interactionSource = interaction,
                indication = ripple(color = c.textPrimary),
                role = Role.Checkbox,
                onValueChange = { onToggle() },
            )
            .semantics { stateDescription = if (habit.completed) "Done" else "Not done" }
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompletionCheck(habit.completed, size = CheckSize, emphasis = emphasis)
        Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(1f)) {
            Text(habit.name, style = RitualType.rowTitle, color = nameColor)
            if (habit.description != null) {
                Spacer(Modifier.height(Spacing.xxs))
                Text(habit.description, style = RitualType.bodySmall, color = c.textSecondary, maxLines = 2)
            }
        }
    }
}
