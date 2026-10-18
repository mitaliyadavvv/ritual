package app.ritualhabits.ui

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import app.ritualhabits.core.model.ThemeMode
import app.ritualhabits.data.repository.PreferencesRepository
import app.ritualhabits.ui.settings.SettingsViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModelsTest {
    @get:Rule val tmp = TemporaryFolder()
    private val storeScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var prefs: PreferencesRepository

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        prefs = PreferencesRepository(
            PreferenceDataStoreFactory.create(scope = storeScope) { tmp.root.resolve("prefs.preferences_pb") },
        )
    }

    @After fun tearDown() {
        storeScope.cancel()
        Dispatchers.resetMain()
    }

    @Test fun `app state becomes loaded with stored appearance`() = runBlocking<Unit> {
        prefs.setThemeMode(ThemeMode.DARK)
        val vm = AppViewModel(prefs)
        val state = withTimeout(5_000) { vm.uiState.first { it.loaded } }
        assertEquals(ThemeMode.DARK, state.themeMode)
        assertEquals(true, state.hapticsEnabled)
    }

    @Test fun `settings changes are persisted and reflected in state`() = runBlocking<Unit> {
        val vm = SettingsViewModel(prefs)
        vm.setThemeMode(ThemeMode.LIGHT)
        vm.setHapticsEnabled(false)
        val stored = withTimeout(5_000) { prefs.preferences.first { it.themeMode == ThemeMode.LIGHT && !it.hapticsEnabled } }
        assertEquals(ThemeMode.LIGHT, stored.themeMode)
        // The ViewModel's state follows the store while observed.
        val state = withTimeout(5_000) { vm.uiState.first { it.themeMode == ThemeMode.LIGHT } }
        assertEquals(false, state.hapticsEnabled)
    }
}
