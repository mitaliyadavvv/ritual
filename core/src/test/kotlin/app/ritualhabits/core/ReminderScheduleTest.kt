package app.ritualhabits.core

import app.ritualhabits.core.rules.ReminderSchedule
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

class ReminderScheduleTest {
    private val kolkata = TimeZone.of("Asia/Kolkata")
    private fun at(text: String, zone: TimeZone = kolkata) = LocalDateTime.parse(text).toInstant(zone)

    @Test fun `later today, or tomorrow once today's time has passed`() {
        val nine = LocalTime(9, 0)
        assertEquals(at("2026-10-18T09:00"), ReminderSchedule.next(nine, at("2026-10-18T07:30"), kolkata))
        assertEquals(at("2026-10-19T09:00"), ReminderSchedule.next(nine, at("2026-10-18T09:00"), kolkata)) // exactly now -> tomorrow
        assertEquals(at("2026-10-19T09:00"), ReminderSchedule.next(nine, at("2026-10-18T21:00"), kolkata))
        assertEquals(LocalTime(9, 0), ReminderSchedule.DEFAULT_TIME)
    }

    @Test fun `around midnight`() {
        assertEquals(at("2026-10-19T00:05"), ReminderSchedule.next(LocalTime(0, 5), at("2026-10-18T23:59"), kolkata))
        assertEquals(at("2026-10-19T23:55"), ReminderSchedule.next(LocalTime(23, 55), at("2026-10-18T23:56"), kolkata))
    }

    @Test fun `a time-zone change keeps the local wall-clock time`() {
        val la = TimeZone.of("America/Los_Angeles")
        val now = at("2026-10-18T09:00") // 20:30 the previous evening in LA
        assertEquals(at("2026-10-18T09:00", la), ReminderSchedule.next(LocalTime(9, 0), now, la))
    }

    @Test fun `daylight saving gaps and overlaps never skip a day`() {
        val la = TimeZone.of("America/Los_Angeles")
        // Spring forward 2027-03-14: 02:30 doesn't exist; fires at 03:30 local instead of being skipped.
        val gap = ReminderSchedule.next(LocalTime(2, 30), at("2027-03-14T00:00", la), la)
        assertEquals(at("2027-03-14T03:30", la), gap)
        // Fall back 2026-11-01: 09:00 still exactly once, 25 hours after the previous day's.
        val before = ReminderSchedule.next(LocalTime(9, 0), at("2026-10-31T10:00", la), la)
        assertEquals(at("2026-11-01T09:00", la), before)
        assertEquals(25.hours, before - at("2026-10-31T09:00", la))
    }

    @Test fun `stale deliveries from a previous day are dropped`() {
        val scheduled = at("2026-10-18T09:00")
        assertTrue(ReminderSchedule.isForToday(scheduled, scheduled + 2.minutes, kolkata))
        assertTrue(ReminderSchedule.isForToday(scheduled, at("2026-10-18T23:59"), kolkata))
        assertFalse(ReminderSchedule.isForToday(scheduled, at("2026-10-19T08:00"), kolkata))
        assertFalse(ReminderSchedule.isForToday(scheduled, scheduled - 1.minutes, kolkata))
    }
}
