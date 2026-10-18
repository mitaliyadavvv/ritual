package app.ritualhabits.ui.manage

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.clickable
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.ritualhabits.ui.RitualViewModels
import app.ritualhabits.ui.components.ConfirmDialog
import app.ritualhabits.ui.components.DelayedLoadingIndicator
import app.ritualhabits.ui.components.EmptyState
import app.ritualhabits.ui.components.Hairline
import app.ritualhabits.ui.components.MenuAction
import app.ritualhabits.ui.components.Module
import app.ritualhabits.ui.components.PrimaryButton
import app.ritualhabits.ui.components.RitualIconButton
import app.ritualhabits.ui.components.RitualIcons
import app.ritualhabits.ui.components.RitualMenu
import app.ritualhabits.ui.components.RitualSnackbarHost
import app.ritualhabits.ui.components.ScreenColumn
import app.ritualhabits.ui.components.ScreenHeader
import app.ritualhabits.ui.components.SecondaryButton
import app.ritualhabits.ui.components.SegmentedChoice
import app.ritualhabits.ui.components.animatePlacement
import app.ritualhabits.ui.habits.HabitEditorSheet
import app.ritualhabits.ui.navigation.SettingsButton
import app.ritualhabits.ui.theme.Ritual
import app.ritualhabits.ui.theme.RitualType
import app.ritualhabits.ui.theme.Spacing
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toJavaLocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** "18 Oct 2026" — compact date for row metadata. */
fun formatShortDate(date: LocalDate, locale: Locale = Locale.getDefault()): String =
    DateTimeFormatter.ofPattern("d MMM yyyy", locale).format(date.toJavaLocalDate())

@Composable
fun ManageRoute(
    bottomInset: PaddingValues,
    onOpenSettings: () -> Unit,
    viewModel: ManageViewModel = viewModel(factory = RitualViewModels.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val tab by viewModel.tab.collectAsStateWithLifecycle()
    val reordering by viewModel.reordering.collectAsStateWithLifecycle()
    val editor by viewModel.editor.collectAsStateWithLifecycle()
    val pendingDelete by viewModel.pendingDelete.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val haptics by rememberUpdatedState(Ritual.haptics)

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is ManageEvent.Haptic -> haptics.perform(event.event)
                is ManageEvent.Message -> {
                    snackbar.currentSnackbarData?.dismiss()
                    val result = snackbar.showSnackbar(
                        message = event.text,
                        actionLabel = if (event.undo != null) "Undo" else null,
                        duration = if (event.undo != null) SnackbarDuration.Long else SnackbarDuration.Short,
                    )
                    if (result == SnackbarResult.ActionPerformed && event.undo != null) viewModel.undo(event.undo)
                }
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        ManageScreen(
            state = state,
            tab = tab,
            reordering = reordering,
            bottomInset = bottomInset,
            actions = ManageActions(
                onSelectTab = viewModel::selectTab,
                onCreate = viewModel::openCreate,
                onEdit = viewModel::openEdit,
                onArchive = { viewModel.archive(it) },
                onRestore = { viewModel.restore(it) },
                onDelete = viewModel::requestDelete,
                onMove = { habit, by -> viewModel.move(habit.id, by) },
                onReorder = viewModel::setReordering,
                onRetry = viewModel::retry,
                onOpenSettings = onOpenSettings,
            ),
        )
        RitualSnackbarHost(snackbar, bottomInset, Modifier.align(Alignment.BottomCenter))
    }

    editor?.let {
        HabitEditorSheet(
            title = if (it.isNew) "New habit" else "Edit habit",
            confirmLabel = if (it.isNew) "Add habit" else "Save",
            state = it.form,
            onNameChange = viewModel::onNameChange,
            onDescriptionChange = viewModel::onDescriptionChange,
            onSave = viewModel::saveEditor,
            onDismiss = viewModel::dismissEditor,
        )
    }

    pendingDelete?.let { habit ->
        ConfirmDialog(
            title = "Delete “${habit.name}”?",
            body = "It will be removed from Today and Manage. Days you've already completed keep their " +
                "records, so past streaks and progress don't change.",
            confirmLabel = "Delete",
            onConfirm = viewModel::confirmDelete,
            onDismiss = viewModel::cancelDelete,
        )
    }
}

