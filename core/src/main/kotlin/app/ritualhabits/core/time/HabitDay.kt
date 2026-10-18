package app.ritualhabits.core.time

import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

/**
 * A habit day is the calendar date of an instant in the device's current time zone.
 * The day changes at local midnight. Once a habit day is stored it is never recomputed.
 */
object HabitDay {

    fun of(instant: Instant, zone: TimeZone): LocalDate = instant.toLocalDateTime(zone).date

    /**
     * First instant of the habit day after the one containing [after].
     * DST-safe: when local midnight does not exist, this is the first valid instant of that date.
     */
    fun nextBoundary(after: Instant, zone: TimeZone): Instant =
        of(after, zone).plus(1, DateTimeUnit.DAY).atStartOfDayIn(zone)
}

/** Source of "now" and the current zone. Injected everywhere so tests can control time. */
interface AppClock {
    fun now(): Instant
    fun zone(): TimeZone
    fun today(): LocalDate = HabitDay.of(now(), zone())
}

object SystemAppClock : AppClock {
    override fun now(): Instant = Clock.System.now()

    // Re-read on every call so device time-zone changes take effect immediately.
    override fun zone(): TimeZone = TimeZone.currentSystemDefault()
}

/** A clock that only moves when told to. For tests and Compose previews. */
class ManualClock(
    var instant: Instant,
    var timeZone: TimeZone = TimeZone.UTC,
) : AppClock {
    override fun now(): Instant = instant
    override fun zone(): TimeZone = timeZone

    fun advanceBy(duration: kotlin.time.Duration) {
        instant += duration
    }
}
