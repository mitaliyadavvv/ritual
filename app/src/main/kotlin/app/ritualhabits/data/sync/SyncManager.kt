package app.ritualhabits.data.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

enum class SyncProblem {
    /** No connection: changes are kept on the phone and sent when it's back. */
    OFFLINE,
    /** Something else went wrong; retried automatically. */
    FAILED,
}

data class SyncStatus(
    val syncing: Boolean = false,
    /** Local changes not yet uploaded. */
    val pending: Int = 0,
    val lastSyncedAt: Instant? = null,
    val problem: SyncProblem? = null,
)

sealed interface AccountState {
    /** This build has no Firebase configuration: backup isn't offered. */
    data object Unavailable : AccountState
    data object Loading : AccountState
    /** [previousEmail]: the account this phone's data belongs to, if it was signed in before. */
    data class SignedOut(val previousEmail: String?) : AccountState
    /** Signed in as [user], but the phone holds [previousEmail]'s data. Waits for the user's choice. */
    data class ChooseAccount(val user: AccountUser, val previousEmail: String?, val unsyncedChanges: Int) : AccountState
    data class SignedIn(val user: AccountUser, val sync: SyncStatus) : AccountState
}

/**
 * Account + sync lifecycle for the whole app. Syncs on sign-in, when local data changes, when the
 * app comes to the foreground, when the network returns, and on request; retries failures with
 * backoff. Nothing here blocks or changes the offline experience: the UI only reads the local database.
 */
class SyncManager(
    private val auth: AuthGateway?,
    private val engine: SyncEngine,
    private val scope: CoroutineScope,
    private val online: Flow<Boolean> = emptyFlow(),
    private val changeDebounce: Duration = 2.seconds,
    private val retryDelays: List<Duration> = listOf(15.seconds, 1.minutes, 5.minutes),
) {
    private val _state = MutableStateFlow<AccountState>(if (auth == null) AccountState.Unavailable else AccountState.Loading)
    val state: StateFlow<AccountState> = _state.asStateFlow()

    private var requests: Channel<Unit>? = null
    private var choice: CompletableDeferred<Boolean>? = null

    fun start() {
        val auth = auth ?: return
        scope.launch {
            auth.user.collectLatest { user ->
                if (user == null) {
                    _state.value = AccountState.SignedOut(engine.ownerEmail())
                } else {
                    onSignedIn(user)
                }
            }
        }
    }

    /** Throws [SignInException]. The new state arrives through [state]. */
    suspend fun signInWithGoogle(idToken: String) {
        checkNotNull(auth).signInWithGoogle(idToken)
    }

    /** Answer to [AccountState.ChooseAccount]: true = replace this phone's data with the new account's. */
    fun chooseAccount(switch: Boolean) {
        choice?.complete(switch)
    }

    fun signOut() {
        scope.launch { auth?.signOut() }
    }

    /**
     * Deletes the signed-in account and its cloud backup after confirming the user again with a
     * fresh Google sign-in ([idToken]). Throws [SignInException] (e.g. offline, wrong account).
     */
    suspend fun deleteAccount(idToken: String) {
        val auth = checkNotNull(auth)
        val user = (state.value as? AccountState.SignedIn)?.user ?: throw SignInException(SignInFailure.OTHER)
        auth.reauthenticateWithGoogle(idToken)
        try {
            engine.deleteAccount(user.uid) { auth.deleteCurrentUser() }
        } catch (e: CloudUnavailableException) {
            throw SignInException(SignInFailure.NETWORK, e)
        }
        _state.value = AccountState.SignedOut(previousEmail = null)
    }

    fun syncNow() {
        requests?.trySend(Unit)
    }

    private suspend fun onSignedIn(user: AccountUser) {
        when (val attach = engine.attach(user.uid, user.email)) {
            Attach.Claimed, Attach.Resumed -> runSync(user)
            is Attach.OtherAccount -> {
                val decision = CompletableDeferred<Boolean>().also { choice = it }
                _state.value = AccountState.ChooseAccount(user, attach.previousEmail, attach.unsyncedChanges)
                if (decision.await()) {
                    engine.replaceLocalDataWith(user.uid, user.email)
                    runSync(user)
                } else {
                    auth?.signOut() // keeps the previous account's data exactly as it was
                }
            }
        }
    }

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    private suspend fun runSync(user: AccountUser) = coroutineScope {
        val queue = Channel<Unit>(Channel.CONFLATED).also { requests = it }
        val status = MutableStateFlow(SyncStatus(pending = engine.pendingCount(), lastSyncedAt = engine.lastSyncedAt()))
        _state.value = AccountState.SignedIn(user, status.value)
        queue.trySend(Unit)

        launch { status.collect { _state.value = AccountState.SignedIn(user, it) } }
        launch {
            engine.observePendingCount().collect { count -> status.update { it.copy(pending = count) } }
        }
        launch {
            // Every non-empty outbox asks for a sync (requests are conflated, so extras cost nothing).
            engine.observePendingCount().filter { it > 0 }.debounce(changeDebounce).collect { queue.trySend(Unit) }
        }
        launch {
            online.distinctUntilChanged().filter { it }.collect { queue.trySend(Unit) }
        }
        try {
            var failures = 0
            while (true) {
                if (failures == 0) queue.receive() else withTimeoutOrNull(retryDelays[minOf(failures, retryDelays.size) - 1]) { queue.receive() }
                status.update { it.copy(syncing = true) }
                failures = try {
                    engine.sync(user.uid)
                    val syncedAt = engine.lastSyncedAt()
                    status.update { it.copy(syncing = false, problem = null, lastSyncedAt = syncedAt) }
                    0
                } catch (e: CancellationException) {
                    throw e
                } catch (e: CloudUnavailableException) {
                    status.update { it.copy(syncing = false, problem = SyncProblem.OFFLINE) }
                    failures + 1
                } catch (e: Exception) {
                    Log.w("Ritual", "Sync failed", e)
                    status.update { it.copy(syncing = false, problem = SyncProblem.FAILED) }
                    failures + 1
                }
            }
        } finally {
            requests = null
        }
    }

    companion object {
        /** True while the device has a usable network. */
        fun networkAvailability(context: Context): Flow<Boolean> = callbackFlow {
            val cm = context.getSystemService(ConnectivityManager::class.java)
            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    trySend(true)
                }
                override fun onLost(network: Network) {
                    trySend(false)
                }
            }
            runCatching { cm.registerDefaultNetworkCallback(callback) }
            awaitClose { runCatching { cm.unregisterNetworkCallback(callback) } }
        }
    }
}
