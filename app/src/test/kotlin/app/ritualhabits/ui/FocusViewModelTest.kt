package app.ritualhabits.ui

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.ritualhabits.core.model.CompletionSource
import app.ritualhabits.core.model.FocusSession
import app.ritualhabits.core.rules.FocusTimer
import app.ritualhabits.core.time.ManualClock
import app.ritualhabits.data.focus.FocusAlarms
import app.ritualhabits.data.focus.FocusCompletionHandler
import app.ritualhabits.data.focus.FocusNotifier
import app.ritualhabits.data.local.RitualDatabase
import app.ritualhabits.data.repository.FocusOutcome
import app.ritualhabits.data.repository.FocusRepository
import app.ritualhabits.data.repository.HabitRepository
import app.ritualhabits.data.repository.HabitWriteResult
import app.ritualhabits.ui.focus.FocusEvent
import app.ritualhabits.ui.focus.FocusPhase
import app.ritualhabits.ui.focus.FocusUiState
import app.ritualhabits.ui.focus.FocusViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class FakeAlarms(override val canBeExact: Boolean = true) : FocusAlarms {
    val scheduled = mutableListOf<FocusSession>()
    var cancels = 0
    override fun schedule(session: FocusSession) { scheduled += session }
    override fun cancel() { cancels++ }
}

