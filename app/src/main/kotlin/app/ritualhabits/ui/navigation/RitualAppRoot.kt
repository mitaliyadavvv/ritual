package app.ritualhabits.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.ritualhabits.ui.components.BottomBarItem
import app.ritualhabits.ui.components.EmptyState
import app.ritualhabits.ui.components.RitualBottomBar
import app.ritualhabits.ui.components.RitualIconButton
import app.ritualhabits.ui.components.RitualIcons
import app.ritualhabits.ui.components.ScreenColumn
import app.ritualhabits.ui.components.ScreenHeader
import app.ritualhabits.ui.manage.ManageRoute
import app.ritualhabits.ui.settings.SettingsRoute
import app.ritualhabits.ui.theme.Ritual
import app.ritualhabits.ui.theme.Sizes
import app.ritualhabits.ui.theme.Spacing
import app.ritualhabits.ui.today.TodayRoute

/**
 * App shell: four tabs with a floating bottom bar, plus Settings pushed on top (bar hidden).
 * Tab switches cross-fade; Settings slides in. Each tab keeps its own back stack and scroll state.
 */
@Composable
fun RitualAppRoot(navController: NavHostController = rememberNavController()) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentTab = TopLevelDestination.fromRoute(backStackEntry?.destination?.route)
    val motion = Ritual.motion

    // Space reserved under scrolling content so the floating bar never covers it. The bar grows
    // with the user's font size, so its measured height is used once known.
    val density = LocalDensity.current
    val navBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    var barHeight by remember { mutableStateOf(navBarInset + Sizes.bottomBarHeight + Spacing.lg) }
    val tabBottomInset = PaddingValues(bottom = barHeight + Spacing.sm)

    val tabEnter: EnterTransition = if (motion.reduced) EnterTransition.None else fadeIn(motion.standard())
    val tabExit: ExitTransition = if (motion.reduced) ExitTransition.None else fadeOut(motion.exit())
    val pushEnter: EnterTransition =
        if (motion.reduced) EnterTransition.None
        else slideInHorizontally(motion.emphasized<IntOffset>()) { it / 6 } + fadeIn(motion.emphasized())
    val popExit: ExitTransition =
        if (motion.reduced) ExitTransition.None
        else slideOutHorizontally(motion.standard<IntOffset>()) { it / 6 } + fadeOut(motion.standard())

    Box(
        Modifier
            .fillMaxSize()
            .background(Ritual.colors.background),
    ) {
        NavHost(
            navController = navController,
            startDestination = TopLevelDestination.start.route,
            enterTransition = { tabEnter },
            exitTransition = { tabExit },
            popEnterTransition = { tabEnter },
            popExitTransition = { tabExit },
        ) {
            composable(TopLevelDestination.TODAY.route) {
                TodayRoute(tabBottomInset, onOpenSettings = { navController.navigate(Routes.SETTINGS) })
            }
            composable(TopLevelDestination.PROGRESS.route) {
                TabPlaceholder(
                    TopLevelDestination.PROGRESS, tabBottomInset, { navController.navigate(Routes.SETTINGS) },
                    title = "Your progress", body = "Streaks, completion rates and history will appear here.",
                )
            }
            composable(TopLevelDestination.FOCUS.route) {
                TabPlaceholder(
                    TopLevelDestination.FOCUS, tabBottomInset, { navController.navigate(Routes.SETTINGS) },
                    title = "Focus sessions", body = "Timed sessions for a habit will appear here.",
                )
            }
            composable(TopLevelDestination.MANAGE.route) {
                ManageRoute(tabBottomInset, onOpenSettings = { navController.navigate(Routes.SETTINGS) })
            }
            composable(
                Routes.SETTINGS,
                enterTransition = { pushEnter },
                exitTransition = { tabExit },
                popEnterTransition = { tabEnter },
                popExitTransition = { popExit },
            ) {
                SettingsRoute(onBack = { navController.popBackStack() })
            }
        }

        AnimatedVisibility(
            visible = currentTab != null,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .onSizeChanged { if (it.height > 0) barHeight = with(density) { it.height.toDp() } },
            enter = if (motion.reduced) EnterTransition.None else slideInVertically(motion.standard<IntOffset>()) { it / 2 } + fadeIn(motion.standard()),
            exit = if (motion.reduced) ExitTransition.None else slideOutVertically(motion.exit<IntOffset>()) { it / 2 } + fadeOut(motion.exit()),
        ) {
            RitualBottomBar(
                items = TopLevelDestination.entries.map { BottomBarItem(it, it.label, it.icon) },
                selected = currentTab,
                onSelect = { navController.navigateToTab(it) },
            )
        }
    }
}

fun NavHostController.navigateToTab(tab: TopLevelDestination) {
    navigate(tab.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/** Settings entry point shown in every tab header (no fifth tab). */
@Composable
fun SettingsButton(onClick: () -> Unit) {
    RitualIconButton(RitualIcons.Settings, contentDescription = "Settings", onClick = onClick)
}

/** Header + deliberate empty state for tabs whose features arrive in Stage 4. */
@Composable
private fun TabPlaceholder(
    tab: TopLevelDestination,
    bottomInset: PaddingValues,
    onOpenSettings: () -> Unit,
    title: String,
    body: String,
) {
    ScreenColumn(bottomInset) {
        ScreenHeader(title = tab.label, actions = { SettingsButton(onOpenSettings) })
        Spacer(Modifier.height(Spacing.xl))
        EmptyState(icon = tab.icon, title = title, body = body)
    }
}
