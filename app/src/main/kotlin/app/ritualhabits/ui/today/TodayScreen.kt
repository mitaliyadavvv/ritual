package app.ritualhabits.ui.today

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.progressSemantics
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.ritualhabits.ui.RitualViewModels
import app.ritualhabits.ui.components.CompletionCheck
import app.ritualhabits.ui.components.DelayedLoadingIndicator
import app.ritualhabits.ui.components.EmptyState
import app.ritualhabits.ui.components.FitText
import app.ritualhabits.ui.components.Hairline
import app.ritualhabits.ui.components.PrimaryButton
import app.ritualhabits.ui.components.RitualIconButton
import app.ritualhabits.ui.components.RitualIcons
import app.ritualhabits.ui.components.RitualSnackbarHost
import app.ritualhabits.ui.components.ScreenColumn
import app.ritualhabits.ui.components.ScreenHeader
import app.ritualhabits.ui.components.SecondaryButton
import app.ritualhabits.ui.components.SegmentedProgress
import app.ritualhabits.ui.habits.HabitEditorSheet
import app.ritualhabits.ui.navigation.SettingsButton
import app.ritualhabits.ui.theme.Radii
import app.ritualhabits.ui.theme.Ritual
import app.ritualhabits.ui.theme.RitualType
import app.ritualhabits.ui.theme.Sizes
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

private val SpelledNumbers = listOf("Zero", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine", "Ten")

/** The short editorial line beside the streak block. */
fun todayStatement(state: TodayUiState.Ready): String = when {
    state.allDone -> "Day complete."
    state.completed == 0 && state.currentStreak > 0 -> "Keep it going."
    state.completed == 0 -> "Let's begin."
    else -> "${SpelledNumbers.getOrNull(state.remaining) ?: state.remaining} to go."
}

fun todayRuleLine(state: TodayUiState.Ready): String =
    if (state.allDone) "Today counts toward your streak." else "Finish all ${state.total} and today counts."

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

        when (state) {
            TodayUiState.Loading -> DelayedLoadingIndicator("Loading habits")
            TodayUiState.Error -> EmptyState(
                icon = RitualIcons.Today,
                title = "Couldn't load your habits.",
                body = "Your data is still safe on this device.",
                action = { SecondaryButton("Try again", onClick = onRetry) },
            )
            is TodayUiState.Ready -> when {
                state.habits.isEmpty() && !state.hasActiveHabits -> FirstRun(onAddHabit)
                state.habits.isEmpty() -> EmptyState(
                    icon = RitualIcons.Today,
                    title = "Nothing due today.",
                    body = "Habits count from the day they were created.",
                )
                else -> {
                    Spacer(Modifier.height(Spacing.md))
                    HeroCount(state)
                    Spacer(Modifier.height(Spacing.xxl))
                    StatementAndStreak(state)
                    Spacer(Modifier.height(Spacing.xxxl))
                    HabitList(state, onToggle)
                }
            }
        }
    }
}

/** Giant count, outlined total, and the segmented bar welded beneath them. */
@Composable
private fun HeroCount(state: TodayUiState.Ready) {
    val c = Ritual.colors
    val description = "${state.completed} of ${state.total} habits done today"
    Column(
        Modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = description }
            .progressSemantics(state.progress),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            AnimatedCount(state.completed, Modifier.weight(1f, fill = false))
            Spacer(Modifier.width(Spacing.sm))
            Column(Modifier.padding(bottom = Spacing.lg)) {
                FitText("/${state.total}", RitualType.numeral, c.textPrimary, outlined = true, minFontSize = 24.sp)
                Text(
                    if (state.allDone) "ALL DONE" else "DONE TODAY",
                    style = RitualType.overline,
                    color = c.textPrimary,
                )
            }
        }
        Spacer(Modifier.height(Spacing.sm))
        SegmentedProgress(state.total, state.completed)
    }
}

/** The count rolls up or down when it changes (snaps when animations are off). */
@Composable
private fun AnimatedCount(count: Int, modifier: Modifier) {
    val c = Ritual.colors
    val motion = Ritual.motion
    AnimatedContent(
        targetState = count,
        modifier = modifier,
        transitionSpec = {
            if (motion.reduced) {
                ContentTransform(EnterTransition.None, ExitTransition.None)
            } else {
                val up = targetState > initialState
                (slideInVertically(motion.playful()) { if (up) it / 2 else -it / 2 } + fadeIn(motion.quick()))
                    .togetherWith(slideOutVertically(motion.standard()) { if (up) -it / 2 else it / 2 } + fadeOut(motion.exit()))
                    .using(SizeTransform(clip = true))
            }
        },
        label = "count",
    ) { value ->
        FitText(value.toString(), RitualType.hero, c.textPrimary, minFontSize = 64.sp)
    }
}

