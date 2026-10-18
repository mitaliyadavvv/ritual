package app.ritualhabits.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.ritualhabits.ui.theme.HapticEvent
import app.ritualhabits.ui.theme.Radii
import app.ritualhabits.ui.theme.Ritual
import app.ritualhabits.ui.theme.RitualType
import app.ritualhabits.ui.theme.Sizes
import app.ritualhabits.ui.theme.Spacing

data class BottomBarItem<K>(val key: K, val label: String, val icon: ImageVector)

private val TabLabelStyle = RitualType.caption.copy(fontWeight = FontWeight.Medium, fontFeatureSettings = null)
private val TabSidePadding = Spacing.xs

/**
 * Floating tab bar on Ritual's glass material, inset from the screen edges and the gesture bar.
 * The selected tab gets a soft tonal pill behind its icon that settles in with a spring; labels
 * stay at full contrast. Labels follow the user's font size and are never capped: when they no
 * longer fit, only the selected label shows (every tab keeps its accessible name) and the bar
 * grows taller as needed.
 */
@Composable
fun <K> RitualBottomBar(
    items: List<BottomBarItem<K>>,
    selected: K?,
    onSelect: (K) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(Radii.xl)
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = Spacing.xl, vertical = Spacing.sm),
        contentAlignment = Alignment.Center,
    ) {
        val barWidth = if (maxWidth < Ritual.layout.bottomBarMaxWidth) maxWidth else Ritual.layout.bottomBarMaxWidth
        val tabWidth = (barWidth - Spacing.sm * 2) / items.size - TabSidePadding * 2
        val showAllLabels = labelsFit(items.map { it.label }, tabWidth)
        Row(
            Modifier
                .widthIn(max = Ritual.layout.bottomBarMaxWidth)
                .fillMaxWidth()
                .heightIn(min = Sizes.bottomBarHeight)
                .glassSurface(shape)
                .padding(horizontal = Spacing.sm, vertical = Spacing.xs)
                .selectableGroup(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEach { item ->
                val isSelected = item.key == selected
                BottomBarTab(
                    item = item,
                    isSelected = isSelected,
                    showLabel = showAllLabels || isSelected,
                    onClick = { onSelect(item.key) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** True when every label fits its tab at the current font scale. */
@Composable
private fun labelsFit(labels: List<String>, tabWidth: Dp): Boolean {
    val measurer = rememberTextMeasurer()
    val available = with(LocalDensity.current) { tabWidth.roundToPx() }
    return labels.all { measurer.measure(it, TabLabelStyle, maxLines = 1).size.width <= available }
}

@Composable
private fun <K> BottomBarTab(
    item: BottomBarItem<K>,
    isSelected: Boolean,
    showLabel: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val c = Ritual.colors
    val motion = Ritual.motion
    val haptics = Ritual.haptics
    val interaction = remember { MutableInteractionSource() }
    val content by animateColorAsState(if (isSelected) c.textPrimary else c.textSecondary, motion.quick(), label = "tabContent")
    val pill by animateFloatAsState(if (isSelected) 1f else 0f, motion.settle(), label = "tabPill")

    Column(
        modifier
            .heightIn(min = Sizes.minTouchTarget)
            .selectable(
                selected = isSelected,
                interactionSource = interaction,
                indication = null,
                role = Role.Tab,
            ) {
                if (!isSelected) haptics.perform(HapticEvent.Selection)
                onClick()
            }
            // Keeps the tab's name available to TalkBack even when its label is hidden.
            .then(if (showLabel) Modifier else Modifier.semantics { contentDescription = item.label })
            .pressScale(interaction)
            .padding(horizontal = TabSidePadding, vertical = Spacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(width = 56.dp, height = 30.dp)
                    .graphicsLayer {
                        alpha = pill.coerceIn(0f, 1f)
                        scaleX = 0.6f + 0.4f * pill
                        scaleY = 0.6f + 0.4f * pill
                    }
                    .clip(CircleShape)
                    .background(c.fill),
            )
            Icon(item.icon, contentDescription = null, tint = content, modifier = Modifier.size(Sizes.icon))
        }
        AnimatedVisibility(
            visible = showLabel,
            enter = fadeIn(motion.quick()) + expandVertically(motion.quick()),
            exit = fadeOut(motion.exit()) + shrinkVertically(motion.exit()),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    item.label,
                    style = TabLabelStyle.copy(fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium),
                    color = content,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}

