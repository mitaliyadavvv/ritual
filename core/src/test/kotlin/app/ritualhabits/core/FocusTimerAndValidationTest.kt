package app.ritualhabits.core

import app.ritualhabits.core.model.FocusStatus
import app.ritualhabits.core.rules.FocusTimer
import app.ritualhabits.core.rules.HabitInput
import app.ritualhabits.core.rules.HabitInputError
import app.ritualhabits.core.rules.HabitValidation
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class FocusTimerTest {
    private val zone = TimeZone.of("Asia/Kolkata")
    private val t0 = LocalDateTime.parse("2026-10-18T10:00").toInstant(zone)

    @Test fun `remaining is derived from the end timestamp`() {
        val s = FocusTimer.start("s", "h", 25.minutes, t0)
        assertEquals(25.minutes, FocusTimer.remaining(s, t0))
        assertEquals(15.minutes, FocusTimer.remaining(s, t0 + 10.minutes))
        assertEquals(0.minutes, FocusTimer.remaining(s, t0 + 2.minutes * 60)) // long after: clamps to 0
        assertTrue(FocusTimer.isDue(s, t0 + 25.minutes))
        assertFalse(FocusTimer.isDue(s, t0 + 25.minutes - 1.seconds))
    }

    @Test fun `pause freezes remaining time and resume shifts the end`() {
        val s = FocusTimer.start("s", "h", 25.minutes, t0)
        val paused = FocusTimer.pause(s, t0 + 10.minutes)
        assertEquals(FocusStatus.PAUSED, paused.status)
        assertEquals(15.minutes, FocusTimer.remaining(paused, t0 + 3.minutes * 60))
        val resumed = FocusTimer.resume(paused, t0 + 60.minutes)
        assertEquals(t0 + 75.minutes, resumed.endsAt)
    }

    @Test fun `completion date is the day the session ended`() {
        val late = LocalDateTime.parse("2026-10-18T23:50").toInstant(zone)
        val s = FocusTimer.start("s", "h", 25.minutes, late)
        val done = FocusTimer.complete(s, zone)
        assertEquals(LocalDate(2026, 10, 19), done.completionDate)
        assertEquals(s.endsAt, done.finishedAt)
    }

    @Test fun `cancel never sets a completion date`() {
        val s = FocusTimer.cancel(FocusTimer.start("s", "h", 15.minutes, t0), t0 + 1.minutes)
        assertEquals(FocusStatus.CANCELLED, s.status)
        assertNull(s.completionDate)
    }

    @Test fun `invalid transitions are rejected`() {
        val s = FocusTimer.start("s", "h", 15.minutes, t0)
        assertFailsWith<IllegalStateException> { FocusTimer.resume(s, t0) }
        assertFailsWith<IllegalStateException> { FocusTimer.pause(s, t0 + 15.minutes) }
        val cancelled = FocusTimer.cancel(s, t0)
        assertFailsWith<IllegalStateException> { FocusTimer.complete(cancelled, zone) }
        assertFailsWith<IllegalArgumentException> { FocusTimer.start("s", "h", 0.minutes, t0) }
    }
}

class HabitValidationTest {
    @Test fun `names are normalised`() {
        assertEquals(HabitInput.Valid("Read 20 pages", null), HabitValidation.validate("  Read   20 pages ", "  ", emptyList()))
    }

    @Test fun `empty, too long and duplicate names are rejected`() {
        assertEquals(HabitInput.Invalid(HabitInputError.NAME_EMPTY), HabitValidation.validate("   ", null, emptyList()))
        assertEquals(HabitInput.Invalid(HabitInputError.NAME_TOO_LONG), HabitValidation.validate("x".repeat(61), null, emptyList()))
        assertEquals(HabitInput.Invalid(HabitInputError.NAME_DUPLICATE), HabitValidation.validate("read", null, listOf("Read")))
        assertEquals(
            HabitInput.Invalid(HabitInputError.DESCRIPTION_TOO_LONG),
            HabitValidation.validate("Read", "x".repeat(281), emptyList()),
        )
    }
}
