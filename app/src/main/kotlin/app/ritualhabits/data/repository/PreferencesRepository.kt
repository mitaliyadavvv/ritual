package app.ritualhabits.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import app.ritualhabits.core.model.ThemeMode
import app.ritualhabits.data.local.RitualDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.IOException

data class UserPreferences(
    val userName: String = "",
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val hapticsEnabled: Boolean = true,
    val notificationPermissionRequested: Boolean = false,
)

/** Appearance settings that follow the user's account, with the time they last changed (epoch ms). */
data class SyncedPreferences(val themeMode: ThemeMode, val hapticsEnabled: Boolean, val changedAt: Long)

class PreferencesRepository(
    private val dataStore: DataStore<Preferences>,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {

    private object Keys {
        val userName = stringPreferencesKey("user_name")
        val themeMode = stringPreferencesKey("theme_mode")
        val haptics = booleanPreferencesKey("haptics_enabled")
        val notificationAsked = booleanPreferencesKey("notification_permission_requested")
        /** When theme or haptics last changed (cloud backup: last writer wins). */
        val syncedChangedAt = longPreferencesKey("synced_preferences_changed_at")
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
    suspend fun setThemeMode(mode: ThemeMode) = dataStore.edit {
        it[Keys.themeMode] = mode.name
        it[Keys.syncedChangedAt] = nowMillis()
    }
    suspend fun setHapticsEnabled(enabled: Boolean) = dataStore.edit {
        it[Keys.haptics] = enabled
        it[Keys.syncedChangedAt] = nowMillis()
    }
    suspend fun markNotificationPermissionRequested() = dataStore.edit { it[Keys.notificationAsked] = true }
    suspend fun clear() = dataStore.edit { it.clear() }

    suspend fun syncedPreferences(): SyncedPreferences {
        val p = runCatching { dataStore.data.first() }.getOrElse { emptyPreferences() }
        val current = preferences.first()
        return SyncedPreferences(current.themeMode, current.hapticsEnabled, p[Keys.syncedChangedAt] ?: 0L)
    }

    /** Applies settings downloaded from the user's account (doesn't count as a local change). */
    suspend fun applySyncedPreferences(themeMode: ThemeMode, hapticsEnabled: Boolean, changedAt: Long) = dataStore.edit {
        it[Keys.themeMode] = themeMode.name
        it[Keys.haptics] = hapticsEnabled
        it[Keys.syncedChangedAt] = changedAt
    }

    /** After switching accounts: this phone's settings no longer outrank the new account's. */
    suspend fun resetSyncedPreferencesClock() = dataStore.edit { it[Keys.syncedChangedAt] = 0L }
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
