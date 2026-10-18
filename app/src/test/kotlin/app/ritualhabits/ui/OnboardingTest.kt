package app.ritualhabits.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.ritualhabits.data.Device
import app.ritualhabits.data.FakeCloud
import app.ritualhabits.data.local.SyncMetaEntity
import app.ritualhabits.data.local.SyncTriggers
import app.ritualhabits.data.repository.FirstRunGate
import app.ritualhabits.data.repository.FirstRunStage
import app.ritualhabits.data.sync.AccountState
import app.ritualhabits.data.sync.AccountUser
import app.ritualhabits.data.sync.SyncManager
import app.ritualhabits.data.sync.SyncStatus
import app.ritualhabits.core.model.ThemeMode
import app.ritualhabits.ui.onboarding.OnboardingScreen
import app.ritualhabits.ui.onboarding.SignInScreen
import app.ritualhabits.ui.onboarding.WelcomeViewModel
import app.ritualhabits.ui.settings.AccountUiState
import app.ritualhabits.ui.theme.RitualTheme
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
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

/** First-run decisions and the welcome flow's stage changes (no UI). */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class FirstRunTest {
    @get:Rule val tmp = TemporaryFolder()
    private val cloud = FakeCloud()
    private val devices = mutableListOf<Device>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private fun device() = Device(cloud, tmp.newFolder()).also(devices::add)
    private suspend fun FirstRunGate.now() = withTimeout(5_000) { stage.first() }

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun tearDown() {
        scope.cancel()
        devices.forEach(Device::close)
        Dispatchers.resetMain()
    }

    @Test fun `a brand-new install starts with onboarding, and the choice is saved`() = runBlocking<Unit> {
        val phone = device()
        val gate = FirstRunGate(phone.preferences, phone.db)
        assertEquals(FirstRunStage.ONBOARDING, gate.now())
        assertEquals(FirstRunStage.ONBOARDING, phone.preferences.firstRunStage.first())
        // Creating a habit later doesn't change a decision already made.
        phone.create("Read")
        assertEquals(FirstRunStage.ONBOARDING, gate.now())
    }

    @Test fun `installs from before onboarding go straight to the app`() = runBlocking<Unit> {
        val withHabits = device().apply { create("Read") }
        assertEquals(FirstRunStage.DONE, FirstRunGate(withHabits.preferences, withHabits.db).now())

        val deletedOnly = device().apply { habits.delete(create("Old")) }
        assertEquals(FirstRunStage.DONE, FirstRunGate(deletedOnly.preferences, deletedOnly.db).now())

        val withSettings = device().apply { preferences.setThemeMode(ThemeMode.DARK) }
        assertEquals(FirstRunStage.DONE, FirstRunGate(withSettings.preferences, withSettings.db).now())

        val withAccount = device().apply { db.syncDao().setMeta(SyncMetaEntity(SyncTriggers.META_OWNER_UID, "alice")) }
        assertEquals(FirstRunStage.DONE, FirstRunGate(withAccount.preferences, withAccount.db).now())
    }

    @Test fun `stages persist and nothing else is touched`() = runBlocking<Unit> {
        val phone = device()
        val gate = FirstRunGate(phone.preferences, phone.db)
        gate.now()
        gate.moveTo(FirstRunStage.SIGN_IN)
        // "Restart": a new gate over the same stores resumes at sign-in.
        assertEquals(FirstRunStage.SIGN_IN, FirstRunGate(phone.preferences, phone.db).now())
        gate.moveTo(FirstRunStage.DONE)
        assertEquals(FirstRunStage.DONE, FirstRunGate(phone.preferences, phone.db).now())
        assertTrue(phone.names().isEmpty())
        assertEquals(ThemeMode.SYSTEM, phone.preferences.preferences.first().themeMode)
    }

    @Test fun `welcome stages, sign-in waits for the first restore, sign-out never brings onboarding back`() = runBlocking<Unit> {
        val old = device().apply { create("Read") }
        old.engine.attach("alice-uid", "alice@example.com")
        old.engine.sync("alice-uid")

        val phone = device()
        val gate = FirstRunGate(phone.preferences, phone.db)
        assertEquals(FirstRunStage.ONBOARDING, gate.now())
        val auth = FakeAuth()
        val manager = SyncManager(auth, phone.engine, scope, changeDebounce = 50.milliseconds, retryDelays = listOf(10.minutes)).apply { start() }
        val welcome = WelcomeViewModel(gate, manager)

        welcome.leaveOnboarding().join()
        assertEquals(FirstRunStage.SIGN_IN, gate.now())
        welcome.backToOnboarding().join()
        assertEquals(FirstRunStage.ONBOARDING, gate.now())
        welcome.leaveOnboarding().join()

        manager.signInWithGoogle("alice")
        withTimeout(10_000) { gate.stage.first { it == FirstRunStage.DONE } }
        // The app opens with the restored habit already there.
        assertEquals(listOf("Read"), phone.names())

        manager.signOut()
        withTimeout(10_000) { manager.state.first { it is AccountState.SignedOut } }
        assertEquals(FirstRunStage.DONE, gate.now())
    }

    @Test fun `without a backup service, Get started goes straight into the app, and Not now works`() = runBlocking<Unit> {
        val phone = device()
        val gate = FirstRunGate(phone.preferences, phone.db)
        gate.now()
        val unavailable = SyncManager(null, phone.engine, scope)
        WelcomeViewModel(gate, unavailable).leaveOnboarding().join()
        assertEquals(FirstRunStage.DONE, gate.now())

        val other = device()
        val gate2 = FirstRunGate(other.preferences, other.db)
        gate2.now()
        val welcome = WelcomeViewModel(gate2, SyncManager(FakeAuth(), other.engine, scope).apply { start() })
        welcome.leaveOnboarding().join()
        assertEquals(FirstRunStage.SIGN_IN, gate2.now())
        welcome.continueWithoutAccount().join()
        assertEquals(FirstRunStage.DONE, gate2.now())
    }
}

