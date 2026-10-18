package app.ritualhabits.core

import app.ritualhabits.core.model.CompletionKey
import app.ritualhabits.core.model.EligibilityPeriod
import app.ritualhabits.core.rules.CalendarMonth
import app.ritualhabits.core.rules.DayStatus
import app.ritualhabits.core.rules.DayStatus.COMPLETE
import app.ritualhabits.core.rules.DayStatus.FUTURE
import app.ritualhabits.core.rules.DayStatus.IN_PROGRESS
import app.ritualhabits.core.rules.DayStatus.MISSED
import app.ritualhabits.core.rules.DayStatus.NOTHING_DUE
import app.ritualhabits.core.rules.DayStatus.PARTIAL
import app.ritualhabits.core.rules.HabitDayStatus
import app.ritualhabits.core.rules.HabitHistory
import app.ritualhabits.core.rules.ProgressCalculator
import app.ritualhabits.core.rules.StreakCalculator
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class ProgressCalculatorTest {
    private val oct = CalendarMonth(2026, 10)
    private fun d(day: Int, month: Int = 10) = LocalDate(2026, month, day)
    private fun open(id: String, from: LocalDate) = EligibilityPeriod(id, from, null)
    private fun done(id: String, vararg days: Int) = days.map { CompletionKey(id, d(it)) }

    @Test fun `month lengths follow the real calendar including leap years`() {
        assertEquals(31, CalendarMonth(2026, 1).length)
        assertEquals(28, CalendarMonth(2026, 2).length)
        assertEquals(29, CalendarMonth(2028, 2).length)  // leap year
        assertEquals(28, CalendarMonth(2100, 2).length)  // divisible by 100, not a leap year
        assertEquals(29, CalendarMonth(2000, 2).length)  // divisible by 400, leap year
        assertEquals(30, CalendarMonth(2026, 4).length)
        assertEquals(31, CalendarMonth(2026, 12).length)
        assertEquals(30, oct.plus(1).days.size)
    }

    @Test fun `navigation crosses year boundaries`() {
        assertEquals(CalendarMonth(2027, 1), CalendarMonth(2026, 12).plus(1))
        assertEquals(CalendarMonth(2025, 12), CalendarMonth(2026, 1).plus(-1))
        assertEquals(true, CalendarMonth(2026, 9) < oct)
    }

    @Test fun `day 1 is aligned under its weekday`() {
        // 1 October 2026 is a Thursday.
        assertEquals(3, oct.leadingBlanks(DayOfWeek.MONDAY))
        assertEquals(4, oct.leadingBlanks(DayOfWeek.SUNDAY))
        // 1 June 2026 is a Monday.
        assertEquals(0, CalendarMonth(2026, 6).leadingBlanks(DayOfWeek.MONDAY))
        assertEquals(1, CalendarMonth(2026, 6).leadingBlanks(DayOfWeek.SUNDAY))
    }

    @Test fun `day statuses use the streak rule and never call the future missed`() {
        val periods = listOf(open("a", d(5)), open("b", d(5)))
        val completions = done("a", 5, 6, 8, 18) + done("b", 5, 8)
        val m = ProgressCalculator.month(HabitHistory(periods, completions), oct, today = d(18))
        fun s(day: Int): DayStatus = m.days[day - 1].status
        assertEquals(NOTHING_DUE, s(4))   // before any habit existed
        assertEquals(COMPLETE, s(5))
        assertEquals(PARTIAL, s(6))
        assertEquals(MISSED, s(7))
        assertEquals(IN_PROGRESS, s(18)) // today, half done: not partial, not missed
        assertEquals(FUTURE, s(19))
        assertEquals(FUTURE, s(31))
        assertEquals(true, m.days[17].isToday)
        assertEquals(2, m.completeDays)     // 5th and 8th
        assertEquals(1, m.partialDays)      // 6th only
        // Rate over days that have occurred: 5..18 = 14 days x 2 habits = 28 slots; 6 done (a x4, b x2).
        assertEquals(6, m.rate.completed)
        assertEquals(28, m.rate.required)
    }

    @Test fun `complete days match qualifying streak days exactly`() {
        val periods = listOf(open("a", d(1)))
        val history = HabitHistory(periods, done("a", 1, 2, 3, 10))
        val m = ProgressCalculator.month(history, oct, today = d(12))
        m.days.filter { it.date <= d(12) }.forEach {
            assertEquals(history.day(it.date).qualified, it.status == COMPLETE, "day ${it.date}")
        }
        assertEquals(StreakCalculator.summarize(history, d(12)).longestStreak, 3)
    }

    @Test fun `a habit created mid-month is not due before its creation`() {
        val history = HabitHistory(listOf(open("a", d(1)), open("new", d(15))), done("a", 10) + done("new", 15))
        val habit = ProgressCalculator.habitMonth(history, "new", oct, today = d(16))
        assertEquals(HabitDayStatus.NOT_DUE, habit.days[13])  // 14th
        assertEquals(HabitDayStatus.DONE, habit.days[14])     // 15th
        assertEquals(HabitDayStatus.PENDING, habit.days[15])  // today
        assertEquals(HabitDayStatus.FUTURE, habit.days[16])
        assertEquals(1, habit.rate.completed)
        assertEquals(2, habit.rate.required)
        // Overall day 10 only needed "a".
        assertEquals(COMPLETE, ProgressCalculator.month(history, oct, d(16)).days[9].status)
    }

    @Test fun `archived gaps and deleted habits keep their history`() {
        // Archived on the 10th, restored on the 20th; a second habit deleted on the 12th.
        val periods = listOf(
            EligibilityPeriod("a", d(1), d(10)), open("a", d(20)),
            EligibilityPeriod("gone", d(1), d(12)),
        )
        val history = HabitHistory(periods, done("a", 1, 2, 21) + done("gone", 1, 11))
        val a = ProgressCalculator.habitMonth(history, "a", oct, today = d(25))
        assertEquals(HabitDayStatus.DONE, a.days[0])
        assertEquals(HabitDayStatus.MISSED, a.days[2])
        assertEquals(HabitDayStatus.NOT_DUE, a.days[14])   // archived gap is not a miss
        assertEquals(HabitDayStatus.DONE, a.days[20])
        val gone = ProgressCalculator.habitMonth(history, "gone", oct, today = d(25))
        assertEquals(HabitDayStatus.DONE, gone.days[10])
        assertEquals(HabitDayStatus.NOT_DUE, gone.days[12]) // after deletion
        assertEquals(setOf("a", "gone"), ProgressCalculator.habitsInMonth(history, oct, d(25)))
        assertEquals(emptySet(), ProgressCalculator.habitsInMonth(history, CalendarMonth(2026, 9), d(25)))
    }

    @Test fun `day detail lists exactly the habits due that day`() {
        val periods = listOf(open("a", d(1)), EligibilityPeriod("b", d(1), d(5)), open("c", d(10)))
        val history = HabitHistory(periods, done("a", 3) + done("b", 3) + done("c", 9))
        assertEquals(
            setOf("a" to true, "b" to true),
            ProgressCalculator.dayDetail(history, d(3)).map { it.habitId to it.done }.toSet(),
        )
        assertEquals(setOf("a" to false), ProgressCalculator.dayDetail(history, d(9)).map { it.habitId to it.done }.toSet())
        assertEquals(emptyList(), ProgressCalculator.dayDetail(HabitHistory(periods, emptyList()), LocalDate(2026, 9, 30)))
    }

    @Test fun `last day of the month as today and the first day of the next month`() {
        val history = HabitHistory(listOf(open("a", d(1))), done("a", 31))
        val m = ProgressCalculator.month(history, oct, today = d(31))
        assertEquals(COMPLETE, m.days.last().status)
        assertEquals(true, m.days.none { it.status == FUTURE })
        val nov = ProgressCalculator.month(history, CalendarMonth(2026, 11), today = LocalDate(2026, 11, 1))
        assertEquals(IN_PROGRESS, nov.days[0].status)
        assertEquals(29, nov.days.count { it.status == FUTURE })
    }
}
