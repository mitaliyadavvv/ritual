package app.ritualhabits

import android.graphics.Color
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.ritualhabits.data.repository.FirstRunStage
import app.ritualhabits.ui.AppViewModel
import app.ritualhabits.ui.RitualViewModels
import app.ritualhabits.ui.navigation.RitualAppRoot
import app.ritualhabits.ui.navigation.TopLevelDestination
import app.ritualhabits.ui.onboarding.WelcomeFlow
import kotlinx.coroutines.flow.MutableStateFlow
import app.ritualhabits.ui.theme.RitualTheme
import app.ritualhabits.ui.theme.isDark

class MainActivity : ComponentActivity() {
    private val appViewModel: AppViewModel by viewModels { RitualViewModels.Factory }

    /** A tab requested from outside (the focus-complete notification), consumed once by the app shell. */
    private val requestedTab = MutableStateFlow<TopLevelDestination?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        // Hold the splash until the saved theme and first-run state are known, so neither flashes.
        installSplashScreen().setKeepOnScreenCondition { !appViewModel.uiState.value.loaded }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) requestedTab.value = tabFrom(intent)

        setContent {
            val state by appViewModel.uiState.collectAsStateWithLifecycle()
            val dark = state.themeMode.isDark(isSystemInDarkTheme())

            // Status/navigation bar icons follow the in-app theme, not just the system one.
            DisposableEffect(dark) {
                val style = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose { }
            }

            RitualTheme(themeMode = state.themeMode, hapticsEnabled = state.hapticsEnabled) {
                // Nothing until the first-run stage is known (the splash covers it), so the app never
                // flashes before onboarding. First launch only: onboarding and sign-in, then the app.
                if (state.loaded) Crossfade(state.firstRun == FirstRunStage.DONE, label = "entry") { inApp ->
                    if (inApp) {
                        val openTab by requestedTab.collectAsStateWithLifecycle()
                        RitualAppRoot(openTab = openTab, onOpenTabHandled = { requestedTab.value = null })
                    } else {
                        WelcomeFlow(state.firstRun)
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        tabFrom(intent)?.let { requestedTab.value = it }
    }

    private fun tabFrom(intent: Intent?): TopLevelDestination? = when (intent?.getStringExtra(EXTRA_OPEN_TAB)) {
        TAB_FOCUS -> TopLevelDestination.FOCUS
        TAB_TODAY -> TopLevelDestination.TODAY
        else -> null
    }

    companion object {
        const val EXTRA_OPEN_TAB = "app.ritualhabits.extra.OPEN_TAB"
        const val TAB_FOCUS = "focus"
        const val TAB_TODAY = "today"
    }
}
