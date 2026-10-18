package app.ritualhabits.ui.navigation

import androidx.compose.ui.graphics.vector.ImageVector
import app.ritualhabits.ui.components.RitualIcons

/** The four bottom-bar destinations, in display order. */
enum class TopLevelDestination(val route: String, val label: String) {
    TODAY("today", "Today"),
    PROGRESS("progress", "Progress"),
    FOCUS("focus", "Focus"),
    MANAGE("manage", "Manage");

    val icon: ImageVector
        get() = when (this) {
            TODAY -> RitualIcons.Today
            PROGRESS -> RitualIcons.Progress
            FOCUS -> RitualIcons.Focus
            MANAGE -> RitualIcons.Manage
        }

    companion object {
        val start = TODAY

        /** The tab a route belongs to, or null for pushed screens (Settings) that hide the bar. */
        fun fromRoute(route: String?): TopLevelDestination? = entries.firstOrNull { it.route == route }
    }
}

/** Screens pushed on top of the tabs. */
object Routes {
    const val SETTINGS = "settings"
}
