package app.ritualhabits.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.ritualhabits.ui.theme.Radii
import app.ritualhabits.ui.theme.Ritual
import app.ritualhabits.ui.theme.RitualType
import app.ritualhabits.ui.theme.Sizes
import app.ritualhabits.ui.theme.Spacing

/**
 * Standard scrolling screen body: status-bar inset, responsive gutters, centred max width on
 * tablets/foldables, and [bottomInset] clearance so content never hides under the bottom bar.
 */
@Composable
fun ScreenColumn(
    bottomInset: PaddingValues,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val layout = Ritual.layout
    Box(
        modifier
            .fillMaxSize()
            .background(Ritual.colors.background),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            Modifier
                .widthIn(max = layout.contentMaxWidth)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = layout.gutter)
                .padding(bottomInset)
                .padding(top = Spacing.lg, bottom = Spacing.xxl),
            content = content,
        )
    }
}

/**
 * Screen title block: optional overline (e.g. the date), large title, trailing actions.
 * The title is marked as a heading for TalkBack navigation.
 */
@Composable
fun ScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    overline: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f)) {
            if (overline != null) {
                Text(overline.uppercase(), style = RitualType.overline, color = Ritual.colors.textSecondary)
                Spacer(Modifier.height(Spacing.xs))
            }
            Text(
                title,
                style = RitualType.title,
                color = Ritual.colors.textPrimary,
                modifier = Modifier.semantics { heading() },
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), content = actions)
    }
}

/** Small uppercase label above a group of content. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = RitualType.overline,
        color = Ritual.colors.textSecondary,
        modifier = modifier
            .padding(bottom = Spacing.sm)
            .semantics { heading() },
    )
}

/**
 * Solid surface with a hairline border. The building block for dashboard modules and grouped
 * rows. No shadow, no blur.
 */
@Composable
fun Module(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(Spacing.lg),
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(Radii.md)
    Column(
        modifier
            .fillMaxWidth()
            .background(Ritual.colors.surface, shape)
            .border(Sizes.hairline, Ritual.colors.border, shape)
            .padding(contentPadding),
        content = content,
    )
}

@Composable
fun Hairline(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(Sizes.hairline)
            .background(Ritual.colors.border),
    )
}

/** A row inside a [Module]: title, optional supporting text, trailing control. ≥ 56 dp tall. */
@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .padding(vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = Spacing.md)) {
            Text(title, style = RitualType.body, color = Ritual.colors.textPrimary)
            if (supporting != null) {
                Spacer(Modifier.height(Spacing.xxs))
                Text(supporting, style = RitualType.bodySmall, color = Ritual.colors.textSecondary)
            }
        }
        if (trailing != null) trailing()
    }
}

/** Deliberate empty state: quiet icon in a hairline circle, a title, one line of guidance, one action. */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.xxxl, horizontal = Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(56.dp)
                .border(Sizes.hairline, Ritual.colors.borderStrong, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = Ritual.colors.textSecondary, modifier = Modifier.size(Sizes.icon))
        }
        Spacer(Modifier.height(Spacing.lg))
        Text(title, style = RitualType.heading, color = Ritual.colors.textPrimary, textAlign = TextAlign.Center)
        Spacer(Modifier.height(Spacing.xs))
        Text(body, style = RitualType.bodySmall, color = Ritual.colors.textSecondary, textAlign = TextAlign.Center)
        if (action != null) {
            Spacer(Modifier.height(Spacing.xl))
            action()
        }
    }
}
