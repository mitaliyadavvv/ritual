package app.ritualhabits.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import app.ritualhabits.core.model.CompletionSource
import app.ritualhabits.core.model.EligibilityPeriod
import app.ritualhabits.core.model.FocusSession
import app.ritualhabits.core.model.FocusStatus
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.time.Duration.Companion.milliseconds

@Entity(tableName = "habits")
data class HabitEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String?,
    @ColumnInfo(name = "sort_order") val sortOrder: Int,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
    @ColumnInfo(name = "archived_at") val archivedAt: Instant?,
    @ColumnInfo(name = "deleted_at") val deletedAt: Instant?,
    /** Daily reminder switch (schema v2). Existing habits migrate with reminders off. */
    @ColumnInfo(name = "reminder_enabled", defaultValue = "0") val reminderEnabled: Boolean = false,
    /** Local wall-clock time of the reminder; kept when the switch is off so it's remembered. */
    @ColumnInfo(name = "reminder_time") val reminderTime: LocalTime? = null,
) {
    val isDeleted: Boolean get() = deletedAt != null
    val isArchived: Boolean get() = !isDeleted && archivedAt != null
    val isActive: Boolean get() = archivedAt == null && deletedAt == null
}

@Entity(
    tableName = "habit_periods",
    foreignKeys = [ForeignKey(HabitEntity::class, ["id"], ["habit_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("habit_id")],
)
data class HabitPeriodEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "habit_id") val habitId: String,
    @ColumnInfo(name = "start_date") val startDate: LocalDate,
    /** Exclusive; null = still required. */
    @ColumnInfo(name = "end_date") val endDate: LocalDate?,
) {
    fun toModel() = EligibilityPeriod(habitId, startDate, endDate, id)

    companion object {
        fun from(p: EligibilityPeriod) = HabitPeriodEntity(p.id, p.habitId, p.startDate, p.endDateExclusive)
    }
}

@Entity(
    tableName = "completions",
    primaryKeys = ["habit_id", "date"],
    foreignKeys = [ForeignKey(HabitEntity::class, ["id"], ["habit_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("date")],
)
data class CompletionEntity(
    @ColumnInfo(name = "habit_id") val habitId: String,
    val date: LocalDate,
    @ColumnInfo(name = "completed_at") val completedAt: Instant,
    val source: CompletionSource,
    @ColumnInfo(name = "focus_session_id") val focusSessionId: String?,
)

@Entity(
    tableName = "focus_sessions",
    foreignKeys = [ForeignKey(HabitEntity::class, ["id"], ["habit_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("habit_id"), Index("status")],
)
data class FocusSessionEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "habit_id") val habitId: String,
    @ColumnInfo(name = "planned_duration_ms") val plannedDurationMs: Long,
    val status: FocusStatus,
    @ColumnInfo(name = "started_at") val startedAt: Instant,
    @ColumnInfo(name = "ends_at") val endsAt: Instant?,
    @ColumnInfo(name = "remaining_ms") val remainingMs: Long?,
    @ColumnInfo(name = "finished_at") val finishedAt: Instant?,
    @ColumnInfo(name = "completion_date") val completionDate: LocalDate?,
    val acknowledged: Boolean,
) {
    fun toModel() = FocusSession(
        id = id,
        habitId = habitId,
        plannedDuration = plannedDurationMs.milliseconds,
        status = status,
        startedAt = startedAt,
        endsAt = endsAt,
        remainingWhenPaused = remainingMs?.milliseconds,
        finishedAt = finishedAt,
        completionDate = completionDate,
        acknowledged = acknowledged,
    )

    companion object {
        fun from(s: FocusSession) = FocusSessionEntity(
            id = s.id,
            habitId = s.habitId,
            plannedDurationMs = s.plannedDuration.inWholeMilliseconds,
            status = s.status,
            startedAt = s.startedAt,
            endsAt = s.endsAt,
            remainingMs = s.remainingWhenPaused?.inWholeMilliseconds,
            finishedAt = s.finishedAt,
            completionDate = s.completionDate,
            acknowledged = s.acknowledged,
        )
    }
}

/**
 * A local change waiting to be uploaded (schema v3). Rows are written by SQLite triggers
 * ([SyncTriggers]), so every existing write path is captured without changing it. One row per record:
 * a later change to the same record just moves [changedAt] forward.
 */
@Entity(tableName = "sync_outbox", primaryKeys = ["entity", "key"])
data class SyncOutboxEntity(
    /** [SyncEntity.HABIT] (habit row and its eligibility periods) or [SyncEntity.COMPLETION]. */
    val entity: String,
    /** Habit id, or "<habitId>|<yyyy-MM-dd>" for a completion. */
    val key: String,
    /** Device wall-clock time of the change, epoch ms: the last-writer-wins timestamp. */
    @ColumnInfo(name = "changed_at") val changedAt: Long,
)

/** Small key/value table for sync state that must change atomically with the data (schema v3). */
@Entity(tableName = "sync_meta")
data class SyncMetaEntity(
    @PrimaryKey val key: String,
    val value: String,
)

object SyncEntity {
    const val HABIT = "habit"
    const val COMPLETION = "completion"
}
