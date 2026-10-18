package app.ritualhabits.di

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import app.ritualhabits.core.time.AppClock
import app.ritualhabits.core.time.SystemAppClock
import app.ritualhabits.data.day.HabitDayTracker
import app.ritualhabits.data.focus.AndroidFocusAlarms
import app.ritualhabits.data.focus.AndroidFocusNotifier
import app.ritualhabits.data.focus.FocusAlarms
import app.ritualhabits.data.focus.FocusCompletionHandler
import app.ritualhabits.data.local.RitualDatabase
import app.ritualhabits.data.repository.CompletionRepository
import app.ritualhabits.data.repository.FocusRepository
import app.ritualhabits.data.repository.HabitRepository
import app.ritualhabits.data.repository.LocalDataManager
import app.ritualhabits.data.repository.PreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

/** Manual dependency graph; one instance per process, owned by [app.ritualhabits.RitualApp]. */
class AppContainer(
    context: Context,
    val clock: AppClock = SystemAppClock,
    val appScope: CoroutineScope = MainScope(),
) {
    private val appContext = context.applicationContext

    val database: RitualDatabase by lazy { RitualDatabase.build(appContext) }
    val habits by lazy { HabitRepository(database, clock) }
    val completions by lazy { CompletionRepository(database, clock) }
    val focus by lazy { FocusRepository(database, clock) }
    val preferences by lazy { PreferencesRepository(appContext.settingsDataStore) }
    val localData by lazy { LocalDataManager(database, preferences) }
    val habitDay by lazy { HabitDayTracker(appContext, clock, appScope) }
    val focusAlarms: FocusAlarms by lazy { AndroidFocusAlarms(appContext) }
    val focusNotifier by lazy { AndroidFocusNotifier(appContext) }
    val focusCompletion by lazy {
        FocusCompletionHandler(focus, habits, focusAlarms, focusNotifier) {
            ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        }
    }
}
