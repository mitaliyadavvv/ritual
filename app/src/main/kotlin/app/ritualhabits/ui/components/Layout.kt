package app.ritualhabits.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.ritualhabits.ui.theme.Radii
import app.ritualhabits.ui.theme.Ritual
import app.ritualhabits.ui.theme.RitualType
import app.ritualhabits.ui.theme.Sizes
import app.ritualhabits.ui.theme.Spacing

/**
 * Standard scrolling screen: status-bar inset outside the scroll (content never slides under the
 * status bar), responsive gutters, centred max width on large screens, and [bottomInset]
 * clearance so content can always scroll clear of the floating bar.
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
                .windowInsetsPadding(WindowInsets.statusBars)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = layout.gutter)
                .padding(bottomInset)
                .padding(top = Spacing.md, bottom = Spacing.xl),
            content = content,
        )
    }
}

/**
 * Large-title header: an optional small eyebrow (e.g. the date) above a 30 sp title, with quiet
 * tonal actions aligned to the title. The title is a heading for TalkBack.
 */
@Composable
fun ScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    overline: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f).padding(end = Spacing.sm)) {
            if (overline != null) {
                Text(overline, style = RitualType.overline, color = Ritual.colors.textSecondary)
                Spacer(Modifier.height(Spacing.xs))
            }
            Text(
                title,
                style = RitualType.title,
                color = Ritual.colors.textPrimary,
                modifier = Modifier.semantics { heading() },
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically, content = actions)
    }
}

/** Section title with optional trailing detail, e.g. "Habits ……… 2 of 5". */
@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, trailing: String? = null) {
    Row(modifier.fillMaxWidth().padding(horizontal = Spacing.xs), verticalAlignment = Alignment.Bottom) {
        Text(
            title,
            style = RitualType.heading,
            color = Ritual.colors.textPrimary,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        if (trailing != null) {
            Text(trailing, style = RitualType.bodySmall, color = Ritual.colors.textSecondary)
        }
    }
}

/** Small label above a grouped surface (Settings style). */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = RitualType.overline,
        color = Ritual.colors.textSecondary,
        modifier = modifier
            .padding(start = Spacing.lg, bottom = Spacing.sm)
            .semantics { heading() },
    )
}

/**
 * Grouped surface: one opaque rounded container for related rows, separated from the page by tone
 * alone (no borders, no shadow). Rows inside use [Hairline] separators.
 */
@Composable
fun Module(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(Spacing.lg),
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(Radii.lg)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Ritual.colors.surface)
            .padding(contentPadding),
        content = content,
    )
}

/** Hairline separator. [startIndent] aligns it with row text rather than the container edge. */
@Composable
fun Hairline(modifier: Modifier = Modifier, startIndent: Dp = 0.dp) {
    Box(
        modifier
            .padding(start = startIndent)
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
            .padding(vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = Spacing.md)) {
            Text(title, style = RitualType.rowTitle, color = Ritual.colors.textPrimary)
            if (supporting != null) {
                Spacer(Modifier.height(Spacing.xs))
                Text(supporting, style = RitualType.bodySmall, color = Ritual.colors.textSecondary)
            }
        }
        if (trailing != null) trailing()
    }
}

/**
 * Calm, centred empty state: a symbol in a soft tonal circle, a short title, one line of guidance,
 * and at most one action.
 */
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
            .padding(top = Spacing.xxxl, bottom = Spacing.xl, start = Spacing.lg, end = Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(Ritual.colors.fill),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = Ritual.colors.textPrimary, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.height(Spacing.xl))
        Text(title, style = RitualType.heading, color = Ritual.colors.textPrimary, textAlign = TextAlign.Center)
        Spacer(Modifier.height(Spacing.sm))
        Text(
            body,
            style = RitualType.bodySmall,
            color = Ritual.colors.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 320.dp),
        )
        if (action != null) {
            Spacer(Modifier.height(Spacing.xl))
            action()
        }
    }
}
