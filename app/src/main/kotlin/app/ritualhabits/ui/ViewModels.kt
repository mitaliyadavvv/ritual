package app.ritualhabits.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.ritualhabits.RitualApp
import app.ritualhabits.core.model.ThemeMode
import app.ritualhabits.data.repository.PreferencesRepository
import app.ritualhabits.di.AppContainer
import app.ritualhabits.ui.focus.FocusViewModel
import app.ritualhabits.ui.manage.ManageViewModel
import app.ritualhabits.ui.progress.ProgressViewModel
import app.ritualhabits.ui.settings.SettingsViewModel
import app.ritualhabits.ui.today.TodayViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** App-wide appearance state. [loaded] is false until preferences have been read once. */
data class AppUiState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val hapticsEnabled: Boolean = true,
    val loaded: Boolean = false,
)

class AppViewModel(preferences: PreferencesRepository) : ViewModel() {
    val uiState: StateFlow<AppUiState> = preferences.preferences
        .map { AppUiState(it.themeMode, it.hapticsEnabled, loaded = true) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppUiState())
}

/** Single factory for every ViewModel; dependencies come from [AppContainer]. */
object RitualViewModels {
    val Factory = viewModelFactory {
        initializer { AppViewModel(container().preferences) }
        initializer { SettingsViewModel(container().preferences) }
        initializer { container().run { TodayViewModel(habits, completions, habitDay.today) } }
        initializer { container().run { ManageViewModel(habits, clock) } }
        initializer { container().run { ProgressViewModel(habits, habitDay.today) } }
        initializer { container().run { FocusViewModel(focus, habits, focusAlarms, clock, habitDay.today, preferences) } }
    }

    private fun CreationExtras.container(): AppContainer = (this[APPLICATION_KEY] as RitualApp).container
}
