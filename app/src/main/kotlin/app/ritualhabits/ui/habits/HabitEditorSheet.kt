package app.ritualhabits.ui.habits

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.ritualhabits.core.rules.ReminderSchedule
import app.ritualhabits.data.repository.ReminderSetting
import app.ritualhabits.ui.components.RitualSwitch
import app.ritualhabits.ui.components.SecondaryButton
import app.ritualhabits.ui.theme.HapticEvent
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.datetime.LocalTime
import kotlinx.datetime.toJavaLocalTime
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.ritualhabits.core.rules.HabitInputError
import app.ritualhabits.core.rules.HabitValidation
import app.ritualhabits.ui.components.Hairline
import app.ritualhabits.ui.components.Module
import app.ritualhabits.ui.components.PrimaryButton
import app.ritualhabits.ui.theme.Radii
import app.ritualhabits.ui.theme.Ritual
import app.ritualhabits.ui.theme.RitualType
import app.ritualhabits.ui.theme.Spacing

/** Form state shared by "add" (Today, Manage) and "edit" (Manage). */
data class HabitFormState(
    val name: String = "",
    val description: String = "",
    val error: HabitInputError? = null,
    val saving: Boolean = false,
    /** Daily reminder. Off by default; switching it on starts at 9:00 AM. */
    val reminderEnabled: Boolean = false,
    val reminderTime: LocalTime = ReminderSchedule.DEFAULT_TIME,
) {
    val canSave: Boolean get() = name.isNotBlank() && !saving

    /** The reminder to save with the habit. */
    val reminder: ReminderSetting get() = ReminderSetting(reminderEnabled, reminderTime)
}

/** "9:00 AM" or "09:00", following the device's 12/24-hour setting. */
fun formatReminderTime(time: LocalTime, is24Hour: Boolean, locale: Locale = Locale.getDefault()): String =
    DateTimeFormatter.ofPattern(if (is24Hour) "HH:mm" else "h:mm a", locale).format(time.toJavaLocalTime())

/** User-facing copy for validation errors. Rules themselves live in core's HabitValidation. */
fun HabitInputError.message(): String = when (this) {
    HabitInputError.NAME_EMPTY -> "Give your habit a name."
    HabitInputError.NAME_TOO_LONG -> "Keep the name to ${HabitValidation.NAME_MAX} characters or fewer."
    HabitInputError.NAME_DUPLICATE -> "You already have a habit with this name."
    HabitInputError.DESCRIPTION_TOO_LONG -> "Keep the description to ${HabitValidation.DESCRIPTION_MAX} characters or fewer."
}

