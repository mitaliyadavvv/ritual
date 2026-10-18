package app.ritualhabits.core.rules

import app.ritualhabits.core.model.FocusSession
import app.ritualhabits.core.model.FocusStatus
import app.ritualhabits.core.time.HabitDay
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/**
 * Pure focus-session state machine. Remaining time is always derived from a stored
 * end timestamp, never decremented, so it cannot drift or reset on process death.
 */
object FocusTimer {

    val PRESETS: List<Duration> = listOf(15.minutes, 25.minutes, 45.minutes, 60.minutes)
    private val MAX_DURATION = 3.hours

    fun start(id: String, habitId: String, duration: Duration, now: Instant): FocusSession {
        require(duration.isPositive() && duration <= MAX_DURATION) { "Invalid duration $duration" }
        return FocusSession(
            id = id,
            habitId = habitId,
            plannedDuration = duration,
            status = FocusStatus.RUNNING,
            startedAt = now,
            endsAt = now + duration,
            remainingWhenPaused = null,
            finishedAt = null,
            completionDate = null,
        )
    }

    fun remaining(session: FocusSession, now: Instant): Duration = when (session.status) {
        FocusStatus.RUNNING -> (session.endsAt!! - now).coerceAtLeast(Duration.ZERO)
        FocusStatus.PAUSED -> session.remainingWhenPaused!!
        FocusStatus.COMPLETED, FocusStatus.CANCELLED -> Duration.ZERO
    }

    /** 0.0 at start, 1.0 at the end. */
    fun progress(session: FocusSession, now: Instant): Float {
        val total = session.plannedDuration
        return (1.0 - remaining(session, now) / total).toFloat().coerceIn(0f, 1f)
    }

    fun isDue(session: FocusSession, now: Instant): Boolean =
        session.status == FocusStatus.RUNNING && now >= session.endsAt!!

    fun pause(session: FocusSession, now: Instant): FocusSession {
        check(session.status == FocusStatus.RUNNING) { "Cannot pause ${session.status}" }
        check(!isDue(session, now)) { "Session already finished; reconcile instead" }
        return session.copy(
            status = FocusStatus.PAUSED,
            remainingWhenPaused = session.endsAt!! - now,
            endsAt = null,
        )
    }

    fun resume(session: FocusSession, now: Instant): FocusSession {
        check(session.status == FocusStatus.PAUSED) { "Cannot resume ${session.status}" }
        return session.copy(
            status = FocusStatus.RUNNING,
            endsAt = now + session.remainingWhenPaused!!,
            remainingWhenPaused = null,
        )
    }

    /** Cancelling never produces a completion. */
    fun cancel(session: FocusSession, now: Instant): FocusSession {
        check(session.isActive) { "Cannot cancel ${session.status}" }
        return session.copy(status = FocusStatus.CANCELLED, finishedAt = now)
    }

    /**
     * Natural completion. The habit day is the day the session *ended* (in [zone]),
     * regardless of when the app noticed.
     */
    fun complete(session: FocusSession, zone: TimeZone): FocusSession {
        check(session.status == FocusStatus.RUNNING) { "Cannot complete ${session.status}" }
        val end = session.endsAt!!
        return session.copy(
            status = FocusStatus.COMPLETED,
            finishedAt = end,
            completionDate = HabitDay.of(end, zone),
        )
    }
}
