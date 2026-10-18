package app.ritualhabits.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.ritualhabits.core.model.CompletionSource
import app.ritualhabits.core.model.FocusStatus
import app.ritualhabits.core.rules.HabitInputError
import app.ritualhabits.core.rules.StreakCalculator
import app.ritualhabits.core.time.ManualClock
import app.ritualhabits.data.local.RitualDatabase
import app.ritualhabits.data.repository.CompletionRepository
import app.ritualhabits.data.repository.FocusRepository
import app.ritualhabits.data.repository.FocusStartResult
import app.ritualhabits.data.repository.HabitRepository
import app.ritualhabits.data.repository.HabitWriteResult
import app.ritualhabits.data.repository.ToggleResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

@RunWith(RobolectricTestRunner::class)
class DataLayerTest {
    private val zone = TimeZone.of("Asia/Kolkata")
    private val clock = ManualClock(LocalDateTime.parse("2026-10-18T09:00").toInstant(zone), zone)
    private val context: Context = ApplicationProvider.getApplicationContext()

    private lateinit var db: RitualDatabase
    private lateinit var habits: HabitRepository
    private lateinit var completions: CompletionRepository
    private lateinit var focus: FocusRepository

    @Before fun setUp() = open(Room.inMemoryDatabaseBuilder(context, RitualDatabase::class.java).build())

    @After fun tearDown() = db.close()

    private fun open(database: RitualDatabase) {
        db = database
        habits = HabitRepository(db, clock)
        completions = CompletionRepository(db, clock)
        focus = FocusRepository(db, clock)
    }

    private fun nextDay() = clock.advanceBy(24.hours) // Kolkata has no DST
    private fun daysAgo(n: Int) = clock.today().minus(n, DateTimeUnit.DAY)
    private suspend fun history() = habits.observeHistory().first()
    private suspend fun summary() = StreakCalculator.summarize(history(), clock.today())
    private suspend fun create(name: String) = (habits.create(name, null) as HabitWriteResult.Success).habitId
    private suspend fun completeToday(vararg ids: String) =
        ids.forEach { assertEquals(ToggleResult.COMPLETED, completions.toggle(it, clock.today())) }

    // --- Completions ---------------------------------------------------------------------

    @Test fun `toggle completes and uncompletes without duplicates`() = runBlocking<Unit> {
        val a = create("Read")
        completeToday(a)
        assertEquals(1, summary().today.completed)
        assertEquals(ToggleResult.UNCOMPLETED, completions.toggle(a, clock.today()))
        assertEquals(0, db.completionDao().count())
        completeToday(a)
        assertEquals(1, db.completionDao().count())
    }

    @Test fun `stale screen cannot write into the wrong day`() = runBlocking<Unit> {
        val a = create("Read")
        val shownDay = clock.today()
        clock.advanceBy(15.hours) // past midnight
        assertEquals(ToggleResult.STALE_DATE, completions.toggle(a, shownDay))
        assertEquals(0, db.completionDao().count())
    }

    @Test fun `archived habit cannot be completed`() = runBlocking<Unit> {
        val a = create("Read")
        habits.archive(a)
        assertEquals(ToggleResult.NOT_ELIGIBLE, completions.toggle(a, clock.today()))
    }

    // --- Historical integrity ------------------------------------------------------------

    @Test fun `adding, archiving and deleting habits never changes past results`() = runBlocking<Unit> {
        val a = create("Read")
        val b = create("Walk")
        completeToday(a, b) // day 1
        nextDay()
        completeToday(a, b) // day 2
        nextDay()
        val past = StreakCalculator.days(history(), daysAgo(2), daysAgo(1))
        assertEquals(2, summary().currentStreak)

        create("Stretch")
        habits.archive(b)
        habits.delete(a)

        assertEquals(past, StreakCalculator.days(history(), daysAgo(2), daysAgo(1)))
        val s = summary()
        assertEquals(2, s.currentStreak) // today pending
        assertEquals(2, s.longestStreak)
        assertEquals(1, s.today.required) // only "Stretch" today
    }

    @Test fun `deleting the habit you missed does not turn a failed day into a success`() = runBlocking<Unit> {
        val a = create("Read")
        val b = create("Walk")
        completeToday(a) // b missed
        nextDay()
        habits.delete(b)
        assertEquals(false, history().day(daysAgo(1)).qualified)
        assertEquals(0, summary().currentStreak)
    }

    @Test fun `new habit is required from creation day only`() = runBlocking<Unit> {
        val a = create("Read")
        completeToday(a)
        nextDay()
        completeToday(a)
        val c = create("Journal")
        assertEquals(1, history().day(daysAgo(1)).required)
        assertEquals(false, summary().today.qualified)
        completeToday(c)
        assertEquals(2, summary().currentStreak)
    }

    @Test fun `restore same day leaves no gap, restore later resumes from that day`() = runBlocking<Unit> {
        val a = create("Read")
        nextDay()
        habits.archive(a)
        habits.restore(a)
        assertEquals(1, db.periodDao().forHabit(a).size)
        assertEquals(true, history().isEligible(a, clock.today()))

        habits.archive(a)
        nextDay(); nextDay()
        habits.restore(a)
        val h = history()
        assertEquals(false, h.isEligible(a, daysAgo(1)))
        assertEquals(true, h.isEligible(a, clock.today()))
    }

