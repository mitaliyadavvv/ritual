package app.ritualhabits.ui

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.ritualhabits.core.rules.CalendarMonth
import app.ritualhabits.core.rules.DayStatus
import app.ritualhabits.core.rules.HabitDayStatus
import app.ritualhabits.core.rules.StreakCalculator
import app.ritualhabits.core.time.ManualClock
import app.ritualhabits.data.local.RitualDatabase
import app.ritualhabits.data.repository.CompletionRepository
import app.ritualhabits.data.repository.HabitRepository
import app.ritualhabits.data.repository.HabitWriteResult
import app.ritualhabits.data.repository.ToggleResult
import app.ritualhabits.ui.progress.HabitState
import app.ritualhabits.ui.progress.ProgressUiState
import app.ritualhabits.ui.progress.ProgressViewModel
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
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.hours

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ProgressViewModelTest {
    private val zone = TimeZone.of("Asia/Kolkata")
    private val clock = ManualClock(LocalDateTime.parse("2026-09-28T09:00").toInstant(zone), zone)
    private val today = MutableStateFlow(clock.today())
    private lateinit var db: RitualDatabase
    private lateinit var habits: HabitRepository
    private lateinit var completions: CompletionRepository

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), RitualDatabase::class.java).build()
        habits = HabitRepository(db, clock)
        completions = CompletionRepository(db, clock)
    }

    @After fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    private fun vm() = ProgressViewModel(habits, today)
    private fun setDay(text: String) {
        clock.instant = LocalDateTime.parse("${text}T09:00").toInstant(zone)
        today.value = clock.today()
    }
    private suspend fun create(name: String) = (habits.create(name, null) as HabitWriteResult.Success).habitId
    private suspend fun complete(id: String) = assertEquals(ToggleResult.COMPLETED, completions.toggle(id, clock.today()))
    private suspend fun ProgressViewModel.ready(p: (ProgressUiState.Ready) -> Boolean = { true }) =
        withTimeout(5_000) { uiState.first { it is ProgressUiState.Ready && p(it) } as ProgressUiState.Ready }

    /** Read (28 Sep →) and Walk (2 Oct →); Walk deleted 10 Oct; Stretch archived 5 Oct. Today 18 Oct. */
    private suspend fun seed(): Triple<String, String, String> {
        val read = create("Read")
        complete(read)                                       // 28 Sep
        setDay("2026-09-30"); complete(read)
        setDay("2026-10-02")
        val walk = create("Walk")
        val stretch = create("Stretch")
        complete(read); complete(walk); complete(stretch)     // 2 Oct: all done
        setDay("2026-10-03"); complete(read)                  // 3 Oct: partial
        setDay("2026-10-05"); habits.archive(stretch)
        setDay("2026-10-10"); complete(walk); habits.delete(walk)
        setDay("2026-10-18")
        return Triple(read, walk, stretch)
    }

    @Test fun `no habits ever means the empty state`() = runBlocking<Unit> {
        assertEquals(ProgressUiState.Empty, withTimeout(5_000) { vm().uiState.first { it !is ProgressUiState.Loading } })
    }

    @Test fun `defaults to the current month and cannot go into the future`() = runBlocking<Unit> {
        seed()
        val vm = vm()
        val oct = vm.ready()
        assertEquals(CalendarMonth(2026, 10), oct.month)
        assertEquals(31, oct.overview.days.size)
        assertEquals(false, oct.canGoForward)
        assertEquals(true, oct.canGoBack)
        vm.nextMonth()
        assertEquals(CalendarMonth(2026, 10), vm.ready().month)

        vm.previousMonth()
        val sep = vm.ready { it.month == CalendarMonth(2026, 9) }
        assertEquals(30, sep.overview.days.size)
        assertEquals(false, sep.canGoBack) // tracking began in September
        vm.previousMonth()
        assertEquals(CalendarMonth(2026, 9), vm.ready().month)
        vm.nextMonth()
        vm.ready { it.month == CalendarMonth(2026, 10) }
    }

    @Test fun `month statuses and summary come from stored history`() = runBlocking<Unit> {
        seed()
        val oct = vm().ready()
        fun s(day: Int) = oct.overview.days[day - 1].status
        assertEquals(DayStatus.COMPLETE, s(2))
        assertEquals(DayStatus.PARTIAL, s(3))
        assertEquals(DayStatus.MISSED, s(4))
        assertEquals(DayStatus.IN_PROGRESS, s(18))
        assertEquals(DayStatus.FUTURE, s(19))
        assertEquals(1, oct.overview.completeDays)
        val history = habits.observeHistory().first()
        val streak = StreakCalculator.summarize(history, LocalDate(2026, 10, 18))
        assertEquals(streak.currentStreak, oct.currentStreak)
        assertEquals(streak.longestStreak, oct.longestStreak)
    }

    @Test fun `habits view keeps archived and deleted habits with their history`() = runBlocking<Unit> {
        val (read, walk, stretch) = seed()
        val oct = vm().ready()
        val byId = oct.habits.associateBy { it.id }
        assertEquals(listOf(read, stretch, walk), oct.habits.map { it.id }) // active, archived, deleted
        assertEquals(HabitState.ARCHIVED, byId.getValue(stretch).state)
        assertEquals(HabitState.DELETED, byId.getValue(walk).state)
        val walkDays = byId.getValue(walk).month.days
        assertEquals(HabitDayStatus.NOT_DUE, walkDays[0])   // 1 Oct, before creation
        assertEquals(HabitDayStatus.DONE, walkDays[1])      // 2 Oct
        assertEquals(HabitDayStatus.MISSED, walkDays[2])    // 3 Oct
        assertEquals(HabitDayStatus.NOT_DUE, walkDays[9])   // deleted on the 10th (completion that day no longer counts)
        assertEquals(31, walkDays.size)
        // September only lists the habit that existed then.
        val vm = vm()
        vm.ready(); vm.previousMonth()
        assertEquals(listOf(read), vm.ready { it.month.month == 9 }.habits.map { it.id })
    }

    @Test fun `day detail lists that day's due habits, focused habit first`() = runBlocking<Unit> {
        val (read, walk, stretch) = seed()
        val vm = vm()
        vm.ready()
        vm.openDay(LocalDate(2026, 10, 3), walk)
        val detail = withTimeout(5_000) { vm.dayDetail.first { it != null } }!!
        assertEquals(walk, detail.items.first().habitId)
        assertEquals(setOf(read to true, walk to false, stretch to false), detail.items.map { it.habitId to it.done }.toSet())
        assertEquals(DayStatus.PARTIAL, detail.status)
        assertEquals(HabitState.DELETED, detail.items.first().state)

        vm.openDay(LocalDate(2026, 10, 25)) // future: ignored
        assertEquals(LocalDate(2026, 10, 3), vm.dayDetail.value?.date)
        vm.closeDay()
        assertNull(withTimeout(5_000) { vm.dayDetail.first { it == null } })
    }

    @Test fun `following the current month rolls over at the month boundary`() = runBlocking<Unit> {
        seed()
        setDay("2026-10-31")
        val vm = vm()
        vm.ready { it.month == CalendarMonth(2026, 10) }
        clock.advanceBy(24.hours); today.value = clock.today() // 1 Nov
        val nov = vm.ready { it.month == CalendarMonth(2026, 11) }
        assertEquals(30, nov.overview.days.size)
        assertEquals(DayStatus.IN_PROGRESS, nov.overview.days[0].status)
        assertEquals(29, nov.overview.days.count { it.status == DayStatus.FUTURE })
    }

    @Test fun `database failure shows the error state`() = runBlocking<Unit> {
        db.close()
        assertIs<ProgressUiState.Error>(withTimeout(5_000) { vm().uiState.first { it !is ProgressUiState.Loading } })
    }
}