class ManageActions(
    val onSelectTab: (ManageTab) -> Unit,
    val onCreate: () -> Unit,
    val onEdit: (ManagedHabit) -> Unit,
    val onArchive: (ManagedHabit) -> Unit,
    val onRestore: (ManagedHabit) -> Unit,
    val onDelete: (ManagedHabit) -> Unit,
    val onMove: (ManagedHabit, Int) -> Unit,
    val onReorder: (Boolean) -> Unit,
    val onRetry: () -> Unit,
    val onOpenSettings: () -> Unit,
)

@Composable
fun ManageScreen(
    state: ManageUiState,
    tab: ManageTab,
    reordering: Boolean,
    bottomInset: PaddingValues,
    actions: ManageActions,
) {
    ScreenColumn(bottomInset) {
        ScreenHeader(
            title = "Manage",
            actions = {
                if (state is ManageUiState.Ready) {
                    RitualIconButton(RitualIcons.Add, contentDescription = "Add habit", onClick = actions.onCreate)
                }
                SettingsButton(actions.onOpenSettings)
            },
        )
        Spacer(Modifier.height(Spacing.xl))

        when (state) {
            ManageUiState.Loading -> DelayedLoadingIndicator("Loading habits")
            ManageUiState.Error -> EmptyState(
                icon = RitualIcons.Manage,
                title = "Couldn't load your habits",
                body = "Your data is still safe on this device.",
                action = { SecondaryButton("Try again", onClick = actions.onRetry) },
            )
            is ManageUiState.Ready -> ReadyContent(state, tab, reordering, actions)
        }
    }
}

@Composable
private fun ReadyContent(state: ManageUiState.Ready, tab: ManageTab, reordering: Boolean, actions: ManageActions) {
    if (state.active.isEmpty() && state.archived.isEmpty()) {
        EmptyState(
            icon = RitualIcons.Manage,
            title = "No habits yet",
            body = "Create a habit and it will appear on Today, starting today.",
            action = { PrimaryButton("Add a habit", onClick = actions.onCreate) },
        )
        return
    }

    SegmentedChoice(
        options = ManageTab.entries,
        selected = tab,
        label = { if (it == ManageTab.ACTIVE) "Active · ${state.active.size}" else "Archived · ${state.archived.size}" },
        onSelect = actions.onSelectTab,
    )
    Spacer(Modifier.height(Spacing.xl))

    when (tab) {
        ManageTab.ACTIVE -> if (state.active.isEmpty()) {
            EmptyState(
                icon = RitualIcons.Today,
                title = "No active habits",
                body = "Restore one from Archived, or add a new habit.",
                action = { PrimaryButton("Add a habit", onClick = actions.onCreate) },
            )
        } else {
            ActiveList(state.active, reordering, actions)
        }
        ManageTab.ARCHIVED -> if (state.archived.isEmpty()) {
            EmptyState(
                icon = RitualIcons.Manage,
                title = "Nothing archived",
                body = "Archived habits leave Today but keep their history. You can restore them any time.",
            )
        } else {
            Text(
                "Not shown on Today. History is kept.",
                style = RitualType.bodySmall,
                color = Ritual.colors.textSecondary,
                modifier = Modifier.padding(start = Spacing.lg, bottom = Spacing.sm),
            )
            HabitModule(state.archived) { habit, _ ->
                HabitRow(
                    habit = habit,
                    meta = "Archived ${habit.archivedOn?.let(::formatShortDate).orEmpty()}",
                    muted = true,
                    onClick = { actions.onEdit(habit) },
                    menu = listOf(
                        MenuAction("Restore") { actions.onRestore(habit) },
                        MenuAction("Edit") { actions.onEdit(habit) },
                        MenuAction("Delete…") { actions.onDelete(habit) },
                    ),
                )
            }
        }
    }
}

