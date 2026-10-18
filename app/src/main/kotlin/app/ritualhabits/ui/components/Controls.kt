package app.ritualhabits.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.ritualhabits.ui.theme.HapticEvent
import app.ritualhabits.ui.theme.Radii
import app.ritualhabits.ui.theme.Ritual
import app.ritualhabits.ui.theme.RitualType
import app.ritualhabits.ui.theme.Sizes
import app.ritualhabits.ui.theme.Spacing

/** Tactile press response shared by custom tappables: a gentle 0.96 scale while pressed. */
@Composable
fun Modifier.pressScale(interaction: MutableInteractionSource): Modifier {
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, Ritual.motion.quick(), label = "press")
    return scale(scale)
}

/** Filled capsule: the one primary action on a screen or sheet. */
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val interaction = remember { MutableInteractionSource() }
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.defaultMinSize(minHeight = 52.dp).pressScale(interaction),
        interactionSource = interaction,
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = Ritual.colors.accent,
            contentColor = Ritual.colors.onAccent,
            disabledContainerColor = Ritual.colors.fill,
            disabledContentColor = Ritual.colors.textTertiary,
        ),
        elevation = null,
        contentPadding = PaddingValues(horizontal = Spacing.xl, vertical = Spacing.md),
    ) { Text(text, style = RitualType.label, textAlign = TextAlign.Center) }
}

/** Tonal capsule for secondary actions: no outline, just a quiet fill. */
@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val interaction = remember { MutableInteractionSource() }
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.defaultMinSize(minHeight = 52.dp).pressScale(interaction),
        interactionSource = interaction,
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = Ritual.colors.fill,
            contentColor = Ritual.colors.textPrimary,
            disabledContainerColor = Ritual.colors.fill,
            disabledContentColor = Ritual.colors.textTertiary,
        ),
        elevation = null,
        contentPadding = PaddingValues(horizontal = Spacing.xl, vertical = Spacing.md),
    ) { Text(text, style = RitualType.label, textAlign = TextAlign.Center) }
}

/**
 * Circular icon button: a 48 dp touch target with a 40 dp tonal disc ([outlined] = true) or a bare
 * glyph. [contentDescription] is required — icon-only controls must be labelled.
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
            .clickable(
                interactionSource = interaction,
                indication = ripple(color = Ritual.colors.textPrimary),
                onClick = onClick,
            )
            .semantics {
                this.contentDescription = contentDescription
                role = Role.Button
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(Sizes.iconButton)
                .pressScale(interaction)
                .clip(CircleShape)
                .background(if (outlined) Ritual.colors.fill else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = Ritual.colors.textPrimary, modifier = Modifier.size(Sizes.iconSmall))
        }
    }
}

/** Monochrome switch: ink track when on, tonal track when off, no outlines. */
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
            checkedBorderColor = Color.Transparent,
            uncheckedThumbColor = if (c.isDark) c.textSecondary else c.surface,
            uncheckedTrackColor = c.fill,
            uncheckedBorderColor = Color.Transparent,
        ),
    )
}

/**
 * Segmented single choice with a raised thumb that slides between options. Each segment is a
 * radio button for TalkBack ("selected, 2 of 3") and at least 48 dp tall; the control grows with
 * the font size.
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
    val motion = Ritual.motion
    val index = options.indexOf(selected).coerceAtLeast(0)
    val trackShape = RoundedCornerShape(Radii.md)
    val thumbShape = RoundedCornerShape(Radii.md - 3.dp)

    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .clip(trackShape)
            .background(c.fill)
            .padding(3.dp),
    ) {
        val segmentWidth = maxWidth / options.size
        val thumbOffset by animateDpAsState(segmentWidth * index, motion.settle(), label = "thumb")
        Box(Modifier.matchParentSize()) {
            Box(
                Modifier
                    .offset(x = thumbOffset)
                    .width(segmentWidth)
                    .fillMaxHeight()
                    .shadow(if (c.isDark) 0.dp else 2.dp, thumbShape, clip = false)
                    .clip(thumbShape)
                    .background(c.surfaceRaised),
            )
        }
        Row(Modifier.fillMaxWidth().selectableGroup()) {
            options.forEach { option ->
                val isSelected = option == selected
                val fg by animateColorAsState(if (isSelected) c.textPrimary else c.textSecondary, motion.quick(), label = "segText")
                Box(
                    Modifier
                        .weight(1f)
                        .heightIn(min = Sizes.minTouchTarget - 6.dp)
                        .selectable(selected = isSelected, role = Role.RadioButton, interactionSource = null, indication = null) {
                            if (!isSelected) {
                                haptics.perform(HapticEvent.Selection)
                                onSelect(option)
                            }
                        }
                        .padding(horizontal = Spacing.sm, vertical = Spacing.sm),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label(option),
                        style = RitualType.overline.copy(fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium),
                        color = fg,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

/** Clickable with a neutral ripple. Pair with semantics contentDescription for icon-only targets. */
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
