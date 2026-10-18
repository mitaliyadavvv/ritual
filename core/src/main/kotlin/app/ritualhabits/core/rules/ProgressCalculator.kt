package app.ritualhabits.core.rules

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/** A calendar month. Month is 1-12. */
data class CalendarMonth(val year: Int, val month: Int) : Comparable<CalendarMonth> {
    init {
        require(month in 1..12) { "month must be 1-12" }
    }

    val firstDay: LocalDate get() = LocalDate(year, month, 1)

    /** 28, 29, 30 or 31, using the real calendar (leap years included). */
    val length: Int get() = firstDay.plus(1, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY).dayOfMonth

    val lastDay: LocalDate get() = LocalDate(year, month, length)

    val days: List<LocalDate> get() = (1..length).map { LocalDate(year, month, it) }

    fun plus(months: Int): CalendarMonth = firstDay.plus(months, DateTimeUnit.MONTH).let { CalendarMonth(it.year, it.monthNumber) }

    /** Empty cells before day 1 so that day 1 sits under its weekday, for a week starting on [firstDayOfWeek]. */
    fun leadingBlanks(firstDayOfWeek: DayOfWeek): Int = (firstDay.dayOfWeek.isoDayNumber - firstDayOfWeek.isoDayNumber + 7) % 7

    override fun compareTo(other: CalendarMonth): Int = compareValuesBy(this, other, { it.year }, { it.month })

    companion object {
        fun of(date: LocalDate) = CalendarMonth(date.year, date.monthNumber)
    }
}

/** How one calendar day looks in the overall month view. */
enum class DayStatus {
    /** Every habit due that day was done: the day counts (same rule as streaks). */
    COMPLETE,
    /** Some, but not all, habits due that day were done. */
    PARTIAL,
    /** Habits were due and none were done. */
    MISSED,
    /** Today, not yet complete. Never shown as missed. */
    IN_PROGRESS,
    /** No habit was due (before tracking started, or everything archived). Not a success, not a miss. */
    NOTHING_DUE,
    /** Hasn't happened yet. */
    FUTURE,
}

data class DayCell(
    val date: LocalDate,
    val status: DayStatus,
    val required: Int,
    val completed: Int,
    val isToday: Boolean,
)

data class MonthOverview(
    val month: CalendarMonth,
    val days: List<DayCell>,
    /** Days (up to today) on which every due habit was done. */
    val completeDays: Int,
    /** Finished days (before today) with some but not all habits done. Today is "in progress", not partial. */
    val partialDays: Int,
    /** Completed due-habit slots ÷ due-habit slots, over days of this month that have occurred (today included). */
    val rate: CompletionRate,
)

/** How one habit looks on one calendar day. */
enum class HabitDayStatus {
    DONE,
    /** Due on a finished day and not done. */
    MISSED,
    /** Due today and not done yet. */
    PENDING,
    /** Not due that day: before it was created, while archived, or after it was deleted. Never a miss. */
    NOT_DUE,
    FUTURE,
}

data class HabitMonth(
    val habitId: String,
    val days: List<HabitDayStatus>,
    /** Done ÷ due over days of this month that have occurred. */
    val rate: CompletionRate,
)

data class DayDetailEntry(val habitId: String, val done: Boolean)

/**
 * Month and habit views for Progress. Every answer comes from [HabitHistory]: the same eligibility
 * periods and completions, and the same "all due habits done" rule, that drive streaks. Nothing here
 * is a second definition of a successful day.
 */
object ProgressCalculator {

    fun month(history: HabitHistory, month: CalendarMonth, today: LocalDate): MonthOverview {
        val cells = month.days.map { date ->
            if (date > today) {
                DayCell(date, DayStatus.FUTURE, 0, 0, isToday = false)
            } else {
                val r = history.day(date)
                val status = when {
                    r.required == 0 -> DayStatus.NOTHING_DUE
                    r.qualified -> DayStatus.COMPLETE
                    date == today -> DayStatus.IN_PROGRESS
                    r.completed > 0 -> DayStatus.PARTIAL
                    else -> DayStatus.MISSED
                }
                DayCell(date, status, r.required, r.completed, isToday = date == today)
            }
        }
        val occurred = cells.filter { it.status != DayStatus.FUTURE }
        return MonthOverview(
            month = month,
            days = cells,
            completeDays = cells.count { it.status == DayStatus.COMPLETE },
            partialDays = cells.count { it.status == DayStatus.PARTIAL },
            rate = CompletionRate(occurred.sumOf { it.completed }, occurred.sumOf { it.required }),
        )
    }

    fun habitMonth(history: HabitHistory, habitId: String, month: CalendarMonth, today: LocalDate): HabitMonth {
        var done = 0
        var due = 0
        val days = month.days.map { date ->
            when {
                date > today -> HabitDayStatus.FUTURE
                !history.isEligible(habitId, date) -> HabitDayStatus.NOT_DUE
                history.isCompleted(habitId, date) -> HabitDayStatus.DONE.also { done++; due++ }
                date == today -> HabitDayStatus.PENDING.also { due++ }
                else -> HabitDayStatus.MISSED.also { due++ }
            }
        }
        return HabitMonth(habitId, days, CompletionRate(done, due))
    }

    /** Habits that were due at some point in [month] up to [today] — including later-archived or deleted ones. */
    fun habitsInMonth(history: HabitHistory, month: CalendarMonth, today: LocalDate): Set<String> {
        val end = minOf(month.lastDay, today)
        if (end < month.firstDay) return emptySet()
        return history.habitIds.filterTo(linkedSetOf()) { id ->
            month.days.any { it <= end && history.isEligible(id, it) }
        }
    }

    /** Habits due on [date] and whether each was done. Empty when nothing was due. */
    fun dayDetail(history: HabitHistory, date: LocalDate): List<DayDetailEntry> =
        history.requiredOn(date).map { DayDetailEntry(it, history.isCompleted(it, date)) }
}
