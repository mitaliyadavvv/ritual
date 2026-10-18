package app.ritualhabits.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.ritualhabits.ui.theme.HapticEvent
import app.ritualhabits.ui.theme.Radii
import app.ritualhabits.ui.theme.Ritual
import app.ritualhabits.ui.theme.RitualType
import app.ritualhabits.ui.theme.Sizes
import app.ritualhabits.ui.theme.Spacing

/** Subtle press response shared by custom tappables: scale to 0.97 while pressed. */
@Composable
fun Modifier.pressScale(interaction: MutableInteractionSource): Modifier {
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, Ritual.motion.quick(), label = "press")
    return scale(scale)
}

/** Filled, high-emphasis action. One per screen at most. */
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val interaction = remember { MutableInteractionSource() }
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.defaultMinSize(minHeight = Sizes.minTouchTarget).pressScale(interaction),
        interactionSource = interaction,
        shape = RoundedCornerShape(Radii.md),
        colors = ButtonDefaults.buttonColors(
            containerColor = Ritual.colors.accent,
            contentColor = Ritual.colors.onAccent,
            disabledContainerColor = Ritual.colors.surfaceRaised,
            disabledContentColor = Ritual.colors.textTertiary,
        ),
        elevation = null,
        contentPadding = PaddingValues(horizontal = Spacing.xl, vertical = Spacing.md),
    ) { Text(text, style = RitualType.label) }
}

/** Hairline-outlined, medium-emphasis action. */
@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val interaction = remember { MutableInteractionSource() }
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.defaultMinSize(minHeight = Sizes.minTouchTarget).pressScale(interaction),
        interactionSource = interaction,
        shape = RoundedCornerShape(Radii.md),
        border = BorderStroke(Sizes.hairline, Ritual.colors.borderStrong),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Ritual.colors.textPrimary),
        contentPadding = PaddingValues(horizontal = Spacing.xl, vertical = Spacing.md),
    ) { Text(text, style = RitualType.label) }
}

/**
 * Circular icon button with a 48 dp touch target and a 40 dp visible hairline ring.
 * [contentDescription] is required: icon-only controls must be labelled.
 */
@Composable
fun RitualIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    outlined: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .size(Sizes.minTouchTarget)
            .clip(CircleShape)
            .semantics { this.contentDescription = contentDescription }
            .ritualClickable(interaction, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(40.dp)
                .pressScale(interaction)
                .then(if (outlined) Modifier.border(Sizes.hairline, Ritual.colors.border, CircleShape) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = Ritual.colors.textPrimary, modifier = Modifier.size(Sizes.iconSmall))
        }
    }
}

/** Monochrome switch: filled track when on, hairline track when off. */
@Composable
fun RitualSwitch(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, modifier: Modifier = Modifier) {
    val c = Ritual.colors
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        colors = SwitchDefaults.colors(
            checkedThumbColor = c.onAccent,
            checkedTrackColor = c.accent,
            checkedBorderColor = c.accent,
            uncheckedThumbColor = c.textSecondary,
            uncheckedTrackColor = c.surfaceRaised,
            uncheckedBorderColor = c.borderStrong,
        ),
    )
}

/**
 * Segmented single choice (e.g. System / Light / Dark). Each segment is a 48 dp target and
 * exposes radio-button semantics, so TalkBack announces "selected, 2 of 3".
 */
@Composable
fun <T> SegmentedChoice(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Ritual.colors
    val haptics = Ritual.haptics
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(Sizes.minTouchTarget)
            .clip(RoundedCornerShape(Radii.md))
            .background(c.surfaceRaised)
            .padding(Spacing.xs)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            val bg by animateColorAsState(if (isSelected) c.surface else c.surfaceRaised, Ritual.motion.quick(), label = "seg")
            val fg by animateColorAsState(if (isSelected) c.textPrimary else c.textSecondary, Ritual.motion.quick(), label = "segText")
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .height(Sizes.minTouchTarget - Spacing.sm)
                    .clip(RoundedCornerShape(Radii.sm))
                    .background(bg)
                    .then(if (isSelected) Modifier.border(Sizes.hairline, c.border, RoundedCornerShape(Radii.sm)) else Modifier)
                    .selectable(selected = isSelected, role = Role.RadioButton) {
                        if (!isSelected) {
                            haptics.perform(HapticEvent.Selection)
                            onSelect(option)
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(label(option), style = RitualType.label, color = fg, textAlign = TextAlign.Center, maxLines = 1)
            }
        }
    }
}

/** Clickable with a neutral ripple. Pair with [semantics] contentDescription for icon-only targets. */
@Composable
fun Modifier.ritualClickable(
    interaction: MutableInteractionSource,
    role: Role,
    onClick: () -> Unit,
): Modifier = clickable(
    interactionSource = interaction,
    indication = ripple(color = Ritual.colors.textPrimary),
    role = role,
    onClick = onClick,
)