/**
 * Sheet in the style of a native form: a Cancel / title header, name and description as one grouped
 * field block on the page tone, validation in plain text below (announced politely), and a single
 * full-width primary action. The name field is focused on open; the keyboard's Done saves.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitEditorSheet(
    title: String,
    confirmLabel: String,
    state: HabitFormState,
    onNameChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
    onReminderEnabledChange: (Boolean) -> Unit = {},
    onReminderTimeChange: (LocalTime) -> Unit = {},
) {
    val c = Ritual.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val nameFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { nameFocus.requestFocus() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = c.background,
        contentColor = c.textPrimary,
        scrimColor = c.scrim,
        shape = RoundedCornerShape(topStart = Radii.xl, topEnd = Radii.xl),
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
                .imePadding()
                .padding(horizontal = Ritual.layout.gutter)
                .padding(bottom = Spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.widthIn(max = 560.dp).fillMaxWidth()) {
                // Header: Cancel on the leading side, title centred.
                Box(Modifier.fillMaxWidth().heightIn(min = 48.dp), contentAlignment = Alignment.Center) {
                    TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.CenterStart).heightIn(min = 48.dp)) {
                        Text("Cancel", style = RitualType.rowTitle, color = c.textPrimary)
                    }
                    Text(
                        title,
                        style = RitualType.value,
                        color = c.textPrimary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.semantics { heading() },
                    )
                }
                Spacer(Modifier.height(Spacing.lg))

                Module(contentPadding = PaddingValues(0.dp)) {
                    TextField(
                        value = state.name,
                        onValueChange = onNameChange,
                        label = { Text("Name") },
                        singleLine = true,
                        isError = state.error != null && state.error != HabitInputError.DESCRIPTION_TOO_LONG,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                        colors = fieldColors(),
                        textStyle = RitualType.rowTitle,
                        modifier = Modifier.fillMaxWidth().focusRequester(nameFocus),
                    )
                    Hairline(startIndent = Spacing.lg)
                    TextField(
                        value = state.description,
                        onValueChange = onDescriptionChange,
                        label = { Text("Description (optional)") },
                        maxLines = 3,
                        isError = state.error == HabitInputError.DESCRIPTION_TOO_LONG,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { if (state.canSave) onSave() }),
                        colors = fieldColors(),
                        textStyle = RitualType.rowTitle,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                FormFootnote(state)
                Spacer(Modifier.height(Spacing.xl))
                ReminderSection(state, onReminderEnabledChange, onReminderTimeChange)
                Spacer(Modifier.height(Spacing.xl))
                PrimaryButton(confirmLabel, onClick = onSave, enabled = state.canSave, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

/** Below the field group: the validation message, or a character count near the limits. */
@Composable
private fun FormFootnote(state: HabitFormState) {
    val c = Ritual.colors
    val text: String? = when {
        state.error != null -> state.error.message()
        state.name.length > HabitValidation.NAME_MAX * 3 / 4 -> "Name: ${state.name.length} / ${HabitValidation.NAME_MAX}"
        state.description.length > HabitValidation.DESCRIPTION_MAX * 3 / 4 ->
            "Description: ${state.description.length} / ${HabitValidation.DESCRIPTION_MAX}"
        else -> null
    }
    if (text != null) {
        Text(
            text,
            style = RitualType.caption,
            color = if (state.error != null) c.textPrimary else c.textSecondary,
            modifier = Modifier
                .padding(start = Spacing.lg, top = Spacing.sm)
                .semantics { if (state.error != null) liveRegion = LiveRegionMode.Polite },
        )
    }
}

@Composable
private fun fieldColors() = Ritual.colors.let { c ->
    TextFieldDefaults.colors(
        focusedTextColor = c.textPrimary,
        unfocusedTextColor = c.textPrimary,
        errorTextColor = c.textPrimary,
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent,
        errorContainerColor = Color.Transparent,
        focusedIndicatorColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent,
        errorIndicatorColor = Color.Transparent,
        focusedLabelColor = c.textSecondary,
        unfocusedLabelColor = c.textSecondary,
        errorLabelColor = c.textPrimary,
        cursorColor = c.textPrimary,
        errorCursorColor = c.textPrimary,
    )
}

