package app.ritualhabits.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.ritualhabits.ui.theme.HapticEvent
import app.ritualhabits.ui.theme.Ritual
import app.ritualhabits.ui.theme.RitualType
import app.ritualhabits.ui.theme.Sizes
import app.ritualhabits.ui.theme.Spacing
import kotlinx.coroutines.launch

data class BottomBarItem<K>(val key: K, val label: String, val icon: ImageVector)

private val TabLabelStyle = RitualType.caption.copy(fontWeight = FontWeight.Medium, fontFeatureSettings = null)

/** Gap between the capsule's edge and the selection bubble, on every side. */
private val BarInset = 5.dp
private val TabSidePadding = Spacing.xs

/**
 * Floating glass capsule with a raised glass bubble around the selected tab's icon and label,
 * modelled on a native "liquid glass" tab bar:
 *
 * - The bar is a full stadium capsule, inset from the screen edges and the gesture bar, on the
 *   translucent glass material with a luminous rim.
 * - The selected tab sits inside a brighter bubble with its own rim, inset [BarInset] from the
 *   capsule. Selection is shown by the bubble's shape, not by colour: every icon and label stays at
 *   full strength.
 * - Switching tabs glides the bubble to its new place; its leading edge moves slightly ahead of the
 *   trailing edge, so it stretches a little and settles (snaps when animations are off).
 * - A soft fade of the page colour sits behind the bar so content scrolling underneath recedes
 *   instead of colliding with it.
 *
 * Labels follow the user's font size and are never capped: when they no longer fit, only the
 * selected label shows (every tab keeps its accessible name) and the bar grows taller.
 */
@Composable
fun <K> RitualBottomBar(
    items: List<BottomBarItem<K>>,
    selected: K?,
    onSelect: (K) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Ritual.colors
    Box(modifier.fillMaxWidth()) {
        // Content fade behind the floating bar.
        Box(
            Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        0f to c.background.copy(alpha = 0f),
                        0.45f to c.background.copy(alpha = 0.7f),
                        1f to c.background,
                    ),
                ),
        )
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = Spacing.xl)
                .padding(top = Spacing.lg, bottom = Spacing.sm),
            contentAlignment = Alignment.Center,
        ) {
            val barWidth = if (maxWidth < Ritual.layout.bottomBarMaxWidth) maxWidth else Ritual.layout.bottomBarMaxWidth
            val selectedIndex = items.indexOfFirst { it.key == selected }
            val labelWidths = measureLabels(items.map { it.label })
            val layout = tabLayout(barWidth - BarInset * 2, labelWidths, selectedIndex)

            Box(
                Modifier
                    .width(barWidth)
                    .heightIn(min = Sizes.bottomBarHeight)
                    .glassSurface(CircleShape)
                    .padding(BarInset),
            ) {
                if (selectedIndex >= 0) {
                    val left = layout.widths.take(selectedIndex).fold(0.dp) { acc, w -> acc + w }
                    SelectionBubble(left, left + layout.widths[selectedIndex], Modifier.matchParentSize())
                }
                Row(
                    Modifier.fillMaxWidth().selectableGroup(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    items.forEachIndexed { index, item ->
                        val width by animateDpAsState(layout.widths[index], Ritual.motion.settle(), label = "tabWidth")
                        BottomBarTab(
                            item = item,
                            isSelected = index == selectedIndex,
                            showLabel = layout.showAllLabels || index == selectedIndex,
                            onClick = { onSelect(item.key) },
                            modifier = Modifier.width(width),
                        )
                    }
                }
            }
        }
    }
}

/**
 * The raised bubble behind the selected tab, spanning [targetLeft]..[targetRight]. Its two edges
 * animate separately: the edge in the direction of travel leads on a stiffer spring and the other
 * follows, giving a subtle liquid stretch before it settles.
 */
@Composable
private fun SelectionBubble(targetLeft: Dp, targetRight: Dp, modifier: Modifier) {
    val c = Ritual.colors
    val motion = Ritual.motion
    val left = remember { Animatable(targetLeft, Dp.VectorConverter) }
    val right = remember { Animatable(targetRight, Dp.VectorConverter) }
    var placed by remember { mutableStateOf(false) }

    LaunchedEffect(targetLeft, targetRight) {
        if (motion.reduced || !placed) {
            // First placement appears in place; with animations off every move snaps.
            left.snapTo(targetLeft)
            right.snapTo(targetRight)
            placed = true
            return@LaunchedEffect
        }
        val movingRight = targetLeft > left.value
        val lead = spring<Dp>(dampingRatio = 0.78f, stiffness = Spring.StiffnessMedium)
        val trail = spring<Dp>(dampingRatio = 0.86f, stiffness = Spring.StiffnessMediumLow)
        launch { left.animateTo(targetLeft, if (movingRight) trail else lead) }
        launch { right.animateTo(targetRight, if (movingRight) lead else trail) }
    }

    Box(modifier) {
        val width = (right.value - left.value).coerceAtLeast(0.dp)
        Box(
            Modifier
                .offset { IntOffset(left.value.roundToPx(), 0) }
                .width(width)
                .fillMaxHeight()
                .glassSurface(CircleShape, elevation = 6.dp, fill = c.glassSelected, edgeStrength = 1.4f),
        )
    }
}

/** Tab widths for the current font scale, and whether every label is shown. */
internal class TabLayout(val widths: List<Dp>, val showAllLabels: Boolean)

/**
 * Equal tabs when every label fits. Otherwise only the selected label shows: the selected tab widens
 * to fit it (so it is never clipped) and the icon-only tabs share the rest, each ≥ 52 dp.
 */
internal fun tabLayout(inner: Dp, labelWidths: List<Dp>, selectedIndex: Int): TabLayout {
    val n = labelWidths.size
    val equal = inner / n
    val padding = TabSidePadding * 2
    if (labelWidths.all { it + padding <= equal } || selectedIndex < 0) return TabLayout(List(n) { equal }, true)
    val minIconTab = Sizes.minTouchTarget + Spacing.xs
    val wanted = labelWidths[selectedIndex] + padding + Spacing.lg
    val selectedWidth = maxOf(equal, minOf(wanted, inner - minIconTab * (n - 1)))
    val other = (inner - selectedWidth) / (n - 1)
    return TabLayout(List(n) { if (it == selectedIndex) selectedWidth else other }, false)
}

/** Natural one-line width of each tab label at the current font scale. */
@Composable
private fun measureLabels(labels: List<String>): List<Dp> {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    return labels.map { with(density) { measurer.measure(it, TabLabelStyle, maxLines = 1).size.width.toDp() } }
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

    Column(
        modifier
            .heightIn(min = Sizes.bottomBarHeight - BarInset * 2)
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
        Icon(item.icon, contentDescription = null, tint = c.textPrimary, modifier = Modifier.size(Sizes.icon))
        AnimatedVisibility(
            visible = showLabel,
            enter = fadeIn(motion.quick()) + expandVertically(motion.quick()),
            exit = fadeOut(motion.exit()) + shrinkVertically(motion.exit()),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    item.label,
                    style = TabLabelStyle,
                    color = c.textPrimary,
                    maxLines = 1,
                    softWrap = false,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