    @Test fun `names are validated and deleted names can be reused`() = runBlocking<Unit> {
        val a = create("Read")
        assertEquals(HabitWriteResult.Invalid(HabitInputError.NAME_DUPLICATE), habits.create(" read ", null))
        habits.delete(a)
        create("Read")
        assertEquals(HabitWriteResult.Invalid(HabitInputError.NAME_DUPLICATE), habits.restore(a))
    }

    // --- Focus sessions ------------------------------------------------------------------

    @Test fun `natural finish completes the habit exactly once`() = runBlocking<Unit> {
        val a = create("Read")
        val s = (focus.start(a, 25.minutes) as FocusStartResult.Started).session
        clock.advanceBy(26.minutes)
        assertEquals(1, focus.reconcile().size)
        assertEquals(0, focus.reconcile().size) // second caller (e.g. alarm) does nothing
        assertEquals(FocusStatus.COMPLETED, db.focusSessionDao().get(s.id)!!.status)
        assertEquals(1, db.completionDao().count())
        assertEquals(CompletionSource.FOCUS, db.completionDao().get(a, clock.today())!!.source)
    }

    @Test fun `cancelling never completes the habit`() = runBlocking<Unit> {
        val a = create("Read")
        val s = (focus.start(a, 25.minutes) as FocusStartResult.Started).session
        clock.advanceBy(10.minutes)
        assertEquals(FocusStatus.CANCELLED, focus.cancel(s.id)!!.status)
        clock.advanceBy(30.minutes)
        focus.reconcile()
        assertEquals(0, db.completionDao().count())
    }

    @Test fun `finishing an already completed habit does not inflate counts`() = runBlocking<Unit> {
        val a = create("Read")
        completeToday(a)
        focus.start(a, 15.minutes)
        clock.advanceBy(16.minutes)
        focus.reconcile()
        assertEquals(1, db.completionDao().count())
        assertEquals(CompletionSource.MANUAL, db.completionDao().get(a, clock.today())!!.source)
    }

    @Test fun `paused session does not complete and resumes with the right remaining time`() = runBlocking<Unit> {
        val a = create("Read")
        val s = (focus.start(a, 25.minutes) as FocusStartResult.Started).session
        clock.advanceBy(10.minutes)
        focus.pause(s.id)
        clock.advanceBy(2.hours)
        assertEquals(0, focus.reconcile().size)
        val resumed = focus.resume(s.id)!!
        assertEquals(clock.now() + 15.minutes, resumed.endsAt)
        assertIs<FocusStartResult.AlreadyActive>(focus.start(a, 15.minutes))
    }

    @Test fun `session ending after midnight completes the new day, even if noticed hours later`() = runBlocking<Unit> {
        clock.instant = LocalDateTime.parse("2026-10-18T23:50").toInstant(zone)
        val a = create("Read")
        nextDay() // make sure the habit is eligible on the 19th too; restart at 23:50 on the 19th
        val s = (focus.start(a, 25.minutes) as FocusStartResult.Started).session
        clock.advanceBy(9.hours) // phone opened next morning
        focus.reconcile()
        assertEquals(LocalDate(2026, 10, 20), db.focusSessionDao().get(s.id)!!.completionDate)
        assertEquals(1, db.completionDao().count())
    }

    @Test fun `archiving a habit cancels its running session`() = runBlocking<Unit> {
        val a = create("Read")
        val s = (focus.start(a, 25.minutes) as FocusStartResult.Started).session
        habits.archive(a)
        clock.advanceBy(30.minutes)
        focus.reconcile()
        assertEquals(FocusStatus.CANCELLED, db.focusSessionDao().get(s.id)!!.status)
        assertEquals(0, db.completionDao().count())
    }

    @Test fun `acknowledged completion disappears from the current session`() = runBlocking<Unit> {
        val a = create("Read")
        val s = (focus.start(a, 15.minutes) as FocusStartResult.Started).session
        clock.advanceBy(15.minutes)
        focus.reconcile()
        assertEquals(s.id, focus.observeCurrent().first()!!.id)
        focus.acknowledge(s.id)
        assertNull(focus.observeCurrent().first())
    }

    // --- Persistence and time zones ------------------------------------------------------

    @Test fun `records survive closing and reopening the database`() = runBlocking<Unit> {
        db.close()
        context.deleteDatabase("persist-test.db")
        open(Room.databaseBuilder(context, RitualDatabase::class.java, "persist-test.db").build())
        val a = create("Read")
        completeToday(a)
        db.close()

        open(Room.databaseBuilder(context, RitualDatabase::class.java, "persist-test.db").build())
        assertEquals(1, summary().today.completed)
        assertEquals("Read", habits.observeActive().first().single().name)
    }

    @Test fun `changing time zone never rewrites stored dates`() = runBlocking<Unit> {
        val a = create("Read")
        nextDay()
        clock.instant = LocalDateTime.parse("2026-10-19T00:30").toInstant(zone)
        completeToday(a) // stored as the 19th
        clock.timeZone = TimeZone.of("America/Los_Angeles") // same instant is the 18th there
        assertEquals(LocalDate(2026, 10, 18), clock.today())
        assertEquals(LocalDate(2026, 10, 19), db.completionDao().observeAll().first().single().date)
        assertEquals(0, summary().today.completed) // the 18th has nothing recorded
    }
}