/**
 * Daily reminder: a switch, and when on, the time (opens the time picker). Turning it on is when
 * Ritual asks for notification permission (Android 13+); if notifications are off, a note says so.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderSection(
    state: HabitFormState,
    onEnabledChange: (Boolean) -> Unit,
    onTimeChange: (LocalTime) -> Unit,
) {
    val c = Ritual.colors
    val context = LocalContext.current
    val haptics = Ritual.haptics
    val is24Hour = DateFormat.is24HourFormat(context)
    var picking by remember { mutableStateOf(false) }
    var notificationsOn by remember { mutableStateOf(notificationsAllowed(context)) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        notificationsOn = notificationsAllowed(context)
    }
    LifecycleResumeEffect(Unit) {
        notificationsOn = notificationsAllowed(context)
        onPauseOrDispose { }
    }

    Text(
        "Reminder",
        style = RitualType.overline,
        color = c.textSecondary,
        modifier = Modifier.padding(start = Spacing.lg, bottom = Spacing.sm).semantics { heading() },
    )
    Module(contentPadding = PaddingValues(0.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .toggleable(value = state.reminderEnabled, role = Role.Switch) { enabled ->
                    haptics.perform(if (enabled) HapticEvent.ToggleOn else HapticEvent.ToggleOff)
                    onEnabledChange(enabled)
                    if (enabled && Build.VERSION.SDK_INT >= 33 && !notificationsAllowed(context)) {
                        permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
                .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Daily reminder", style = RitualType.rowTitle, color = c.textPrimary)
                Text("A gentle nudge if it isn't done yet.", style = RitualType.caption.copy(fontFeatureSettings = null), color = c.textSecondary)
            }
            RitualSwitch(checked = state.reminderEnabled, onCheckedChange = null)
        }
        AnimatedVisibility(
            visible = state.reminderEnabled,
            enter = fadeIn(Ritual.motion.quick()) + expandVertically(Ritual.motion.standard()),
            exit = fadeOut(Ritual.motion.exit()) + shrinkVertically(Ritual.motion.standard()),
        ) {
            Column {
                Hairline(startIndent = Spacing.lg)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .clickable(role = Role.Button, onClickLabel = "Change reminder time") { picking = true }
                        .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Time", style = RitualType.rowTitle, color = c.textPrimary, modifier = Modifier.weight(1f))
                    Text(
                        formatReminderTime(state.reminderTime, is24Hour),
                        style = RitualType.value,
                        color = c.textPrimary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(Radii.sm))
                            .background(c.fill)
                            .padding(horizontal = Spacing.md, vertical = Spacing.xs),
                    )
                }
            }
        }
    }
    if (state.reminderEnabled && !notificationsOn) {
        Row(Modifier.padding(start = Spacing.lg, top = Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Notifications are off for Ritual, so this reminder can't appear.",
                style = RitualType.caption.copy(fontFeatureSettings = null),
                color = c.textSecondary,
                modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
            )
            TextButton(
                onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                    )
                },
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text("Settings", style = RitualType.label, color = c.textPrimary) }
        }
    }

    if (picking) {
        val pickerState = rememberTimePickerState(state.reminderTime.hour, state.reminderTime.minute, is24Hour)
        AlertDialog(
            onDismissRequest = { picking = false },
            containerColor = c.surface,
            shape = RoundedCornerShape(Radii.xl),
            tonalElevation = 0.dp,
            title = { Text("Reminder time", style = RitualType.heading, color = c.textPrimary) },
            text = {
                TimePicker(
                    state = pickerState,
                    colors = TimePickerDefaults.colors(
                        clockDialColor = c.fill,
                        clockDialSelectedContentColor = c.onAccent,
                        clockDialUnselectedContentColor = c.textPrimary,
                        selectorColor = c.accent,
                        containerColor = c.surface,
                        periodSelectorBorderColor = c.borderStrong,
                        periodSelectorSelectedContainerColor = c.accent,
                        periodSelectorUnselectedContainerColor = c.surface,
                        periodSelectorSelectedContentColor = c.onAccent,
                        periodSelectorUnselectedContentColor = c.textPrimary,
                        timeSelectorSelectedContainerColor = c.accent,
                        timeSelectorUnselectedContainerColor = c.fill,
                        timeSelectorSelectedContentColor = c.onAccent,
                        timeSelectorUnselectedContentColor = c.textPrimary,
                    ),
                )
            },
            confirmButton = {
                PrimaryButton("Done", onClick = {
                    onTimeChange(LocalTime(pickerState.hour, pickerState.minute))
                    picking = false
                })
            },
            dismissButton = { SecondaryButton("Cancel", onClick = { picking = false }) },
        )
    }
}

private fun notificationsAllowed(context: Context): Boolean {
    val granted = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    return granted && NotificationManagerCompat.from(context).areNotificationsEnabled()
}
