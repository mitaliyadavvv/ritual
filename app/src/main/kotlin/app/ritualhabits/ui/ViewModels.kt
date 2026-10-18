package app.ritualhabits.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.ritualhabits.RitualApp
import app.ritualhabits.core.model.ThemeMode
import app.ritualhabits.core.time.AppClock
import app.ritualhabits.core.time.SystemAppClock
import app.ritualhabits.data.repository.FirstRunStage
import app.ritualhabits.data.repository.PreferencesRepository
import app.ritualhabits.di.AppContainer
import app.ritualhabits.ui.focus.FocusViewModel
import app.ritualhabits.ui.manage.ManageViewModel
import app.ritualhabits.ui.onboarding.WelcomeViewModel
import app.ritualhabits.ui.progress.ProgressViewModel
import app.ritualhabits.ui.settings.AccountViewModel
import app.ritualhabits.ui.settings.SettingsViewModel
import app.ritualhabits.ui.today.TodayViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

/**
 * App-wide appearance state. [loaded] is false until preferences and the first-run stage have been
 * read once (the splash screen stays up until then).
 */
data class AppUiState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val hapticsEnabled: Boolean = true,
    val loaded: Boolean = false,
    val firstRun: FirstRunStage = FirstRunStage.DONE,
)

class AppViewModel(
    preferences: PreferencesRepository,
    firstRun: Flow<FirstRunStage> = flowOf(FirstRunStage.DONE),
) : ViewModel() {
    val uiState: StateFlow<AppUiState> = combine(preferences.preferences, firstRun) { prefs, stage ->
        AppUiState(prefs.themeMode, prefs.hapticsEnabled, loaded = true, firstRun = stage)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AppUiState())
}

/** Single factory for every ViewModel; dependencies come from [AppContainer]. */
object RitualViewModels {
    val Factory = viewModelFactory {
        initializer { container().run { AppViewModel(preferences, firstRun.stage) } }
        initializer { container().run { WelcomeViewModel(firstRun, sync) } }
        initializer { SettingsViewModel(container().preferences) }
        initializer { container().run { AccountViewModel(sync, cloud?.credentials) } }
        initializer { container().run { TodayViewModel(habits, completions, habitDay.today) } }
        initializer { container().run { ManageViewModel(habits, clock) } }
        initializer { container().run { ProgressViewModel(habits, habitDay.today) } }
        initializer { container().run { FocusViewModel(focus, habits, focusAlarms, clock, habitDay.today, preferences) } }
    }

    private fun CreationExtras.container(): AppContainer = (this[APPLICATION_KEY] as RitualApp).container

    /** The app clock, for screens that format times relative to now. */
    @Composable
    fun clock(): AppClock = (LocalContext.current.applicationContext as? RitualApp)?.container?.clock ?: SystemAppClock
}
