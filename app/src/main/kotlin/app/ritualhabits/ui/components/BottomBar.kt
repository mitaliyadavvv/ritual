package app.ritualhabits.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.ritualhabits.ui.theme.HapticEvent
import app.ritualhabits.ui.theme.Radii
import app.ritualhabits.ui.theme.Ritual
import app.ritualhabits.ui.theme.RitualType
import app.ritualhabits.ui.theme.Sizes
import app.ritualhabits.ui.theme.Spacing

data class BottomBarItem<K>(val key: K, val label: String, val icon: ImageVector)

/**
 * Floating bottom navigation. This is the one place Ritual uses translucency: a near-opaque
 * "glass" fill (≥ 90 % alpha) plus a hairline border, so labels stay at full contrast over
 * any content scrolling beneath. No blur: it costs GPU time and lowers legibility.
 */
@Composable
fun <K> RitualBottomBar(
    items: List<BottomBarItem<K>>,
    selected: K?,
    onSelect: (K) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Ritual.colors
    val shape = RoundedCornerShape(Radii.xl)
    // Tab labels sit under icons, so (like the system navigation bar) their scale is capped
    // to keep four labels on one line at large font sizes. Everything else scales freely.
    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(density.density, density.fontScale.coerceAtMost(MAX_LABEL_FONT_SCALE)),
    ) {
        Box(
            modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                Modifier
                    .widthIn(max = Ritual.layout.bottomBarMaxWidth)
                    .fillMaxWidth()
                    .height(Sizes.bottomBarHeight)
                    .background(c.glass, shape)
                    .border(Sizes.hairline, c.border, shape)
                    .padding(horizontal = Spacing.xs)
                    .selectableGroup(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items.forEach { item ->
                    BottomBarTab(item, isSelected = item.key == selected, onClick = { onSelect(item.key) }, Modifier.weight(1f))
                }
            }
        }
    }
}

private const val MAX_LABEL_FONT_SCALE = 1.3f

@Composable
private fun <K> BottomBarTab(item: BottomBarItem<K>, isSelected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val c = Ritual.colors
    val haptics = Ritual.haptics
    val interaction = remember { MutableInteractionSource() }
    val tint by animateColorAsState(if (isSelected) c.textPrimary else c.textSecondary, Ritual.motion.quick(), label = "tabTint")
    val pill by animateColorAsState(if (isSelected) c.surfaceRaised else c.glass.copy(alpha = 0f), Ritual.motion.standard(), label = "tabPill")

    Column(
        modifier
            .fillMaxHeight()
            .selectable(
                selected = isSelected,
                interactionSource = interaction,
                indication = ripple(bounded = false, radius = 32.dp, color = c.textPrimary),
                role = Role.Tab,
            ) {
                if (!isSelected) haptics.perform(HapticEvent.Selection)
                onClick()
            }
            .pressScale(interaction),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .size(width = 52.dp, height = 28.dp)
                .background(pill, RoundedCornerShape(Radii.md)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(item.icon, contentDescription = null, tint = tint, modifier = Modifier.size(Sizes.icon))
        }
        Spacer(Modifier.height(Spacing.xxs))
        Text(
            item.label,
            style = RitualType.caption.copy(fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium),
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