/**
 * A full-width statement, then the streak slab: an inverted block that bleeds off the right edge
 * of the screen. Number and week sit side by side, or stack when the font scale needs the room.
 */
@Composable
private fun StatementAndStreak(state: TodayUiState.Ready) {
    val c = Ritual.colors
    Text(todayStatement(state), style = RitualType.title, color = c.textPrimary)
    Spacer(Modifier.height(Spacing.xs))
    Text(todayRuleLine(state), style = RitualType.body, color = c.textSecondary)
    Spacer(Modifier.height(Spacing.xl))
    StreakSlab(state)
}

@Composable
private fun StreakSlab(state: TodayUiState.Ready) {
    val c = Ritual.colors
    val bleed = Ritual.layout.gutter
    val fontScale = LocalDensity.current.fontScale
    val completeDays = state.week.count { it.mark == DayMark.COMPLETE }
    val description = "${streakLabel(state.currentStreak)}. $completeDays of the last 7 days complete."
    val shape = RoundedCornerShape(topStart = Radii.lg, bottomStart = Radii.lg)
    BoxWithConstraints(
        Modifier
            .bleedEnd(bleed)
            .clip(shape)
            .background(c.accent)
            .padding(start = Spacing.xl, top = Spacing.lg, bottom = Spacing.xl, end = bleed + Spacing.lg)
            .clearAndSetSemantics { contentDescription = description },
    ) {
        val stacked = maxWidth < 300.dp || fontScale > 1.3f
        val number: @Composable (Modifier) -> Unit = { modifier ->
            Column(modifier) {
                Text("STREAK", style = RitualType.overline, color = c.onAccentSecondary)
                Row(verticalAlignment = Alignment.Bottom) {
                    FitText(state.currentStreak.toString(), RitualType.numeral, c.onAccent, Modifier.weight(1f, fill = false), minFontSize = 32.sp)
                    Spacer(Modifier.width(Spacing.xs))
                    Text(
                        if (state.currentStreak == 1) "day" else "days",
                        style = RitualType.body,
                        color = c.onAccent,
                        modifier = Modifier.padding(bottom = Spacing.sm),
                    )
                }
            }
        }
        if (stacked) {
            Column {
                number(Modifier)
                Spacer(Modifier.height(Spacing.lg))
                WeekStrip(state.week)
            }
        } else {
            Row(verticalAlignment = Alignment.Bottom) {
                number(Modifier.weight(0.8f).padding(end = Spacing.lg))
                Box(Modifier.weight(1.2f)) { WeekStrip(state.week) }
            }
        }
    }
}

/** Extends the element past the end gutter to the screen edge (editorial bleed). */
private fun Modifier.bleedEnd(bleed: androidx.compose.ui.unit.Dp): Modifier = layout { measurable, constraints ->
    val extra = bleed.roundToPx()
    val placeable = measurable.measure(
        constraints.copy(minWidth = constraints.maxWidth + extra, maxWidth = constraints.maxWidth + extra),
    )
    layout(constraints.maxWidth, placeable.height) { placeable.place(0, 0) }
}

/** Seven squares, oldest first: filled = complete, outlined = missed, dotted = today pending, dash = nothing due. */
@Composable
private fun WeekStrip(week: List<WeekDay>) {
    val c = Ritual.colors
    val locale = Locale.getDefault()
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        week.forEach { day ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Canvas(Modifier.fillMaxWidth().aspectRatio(1f)) {
                    val stroke = Sizes.stroke.toPx()
                    val inset = Offset(stroke / 2, stroke / 2)
                    val boxSize = size.copy(size.width - stroke, size.height - stroke)
                    when (day.mark) {
                        DayMark.COMPLETE -> drawRect(c.onAccent)
                        DayMark.MISSED -> drawRect(c.onAccent, topLeft = inset, size = boxSize, style = Stroke(stroke))
                        DayMark.PENDING -> {
                            drawRect(c.onAccent, topLeft = inset, size = boxSize, style = Stroke(stroke))
                            drawCircle(c.onAccent, radius = size.minDimension * 0.14f)
                        }
                        DayMark.NONE -> drawLine(
                            c.onAccentSecondary,
                            Offset(size.width * 0.25f, size.height / 2),
                            Offset(size.width * 0.75f, size.height / 2),
                            strokeWidth = stroke,
                            cap = StrokeCap.Round,
                        )
                    }
                }
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    day.date.dayOfWeek.getDisplayName(JavaTextStyle.NARROW, locale),
                    style = RitualType.caption.copy(fontWeight = if (day.isToday) FontWeight.Bold else FontWeight.Normal),
                    color = if (day.isToday) c.onAccent else c.onAccentSecondary,
                )
            }
        }
    }
}

