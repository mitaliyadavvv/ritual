package app.ritualhabits.core.rules

import app.ritualhabits.core.time.HabitDay
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant

/** When a daily habit reminder should next fire, and whether a delivered one is still relevant. */
object ReminderSchedule {

    /** 9:00 AM: the time offered when a reminder is first switched on. */
    val DEFAULT_TIME = LocalTime(9, 0)

    /**
     * The first occurrence of [time] (local, in [zone]) strictly after [after]: later today, or
     * tomorrow if today's has passed. If [time] doesn't exist that day (DST gap), the zone rules move
     * it forward, so a reminder is never skipped.
     */
    fun next(time: LocalTime, after: Instant, zone: TimeZone): Instant {
        val today = HabitDay.of(after, zone)
        val todays = LocalDateTime(today, time).toInstant(zone)
        return if (todays > after) todays else LocalDateTime(today.plus(1, DateTimeUnit.DAY), time).toInstant(zone)
    }

    /**
     * A reminder delivered at [now] for [scheduledAt] is only shown on the same local day it was
     * meant for. A late delivery from a previous day is dropped, so there is never a backlog.
     */
    fun isForToday(scheduledAt: Instant, now: Instant, zone: TimeZone): Boolean =
        HabitDay.of(scheduledAt, zone) == HabitDay.of(now, zone) && scheduledAt <= now
}
