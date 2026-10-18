package app.ritualhabits.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.ritualhabits.core.rules.CalendarMonth
import app.ritualhabits.core.rules.FocusTimer
import app.ritualhabits.ui.components.CalendarCell
import app.ritualhabits.ui.components.CalendarGrid
import app.ritualhabits.ui.components.CellStyle
import app.ritualhabits.ui.components.CompletionCheck
import app.ritualhabits.ui.components.DotDay
import app.ritualhabits.ui.components.DotState
import app.ritualhabits.ui.components.Hairline
import app.ritualhabits.ui.components.LegendItem
import app.ritualhabits.ui.components.Module
import app.ritualhabits.ui.components.PrimaryButton
import app.ritualhabits.ui.components.ProgressRing
import app.ritualhabits.ui.components.RitualIconButton
import app.ritualhabits.ui.components.RitualIcons
import app.ritualhabits.ui.components.TimerDial
import app.ritualhabits.ui.components.WeekDots
import app.ritualhabits.ui.theme.Ritual
import app.ritualhabits.ui.theme.RitualType
import app.ritualhabits.ui.theme.Spacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.DayOfWeek
import kotlin.math.min

/** The three onboarding pages: copy plus a small, product-faithful preview. */
enum class OnboardingPage(val headline: String, val body: String, val previewDescription: String) {
    HABITS(
        headline = "Make room for better habits.",
        body = "Build a routine that feels right for you. Create habits, track your daily progress, and turn small actions into lasting rituals.",
        previewDescription = "Preview of Today: three habits, two of them ticked off.",
    ),
    PROGRESS(
        headline = "Your progress, at a glance.",
        body = "Get a clearer picture of your consistency with monthly overview cards. Look back at your progress, spot patterns, and see how far you've come.",
        previewDescription = "Preview of the monthly overview calendar.",
    ),
    FOCUS(
        headline = "Focus on what matters.",
        body = "Set timers to get things done, create space for focused work, and build a routine that helps you show up every day.",
        previewDescription = "Preview of a focus timer.",
    ),
}

/**
 * First-launch onboarding: three swipeable pages with Back, Skip, page dots and Next / Get started.
 * [onFinish] runs for both Skip and Get started.
 */
@Composable
fun OnboardingScreen(onFinish: () -> Unit, initialPage: Int = 0) {
    val pages = OnboardingPage.entries
    val pager = rememberPagerState(initialPage = initialPage) { pages.size }
    val scope = rememberCoroutineScope()
    val motion = Ritual.motion
    fun goTo(page: Int) = scope.launch { if (motion.reduced) pager.scrollToPage(page) else pager.animateScrollToPage(page) }
    val last = pager.currentPage == pages.lastIndex

    BackHandler(enabled = pager.currentPage > 0) { goTo(pager.currentPage - 1) }

    Column(
        Modifier
            .fillMaxSize()
            .background(Ritual.colors.background)
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.widthIn(max = Ritual.layout.contentMaxWidth).fillMaxSize()) {
            // Top bar: Back (after the first page) and Skip.
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .padding(horizontal = Ritual.layout.gutter - Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (pager.currentPage > 0) {
                    RitualIconButton(RitualIcons.Back, contentDescription = "Previous", onClick = { goTo(pager.currentPage - 1) })
                }
                Spacer(Modifier.weight(1f))
                if (!last) {
                    TextButton(onClick = onFinish, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text("Skip", style = RitualType.label, color = Ritual.colors.textSecondary)
                    }
                }
            }

            HorizontalPager(pager, Modifier.weight(1f), verticalAlignment = Alignment.Top) { index ->
                OnboardingPageContent(pages[index], active = pager.settledPage == index)
            }

            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Ritual.layout.gutter)
                    .padding(top = Spacing.md, bottom = Spacing.lg),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                PageDots(pager)
                Spacer(Modifier.height(Spacing.xl))
                PrimaryButton(
                    text = if (last) "Get started" else "Next",
                    onClick = { if (last) onFinish() else goTo(pager.currentPage + 1) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun OnboardingPageContent(page: OnboardingPage, active: Boolean) {
    val c = Ritual.colors
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // The copy always gets the room it needs; the preview takes what's left and scales to fit.
        // Room is reserved for the longest page's copy, so headlines sit at the same height on every
        // page. At very large font sizes the preview keeps a minimum size and the page scrolls.
        val textWidth = with(density) { (maxWidth - Ritual.layout.gutter * 2).roundToPx().coerceAtLeast(1) }
        val tallestCopy = OnboardingPage.entries.maxOf { p ->
            val constraints = Constraints(maxWidth = textWidth)
            measurer.measure(p.headline, RitualType.title, constraints = constraints).size.height +
                measurer.measure(p.body, RitualType.body, constraints = constraints).size.height
        }
        val reserved = with(density) { tallestCopy.toDp() } + Spacing.xl + Spacing.md + Spacing.lg
        val previewHeight = (maxHeight - reserved).coerceIn(minOf(160.dp, maxHeight * 0.4f), maxHeight * 0.62f)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Ritual.layout.gutter),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(previewHeight)
                    .clearAndSetSemantics { contentDescription = page.previewDescription },
                contentAlignment = Alignment.Center,
            ) {
                ScaleToFit(designWidth = 340.dp) {
                    when (page) {
                        OnboardingPage.HABITS -> HabitsPreview(active)
                        OnboardingPage.PROGRESS -> ProgressPreview()
                        OnboardingPage.FOCUS -> FocusPreview(active)
                    }
                }
            }
            Spacer(Modifier.height(Spacing.xl))
            Text(page.headline, style = RitualType.title, color = c.textPrimary, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.height(Spacing.md))
            Text(page.body, style = RitualType.body, color = c.textSecondary)
            Spacer(Modifier.height(Spacing.lg))
        }
    }
}

