package app.ritualhabits.ui.focus

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.ritualhabits.core.rules.FocusTimer
import app.ritualhabits.data.repository.FocusOutcome
import app.ritualhabits.ui.RitualViewModels
import app.ritualhabits.ui.components.CompletionCheck
import app.ritualhabits.ui.components.ConfirmDialog
import app.ritualhabits.ui.components.DelayedLoadingIndicator
import app.ritualhabits.ui.components.EmptyState
import app.ritualhabits.ui.components.Hairline
import app.ritualhabits.ui.components.Module
import app.ritualhabits.ui.components.PrimaryButton
import app.ritualhabits.ui.components.RitualIcons
import app.ritualhabits.ui.components.RitualSnackbarHost
import app.ritualhabits.ui.components.ScreenColumn
import app.ritualhabits.ui.components.ScreenHeader
import app.ritualhabits.ui.components.SecondaryButton
import app.ritualhabits.ui.components.TimerDial
import app.ritualhabits.ui.navigation.SettingsButton
import app.ritualhabits.ui.theme.HapticEvent
import app.ritualhabits.ui.theme.Ritual
import app.ritualhabits.ui.theme.RitualType
import app.ritualhabits.ui.theme.Spacing
import kotlin.math.ceil
import kotlin.time.Duration

/** "25:00", "07:05", "60:00". Rounds up so 00:00 only appears once the session has ended. */
fun formatCountdown(remaining: Duration): String {
    val total = ceil(remaining.inWholeMilliseconds.coerceAtLeast(0) / 1000.0).toLong()
    return "%02d:%02d".format(total / 60, total % 60)
}

fun spokenCountdown(remaining: Duration): String {
    val total = ceil(remaining.inWholeMilliseconds.coerceAtLeast(0) / 1000.0).toLong()
    val m = total / 60
    val s = total % 60
    return buildString {
        if (m > 0) append(if (m == 1L) "1 minute" else "$m minutes")
        if (s > 0) append(if (m > 0) " " else "").append(if (s == 1L) "1 second" else "$s seconds")
        if (m == 0L && s == 0L) append("0 seconds")
    } + " remaining"
}

