package app.ritualhabits.core

import app.ritualhabits.core.time.HabitDay
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals

class HabitDayTest {
    private val kolkata = TimeZone.of("Asia/Kolkata")
    private val la = TimeZone.of("America/Los_Angeles")

    private fun at(text: String, zone: TimeZone): Instant = LocalDateTime.parse(text).toInstant(zone)

    @Test fun `day changes exactly at local midnight`() {
        assertEquals(LocalDate(2026, 10, 18), HabitDay.of(at("2026-10-18T23:59:59.999", kolkata), kolkata))
        assertEquals(LocalDate(2026, 10, 19), HabitDay.of(at("2026-10-19T00:00:00", kolkata), kolkata))
    }

    @Test fun `same instant is a different habit day in another zone`() {
        val instant = at("2026-10-19T00:30", kolkata)
        assertEquals(LocalDate(2026, 10, 19), HabitDay.of(instant, kolkata))
        assertEquals(LocalDate(2026, 10, 18), HabitDay.of(instant, la))
    }

    @Test fun `next boundary is the next local midnight`() {
        val boundary = HabitDay.nextBoundary(at("2026-10-18T15:20", kolkata), kolkata)
        assertEquals(at("2026-10-19T00:00", kolkata), boundary)
    }

    @Test fun `next boundary across a DST change is still midnight`() {
        // LA falls back on 2026-11-01 at 02:00; that day is 25 hours long.
        val boundary = HabitDay.nextBoundary(at("2026-11-01T12:00", la), la)
        assertEquals(at("2026-11-02T00:00", la), boundary)
    }

    @Test fun `next boundary when midnight does not exist`() {
        // Santiago springs forward at 00:00 -> 01:00 on 2026-09-06.
        val santiago = TimeZone.of("America/Santiago")
        val boundary = HabitDay.nextBoundary(at("2026-09-05T20:00", santiago), santiago)
        assertEquals(LocalDate(2026, 9, 6), HabitDay.of(boundary, santiago))
        assertEquals(LocalDate(2026, 9, 5), HabitDay.of(boundary - kotlin.time.Duration.parse("1ms"), santiago))
    }
}
