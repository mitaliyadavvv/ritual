package app.ritualhabits.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.ritualhabits.data.repository.FirstRunGate
import app.ritualhabits.data.repository.FirstRunStage
import app.ritualhabits.data.sync.AccountState
import app.ritualhabits.data.sync.SyncManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Moves a first launch through onboarding → sign-in → the app. Sign-in itself is the existing
 * [SyncManager] / Firebase flow (driven by the shared AccountViewModel); this only decides when the
 * welcome is over. Never touches habits or account data.
 */
class WelcomeViewModel(
    private val gate: FirstRunGate,
    private val sync: SyncManager,
    private val restoreTimeout: Duration = 12.seconds,
) : ViewModel() {
    private val _restoring = MutableStateFlow(false)
    /** Signed in; waiting briefly for the first backup round so a reinstall opens with its habits. */
    val restoring: StateFlow<Boolean> = _restoring.asStateFlow()

    init {
        viewModelScope.launch {
            sync.state.first { it is AccountState.SignedIn }
            _restoring.value = true
            withTimeoutOrNull(restoreTimeout) {
                sync.state.first {
                    it is AccountState.SignedIn && !it.sync.syncing && (it.sync.lastSyncedAt != null || it.sync.problem != null)
                }
            }
            gate.moveTo(FirstRunStage.DONE)
        }
    }

    /** "Skip" or "Get started". Without a backup service in this build there's nothing to sign in to. */
    fun leaveOnboarding() = viewModelScope.launch {
        gate.moveTo(if (sync.state.value == AccountState.Unavailable) FirstRunStage.DONE else FirstRunStage.SIGN_IN)
    }

    /** Back from the sign-in screen to the last onboarding page. */
    fun backToOnboarding() = viewModelScope.launch { gate.moveTo(FirstRunStage.ONBOARDING) }

    /** "Not now": use Ritual without an account (sign-in stays available in Settings). */
    fun continueWithoutAccount() = viewModelScope.launch { gate.moveTo(FirstRunStage.DONE) }
}