@Composable
fun FocusRoute(
    bottomInset: PaddingValues,
    onOpenSettings: () -> Unit,
    onOpenManage: () -> Unit,
    viewModel: FocusViewModel = viewModel(factory = RitualViewModels.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val asked by viewModel.notificationsAsked.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val haptics by rememberUpdatedState(Ritual.haptics)
    val context = LocalContext.current
    var explainNotifications by rememberSaveable { mutableStateOf(false) }
    var confirmCancel by rememberSaveable { mutableStateOf(false) }
    var choosingHabit by rememberSaveable { mutableStateOf(false) }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { viewModel.start() }
    fun notificationsGranted() = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    var exactAlerts by remember { mutableStateOf(viewModel.alertsCanBeExact) }
    LifecycleResumeEffect(viewModel) {
        exactAlerts = viewModel.alertsCanBeExact
        viewModel.onResume()
        onPauseOrDispose { }
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is FocusEvent.Haptic -> haptics.perform(event.event)
                is FocusEvent.Message -> snackbar.showSnackbar(event.text)
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        FocusScreen(
            state = state,
            bottomInset = bottomInset,
            notificationsOff = asked && !notificationsGranted(),
            alertsMayBeLate = notificationsGranted() && !exactAlerts,
            onAllowPreciseAlerts = {
                if (Build.VERSION.SDK_INT >= 31) {
                    context.startActivity(
                        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")),
                    )
                }
            },
            onChooseHabit = { choosingHabit = true },
            onDuration = viewModel::selectDuration,
            onStart = {
                // Ask once, at the moment it matters, after explaining why.
                if (!asked && !notificationsGranted()) explainNotifications = true else viewModel.start()
            },
            onPause = viewModel::pause,
            onResume = viewModel::resume,
            onCancel = { confirmCancel = true },
            onStartAnother = viewModel::startAnother,
            onRetry = viewModel::retry,
            onOpenSettings = onOpenSettings,
            onOpenManage = onOpenManage,
            onOpenNotificationSettings = {
                context.startActivity(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                )
            },
        )
        RitualSnackbarHost(snackbar, bottomInset, Modifier.align(Alignment.BottomCenter))
    }

    if (explainNotifications) {
        ConfirmDialog(
            title = "Know when a session ends?",
            body = "Ritual can send a notification when your focus session finishes, even if the app is closed. " +
                "Your session is recorded either way.",
            confirmLabel = "Continue",
            onConfirm = {
                explainNotifications = false
                viewModel.markNotificationsAsked()
                permission.launch(Manifest.permission.POST_NOTIFICATIONS)
            },
            onDismiss = {
                explainNotifications = false
                viewModel.markNotificationsAsked()
                viewModel.start()
            },
            dismissLabel = "Not now",
        )
    }
    if (confirmCancel) {
        val name = (state as? FocusUiState.Ready)?.habitName ?: "this habit"
        ConfirmDialog(
            title = "End this session?",
            body = "Nothing will be recorded for “$name”.",
            confirmLabel = "End session",
            onConfirm = {
                confirmCancel = false
                viewModel.cancel()
            },
            onDismiss = { confirmCancel = false },
        )
    }
    val ready = state as? FocusUiState.Ready
    if (choosingHabit && ready != null && ready.canChangeSetup) {
        HabitChooserSheet(
            options = ready.habits,
            selected = ready.habitId,
            onSelect = {
                viewModel.selectHabit(it)
                choosingHabit = false
            },
            onDismiss = { choosingHabit = false },
        )
    }
}

@Composable
fun FocusScreen(
    state: FocusUiState,
    bottomInset: PaddingValues,
    notificationsOff: Boolean,
    alertsMayBeLate: Boolean,
    onAllowPreciseAlerts: () -> Unit,
    onChooseHabit: () -> Unit,
    onDuration: (Duration) -> Unit,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onStartAnother: () -> Unit,
    onRetry: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenManage: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
) {
    ScreenColumn(bottomInset) {
        ScreenHeader(title = "Focus", actions = { SettingsButton(onOpenSettings) })
        Spacer(Modifier.height(Spacing.xl))
        when (state) {
            FocusUiState.Loading -> DelayedLoadingIndicator("Loading focus")
            FocusUiState.Error -> EmptyState(
                icon = RitualIcons.Focus,
                title = "Couldn't load Focus",
                body = "Your data is still safe on this device.",
                action = { SecondaryButton("Try again", onClick = onRetry) },
            )
            FocusUiState.NoHabits -> EmptyState(
                icon = RitualIcons.Focus,
                title = "Nothing to focus on yet",
                body = "Focus sessions are for a habit that's due today. Add a habit to get started.",
                action = { PrimaryButton("Go to Manage", onClick = onOpenManage) },
            )
            is FocusUiState.Ready -> Ready(state, notificationsOff, alertsMayBeLate, onAllowPreciseAlerts, onChooseHabit, onDuration, onStart, onPause, onResume, onCancel, onStartAnother, onOpenNotificationSettings)
        }
    }
}

@Composable
private fun Ready(
    state: FocusUiState.Ready,
    notificationsOff: Boolean,
    alertsMayBeLate: Boolean,
    onAllowPreciseAlerts: () -> Unit,
    onChooseHabit: () -> Unit,
    onDuration: (Duration) -> Unit,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onStartAnother: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
) {
    val c = Ritual.colors
    HabitRow(state, onChooseHabit)
    Spacer(Modifier.height(Spacing.xxl))

    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val dialSize = minOf(maxWidth - Spacing.xl, 280.dp)
        val caption = when (state.phase) {
            FocusPhase.IDLE -> "Ready"
            FocusPhase.RUNNING -> "Focusing"
            FocusPhase.PAUSED -> "Paused"
            FocusPhase.COMPLETED -> ""
        }
        TimerDial(
            progress = if (state.phase == FocusPhase.COMPLETED) 1f else state.progress,
            time = formatCountdown(state.remaining),
            caption = caption,
            size = dialSize,
            modifier = Modifier.semantics {
                contentDescription = if (state.phase == FocusPhase.COMPLETED) "Session complete" else spokenCountdown(state.remaining)
                stateDescription = caption
            },
            center = if (state.phase == FocusPhase.COMPLETED) {
                {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CompletionCheck(true, size = 56.dp, emphasis = true)
                        Spacer(Modifier.height(Spacing.md))
                        Text("Complete", style = RitualType.heading, color = c.textPrimary)
                    }
                }
            } else {
                null
            },
        )
    }
    Spacer(Modifier.height(Spacing.xxl))

    if (state.phase == FocusPhase.COMPLETED) {
        CompletionSummary(state, onStartAnother)
        return
    }

    DurationPicker(state, onDuration)
    Spacer(Modifier.height(Spacing.xl))

    when (state.phase) {
        FocusPhase.IDLE -> {
            PrimaryButton("Start", onClick = onStart, enabled = state.canStart, modifier = Modifier.fillMaxWidth())
            if (state.habitId == null) {
                Spacer(Modifier.height(Spacing.sm))
                Text(
                    "Choose a habit to start.",
                    style = RitualType.bodySmall,
                    color = c.textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        FocusPhase.RUNNING, FocusPhase.PAUSED -> Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            SecondaryButton("Cancel", onClick = onCancel, modifier = Modifier.weight(1f))
            if (state.phase == FocusPhase.RUNNING) {
                PrimaryButton("Pause", onClick = onPause, modifier = Modifier.weight(1f))
            } else {
                PrimaryButton("Resume", onClick = onResume, modifier = Modifier.weight(1f))
            }
        }
        FocusPhase.COMPLETED -> Unit
    }

    if (alertsMayBeLate) {
        Spacer(Modifier.height(Spacing.lg))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "The end-of-session alert may arrive a few minutes late. Your session is recorded on time either way.",
                style = RitualType.caption.copy(fontFeatureSettings = null),
                color = c.textSecondary,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onAllowPreciseAlerts, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Allow precise alerts", style = RitualType.label, color = c.textPrimary, textAlign = TextAlign.End)
            }
        }
    }
    if (notificationsOff) {
        Spacer(Modifier.height(Spacing.lg))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Notifications are off, so Ritual can't alert you when a session ends in the background.",
                style = RitualType.caption.copy(fontFeatureSettings = null),
                color = c.textSecondary,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onOpenNotificationSettings, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Settings", style = RitualType.label, color = c.textPrimary)
            }
        }
    }
}

