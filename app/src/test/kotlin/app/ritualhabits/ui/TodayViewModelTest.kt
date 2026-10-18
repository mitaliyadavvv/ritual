package app.ritualhabits.ui

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.ritualhabits.core.rules.HabitInputError
import app.ritualhabits.core.time.ManualClock
import app.ritualhabits.data.local.RitualDatabase
import app.ritualhabits.data.repository.CompletionRepository
import app.ritualhabits.data.repository.HabitRepository
import app.ritualhabits.ui.theme.HapticEvent
import app.ritualhabits.ui.today.TodayEvent
import app.ritualhabits.ui.today.TodayUiState
import app.ritualhabits.ui.today.TodayViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toInstant
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.minutes

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class TodayViewModelTest {
    private val zone = TimeZone.of("Asia/Kolkata")
    private val clock = ManualClock(LocalDateTime.parse("2026-10-18T09:00").toInstant(zone), zone)
    private val today = MutableStateFlow(clock.today())
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: RitualDatabase
    private lateinit var habits: HabitRepository

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = Room.inMemoryDatabaseBuilder(context, RitualDatabase::class.java).build()
        habits = HabitRepository(db, clock)
    }

    @After fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    private fun newViewModel(database: RitualDatabase = db) =
        TodayViewModel(HabitRepository(database, clock), CompletionRepository(database, clock), today)

    private suspend fun TodayViewModel.ready(predicate: (TodayUiState.Ready) -> Boolean = { true }) =
        withTimeout(5_000) { uiState.first { it is TodayUiState.Ready && predicate(it) } as TodayUiState.Ready }

    private suspend fun TodayViewModel.nextEvent() = withTimeout(5_000) { events.first() }

    private suspend fun TodayViewModel.add(name: String) {
        openAddHabit()
        onNameChange(name)
        saveHabit()
        nextEvent()
    }

    @Test fun `starts empty with no sample habits`() = runBlocking<Unit> {
        val state = newViewModel().ready()
        assertEquals(clock.today(), state.date)
        assertEquals(emptyList(), state.habits)
        assertEquals(false, state.hasActiveHabits)
        assertEquals(0, state.currentStreak)
    }

    @Test fun `adding a habit persists it and closes the sheet`() = runBlocking<Unit> {
        val vm = newViewModel()
        vm.ready()
        vm.openAddHabit()
        vm.onNameChange("  Drink   water ")
        vm.onDescriptionChange("Two litres")
        vm.saveHabit()
        assertEquals(TodayEvent.Haptic(HapticEvent.Confirm), vm.nextEvent())
        assertNull(vm.addHabit.value)
        val state = vm.ready { it.total == 1 }
        assertEquals("Drink water", state.habits.single().name)
        assertEquals("Two litres", state.habits.single().description)
        assertEquals(1, habits.observeActive().first().size)
    }

    @Test fun `invalid names keep the sheet open with an error`() = runBlocking<Unit> {
        val vm = newViewModel()
        vm.ready()
        vm.add("Read")

        vm.openAddHabit()
        vm.onNameChange("   ")
        vm.saveHabit()
        assertEquals(TodayEvent.Haptic(HapticEvent.Reject), vm.nextEvent())
        assertEquals(HabitInputError.NAME_EMPTY, vm.addHabit.value!!.error)

        vm.onNameChange("read")
        assertNull(vm.addHabit.value!!.error) // typing clears the error
        vm.saveHabit()
        vm.nextEvent()
        assertEquals(HabitInputError.NAME_DUPLICATE, vm.addHabit.value!!.error)
        assertEquals(1, vm.ready().total)
    }

    @Test fun `complete and uncomplete update progress, haptics and storage`() = runBlocking<Unit> {
        val vm = newViewModel()
        vm.ready()
        vm.add("Read")
        vm.add("Walk")
        val (read, walk) = vm.ready { it.total == 2 }.habits.map { it.id }

        vm.toggle(read)
        assertEquals(TodayEvent.Haptic(HapticEvent.ToggleOn), vm.nextEvent())
        assertEquals(0.5f, vm.ready { it.completed == 1 }.progress)

        vm.toggle(walk)
        assertEquals(TodayEvent.Haptic(HapticEvent.Confirm), vm.nextEvent()) // last one completes the day
        val done = vm.ready { it.allDone }
        assertEquals(1, done.currentStreak)

        vm.toggle(walk)
        assertEquals(TodayEvent.Haptic(HapticEvent.ToggleOff), vm.nextEvent())
        val after = vm.ready { it.completed == 1 }
        assertEquals(0, after.currentStreak)
        assertEquals(1, db.completionDao().count())
    }

    @Test fun `completion survives a new ViewModel and a reopened database`() = runBlocking<Unit> {
        db.close()
        context.deleteDatabase("today-persist.db")
        db = Room.databaseBuilder(context, RitualDatabase::class.java, "today-persist.db").build()
        val vm = newViewModel()
        vm.ready()
        vm.add("Read")
        vm.toggle(vm.ready { it.total == 1 }.habits.single().id)
        vm.nextEvent()
        db.close()

        db = Room.databaseBuilder(context, RitualDatabase::class.java, "today-persist.db").build()
        val reopened = newViewModel().ready { it.total == 1 }
        assertEquals(true, reopened.habits.single().completed)
    }

    @Test fun `midnight rollover shows a fresh day and keeps history`() = runBlocking<Unit> {
        clock.instant = LocalDateTime.parse("2026-10-18T23:58").toInstant(zone)
        today.value = clock.today()
        val vm = newViewModel()
        vm.ready()
        vm.add("Read")
        vm.toggle(vm.ready { it.total == 1 }.habits.single().id)
        vm.nextEvent()
        vm.ready { it.allDone }

        clock.advanceBy(3.minutes) // 00:01 on the 19th
        today.value = clock.today() // what HabitDayTracker publishes at midnight / on resume

        val next = vm.ready { it.date == LocalDate(2026, 10, 19) }
        assertEquals(0, next.completed)
        assertEquals(1, next.total)
        assertEquals(1, next.currentStreak) // yesterday counts; today is pending
        val yesterday = next.date.minus(1, DateTimeUnit.DAY)
        assertEquals(true, db.completionDao().get(next.habits.single().id, yesterday) != null)
    }

    @Test fun `a tap from a screen left open over midnight is not written to either day`() = runBlocking<Unit> {
        clock.instant = LocalDateTime.parse("2026-10-18T23:59").toInstant(zone)
        today.value = clock.today()
        val vm = newViewModel()
        vm.ready()
        vm.add("Read")
        val id = vm.ready { it.total == 1 }.habits.single().id

        clock.advanceBy(2.minutes) // device is now on the 19th, screen still shows the 18th
        vm.toggle(id)
        assertIs<TodayEvent.Message>(vm.nextEvent())
        assertEquals(0, db.completionDao().count())
    }

    @Test fun `database failure shows the error state`() = runBlocking<Unit> {
        db.close()
        val state = withTimeout(5_000) { newViewModel().uiState.first { it !is TodayUiState.Loading } }
        assertEquals(TodayUiState.Error, state)
    }
}