/** Three dots; the current page is a longer ink pill. Spoken as "Page n of 3". */
@Composable
private fun PageDots(pager: PagerState) {
    val c = Ritual.colors
    Row(
        Modifier.clearAndSetSemantics { contentDescription = "Page ${pager.currentPage + 1} of ${pager.pageCount}" },
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(pager.pageCount) { index ->
            val selected = pager.currentPage == index
            val width by animateDpAsState(if (selected) 22.dp else 8.dp, Ritual.motion.standard(), label = "dot")
            val color by animateColorAsState(if (selected) c.accent else c.borderStrong, Ritual.motion.standard(), label = "dotColor")
            Box(
                Modifier
                    .height(8.dp)
                    .width(width)
                    .clip(CircleShape)
                    .background(color),
            )
        }
    }
}

/**
 * Lays [content] out at a fixed design width, then scales it uniformly to fit the space given, so
 * previews keep their proportions on every phone. Purely visual (the preview is decorative).
 */
@Composable
private fun ScaleToFit(designWidth: Dp, content: @Composable () -> Unit) {
    Layout(content) { measurables, constraints ->
        val width = designWidth.roundToPx()
        val placeable = measurables.first().measure(Constraints(minWidth = width, maxWidth = width))
        val scale = min(
            1f,
            min(
                constraints.maxWidth / placeable.width.toFloat(),
                if (constraints.hasBoundedHeight) constraints.maxHeight / placeable.height.toFloat() else 1f,
            ),
        )
        val w = (placeable.width * scale).toInt()
        val h = (placeable.height * scale).toInt()
        layout(w, h) {
            placeable.placeWithLayer(0, 0) {
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin(0f, 0f)
            }
        }
    }
}

// --- Previews built from the app's own components, with sample data ---------------------------

private val sampleHabits = listOf("Read 20 pages", "Walk outside", "Journal")

