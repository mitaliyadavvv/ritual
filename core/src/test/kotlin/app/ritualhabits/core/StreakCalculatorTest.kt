package app.ritualhabits.core

import app.ritualhabits.core.model.CompletionKey
import app.ritualhabits.core.model.EligibilityPeriod
import app.ritualhabits.core.rules.HabitHistory
import app.ritualhabits.core.rules.StreakCalculator
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StreakCalculatorTest {
    private val today = LocalDate(2026, 10, 18)
    private fun ago(n: Int) = today.minus(n, DateTimeUnit.DAY)
    private fun open(habit: String, from: LocalDate) = EligibilityPeriod(habit, from, null)
    private fun done(habit: String, vararg daysAgo: Int) = daysAgo.map { CompletionKey(habit, ago(it)) }

    private fun summary(periods: List<EligibilityPeriod>, completions: List<CompletionKey>) =
        StreakCalculator.summarize(HabitHistory(periods, completions), today)

    @Test fun `streak requires every required habit`() {
        val periods = (1..5).map { open("h$it", ago(10)) }
        val fourOfFive = (1..4).flatMap { done("h$it", 1) }
        assertEquals(0, summary(periods, fourOfFive).currentStreak)
        assertEquals(1, summary(periods, fourOfFive + done("h5", 1)).currentStreak)
    }

    @Test fun `pending today keeps yesterday's streak, completing today extends it`() {
        val periods = listOf(open("a", ago(5)))
        assertEquals(2, summary(periods, done("a", 1, 2)).currentStreak)
        assertEquals(3, summary(periods, done("a", 0, 1, 2)).currentStreak)
    }

    @Test fun `missed yesterday resets current streak but not longest`() {
        val s = summary(listOf(open("a", ago(10))), done("a", 2, 3, 4, 5))
        assertEquals(0, s.currentStreak)
        assertEquals(4, s.longestStreak)
    }

    @Test fun `day with no required habits does not count and breaks the streak`() {
        // a required 10..6 days ago, nothing required 5..3 days ago, b required from 2 days ago.
        val periods = listOf(EligibilityPeriod("a", ago(10), ago(5)), open("b", ago(2)))
        val s = summary(periods, done("a", 6, 7, 8, 9, 10) + done("b", 0, 1, 2))
        assertEquals(3, s.currentStreak)
        assertEquals(5, s.longestStreak)
        assertEquals(0, StreakCalculator.days(HabitHistory(periods, emptyList()), ago(4), ago(4)).single().required)
    }

    @Test fun `no habits at all means no streak`() {
        val s = summary(emptyList(), emptyList())
        assertEquals(0, s.currentStreak)
        assertEquals(0, s.longestStreak)
        assertEquals(false, s.today.qualified)
    }

    @Test fun `new habit is required from creation day and does not change earlier days`() {
        val completions = done("a", 1, 2, 3)
        val before = summary(listOf(open("a", ago(5))), completions)
        // Habit b created today.
        val after = summary(listOf(open("a", ago(5)), open("b", today)), completions + done("a", 0))
        assertEquals(3, before.currentStreak)
        assertEquals(3, after.currentStreak) // today pending: a done, b not
        assertEquals(2, after.today.required)
        assertEquals(1, after.today.completed)
    }

    @Test fun `archiving or deleting a habit today leaves past results untouched`() {
        // Yesterday: a done, b missed -> not qualified. Archive b today.
        val completions = done("a", 1, 2) + done("b", 2)
        val before = HabitHistory(listOf(open("a", ago(3)), open("b", ago(3))), completions)
        val after = HabitHistory(listOf(open("a", ago(3)), EligibilityPeriod("b", ago(3), today)), completions)
        val range = StreakCalculator.days(before, ago(3), ago(1))
        assertEquals(range, StreakCalculator.days(after, ago(3), ago(1)))
        assertEquals(false, after.day(ago(1)).qualified)
        // Today only a is required now.
        assertEquals(1, after.day(today).required)
    }

    @Test fun `completions on days a habit was not required are ignored`() {
        val periods = listOf(EligibilityPeriod("a", ago(3), ago(1)))
        val s = summary(periods, done("a", 0, 1, 2, 3))
        assertEquals(0, s.today.required)
        assertEquals(0, s.currentStreak) // yesterday had nothing required
        assertEquals(2, s.longestStreak)
    }

    @Test fun `future dated rows are ignored`() {
        val periods = listOf(open("a", ago(2)))
        val future = listOf(CompletionKey("a", LocalDate(2026, 10, 20)))
        assertEquals(0, summary(periods, future).currentStreak)
    }

    @Test fun `duplicate completion keys cannot inflate counts`() {
        val periods = listOf(open("a", ago(1)))
        val s = summary(periods, done("a", 0, 0, 0))
        assertEquals(1, s.today.completed)
    }

    @Test fun `completion rate counts required slots only`() {
        val history = HabitHistory(listOf(open("a", ago(1)), open("b", ago(1))), done("a", 0, 1) + done("b", 1))
        val rate = StreakCalculator.completionRate(history, ago(1), today)
        assertEquals(3, rate.completed)
        assertEquals(4, rate.required)
        assertNull(StreakCalculator.completionRate(history, ago(9), ago(5)).fraction)
    }

    @Test fun `per-habit streak skips archived gaps but breaks on misses`() {
        val periods = listOf(EligibilityPeriod("a", ago(9), ago(6)), open("a", ago(3)))
        val history = HabitHistory(periods, done("a", 7, 8, 1, 2, 3))
        val stats = StreakCalculator.habitStats(history, "a", today)
        assertEquals(5, stats.currentStreak) // 8,7 then paused 6..4, then 3,2,1; today pending
        assertEquals(5, stats.totalCompletions)
        assertEquals(7, stats.eligibleDays) // 9,8,7 + 3,2,1,0
        val missed = StreakCalculator.habitStats(HabitHistory(periods, done("a", 1, 2)), "a", today)
        assertEquals(2, missed.currentStreak)
    }

    // Ported from the prototype's tests/streaks.test.mjs.
    @Test fun `prototype case - current 3 and longest 4`() {
        val history = HabitHistory(listOf(open("h1", ago(30))), done("h1", 0, 1, 2, 4, 5, 6, 7))
        val stats = StreakCalculator.habitStats(history, "h1", today)
        assertEquals(3, stats.currentStreak)
        assertEquals(4, stats.longestStreak)
        assertEquals(7, stats.totalCompletions)
    }

    @Test fun `prototype case - strict all habits rule`() {
        val periods = listOf(open("h1", ago(5)), open("h2", ago(5)))
        val partial = summary(periods, done("h1", 0))
        assertEquals(false, partial.today.qualified)
        assertEquals(1, partial.today.completed)
        assertEquals(1, summary(periods, done("h1", 0) + done("h2", 0)).currentStreak)
    }
}
