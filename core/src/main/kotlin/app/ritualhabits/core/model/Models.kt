package app.ritualhabits.core.model

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlin.time.Duration

/**
 * A span of habit days during which a habit is required.
 * [endDateExclusive] == null means the habit is still required.
 */
data class EligibilityPeriod(
    val habitId: String,
    val startDate: LocalDate,
    val endDateExclusive: LocalDate?,
    /** Storage id; 0 for periods not yet persisted. */
    val id: Long = 0,
) {
    init {
        require(endDateExclusive == null || endDateExclusive > startDate) {
            "Empty or inverted period: $startDate..<$endDateExclusive"
        }
    }

    val isOpen: Boolean get() = endDateExclusive == null

    fun covers(date: LocalDate): Boolean =
        date >= startDate && (endDateExclusive == null || date < endDateExclusive)
}

/** "Habit [habitId] was completed on habit day [date]". At most one per pair. */
data class CompletionKey(val habitId: String, val date: LocalDate)

enum class CompletionSource { MANUAL, FOCUS }

enum class FocusStatus { RUNNING, PAUSED, COMPLETED, CANCELLED }

data class FocusSession(
    val id: String,
    val habitId: String,
    val plannedDuration: Duration,
    val status: FocusStatus,
    val startedAt: Instant,
    /** Wall-clock end; present while RUNNING (and kept once COMPLETED). */
    val endsAt: Instant?,
    /** Present while PAUSED. */
    val remainingWhenPaused: Duration?,
    /** COMPLETED: equals [endsAt]. CANCELLED: when it was cancelled. */
    val finishedAt: Instant?,
    /** Habit day the session completed on. */
    val completionDate: LocalDate?,
    val acknowledged: Boolean = false,
) {
    val isActive: Boolean get() = status == FocusStatus.RUNNING || status == FocusStatus.PAUSED
}

enum class ThemeMode { SYSTEM, LIGHT, DARK }