/** Heavy rule, numbered section marker, then the habits as an index (no cards). */
@Composable
private fun HabitList(state: TodayUiState.Ready, onToggle: (String) -> Unit) {
    val c = Ritual.colors
    Box(Modifier.fillMaxWidth().height(Sizes.rule).background(c.textPrimary))
    Spacer(Modifier.height(Spacing.md))
    Row(Modifier.fillMaxWidth()) {
        Text("01", style = RitualType.overline, color = c.textPrimary, modifier = Modifier.clearAndSetSemantics { })
        Spacer(Modifier.width(Spacing.lg))
        Text("HABITS", style = RitualType.overline, color = c.textPrimary, modifier = Modifier.weight(1f))
        Text(
            if (state.allDone) "ALL DONE" else "${state.remaining} LEFT",
            style = RitualType.overline,
            color = c.textSecondary,
        )
    }
    Spacer(Modifier.height(Spacing.sm))
    state.habits.forEachIndexed { index, habit ->
        key(habit.id) {
            if (index > 0) Hairline()
            HabitRow(index, habit, onToggle = { onToggle(habit.id) })
        }
    }
}

/**
 * The whole row is one checkbox target (≥ 80 dp). Done vs pending: filled stamp, strike-through
 * and a quieter name, never colour alone. The index number is decorative and hidden from TalkBack.
 */
@Composable
private fun HabitRow(index: Int, habit: TodayHabit, onToggle: () -> Unit) {
    val c = Ritual.colors
    val interaction = remember { MutableInteractionSource() }
    val nameColor by animateColorAsState(if (habit.completed) c.textSecondary else c.textPrimary, Ritual.motion.quick(), label = "name")
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 80.dp)
            .toggleable(
                value = habit.completed,
                interactionSource = interaction,
                indication = ripple(color = c.textPrimary),
                role = Role.Checkbox,
                onValueChange = { onToggle() },
            )
            .semantics { stateDescription = if (habit.completed) "Done" else "Not done" }
            .padding(vertical = Spacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            (index + 1).toString().padStart(2, '0'),
            style = RitualType.overline,
            color = c.textSecondary,
            modifier = Modifier
                .widthIn(min = 36.dp)
                .padding(end = Spacing.sm)
                .clearAndSetSemantics { },
        )
        Column(Modifier.weight(1f).padding(end = Spacing.lg)) {
            Text(
                habit.name,
                style = RitualType.rowTitle,
                color = nameColor,
                textDecoration = if (habit.completed) TextDecoration.LineThrough else null,
            )
            if (habit.description != null) {
                Spacer(Modifier.height(Spacing.xxs))
                Text(habit.description, style = RitualType.bodySmall, color = c.textSecondary, maxLines = 2)
            }
        }
        CompletionCheck(habit.completed)
    }
}

/** First run: an outlined zero, a statement, and one action. */
@Composable
private fun FirstRun(onAddHabit: () -> Unit) {
    val c = Ritual.colors
    Column(Modifier.fillMaxWidth().padding(top = Spacing.lg)) {
        FitText("0", RitualType.hero, c.textPrimary, Modifier.clearAndSetSemantics { }, outlined = true, minFontSize = 64.sp)
        Text("Start with\none habit.", style = RitualType.title, color = c.textPrimary)
        Spacer(Modifier.height(Spacing.md))
        Text(
            "Choose something small you want to do every day. Ritual counts it from today.",
            style = RitualType.body,
            color = c.textSecondary,
        )
        Spacer(Modifier.height(Spacing.xl))
        PrimaryButton("Add your first habit", onClick = onAddHabit)
    }
}