class FakeNotifier : FocusNotifier {
    val posted = mutableListOf<Pair<String, FocusOutcome>>()
    override fun notifyEnded(habitName: String, outcome: FocusOutcome) { posted += habitName to outcome }
    override fun clear() {}
}

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class FocusViewModelTest {
    private val zone = TimeZone.of("Asia/Kolkata")
    private val clock = ManualClock(LocalDateTime.parse("2026-10-18T09:00").toInstant(zone), zone)
    private val today = MutableStateFlow(clock.today())
    private val alarms = FakeAlarms()
    private lateinit var db: RitualDatabase
    private lateinit var habits: HabitRepository
    private lateinit var focus: FocusRepository

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), RitualDatabase::class.java).build()
        habits = HabitRepository(db, clock)
        focus = FocusRepository(db, clock)
    }

    @After fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    private fun vm() = FocusViewModel(focus, habits, alarms, clock, today, tickInterval = 20.milliseconds)
    private suspend fun create(name: String) = (habits.create(name, null) as HabitWriteResult.Success).habitId
    private suspend fun FocusViewModel.ready(p: (FocusUiState.Ready) -> Boolean = { true }) =
        withTimeout(5_000) { uiState.first { it is FocusUiState.Ready && p(it) } as FocusUiState.Ready }
    private suspend fun FocusViewModel.nextMessage() =
        withTimeout(5_000) { events.first { it is FocusEvent.Message } as FocusEvent.Message }
    private suspend fun completionCount() = db.completionDao().count()

    private suspend fun runningSession(habitId: String, vm: FocusViewModel = vm()): FocusViewModel {
        vm.ready()
        vm.selectHabit(habitId)
        vm.ready { it.habitId == habitId }
        vm.start()
        vm.ready { it.phase == FocusPhase.RUNNING }
        return vm
    }

    @Test fun `no habits due today shows the empty state, archived habits are not offered`() = runBlocking<Unit> {
        assertEquals(FocusUiState.NoHabits, withTimeout(5_000) { vm().uiState.first { it !is FocusUiState.Loading } })
        val read = create("Read")
        val walk = create("Walk")
        habits.archive(walk)
        assertEquals(listOf(read), vm().ready().habits.map { it.id })
    }

    @Test fun `defaults to 25 minutes and every preset updates the display`() = runBlocking<Unit> {
        create("Read")
        val vm = vm()
        assertEquals(25.minutes, vm.ready().duration)
        for (preset in FocusTimer.PRESETS) {
            vm.selectDuration(preset)
            val s = vm.ready { it.duration == preset }
            assertEquals(preset, s.remaining)
            assertEquals(0f, s.progress)
        }
        assertFailsWith<IllegalArgumentException> { vm.selectDuration(7.minutes) }
    }

    @Test fun `cannot start without choosing a habit`() = runBlocking<Unit> {
        create("Read")
        val vm = vm()
        val s = vm.ready()
        assertNull(s.habitId)
        assertEquals(false, s.canStart)
        vm.start()
        assertTrue(focus.activeSessions().isEmpty())
        assertTrue(alarms.scheduled.isEmpty())
    }

    @Test fun `start, pause and resume keep the right remaining time and alarm`() = runBlocking<Unit> {
        val read = create("Read")
        val vm = vm()
        vm.ready(); vm.selectHabit(read); vm.selectDuration(15.minutes); vm.ready { it.duration == 15.minutes && it.habitId == read }
        vm.start()
        vm.ready { it.phase == FocusPhase.RUNNING }
        assertEquals(clock.now() + 15.minutes, alarms.scheduled.single().endsAt)

        clock.advanceBy(5.minutes)
        assertEquals(10.minutes, vm.ready { it.remaining == 10.minutes }.remaining)
        vm.selectDuration(45.minutes) // locked while a session exists
        vm.selectHabit("someone-else")
        vm.pause()
        val paused = vm.ready { it.phase == FocusPhase.PAUSED }
        assertEquals(1, alarms.cancels)
        assertEquals(15.minutes, paused.duration)

        clock.advanceBy(30.minutes) // time passes while paused: nothing changes
        assertEquals(10.minutes, vm.ready { it.phase == FocusPhase.PAUSED }.remaining)
        vm.resume()
        vm.ready { it.phase == FocusPhase.RUNNING }
        assertEquals(clock.now() + 10.minutes, alarms.scheduled.last().endsAt)
        assertEquals(0, completionCount())
    }

    @Test fun `natural completion marks the session's habit done exactly once`() = runBlocking<Unit> {
        val read = create("Read")
        val walk = create("Walk")
        val vm = runningSession(read)
        vm.selectHabit(walk) // ignored while running
        clock.advanceBy(25.minutes + 1.seconds)
        val done = vm.ready { it.phase == FocusPhase.COMPLETED && it.outcome != null }
        assertEquals(read, done.habitId)
        assertEquals(FocusOutcome.MARKED_DONE, done.outcome)
        assertEquals(1, completionCount())
        assertEquals(CompletionSource.FOCUS, db.completionDao().get(read, clock.today())!!.source)
        assertNull(db.completionDao().get(walk, clock.today()))
        // A late alarm and another reconcile change nothing.
        val notifier = FakeNotifier()
        FocusCompletionHandler(focus, habits, alarms, notifier) { false }.onAlarm()
        focus.reconcile()
        assertEquals(1, completionCount())
        assertTrue(notifier.posted.isEmpty())
    }

    @Test fun `cancelling never completes the habit`() = runBlocking<Unit> {
        val read = create("Read")
        val vm = runningSession(read)
        clock.advanceBy(10.minutes)
        vm.cancel()
        assertEquals("Session cancelled. Nothing was recorded.", vm.nextMessage().text)
        vm.ready { it.phase == FocusPhase.IDLE }
        clock.advanceBy(1.minutes * 60)
        focus.reconcile()
        assertEquals(0, completionCount())
        assertTrue(alarms.cancels >= 1)
    }

    @Test fun `a recreated screen reconciles from the stored end time`() = runBlocking<Unit> {
        val read = create("Read")
        runningSession(read)
        // Process death: the old ViewModel is gone. Time passes; a new one is created.
        clock.advanceBy(40.minutes)
        val recreated = vm()
        val s = recreated.ready { it.phase == FocusPhase.COMPLETED && it.outcome != null }
        assertEquals(read, s.habitId)
        assertEquals(FocusOutcome.MARKED_DONE, s.outcome)
        assertEquals(1, completionCount())
    }

    @Test fun `a running session survives recreation with correct remaining time`() = runBlocking<Unit> {
        val read = create("Read")
        runningSession(read)
        clock.advanceBy(7.minutes)
        val recreated = vm()
        assertEquals(18.minutes, recreated.ready { it.phase == FocusPhase.RUNNING }.remaining)
    }

    @Test fun `archiving the habit mid-session ends it safely without recording`() = runBlocking<Unit> {
        val read = create("Read")
        create("Walk")
        val vm = runningSession(read)
        habits.archive(read)
        assertTrue(vm.nextMessage().text.contains("archived or deleted"))
        val idle = vm.ready { it.phase == FocusPhase.IDLE }
        assertNull(idle.habitId)
        clock.advanceBy(30.minutes)
        focus.reconcile()
        assertEquals(0, completionCount())
    }

    @Test fun `a deleted selected habit is dropped from the selection`() = runBlocking<Unit> {
        val read = create("Read")
        create("Walk")
        val vm = vm()
        vm.ready(); vm.selectHabit(read); vm.ready { it.habitId == read }
        habits.delete(read)
        val s = vm.ready { read !in it.habits.map { o -> o.id } }
        assertNull(s.habitId)
        assertEquals(false, s.canStart)
    }

    @Test fun `a session ending after midnight records the new day, never the previous one`() = runBlocking<Unit> {
        clock.instant = LocalDateTime.parse("2026-10-18T23:50").toInstant(zone)
        today.value = clock.today()
        val read = create("Read")
        val vm = runningSession(read)
        clock.advanceBy(26.minutes) // 00:16 on the 19th
        vm.ready { it.phase == FocusPhase.COMPLETED && it.outcome != null }
        assertEquals(null, db.completionDao().get(read, LocalDate(2026, 10, 18)))
        assertEquals(CompletionSource.FOCUS, db.completionDao().get(read, LocalDate(2026, 10, 19))!!.source)
    }

    @Test fun `if the habit is no longer due when the session ends, nothing is recorded and it says so`() = runBlocking<Unit> {
        val read = create("Read")
        val vm = runningSession(read)
        // Eligibility ends without the session being cancelled (e.g. a device clock change).
        val period = db.periodDao().forHabit(read).single()
        db.periodDao().delete(period)
        clock.advanceBy(26.minutes)
        val s = vm.ready { it.phase == FocusPhase.COMPLETED && it.outcome != null }
        assertEquals(FocusOutcome.NOT_DUE, s.outcome)
        assertEquals(0, completionCount())
    }

    @Test fun `returning to the screen re-arms a running session but never a paused one`() = runBlocking<Unit> {
        val read = create("Read")
        val vm = runningSession(read)
        val before = alarms.scheduled.size
        vm.onResume()
        withTimeout(5_000) { while (alarms.scheduled.size == before) kotlinx.coroutines.delay(10) }
        assertEquals(alarms.scheduled.first().endsAt, alarms.scheduled.last().endsAt)
        vm.pause(); vm.ready { it.phase == FocusPhase.PAUSED }
        val afterPause = alarms.scheduled.size
        vm.onResume()
        kotlinx.coroutines.delay(200)
        assertEquals(afterPause, alarms.scheduled.size)
        assertEquals(true, vm.alertsCanBeExact)
        assertEquals(false, FocusViewModel(focus, habits, FakeAlarms(canBeExact = false), clock, today).alertsCanBeExact)
    }

    @Test fun `start another session returns to setup with the same habit and length`() = runBlocking<Unit> {
        val read = create("Read")
        val vm = vm()
        vm.ready(); vm.selectHabit(read); vm.selectDuration(10.minutes); vm.ready { it.duration == 10.minutes && it.habitId == read }
        vm.start(); vm.ready { it.phase == FocusPhase.RUNNING }
        clock.advanceBy(11.minutes)
        vm.ready { it.phase == FocusPhase.COMPLETED }
        vm.startAnother()
        val idle = vm.ready { it.phase == FocusPhase.IDLE }
        assertEquals(read, idle.habitId)
        assertEquals(10.minutes, idle.duration)
        assertEquals(true, idle.canStart)
    }
}

