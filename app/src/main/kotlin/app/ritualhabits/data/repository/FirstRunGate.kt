package app.ritualhabits.data.repository

import app.ritualhabits.data.local.RitualDatabase
import app.ritualhabits.data.local.SyncTriggers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** How far a first launch has got. Saved in DataStore, so it survives restarts and updates. */
enum class FirstRunStage {
    /** Brand-new install: show the three onboarding screens. */
    ONBOARDING,
    /** Onboarding seen or skipped; the sign-in screen is next. */
    SIGN_IN,
    /** Into the app. Never goes back, whatever happens to the account later. */
    DONE,
}

/**
 * Decides once whether this is a genuinely new install. Installations from before onboarding
 * existed have no stage saved; they're recognised by data they already hold (habits, a backup
 * account, or saved settings) and go straight to the app. Reads only; never clears anything.
 */
class FirstRunGate(
    private val preferences: PreferencesRepository,
    private val db: RitualDatabase,
) {
    private val mutex = Mutex()

    val stage: Flow<FirstRunStage> = preferences.firstRunStage.mapNotNull { saved -> saved ?: decide() }.distinctUntilChanged()

    suspend fun moveTo(stage: FirstRunStage) = preferences.setFirstRunStage(stage)

    private suspend fun decide(): FirstRunStage? = mutex.withLock {
        // Another caller may have decided meanwhile; the flow then re-emits with the saved stage.
        if (preferences.firstRunStage.first() != null) return null
        val existingInstall = db.habitDao().maxSortOrder() >= 0 ||
            db.syncDao().meta(SyncTriggers.META_OWNER_UID) != null ||
            preferences.hasStoredValues()
        val stage = if (existingInstall) FirstRunStage.DONE else FirstRunStage.ONBOARDING
        preferences.setFirstRunStage(stage)
        stage
    }
}