@Composable
private fun HabitRow(state: FocusUiState.Ready, onChooseHabit: () -> Unit) {
    val c = Ritual.colors
    val enabled = state.canChangeSetup
    Module(contentPadding = PaddingValues(0.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .clickable(
                    enabled = enabled,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(color = c.textPrimary),
                    role = Role.Button,
                    onClickLabel = "Choose a habit",
                    onClick = onChooseHabit,
                )
                .padding(horizontal = Spacing.lg, vertical = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (state.phase == FocusPhase.IDLE) "Habit" else "Focusing on",
                    style = RitualType.caption.copy(fontFeatureSettings = null),
                    color = c.textSecondary,
                )
                Spacer(Modifier.height(Spacing.xxs))
                Text(
                    state.habitName ?: "Choose a habit",
                    style = RitualType.rowTitle.copy(fontWeight = if (state.habitName != null) FontWeight.Medium else FontWeight.Normal),
                    color = if (state.habitName != null) c.textPrimary else c.textSecondary,
                )
            }
            if (enabled) {
                Icon(RitualIcons.ChevronDown, contentDescription = null, tint = c.textSecondary, modifier = Modifier.size(20.dp))
            }
        }
    }
}

/** Horizontally scrolling capsules; the selection is a filled capsule. Locked once a session exists. */
@Composable
private fun DurationPicker(state: FocusUiState.Ready, onDuration: (Duration) -> Unit) {
    val c = Ritual.colors
    val haptics = Ritual.haptics
    val enabled = state.canChangeSetup
    Text(
        "Duration",
        style = RitualType.overline,
        color = c.textSecondary,
        modifier = Modifier.padding(start = Spacing.xs, bottom = Spacing.sm).semantics { heading() },
    )
    LazyRow(
        Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.4f)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        items(FocusTimer.PRESETS) { preset ->
            val selected = preset == state.duration
            Box(
                Modifier
                    .heightIn(min = 48.dp)
                    .clip(CircleShape)
                    .background(if (selected) c.accent else c.fill)
                    .selectable(
                        selected = selected,
                        enabled = enabled,
                        role = Role.RadioButton,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(color = c.textPrimary),
                    ) {
                        if (!selected) haptics.perform(HapticEvent.Selection)
                        onDuration(preset)
                    }
                    .padding(horizontal = Spacing.lg + 2.dp, vertical = Spacing.md),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "${preset.inWholeMinutes} min",
                    style = RitualType.label,
                    color = if (selected) c.onAccent else c.textPrimary,
                )
            }
        }
    }
}

