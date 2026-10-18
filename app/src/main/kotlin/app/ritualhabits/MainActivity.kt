package app.ritualhabits

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.ritualhabits.ui.AppViewModel
import app.ritualhabits.ui.RitualViewModels
import app.ritualhabits.ui.navigation.RitualAppRoot
import app.ritualhabits.ui.theme.RitualTheme
import app.ritualhabits.ui.theme.isDark

class MainActivity : ComponentActivity() {
    private val appViewModel: AppViewModel by viewModels { RitualViewModels.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Hold the splash until the saved theme is known, so the wrong theme never flashes.
        installSplashScreen().setKeepOnScreenCondition { !appViewModel.uiState.value.loaded }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

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
                RitualAppRoot()
            }
        }
    }
}
