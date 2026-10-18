package app.ritualhabits.ui.habits

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import app.ritualhabits.core.rules.HabitInputError
import app.ritualhabits.core.rules.HabitValidation
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
 * Bottom sheet with name + optional description. The name field is focused on open, the
 * keyboard's Done saves, and errors are announced politely (live region) and shown as text,
 * not colour alone.
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
        containerColor = c.surface,
        contentColor = c.textPrimary,
        scrimColor = c.scrim,
        shape = RoundedCornerShape(topStart = Radii.xl, topEnd = Radii.xl),
        tonalElevation = 0.dp,
        dragHandle = {
            Box(
                Modifier
                    .padding(top = Spacing.md, bottom = Spacing.sm)
                    .size(width = 36.dp, height = 4.dp)
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
                Text(title, style = RitualType.heading, color = c.textPrimary, modifier = Modifier.semantics { heading() })
                Spacer(Modifier.height(Spacing.lg))

                val nameError = state.error?.takeIf { it != HabitInputError.DESCRIPTION_TOO_LONG }
                OutlinedTextField(
                    value = state.name,
                    onValueChange = onNameChange,
                    label = { Text("Name") },
                    singleLine = true,
                    isError = nameError != null,
                    supportingText = { FieldSupport(nameError, state.name.length, HabitValidation.NAME_MAX) },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                    colors = fieldColors(),
                    shape = RoundedCornerShape(Radii.md),
                    modifier = Modifier.fillMaxWidth().focusRequester(nameFocus),
                )
                Spacer(Modifier.height(Spacing.sm))

                val descriptionError = state.error?.takeIf { it == HabitInputError.DESCRIPTION_TOO_LONG }
                OutlinedTextField(
                    value = state.description,
                    onValueChange = onDescriptionChange,
                    label = { Text("Description (optional)") },
                    maxLines = 3,
                    isError = descriptionError != null,
                    supportingText = { FieldSupport(descriptionError, state.description.length, HabitValidation.DESCRIPTION_MAX) },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (state.canSave) onSave() }),
                    colors = fieldColors(),
                    shape = RoundedCornerShape(Radii.md),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(Spacing.lg))
                PrimaryButton(confirmLabel, onClick = onSave, enabled = state.canSave, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun FieldSupport(error: HabitInputError?, length: Int, max: Int) {
    if (error != null) {
        Text(
            error.message(),
            style = RitualType.caption,
            color = Ritual.colors.textPrimary,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
    } else if (length > max * 3 / 4) {
        Text("$length / $max", style = RitualType.caption, color = Ritual.colors.textSecondary)
    }
}

@Composable
private fun fieldColors() = Ritual.colors.let { c ->
    OutlinedTextFieldDefaults.colors(
        focusedTextColor = c.textPrimary,
        unfocusedTextColor = c.textPrimary,
        focusedBorderColor = c.textPrimary,
        unfocusedBorderColor = c.borderStrong,
        errorBorderColor = c.textPrimary,
        focusedLabelColor = c.textPrimary,
        unfocusedLabelColor = c.textSecondary,
        errorLabelColor = c.textPrimary,
        cursorColor = c.textPrimary,
        errorCursorColor = c.textPrimary,
        errorTextColor = c.textPrimary,
        errorSupportingTextColor = c.textPrimary,
        focusedContainerColor = c.surface,
        unfocusedContainerColor = c.surface,
        errorContainerColor = c.surface,
    )
}