@Composable
private fun CompletionSummary(state: FocusUiState.Ready, onStartAnother: () -> Unit) {
    val c = Ritual.colors
    val motion = Ritual.motion
    AnimatedContent(
        targetState = state.outcome,
        transitionSpec = {
            if (motion.reduced) ContentTransform(EnterTransition.None, ExitTransition.None)
            else (fadeIn(motion.standard()) + scaleIn(motion.settle(), initialScale = 0.96f)).togetherWith(fadeOut(motion.exit()))
        },
        label = "outcome",
    ) { outcome ->
        Column(
            Modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Session complete", style = RitualType.heading, color = c.textPrimary, textAlign = TextAlign.Center)
            Spacer(Modifier.height(Spacing.xs))
            val name = state.habitName ?: "Your habit"
            Text(
                when (outcome) {
                    FocusOutcome.MARKED_DONE -> "“$name” is marked done for today."
                    FocusOutcome.NOT_DUE -> "“$name” wasn't due today, so nothing was recorded."
                    null -> "“$name”"
                },
                style = RitualType.bodySmall,
                color = c.textSecondary,
                textAlign = TextAlign.Center,
            )
        }
    }
    Spacer(Modifier.height(Spacing.xl))
    PrimaryButton("Start another session", onClick = onStartAnother, modifier = Modifier.fillMaxWidth())
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HabitChooserSheet(
    options: List<FocusHabitOption>,
    selected: String?,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
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
            Text("Choose a habit", style = RitualType.heading, color = c.textPrimary, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.height(Spacing.xs))
            Text("Finishing a session marks the habit done for today.", style = RitualType.bodySmall, color = c.textSecondary)
            Spacer(Modifier.height(Spacing.lg))
            Module(contentPadding = PaddingValues(0.dp), modifier = Modifier.selectableGroup()) {
                options.forEachIndexed { index, option ->
                    if (index > 0) Hairline(startIndent = Spacing.lg)
                    val isSelected = option.id == selected
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .selectable(
                                selected = isSelected,
                                role = Role.RadioButton,
                                interactionSource = remember { MutableInteractionSource() },
                                indication = ripple(color = c.textPrimary),
                            ) { onSelect(option.id) }
                            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(option.name, style = RitualType.rowTitle, color = c.textPrimary)
                            if (option.doneToday) {
                                Text("Done today", style = RitualType.caption.copy(fontFeatureSettings = null), color = c.textSecondary)
                            }
                        }
                        Box(
                            Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(if (isSelected) c.accent else c.fill)
                                .clearAndSetSemantics { },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (isSelected) Box(Modifier.size(8.dp).clip(CircleShape).background(c.onAccent))
                        }
                    }
                }
            }
        }
    }
}

