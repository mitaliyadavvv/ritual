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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
                // Inset outside the scroll so content never slides under the status bar.
                .windowInsetsPadding(WindowInsets.statusBars)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = layout.gutter)
                .padding(bottomInset)
                .padding(top = Spacing.lg, bottom = Spacing.xxl),
            content = content,
        )
    }
}

/**
 * Masthead: a top row with the overline (e.g. the date) and actions, then a large title on its own
 * line so long titles never compete with buttons. The title is a heading for TalkBack.
 */
@Composable
fun ScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    overline: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                overline?.uppercase().orEmpty(),
                style = RitualType.overline,
                color = Ritual.colors.textPrimary,
                modifier = Modifier.weight(1f),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), content = actions)
        }
        Spacer(Modifier.height(Spacing.sm))
        Text(
            title,
            style = RitualType.title,
            color = Ritual.colors.textPrimary,
            modifier = Modifier.semantics { heading() },
        )
    }
}

/** Small uppercase label above a group of content. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = RitualType.overline,
        color = Ritual.colors.textPrimary,
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

/**
 * Editorial empty state: a small mark, a large left-aligned statement, one line of guidance and
 * one strong action. No illustration, no card.
 */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Column(modifier.fillMaxWidth().padding(top = Spacing.xxl, bottom = Spacing.xl)) {
        Box(
            Modifier
                .size(44.dp)
                .border(Sizes.stroke, Ritual.colors.textPrimary, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = Ritual.colors.textPrimary, modifier = Modifier.size(Sizes.iconSmall))
        }
        Spacer(Modifier.height(Spacing.xl))
        Text(title, style = RitualType.title.copy(fontSize = 36.sp, lineHeight = 38.sp), color = Ritual.colors.textPrimary)
        Spacer(Modifier.height(Spacing.md))
        Text(body, style = RitualType.body, color = Ritual.colors.textSecondary)
        if (action != null) {
            Spacer(Modifier.height(Spacing.xl))
            action()
        }
    }
}