/** Today: the summary card and a habit list whose ticks land one by one when the page appears. */
@Composable
private fun HabitsPreview(active: Boolean) {
    val c = Ritual.colors
    val reduced = Ritual.motion.reduced
    var ticked by remember { mutableStateOf(if (reduced) 2 else 0) }
    LaunchedEffect(active) {
        if (active && ticked < 2) {
            delay(350)
            ticked = 1
            delay(450)
            ticked = 2
        }
    }
    Column {
        Module(contentPadding = PaddingValues(Spacing.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProgressRing(progress = ticked / 3f, size = 64.dp, strokeWidth = 7.dp) {
                    Text("$ticked/3", style = RitualType.value, color = c.textPrimary)
                }
                Spacer(Modifier.width(Spacing.lg))
                Column {
                    Text("${3 - ticked} left today", style = RitualType.heading, color = c.textPrimary)
                    Spacer(Modifier.height(Spacing.xs))
                    Text("4-day streak", style = RitualType.bodySmall, color = c.textSecondary)
                }
            }
            Spacer(Modifier.height(Spacing.lg))
            Hairline()
            Spacer(Modifier.height(Spacing.md))
            WeekDots(
                listOf("M", "T", "W", "T", "F", "S", "S").mapIndexed { i, label ->
                    DotDay(
                        label,
                        state = when {
                            i == 6 -> DotState.TODAY
                            i == 1 -> DotState.MISSED
                            else -> DotState.FILLED
                        },
                        isToday = i == 6,
                    )
                },
            )
        }
        Spacer(Modifier.height(Spacing.md))
        Module(contentPadding = PaddingValues(0.dp)) {
            sampleHabits.forEachIndexed { index, name ->
                if (index > 0) Hairline(startIndent = Spacing.lg + 28.dp + Spacing.md)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.lg, vertical = Spacing.md + 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val done = index < ticked
                    CompletionCheck(done, size = 28.dp)
                    Spacer(Modifier.width(Spacing.md))
                    Text(name, style = RitualType.rowTitle, color = if (done) c.textSecondary else c.textPrimary)
                }
            }
        }
    }
}

/** Progress: a month in the same calendar cells and legend the Progress tab uses. */
@Composable
private fun ProgressPreview() {
    val c = Ritual.colors
    val month = CalendarMonth(2026, 10)
    // A believable month up to the 19th: mostly complete, a few partial or missed days.
    val partial = setOf(3, 11)
    val missed = setOf(6, 14)
    Module(contentPadding = PaddingValues(Spacing.lg)) {
        Text("October 2026", style = RitualType.heading, color = c.textPrimary)
        Spacer(Modifier.height(Spacing.md))
        CalendarGrid(month, DayOfWeek.MONDAY) { date ->
            val day = date.dayOfMonth
            CalendarCell(
                day = day,
                style = when {
                    day > 19 -> CellStyle.UPCOMING
                    day in partial -> CellStyle.HALF
                    day in missed -> CellStyle.OUTLINED
                    else -> CellStyle.FILLED
                },
                isToday = day == 19,
                description = "",
                onClick = null,
            )
        }
        Spacer(Modifier.height(Spacing.lg))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.lg)) {
            LegendItem(CellStyle.FILLED, "All done")
            LegendItem(CellStyle.HALF, "Partly")
            LegendItem(CellStyle.OUTLINED, "Not done")
        }
        Spacer(Modifier.height(Spacing.md))
        Hairline()
        Spacer(Modifier.height(Spacing.md))
        Text("Current streak 5 · Best 9", style = RitualType.bodySmall, color = c.textSecondary)
    }
}

/** Focus: the same dial, habit and duration presets as the Focus tab, mid-session. */
@Composable
private fun FocusPreview(active: Boolean) {
    val c = Ritual.colors
    val reduced = Ritual.motion.reduced
    var progress by remember { mutableStateOf(if (reduced) 0.36f else 0f) }
    LaunchedEffect(active) { if (active) progress = 0.36f }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Module(contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.md + 2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Habit", style = RitualType.caption, color = c.textSecondary)
                    Spacer(Modifier.height(Spacing.xxs))
                    Text(sampleHabits.first(), style = RitualType.rowTitle, color = c.textPrimary)
                }
            }
        }
        Spacer(Modifier.height(Spacing.xl))
        TimerDial(progress = progress, time = "16:00", caption = "Focusing", size = 240.dp)
        Spacer(Modifier.height(Spacing.xl))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            FocusTimer.PRESETS.filter { it.inWholeMinutes in setOf(15L, 25L, 45L) }.forEach { preset ->
                val selected = preset == FocusTimer.DEFAULT_DURATION
                Box(
                    Modifier
                        .clip(CircleShape)
                        .background(if (selected) c.accent else c.fill)
                        .padding(horizontal = Spacing.lg + 2.dp, vertical = Spacing.md),
                ) {
                    Text("${preset.inWholeMinutes} min", style = RitualType.label, color = if (selected) c.onAccent else c.textPrimary, textAlign = TextAlign.Center)
                }
            }
        }
    }
}
