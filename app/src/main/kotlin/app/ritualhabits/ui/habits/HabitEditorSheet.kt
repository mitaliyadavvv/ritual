package app.ritualhabits.ui.habits

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
) {
    val canSave: Boolean get() = name.isNotBlank() && !saving
}

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
