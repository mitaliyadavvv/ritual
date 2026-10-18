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
