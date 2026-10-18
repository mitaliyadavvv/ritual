package app.ritualhabits.data.repository

import androidx.room.withTransaction
import app.ritualhabits.core.model.CompletionSource
import app.ritualhabits.core.time.AppClock
import app.ritualhabits.data.local.CompletionEntity
import app.ritualhabits.data.local.RitualDatabase
import kotlinx.datetime.LocalDate

enum class ToggleResult {
    COMPLETED,
    UNCOMPLETED,
    /** Habit isn't required on this day (archived, deleted, or not yet created). */
    NOT_ELIGIBLE,
    /** The screen showed a day that is no longer today (e.g. left open over midnight). */
    STALE_DATE,
}

class CompletionRepository(
    private val db: RitualDatabase,
    private val clock: AppClock,
) {
    private val completionDao = db.completionDao()

    /**
     * Toggle [habitId] for [date], which must be the current habit day.
     * Past days are read-only in V1, so historical results cannot change.
     */
    suspend fun toggle(habitId: String, date: LocalDate): ToggleResult = db.withTransaction {
        if (date != clock.today()) return@withTransaction ToggleResult.STALE_DATE
        if (completionDao.delete(habitId, date) > 0) return@withTransaction ToggleResult.UNCOMPLETED
        if (!db.isEligible(habitId, date)) return@withTransaction ToggleResult.NOT_ELIGIBLE
        completionDao.insertIgnore(
            CompletionEntity(habitId, date, clock.now(), CompletionSource.MANUAL, focusSessionId = null),
        )
        ToggleResult.COMPLETED
    }
}
