package app.ritualhabits.core

import app.ritualhabits.core.model.EligibilityPeriod
import app.ritualhabits.core.rules.EligibilityRules
import app.ritualhabits.core.rules.PeriodChange
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class EligibilityRulesTest {
    private val d1 = LocalDate(2026, 10, 1)
    private val d5 = LocalDate(2026, 10, 5)
    private val d9 = LocalDate(2026, 10, 9)

    @Test fun `create opens a period on the creation day`() {
        assertEquals(PeriodChange.Insert(EligibilityPeriod("a", d5, null)), EligibilityRules.onCreate("a", d5))
    }

    @Test fun `archive closes the open period at today`() {
        val open = EligibilityPeriod("a", d1, null, id = 1)
        assertEquals(PeriodChange.Close(open, d5), EligibilityRules.onDeactivate(listOf(open), d5))
    }

    @Test fun `archive on the creation day removes the empty period`() {
        val open = EligibilityPeriod("a", d5, null, id = 1)
        assertEquals(PeriodChange.Remove(open), EligibilityRules.onDeactivate(listOf(open), d5))
    }

    @Test fun `archive with nothing open is a no-op`() {
        val closed = EligibilityPeriod("a", d1, d5)
        assertEquals(PeriodChange.None, EligibilityRules.onDeactivate(listOf(closed), d9))
    }

    @Test fun `restore on the same day reopens without a gap`() {
        val closed = EligibilityPeriod("a", d1, d5, id = 1)
        assertEquals(PeriodChange.Reopen(closed), EligibilityRules.onReactivate("a", listOf(closed), d5))
    }

    @Test fun `restore on a later day starts a new period`() {
        val closed = EligibilityPeriod("a", d1, d5, id = 1)
        assertEquals(
            PeriodChange.Insert(EligibilityPeriod("a", d9, null)),
            EligibilityRules.onReactivate("a", listOf(closed), d9),
        )
    }

    @Test fun `restore when already open is a no-op`() {
        val open = EligibilityPeriod("a", d1, null)
        assertEquals(PeriodChange.None, EligibilityRules.onReactivate("a", listOf(open), d9))
    }

    @Test fun `periods are half-open`() {
        val p = EligibilityPeriod("a", d1, d5)
        assertEquals(listOf(true, true, false), listOf(p.covers(d1), p.covers(LocalDate(2026, 10, 4)), p.covers(d5)))
    }
}
