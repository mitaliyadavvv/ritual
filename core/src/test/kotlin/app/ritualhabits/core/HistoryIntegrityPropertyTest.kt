package app.ritualhabits.core

import app.ritualhabits.core.model.CompletionKey
import app.ritualhabits.core.model.EligibilityPeriod
import app.ritualhabits.core.rules.EligibilityRules
import app.ritualhabits.core.rules.HabitHistory
import app.ritualhabits.core.rules.StreakCalculator
import app.ritualhabits.core.rules.after
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Randomised lifecycle simulation. Several actions (create, archive, restore, delete, undo,
 * complete) happen per day, including many same-day archive/restore pairs. After every action on
 * day T, every result for days before T must be identical to what it was before the action.
 */
class HistoryIntegrityPropertyTest {
    private val start = LocalDate(2026, 1, 1)

    private enum class State { ACTIVE, ARCHIVED, DELETED }

    @Test fun `no lifecycle sequence rewrites a past day`() {
        repeat(300) { seed -> simulate(Random(seed)) }
    }

    private fun simulate(random: Random) {
        var periods = listOf<EligibilityPeriod>()
        val completions = mutableSetOf<CompletionKey>()
        val states = linkedMapOf<String, State>()
        var nextId = 0

        for (dayIndex in 0 until 40) {
            val today = start.plus(dayIndex, DateTimeUnit.DAY)
            repeat(random.nextInt(0, 6)) {
                val before = snapshot(periods, completions, today)
                val ids = states.keys.toList()
                val id = ids.randomOrNull(random)
                when (random.nextInt(6)) {
                    0 -> {
                        val newId = "h${nextId++}"
                        states[newId] = State.ACTIVE
                        periods = periods.after(EligibilityRules.onCreate(newId, today))
                    }
                    1, 2 -> if (id != null && states[id] == State.ACTIVE) { // archive or delete
                        states[id] = if (random.nextBoolean()) State.ARCHIVED else State.DELETED
                        periods = periods.after(EligibilityRules.onDeactivate(periods.filter { it.habitId == id }, today))
                    }
                    3 -> if (id != null && states[id] != State.ACTIVE) { // restore / undo delete
                        states[id] = State.ACTIVE
                        periods = periods.after(EligibilityRules.onReactivate(id, periods.filter { it.habitId == id }, today))
                    }
                    else -> if (id != null && states[id] == State.ACTIVE) { // complete today (if eligible)
                        if (HabitHistory(periods, completions).isEligible(id, today)) completions += CompletionKey(id, today)
                    }
                }
                assertEquals(before, snapshot(periods, completions, today), "Past changed on $today")
                // At most one open period per habit, and no overlapping periods.
                periods.groupBy { it.habitId }.forEach { (habit, ps) ->
                    assert(ps.count { it.isOpen } <= 1) { "Two open periods for $habit" }
                    val sorted = ps.sortedBy { it.startDate }
                    sorted.zipWithNext().forEach { (a, b) -> assert(a.endDateExclusive != null && a.endDateExclusive!! <= b.startDate) { "Overlap $a $b" } }
                }
            }
        }
    }

    /** Every day result strictly before [today], plus the streaks those days imply. */
    private fun snapshot(periods: List<EligibilityPeriod>, completions: Set<CompletionKey>, today: LocalDate): Any {
        val history = HabitHistory(periods, completions)
        val yesterday = today.plus(-1, DateTimeUnit.DAY)
        if (yesterday < start) return emptyList<Any>()
        val days = StreakCalculator.days(history, start, yesterday)
        val summary = StreakCalculator.summarize(history, yesterday)
        return days to (summary.currentStreak to summary.longestStreak)
    }
}
