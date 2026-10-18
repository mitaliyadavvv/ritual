package app.ritualhabits.core.rules

import app.ritualhabits.core.model.EligibilityPeriod
import kotlinx.datetime.LocalDate

/** A change to one habit's eligibility periods. Every change only affects days >= today. */
sealed interface PeriodChange {
    data object None : PeriodChange
    data class Insert(val period: EligibilityPeriod) : PeriodChange
    data class Close(val period: EligibilityPeriod, val endDateExclusive: LocalDate) : PeriodChange
    data class Remove(val period: EligibilityPeriod) : PeriodChange
    data class Reopen(val period: EligibilityPeriod) : PeriodChange
}

object EligibilityRules {

    /** A new habit is required from the day it is created. */
    fun onCreate(habitId: String, today: LocalDate): PeriodChange =
        PeriodChange.Insert(EligibilityPeriod(habitId, today, null))

    /** Archive or delete: stop requiring the habit from [today] onwards. */
    fun onDeactivate(periods: List<EligibilityPeriod>, today: LocalDate): PeriodChange {
        val open = periods.lastOrNull { it.isOpen } ?: return PeriodChange.None
        // Created (or restored) today and removed today: it never covered a finished day.
        return if (open.startDate >= today) PeriodChange.Remove(open) else PeriodChange.Close(open, today)
    }

    /** Restore from archive, or undo a delete: require the habit again from [today]. */
    fun onReactivate(habitId: String, periods: List<EligibilityPeriod>, today: LocalDate): PeriodChange {
        if (periods.any { it.isOpen }) return PeriodChange.None
        // Deactivated today (or "later", if the clock moved back): reopen instead of leaving a gap.
        val endingTodayOrLater = periods.lastOrNull { it.endDateExclusive!! >= today }
        return if (endingTodayOrLater != null) {
            PeriodChange.Reopen(endingTodayOrLater)
        } else {
            PeriodChange.Insert(EligibilityPeriod(habitId, today, null))
        }
    }
}
