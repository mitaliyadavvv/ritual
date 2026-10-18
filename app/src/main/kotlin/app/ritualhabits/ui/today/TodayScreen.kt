package app.ritualhabits.ui.today

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.progressSemantics
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.ritualhabits.ui.RitualViewModels
import app.ritualhabits.ui.components.CompletionCheck
import app.ritualhabits.ui.components.DelayedLoadingIndicator
import app.ritualhabits.ui.components.EmptyState
import app.ritualhabits.ui.components.Hairline
import app.ritualhabits.ui.components.Module
import app.ritualhabits.ui.components.PrimaryButton
import app.ritualhabits.ui.components.ProgressTrack
import app.ritualhabits.ui.components.RitualIconButton
import app.ritualhabits.ui.components.RitualIcons
import app.ritualhabits.ui.components.RitualSnackbarHost
import app.ritualhabits.ui.components.ScreenColumn
import app.ritualhabits.ui.components.ScreenHeader
import app.ritualhabits.ui.components.SecondaryButton
import app.ritualhabits.ui.components.SectionLabel
import app.ritualhabits.ui.habits.HabitEditorSheet
import app.ritualhabits.ui.navigation.SettingsButton
import app.ritualhabits.ui.theme.Ritual
import app.ritualhabits.ui.theme.RitualType
import app.ritualhabits.ui.theme.Spacing
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toJavaLocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** "Sunday, 18 October", localised day and month names. */
fun formatHabitDay(date: LocalDate, locale: Locale = Locale.getDefault()): String =
    DateTimeFormatter.ofPattern("EEEE, d MMMM", locale).format(date.toJavaLocalDate())

fun streakLabel(days: Int): String = when (days) {
    0 -> "No streak yet"
    1 -> "1-day streak"
    else -> "$days-day streak"
}

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
            title = "New habit",
            confirmLabel = "Add habit",
            state = it,
            onNameChange = viewModel::onNameChange,
            onDescriptionChange = viewModel::onDescriptionChange,
            onSave = viewModel::saveHabit,
            onDismiss = viewModel::dismissAddHabit,
        )
    }
}

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
                    icon = RitualIcons.Add,
                    title = "Start with one habit",
                    body = "Choose something small you want to do every day.",
                    action = { PrimaryButton("Add your first habit", onClick = onAddHabit) },
                )
                state.habits.isEmpty() -> EmptyState(
                    icon = RitualIcons.Today,
                    title = "Nothing due today",
                    body = "Habits count from the day they were created.",
                )
                else -> {
                    DailySummary(state)
                    Spacer(Modifier.height(Spacing.xxl))
                    SectionLabel("Habits")
                    Module(contentPadding = PaddingValues(horizontal = Spacing.lg)) {
                        state.habits.forEachIndexed { index, habit ->
                            key(habit.id) {
                                if (index > 0) Hairline()
                                HabitRow(habit, onToggle = { onToggle(habit.id) })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DailySummary(state: TodayUiState.Ready) {
    val c = Ritual.colors
    val summary = "${state.completed} of ${state.total} habits done today. ${streakLabel(state.currentStreak)}."
    Column(Modifier.semantics(mergeDescendants = true) { contentDescription = summary }) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Text("${state.completed} of ${state.total}", style = RitualType.value, color = c.textPrimary)
            Spacer(Modifier.width(Spacing.sm))
            Text("done", style = RitualType.bodySmall, color = c.textSecondary, modifier = Modifier.padding(bottom = 3.dp))
            Spacer(Modifier.weight(1f))
            Text(streakLabel(state.currentStreak), style = RitualType.bodySmall, color = c.textSecondary, modifier = Modifier.padding(bottom = 3.dp))
        }
        Spacer(Modifier.height(Spacing.md))
        ProgressTrack(state.progress, Modifier.progressSemantics(state.progress))
        Spacer(Modifier.height(Spacing.sm))
        Text(
            if (state.allDone) "Day complete. Today counts toward your streak."
            else "Complete all ${state.total} to count today toward your streak.",
            style = RitualType.caption,
            color = c.textSecondary,
        )
    }
}

/**
 * The whole row is one checkbox target (≥ 64 dp tall). Done vs pending is shown by the filled
 * check, strike-through and weight of the text, never by colour alone.
 */
@Composable
private fun HabitRow(habit: TodayHabit, onToggle: () -> Unit) {
    val c = Ritual.colors
    val interaction = remember { MutableInteractionSource() }
    val nameColor by animateColorAsState(if (habit.completed) c.textSecondary else c.textPrimary, Ritual.motion.quick(), label = "name")
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .toggleable(
                value = habit.completed,
                interactionSource = interaction,
                indication = ripple(color = c.textPrimary),
                role = Role.Checkbox,
                onValueChange = { onToggle() },
            )
            .semantics { stateDescription = if (habit.completed) "Done" else "Not done" }
            .padding(vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        CompletionCheck(habit.completed)
        Column(Modifier.weight(1f)) {
            Text(
                habit.name,
                style = RitualType.body,
                color = nameColor,
                textDecoration = if (habit.completed) TextDecoration.LineThrough else null,
            )
            if (habit.description != null) {
                Spacer(Modifier.height(Spacing.xxs))
                Text(habit.description, style = RitualType.bodySmall, color = c.textSecondary, maxLines = 2)
            }
        }
    }
}
