package app.ritualhabits.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import app.ritualhabits.core.model.ThemeMode
import app.ritualhabits.data.local.RitualDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.IOException

data class UserPreferences(
    val userName: String = "",
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val hapticsEnabled: Boolean = true,
    val notificationPermissionRequested: Boolean = false,
)

class PreferencesRepository(private val dataStore: DataStore<Preferences>) {

    private object Keys {
        val userName = stringPreferencesKey("user_name")
        val themeMode = stringPreferencesKey("theme_mode")
        val haptics = booleanPreferencesKey("haptics_enabled")
        val notificationAsked = booleanPreferencesKey("notification_permission_requested")
    }

    val preferences: Flow<UserPreferences> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { p ->
            UserPreferences(
                userName = p[Keys.userName].orEmpty(),
                themeMode = p[Keys.themeMode]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                    ?: ThemeMode.SYSTEM,
                hapticsEnabled = p[Keys.haptics] ?: true,
                notificationPermissionRequested = p[Keys.notificationAsked] ?: false,
            )
        }

    suspend fun setUserName(name: String) = dataStore.edit { it[Keys.userName] = name.trim().take(40) }
    suspend fun setThemeMode(mode: ThemeMode) = dataStore.edit { it[Keys.themeMode] = mode.name }
    suspend fun setHapticsEnabled(enabled: Boolean) = dataStore.edit { it[Keys.haptics] = enabled }
    suspend fun markNotificationPermissionRequested() = dataStore.edit { it[Keys.notificationAsked] = true }
    suspend fun clear() = dataStore.edit { it.clear() }
}

/** "Delete all local data": the only path that physically removes history. */
class LocalDataManager(
    private val db: RitualDatabase,
    private val preferences: PreferencesRepository,
) {
    suspend fun clearAll() {
        withContext(Dispatchers.IO) { db.clearAllTables() }
        preferences.clear()
        // Focus stage: also cancel the pending alarm and clear notifications here.
    }
}
