package app.ritualhabits.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.ritualhabits.data.sync.AccountState
import app.ritualhabits.data.sync.GoogleCredentialResult
import app.ritualhabits.data.sync.GoogleCredentialSource
import app.ritualhabits.data.sync.SignInException
import app.ritualhabits.data.sync.SignInFailure
import app.ritualhabits.data.sync.SyncManager
import app.ritualhabits.data.sync.SyncProblem
import app.ritualhabits.data.sync.SyncStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

data class AccountUiState(
    val account: AccountState = AccountState.Loading,
    val signingIn: Boolean = false,
    /** Why the last sign-in didn't work; null after cancel or success. */
    val message: String? = null,
)

class AccountViewModel(
    private val sync: SyncManager,
    private val credentials: GoogleCredentialSource?,
) : ViewModel() {
    private val local = MutableStateFlow(AccountUiState())

    val uiState: StateFlow<AccountUiState> = combine(sync.state, local) { account, ui -> ui.copy(account = account) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccountUiState(sync.state.value))

    /** "Continue with Google". [activityContext] hosts the system account picker. */
    fun signIn(activityContext: Context) {
        val source = credentials ?: return
        if (local.value.signingIn) return
        local.value = AccountUiState(signingIn = true)
        viewModelScope.launch {
            val message = when (val result = source.request(activityContext)) {
                GoogleCredentialResult.Cancelled -> null
                GoogleCredentialResult.NoAccount -> "Add a Google account to this phone, then try again."
                is GoogleCredentialResult.Failed -> "Couldn't open Google sign-in. Please try again."
                is GoogleCredentialResult.Token -> try {
                    sync.signInWithGoogle(result.idToken)
                    null
                } catch (e: SignInException) {
                    if (e.failure == SignInFailure.NETWORK) "You're offline. Connect to the internet and try again."
                    else "Couldn't sign in. Please try again."
                }
            }
            local.value = AccountUiState(signingIn = false, message = message)
        }
    }

    fun chooseAccount(switch: Boolean) = sync.chooseAccount(switch)

    fun signOut() {
        local.value = AccountUiState()
        sync.signOut()
    }

    fun syncNow() = sync.syncNow()
}

/** One line under "Backup" in Settings. */
fun backupStatusText(status: SyncStatus, now: Instant, zone: TimeZone, locale: Locale = Locale.getDefault()): String {
    val changes = if (status.pending == 1) "1 change" else "${status.pending} changes"
    return when {
        status.syncing -> "Backing up…"
        status.problem == SyncProblem.OFFLINE ->
            if (status.pending > 0) "Offline. $changes will back up when you're connected." else "Offline. Ritual will check again when you're connected."
        status.problem == SyncProblem.FAILED -> "Couldn't back up just now. Ritual will try again."
        status.pending > 0 -> "$changes waiting to back up"
        status.lastSyncedAt == null -> "Waiting to back up"
        else -> "Backed up " + whenText(status.lastSyncedAt, now, zone, locale)
    }
}

private fun whenText(at: Instant, now: Instant, zone: TimeZone, locale: Locale): String {
    val ago = now - at
    if (ago < 1.minutes) return "just now"
    if (ago < 1.hours) return "${ago.inWholeMinutes} min ago"
    val local = at.toLocalDateTime(zone)
    val today = now.toLocalDateTime(zone).date
    val javaTime = java.time.LocalTime.of(local.hour, local.minute)
    return if (local.date == today) {
        "at " + javaTime.format(DateTimeFormatter.ofPattern("h:mm a", locale))
    } else {
        "on " + java.time.LocalDate.of(local.year, local.monthNumber, local.dayOfMonth).format(DateTimeFormatter.ofPattern("MMM d", locale))
    }
}
