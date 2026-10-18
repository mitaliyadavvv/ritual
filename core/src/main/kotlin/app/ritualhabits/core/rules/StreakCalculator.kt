package app.ritualhabits.core.rules

import app.ritualhabits.core.model.CompletionKey
import app.ritualhabits.core.model.EligibilityPeriod
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/** Immutable view of everything that decides past results: who was required when, and what was done. */
class HabitHistory(
    periods: Collection<EligibilityPeriod>,
    completions: Collection<CompletionKey>,
) {
    private val periodsByHabit: Map<String, List<EligibilityPeriod>> = periods.groupBy { it.habitId }
    private val completedByDate: Map<LocalDate, Set<String>> =
        completions.groupBy({ it.date }, { it.habitId }).mapValues { it.value.toSet() }

    val firstTrackedDate: LocalDate? = periods.minOfOrNull { it.startDate }

    fun firstTrackedDate(habitId: String): LocalDate? = periodsByHabit[habitId]?.minOfOrNull { it.startDate }

    fun isEligible(habitId: String, date: LocalDate): Boolean =
        periodsByHabit[habitId]?.any { it.covers(date) } == true

    fun requiredOn(date: LocalDate): Set<String> =
        periodsByHabit.filterValues { ps -> ps.any { it.covers(date) } }.keys

    fun isCompleted(habitId: String, date: LocalDate): Boolean =
        completedByDate[date]?.contains(habitId) == true

    fun day(date: LocalDate): DayResult {
        val required = requiredOn(date)
        return DayResult(date, required = required.size, completed = required.count { isCompleted(it, date) })
    }
}

data class DayResult(val date: LocalDate, val required: Int, val completed: Int) {
    /** Earned only when at least one habit was required and every one of them was done. */
    val qualified: Boolean get() = required > 0 && completed == required
}

data class StreakSummary(
    val currentStreak: Int,
    val longestStreak: Int,
    val today: DayResult,
)

data class CompletionRate(val completed: Int, val required: Int) {
    /** null when nothing was required in the range. */
    val fraction: Double? get() = if (required == 0) null else completed.toDouble() / required
}

data class HabitStats(
    val currentStreak: Int,
    val longestStreak: Int,
    val totalCompletions: Int,
    val eligibleDays: Int,
) {
    val rate: CompletionRate get() = CompletionRate(totalCompletions, eligibleDays)
}

object StreakCalculator {

    fun summarize(history: HabitHistory, today: LocalDate): StreakSummary {
        val todayResult = history.day(today)
        val first = history.firstTrackedDate ?: return StreakSummary(0, 0, todayResult)

        // Today still pending does not break the streak; count from yesterday instead.
        var current = 0
        var cursor = if (todayResult.qualified) today else today.minus(1, DateTimeUnit.DAY)
        while (cursor >= first && history.day(cursor).qualified) {
            current++
            cursor = cursor.minus(1, DateTimeUnit.DAY)
        }

        var longest = 0
        var run = 0
        for (result in days(history, first, today)) {
            if (result.qualified) {
                run++
                if (run > longest) longest = run
            } else {
                run = 0
            }
        }
        return StreakSummary(current, maxOf(longest, current), todayResult)
    }

    /** Day-by-day results for [from]..[to] inclusive (empty if the range is inverted). */
    fun days(history: HabitHistory, from: LocalDate, to: LocalDate): List<DayResult> {
        val out = ArrayList<DayResult>()
        var d = from
        while (d <= to) {
            out += history.day(d)
            d = d.plus(1, DateTimeUnit.DAY)
        }
        return out
    }

    /** Completed required slots / required slots over [from]..[to] inclusive. */
    fun completionRate(history: HabitHistory, from: LocalDate, to: LocalDate): CompletionRate =
        days(history, from, to).fold(CompletionRate(0, 0)) { acc, r ->
            CompletionRate(acc.completed + r.completed, acc.required + r.required)
        }

    /** Per-habit stats. Days the habit was not eligible (archived) are skipped, not counted as misses. */
    fun habitStats(history: HabitHistory, habitId: String, today: LocalDate): HabitStats {
        val first = history.firstTrackedDate(habitId) ?: return HabitStats(0, 0, 0, 0)
        val eligible = ArrayList<LocalDate>()
        var d = first
        while (d <= today) {
            if (history.isEligible(habitId, d)) eligible += d
            d = d.plus(1, DateTimeUnit.DAY)
        }

        var longest = 0
        var run = 0
        var total = 0
        for (day in eligible) {
            if (history.isCompleted(habitId, day)) {
                total++
                run++
                if (run > longest) longest = run
            } else {
                run = 0
            }
        }

        var current = 0
        for (i in eligible.indices.reversed()) {
            val day = eligible[i]
            val done = history.isCompleted(habitId, day)
            if (day == today && !done) continue // pending today
            if (!done) break
            current++
        }
        return HabitStats(current, maxOf(longest, current), total, eligible.size)
    }
}