@Composable
private fun ActiveList(active: List<ManagedHabit>, reordering: Boolean, actions: ManageActions) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            if (reordering) "Use the arrows to set the order on Today." else "Shown on Today in this order.",
            style = RitualType.bodySmall,
            color = Ritual.colors.textSecondary,
            modifier = Modifier.weight(1f).padding(start = Spacing.lg),
        )
        if (active.size > 1) {
            TextButton(
                onClick = { actions.onReorder(!reordering) },
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text(if (reordering) "Done" else "Reorder", style = RitualType.label, color = Ritual.colors.textPrimary)
            }
        }
    }
    Spacer(Modifier.height(Spacing.xs))
    HabitModule(active) { habit, index ->
        val moveUp = { actions.onMove(habit, -1) }
        val moveDown = { actions.onMove(habit, 1) }
        if (reordering) {
            ReorderRow(habit, canMoveUp = index > 0, canMoveDown = index < active.lastIndex, moveUp, moveDown)
        } else {
            HabitRow(
                habit = habit,
                meta = "Since ${formatShortDate(habit.createdOn)}",
                muted = false,
                onClick = { actions.onEdit(habit) },
                menu = listOf(
                    MenuAction("Edit") { actions.onEdit(habit) },
                    MenuAction("Archive") { actions.onArchive(habit) },
                    MenuAction("Delete…") { actions.onDelete(habit) },
                ),
                // TalkBack users can reorder from the actions menu without entering reorder mode.
                extraA11yActions = buildList {
                    if (index > 0) add(CustomAccessibilityAction("Move up") { moveUp(); true })
                    if (index < active.lastIndex) add(CustomAccessibilityAction("Move down") { moveDown(); true })
                },
            )
        }
    }
}

/** Hairline-separated rows in a module; rows glide to new positions when the order changes. */
@Composable
private fun HabitModule(habits: List<ManagedHabit>, row: @Composable (ManagedHabit, Int) -> Unit) {
    val motion = Ritual.motion
    Module(contentPadding = PaddingValues(horizontal = Spacing.lg)) {
        habits.forEachIndexed { index, habit ->
            key(habit.id) {
                Column(Modifier.animatePlacement(motion)) {
                    if (index > 0) Hairline()
                    row(habit, index)
                }
            }
        }
    }
}

@Composable
private fun HabitRow(
    habit: ManagedHabit,
    meta: String,
    muted: Boolean,
    onClick: () -> Unit,
    menu: List<MenuAction>,
    extraA11yActions: List<CustomAccessibilityAction> = emptyList(),
) {
    val c = Ritual.colors
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(color = c.textPrimary),
                role = Role.Button,
                onClickLabel = "Edit",
                onClick = onClick,
            )
            .semantics {
                customActions = menu.map { CustomAccessibilityAction(it.label.removeSuffix("…")) { it.onClick(); true } } +
                    extraA11yActions
            }
            .padding(vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = Spacing.sm)) {
            Text(habit.name, style = RitualType.rowTitle, color = if (muted) c.textSecondary else c.textPrimary)
            if (habit.description != null) {
                Spacer(Modifier.height(Spacing.xxs))
                Text(habit.description, style = RitualType.bodySmall, color = c.textSecondary, maxLines = 2)
            }
            Spacer(Modifier.height(Spacing.xxs))
            Text(meta, style = RitualType.caption.copy(fontFeatureSettings = null), color = c.textSecondary)
        }
        Box {
            RitualIconButton(
                RitualIcons.More,
                contentDescription = "More options for ${habit.name}",
                onClick = { menuOpen = true },
                outlined = false,
            )
            RitualMenu(expanded = menuOpen, onDismiss = { menuOpen = false }, actions = menu)
        }
    }
}

@Composable
private fun ReorderRow(
    habit: ManagedHabit,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    val c = Ritual.colors
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .padding(vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(habit.name, style = RitualType.rowTitle, color = c.textPrimary, modifier = Modifier.weight(1f))
        ArrowSlot(canMoveUp) {
            RitualIconButton(RitualIcons.ArrowUp, contentDescription = "Move ${habit.name} up", onClick = onMoveUp)
        }
        ArrowSlot(canMoveDown) {
            RitualIconButton(RitualIcons.ArrowDown, contentDescription = "Move ${habit.name} down", onClick = onMoveDown)
        }
    }
}

/** Keeps arrow columns aligned: an unavailable direction leaves an empty 48 dp slot. */
@Composable
private fun ArrowSlot(visible: Boolean, content: @Composable () -> Unit) {
    Box(Modifier.heightIn(min = 48.dp).padding(start = Spacing.xs), contentAlignment = Alignment.Center) {
        AnimatedVisibility(visible, enter = fadeIn(Ritual.motion.quick()), exit = fadeOut(Ritual.motion.quick())) {
            content()
        }
        if (!visible) Spacer(Modifier.padding(24.dp))
    }
}
