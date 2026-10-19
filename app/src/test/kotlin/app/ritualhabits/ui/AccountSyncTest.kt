package app.ritualhabits.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.ritualhabits.data.Device
import app.ritualhabits.data.FakeCloud
import app.ritualhabits.data.sync.AccountState
import app.ritualhabits.data.sync.AccountUser
import app.ritualhabits.data.sync.AuthGateway
import app.ritualhabits.data.sync.GoogleCredentialResult
import app.ritualhabits.data.sync.SignInException
import app.ritualhabits.data.sync.SignInFailure
import app.ritualhabits.data.sync.SyncManager
import app.ritualhabits.data.sync.SyncProblem
import app.ritualhabits.data.sync.SyncStatus
import app.ritualhabits.ui.settings.AccountViewModel
import app.ritualhabits.ui.settings.backupStatusText
import app.ritualhabits.ui.settings.dataStorageNote
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

/** Firebase Auth stand-in: the token is the account name. */
class FakeAuth : AuthGateway {
    override val user = MutableStateFlow<AccountUser?>(null)
    var failure: SignInFailure? = null

    override suspend fun signInWithGoogle(idToken: String): AccountUser {
        failure?.let { throw SignInException(it) }
        return AccountUser("$idToken-uid", "$idToken@example.com", idToken.replaceFirstChar(Char::uppercase)).also { user.value = it }
    }

    override suspend fun signOut() {
        user.value = null
    }

    var reauthFailure: SignInFailure? = null
    var deleted = mutableListOf<String>()

    override suspend fun reauthenticateWithGoogle(idToken: String) {
        reauthFailure?.let { throw SignInException(it) }
        if ("$idToken-uid" != user.value?.uid) throw SignInException(SignInFailure.WRONG_ACCOUNT)
    }