@RunWith(RobolectricTestRunner::class)
class FocusCompletionHandlerTest {
    private val zone = TimeZone.of("Asia/Kolkata")
    private val clock = ManualClock(LocalDateTime.parse("2026-10-18T09:00").toInstant(zone), zone)
    private lateinit var db: RitualDatabase

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), RitualDatabase::class.java).build()
    }

    @After fun tearDown() = db.close()

    @Test fun `alarm in the background notifies once with the habit name`() = runBlocking<Unit> {
        val habits = HabitRepository(db, clock)
        val focus = FocusRepository(db, clock)
        val notifier = FakeNotifier()
        val alarms = FakeAlarms()
        var foreground = false
        val handler = FocusCompletionHandler(focus, habits, alarms, notifier) { foreground }
        val read = (habits.create("Read", null) as HabitWriteResult.Success).habitId
        focus.start(read, 15.minutes)

        handler.onAlarm() // not due yet
        assertTrue(notifier.posted.isEmpty())
        clock.advanceBy(15.minutes)
        handler.onAlarm()
        handler.onAlarm() // duplicate delivery
        assertEquals(listOf("Read" to FocusOutcome.MARKED_DONE), notifier.posted)
        assertEquals(1, db.completionDao().count())

        // In the foreground the screen shows the result itself.
        focus.start(read, 10.minutes)
        foreground = true
        clock.advanceBy(10.minutes)
        handler.onAlarm()
        assertEquals(1, notifier.posted.size)
    }

    @Test fun `boot re-arms a running session and finishes ones that ended`() = runBlocking<Unit> {
        val habits = HabitRepository(db, clock)
        val focus = FocusRepository(db, clock)
        val alarms = FakeAlarms()
        val handler = FocusCompletionHandler(focus, habits, alarms, FakeNotifier()) { false }
        val read = (habits.create("Read", null) as HabitWriteResult.Success).habitId
        val started = (focus.start(read, 25.minutes) as app.ritualhabits.data.repository.FocusStartResult.Started).session
        clock.advanceBy(5.minutes)
        handler.onBoot()
        assertEquals(started.endsAt, alarms.scheduled.single().endsAt)
        assertEquals(0, db.completionDao().count())
    }
}

