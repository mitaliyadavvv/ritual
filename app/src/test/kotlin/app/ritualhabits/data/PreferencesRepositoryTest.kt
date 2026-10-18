package app.ritualhabits.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import app.ritualhabits.core.model.ThemeMode
import app.ritualhabits.data.repository.PreferencesRepository
import app.ritualhabits.data.repository.UserPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.test.assertEquals

/** Plain JVM test: DataStore needs no Android framework. */
class PreferencesRepositoryTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun repo(scope: CoroutineScope) = PreferencesRepository(
        PreferenceDataStoreFactory.create(scope = scope) { tmp.root.resolve("settings.preferences_pb") },
    )

    @Test fun `defaults then persisted values survive a restart`() = runBlocking<Unit> {
        val first = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val prefs = repo(first)
        assertEquals(UserPreferences(), prefs.preferences.first())
        prefs.setUserName("  Ana  ")
        prefs.setThemeMode(ThemeMode.DARK)
        prefs.setHapticsEnabled(false)
        first.cancel()

        val second = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val reloaded = repo(second).preferences.first()
        assertEquals(UserPreferences(userName = "Ana", themeMode = ThemeMode.DARK, hapticsEnabled = false), reloaded)
        second.cancel()
    }
}
