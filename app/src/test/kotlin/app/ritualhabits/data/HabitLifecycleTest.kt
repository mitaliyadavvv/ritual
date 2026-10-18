package app.ritualhabits.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.ritualhabits.core.rules.HabitInputError
import app.ritualhabits.core.rules.StreakCalculator
import app.ritualhabits.core.time.ManualClock
import app.ritualhabits.data.local.RitualDatabase
import app.ritualhabits.data.repository.CompletionRepository
import app.ritualhabits.data.repository.HabitRepository
import app.ritualhabits.data.repository.HabitWriteResult
import app.ritualhabits.data.repository.ToggleResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.DateTimeUnit
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours

/** Manage lifecycle operations against a real Room database, with emphasis on same-day cases. */
@RunWith(RobolectricTestRunner::class)
class HabitLifecycleTest {
    private val zone = TimeZone.of("Asia/Kolkata")
    private val clock = ManualClock(LocalDateTime.parse("2026-10-18T09:00").toInstant(zone), zone)
    private lateinit var db: RitualDatabase
    private lateinit var habits: HabitRepository
    private lateinit var completions: CompletionRepository

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), RitualDatabase::class.java).build()
        habits = HabitRepository(db, clock)
        completions = CompletionRepository(db, clock)
    }

    @After fun tearDown() = db.close()

    private fun nextDay() = clock.advanceBy(24.hours)
    private fun daysAgo(n: Int) = clock.today().minus(n, DateTimeUnit.DAY)
    private suspend fun create(name: String) = (habits.create(name, null) as HabitWriteResult.Success).habitId
    private suspend fun complete(id: String) = assertEquals(ToggleResult.COMPLETED, completions.toggle(id, clock.today()))
    private suspend fun history() = habits.observeHistory().first()
    private suspend fun periods(id: String) = db.periodDao().forHabit(id).map { it.startDate to it.endDate }
    private suspend fun pastResults() = StreakCalculator.days(history(), daysAgo(3), daysAgo(1))

    /** Two habits over three finished days: day -3 both done, -2 only A, -1 both done. */
    private suspend fun seed(): Pair<String, String> {
        clock.advanceBy(-(3 * 24).hours)
        val a = create("Read")
        val b = create("Walk")
        complete(a); complete(b); nextDay()
        complete(a); nextDay()
        complete(a); complete(b); nextDay()
        return a to b
    }

    @Test fun `same-day archive then restore leaves periods and past results identical`() = runBlocking<Unit> {
        val (a, _) = seed()
        complete(a) // today
        val periodsBefore = periods(a)
        val pastBefore = pastResults()

        habits.archive(a)
        assertFalse(history().isEligible(a, clock.today()))
        assertEquals(pastBefore, pastResults())

        habits.restore(a)
        assertEquals(periodsBefore, periods(a))
        assertEquals(pastBefore, pastResults())
        assertTrue(history().isCompleted(a, clock.today())) // today's completion counts again
        assertEquals(1, history().day(clock.today()).completed)
    }

    @Test fun `created, completed, archived and restored on the same day`() = runBlocking<Unit> {
        val a = create("Read")
        complete(a)
        habits.archive(a)
        assertEquals(emptyList(), periods(a)) // never covered a finished day
        assertEquals(0, history().day(clock.today()).required)

        habits.restore(a)
        assertEquals(listOf(clock.today() to null), periods(a))
        assertTrue(history().day(clock.today()).qualified)
    }

    @Test fun `delete then undo on the same day restores everything`() = runBlocking<Unit> {
        val (a, b) = seed()
        val periodsBefore = periods(b)
        val pastBefore = pastResults()
        val streakBefore = StreakCalculator.summarize(history(), clock.today())

        habits.delete(b)
        assertEquals(listOf("Read"), habits.observeActive().first().map { it.name })
        assertEquals(pastBefore, pastResults())
        assertEquals(streakBefore.longestStreak, StreakCalculator.summarize(history(), clock.today()).longestStreak)

        habits.undoDelete(b)
        assertEquals(periodsBefore, periods(b))
        assertEquals(listOf("Read", "Walk"), habits.observeActive().first().map { it.name })
        assertEquals(pastBefore, pastResults())
        assertEquals(a, habits.observeActive().first().first().id)
    }

    @Test fun `undoing the delete of an archived habit keeps it archived`() = runBlocking<Unit> {
        val a = create("Read")
        nextDay()
        habits.archive(a)
        habits.delete(a)
        assertEquals(emptyList(), habits.observeArchived().first())

        habits.undoDelete(a)
        assertEquals(listOf("Read"), habits.observeArchived().first().map { it.name })
        assertEquals(emptyList(), habits.observeActive().first())
        assertFalse(history().isEligible(a, clock.today()))
    }

    @Test fun `archive one day and restore later leaves a gap that is never required`() = runBlocking<Unit> {
        val (a, _) = seed()
        habits.archive(a)
        nextDay(); nextDay()
        habits.restore(a)
        val h = history()
        assertFalse(h.isEligible(a, daysAgo(1)))
        assertFalse(h.isEligible(a, daysAgo(2)))
        assertTrue(h.isEligible(a, clock.today()))
        assertTrue(h.isEligible(a, daysAgo(3)))
    }

    @Test fun `renaming keeps identity, completions and streaks`() = runBlocking<Unit> {
        val (a, _) = seed()
        val before = StreakCalculator.habitStats(history(), a, clock.today())
        assertEquals(HabitWriteResult.Success(a), habits.update(a, "Read 20 pages", "Before bed"))
        val after = StreakCalculator.habitStats(history(), a, clock.today())
        assertEquals(before, after)
        assertEquals("Before bed", habits.observeActive().first().first { it.id == a }.description)
        assertEquals(HabitWriteResult.Invalid(HabitInputError.NAME_DUPLICATE), habits.update(a, "walk", null))
    }

    @Test fun `move reorders active habits atomically and clamps at the ends`() = runBlocking<Unit> {
        val a = create("A"); val b = create("B"); val c = create("C")
        suspend fun order() = habits.observeActive().first().map { it.name }

        assertTrue(habits.move(c, -1))
        assertEquals(listOf("A", "C", "B"), order())
        assertTrue(habits.move(c, -1))
        assertFalse(habits.move(c, -1)) // already first
        assertEquals(listOf("C", "A", "B"), order())
        assertFalse(habits.move(b, 1)) // already last

        habits.archive(a)
        assertTrue(habits.move(b, -1))
        assertEquals(listOf("B", "C"), order())
        habits.restore(a)
        assertEquals(3, order().size)
    }

    @Test fun `deleting every habit leaves nothing behind and no samples reappear`() = runBlocking<Unit> {
        val a = create("Read"); val b = create("Walk")
        complete(a)
        habits.delete(a); habits.delete(b)
        assertEquals(emptyList(), habits.observeActive().first())
        assertEquals(emptyList(), habits.observeArchived().first())
        assertEquals(0, history().day(clock.today()).required)
        // Records are kept for history; nothing was hard-deleted.
        assertEquals(1, db.completionDao().count())
        nextDay()
        assertEquals(emptyList(), habits.observeActive().first())
    }
}