    override suspend fun deleteCurrentUser() {
        user.value?.uid?.let(deleted::add)
        user.value = null
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class AccountSyncTest {
    @get:Rule val tmp = TemporaryFolder()
    private val cloud = FakeCloud()
    private val auth = FakeAuth()
    // Buffered like the real ConnectivityManager callback flow (a StateFlow could conflate off→on).
    private val online = MutableSharedFlow<Boolean>(replay = 1, extraBufferCapacity = 8).apply { tryEmit(true) }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var phone: Device
    private lateinit var manager: SyncManager

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        phone = Device(cloud, tmp.newFolder())
        manager = SyncManager(auth, phone.engine, scope, online, changeDebounce = 50.milliseconds, retryDelays = listOf(10.minutes))
        manager.start()
    }

    @After fun tearDown() {
        scope.cancel()
        phone.close()
        Dispatchers.resetMain()
    }

    private suspend fun awaitState(predicate: (AccountState) -> Boolean): AccountState =
        try {
            withTimeout(30_000) { manager.state.first(predicate) }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            throw AssertionError("Last state: ${manager.state.value}; pending=${phone.engine.pendingCount()}", e)
        }

    private suspend fun awaitSynced() = awaitState {
        it is AccountState.SignedIn && !it.sync.syncing && it.sync.pending == 0 && it.sync.lastSyncedAt != null && it.sync.problem == null
    }

    @Test fun `signed out by default, sign in uploads existing habits, sign out keeps them`() = runBlocking<Unit> {
        phone.create("Read")
        assertEquals(AccountState.SignedOut(null), awaitState { it is AccountState.SignedOut })

        manager.signInWithGoogle("alice")
        val signedIn = awaitSynced() as AccountState.SignedIn
        assertEquals("alice@example.com", signedIn.user.email)
        assertEquals(listOf("Read"), cloud.account("alice-uid").habits.values.map { it.name })

        // Later edits upload on their own.
        phone.create("Walk")
        withTimeout(10_000) { while (cloud.account("alice-uid").habits.size < 2) kotlinx.coroutines.delay(20) }

        manager.signOut()
        assertEquals(AccountState.SignedOut("alice@example.com"), awaitState { it is AccountState.SignedOut })
        assertEquals(listOf("Read", "Walk"), phone.names())
    }

    @Test fun `offline sign-in session keeps changes and syncs when the network returns`() = runBlocking<Unit> {
        manager.signInWithGoogle("alice")
        awaitSynced()
        cloud.online = false
        online.emit(false)
        phone.create("Read")
        val offline = awaitState { it is AccountState.SignedIn && it.sync.problem == SyncProblem.OFFLINE } as AccountState.SignedIn
        assertEquals(1, offline.sync.pending)

        cloud.online = true
        online.emit(true)
        awaitSynced()
        assertEquals(listOf("Read"), cloud.account("alice-uid").habits.values.map { it.name })
    }

    @Test fun `a different account asks first, and cancelling leaves everything as it was`() = runBlocking<Unit> {
        manager.signInWithGoogle("alice")
        awaitSynced()
        phone.create("Alice habit")
        withTimeout(10_000) { while (cloud.account("alice-uid").habits.isEmpty()) kotlinx.coroutines.delay(20) }
        manager.signOut()
        awaitState { it is AccountState.SignedOut }

        manager.signInWithGoogle("bob")
        val choice = awaitState { it is AccountState.ChooseAccount } as AccountState.ChooseAccount
        assertEquals("alice@example.com", choice.previousEmail)
        manager.chooseAccount(false)
        assertEquals(AccountState.SignedOut("alice@example.com"), awaitState { it is AccountState.SignedOut })
        assertEquals(listOf("Alice habit"), phone.names())
        assertTrue(cloud.account("bob-uid").habits.isEmpty())

        manager.signInWithGoogle("bob")
        awaitState { it is AccountState.ChooseAccount }
        manager.chooseAccount(true)
        awaitSynced()
        assertTrue(phone.names().isEmpty())
        assertEquals(listOf("Alice habit"), cloud.account("alice-uid").habits.values.map { it.name })
    }

    @Test fun `view model reports cancel, offline and missing account clearly`() = runBlocking<Unit> {
        val context = ApplicationProvider.getApplicationContext<Context>()
        var next: GoogleCredentialResult = GoogleCredentialResult.Cancelled
        val vm = AccountViewModel(manager) { next }
        awaitState { it is AccountState.SignedOut }

        vm.signIn(context)
        withTimeout(5_000) { vm.uiState.first { !it.signingIn && it.account is AccountState.SignedOut } }
        assertNull(vm.uiState.value.message)

        next = GoogleCredentialResult.NoAccount
        vm.signIn(context)
        assertEquals("Add a Google account to this phone, then try again.", withTimeout(5_000) { vm.uiState.first { it.message != null } }.message)

        next = GoogleCredentialResult.Token("alice")
        auth.failure = SignInFailure.NETWORK
        vm.signIn(context)
        assertEquals("You're offline. Connect to the internet and try again.", withTimeout(5_000) { vm.uiState.first { it.message?.startsWith("You're offline") == true } }.message)

        auth.failure = null
        vm.signIn(context)
        val signedIn = withTimeout(5_000) { vm.uiState.first { it.account is AccountState.SignedIn } }
        assertNull(signedIn.message)
        assertIs<AccountState.SignedIn>(signedIn.account)
    }

    @Test fun `delete account from the view model, with wrong-account and cancel handled`() = runBlocking<Unit> {
        val context = ApplicationProvider.getApplicationContext<Context>()
        phone.create("Read")
        manager.signInWithGoogle("alice")
        awaitSynced()
        var next: GoogleCredentialResult = GoogleCredentialResult.Cancelled
        val vm = AccountViewModel(manager) { next }

        vm.deleteAccount(context) // cancelled at the Google picker
        withTimeout(5_000) { vm.uiState.first { !it.deleting } }
        assertIs<AccountState.SignedIn>(manager.state.value)
        assertTrue(auth.deleted.isEmpty())

        next = GoogleCredentialResult.Token("bob") // someone else's account
        vm.deleteAccount(context)
        assertEquals(
            "Choose the Google account you're signed in with to delete it.",
            withTimeout(5_000) { vm.uiState.first { it.message != null } }.message,
        )
        assertEquals(listOf("Read"), cloud.account("alice-uid").habits.values.map { it.name })

        next = GoogleCredentialResult.Token("alice")
        vm.deleteAccount(context)
        assertEquals(
            "Your account and its backup were deleted. Your habits are still on this phone.",
            withTimeout(5_000) { vm.uiState.first { it.message?.startsWith("Your account") == true } }.message,
        )
        assertEquals(listOf("alice-uid"), auth.deleted)
        assertTrue("alice-uid" !in cloud.accounts)
        assertEquals(AccountState.SignedOut(null), awaitState { it is AccountState.SignedOut })
        assertEquals(listOf("Read"), phone.names())
    }

    @Test fun `status and data copy`() {
        val now = Instant.parse("2026-10-19T10:00:00Z")
        val zone = TimeZone.UTC
        fun text(s: SyncStatus) = backupStatusText(s, now, zone, Locale.US)
        assertEquals("Backing up…", text(SyncStatus(syncing = true)))
        assertEquals("Offline. 2 changes will back up when you're connected.", text(SyncStatus(pending = 2, problem = SyncProblem.OFFLINE)))
        assertEquals("Couldn't back up just now. Ritual will try again.", text(SyncStatus(problem = SyncProblem.FAILED)))
        assertEquals("1 change waiting to back up", text(SyncStatus(pending = 1)))
        assertEquals("Backed up just now", text(SyncStatus(lastSyncedAt = now)))
        assertEquals("Backed up 5 min ago", text(SyncStatus(lastSyncedAt = Instant.parse("2026-10-19T09:55:00Z"))))
        assertEquals("Backed up at 7:30 AM", text(SyncStatus(lastSyncedAt = Instant.parse("2026-10-19T07:30:00Z"))))
        assertEquals("Backed up on Oct 17", text(SyncStatus(lastSyncedAt = Instant.parse("2026-10-17T07:30:00Z"))))

        assertEquals("Ritual works offline. Habits and history are stored only on this device.", dataStorageNote(AccountState.SignedOut(null)))
        assertEquals(
            "Ritual works offline. Habits and history are stored on this device and backed up to a@example.com.",
            dataStorageNote(AccountState.SignedIn(AccountUser("u", "a@example.com", null), SyncStatus())),
        )
    }
}