/** The onboarding and sign-in screens' controls (Robolectric Compose). */
@RunWith(RobolectricTestRunner::class)
class OnboardingUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `next, back, get started, with page dots`() {
        var finished = 0
        compose.setContent { RitualTheme { OnboardingScreen(onFinish = { finished++ }) } }
        compose.onNodeWithText("Make room for better habits.").assertIsDisplayed()
        compose.onNodeWithContentDescription("Page 1 of 3").assertIsDisplayed()
        compose.onNodeWithText("Next").performClick()
        compose.onNodeWithText("Your progress, at a glance.").assertIsDisplayed()
        compose.onNodeWithContentDescription("Page 2 of 3").assertIsDisplayed()
        compose.onNodeWithContentDescription("Previous").performClick()
        compose.onNodeWithText("Make room for better habits.").assertIsDisplayed()
        compose.onNodeWithText("Next").performClick()
        compose.onNodeWithText("Next").performClick()
        compose.onNodeWithText("Focus on what matters.").assertIsDisplayed()
        compose.onNodeWithContentDescription("Page 3 of 3").assertIsDisplayed()
        compose.onNodeWithText("Skip").assertDoesNotExist()
        compose.onNodeWithText("Get started").performClick()
        assertEquals(1, finished)
    }

    @Test fun `skip goes straight on`() {
        var finished = 0
        compose.setContent { RitualTheme { OnboardingScreen(onFinish = { finished++ }) } }
        compose.onNodeWithText("Skip").performClick()
        assertEquals(1, finished)
    }

    @Test fun `sign-in screen states`() {
        var state by mutableStateOf(AccountUiState(AccountState.SignedOut(null)))
        var restoring by mutableStateOf(false)
        var taps = 0
        var notNow = 0
        compose.setContent {
            RitualTheme {
                SignInScreen(state, restoring, onSignIn = { taps++ }, onChooseAccount = {}, onNotNow = { notNow++ }, onBack = {})
            }
        }
        compose.onNodeWithText("Your rituals, always with you.").assertIsDisplayed()
        compose.onNodeWithText("Your habits, your progress, across reinstalls.").assertIsDisplayed()
        compose.onNodeWithText("Continue with Google").performClick()
        assertEquals(1, taps)

        state = AccountUiState(AccountState.SignedOut(null), signingIn = true)
        compose.onNodeWithText("Connecting…").assertIsNotEnabled()

        state = AccountUiState(AccountState.SignedOut(null), message = "You're offline. Connect to the internet and try again.")
        compose.onNodeWithText("You're offline. Connect to the internet and try again.").assertIsDisplayed()
        compose.onNodeWithText("Try again").performClick()
        assertEquals(2, taps)

        compose.onNodeWithText("Not now").performClick()
        assertEquals(1, notNow)

        state = AccountUiState(AccountState.SignedIn(AccountUser("u", "a@example.com", "A"), SyncStatus(syncing = true)))
        restoring = true
        compose.onNodeWithText("Restoring your habits…").assertIsDisplayed()
        compose.onNodeWithText("Not now").assertDoesNotExist()
    }
}
