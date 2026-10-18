package app.ritualhabits.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate

@Dao
interface HabitDao {
    @Query("SELECT * FROM habits WHERE archived_at IS NULL AND deleted_at IS NULL ORDER BY sort_order, created_at")
    fun observeActive(): Flow<List<HabitEntity>>

    @Query("SELECT * FROM habits WHERE archived_at IS NOT NULL AND deleted_at IS NULL ORDER BY archived_at DESC")
    fun observeArchived(): Flow<List<HabitEntity>>

    /** Includes deleted habits, so analytics can still label historical rows. */
    @Query("SELECT * FROM habits ORDER BY sort_order, created_at")
    fun observeAll(): Flow<List<HabitEntity>>

    @Query("SELECT * FROM habits WHERE archived_at IS NULL AND deleted_at IS NULL ORDER BY sort_order, created_at")
    suspend fun activeOrdered(): List<HabitEntity>

    @Query("SELECT * FROM habits WHERE id = :id")
    suspend fun get(id: String): HabitEntity?

    @Query("SELECT name FROM habits WHERE deleted_at IS NULL AND id != :excludingId")
    suspend fun liveNames(excludingId: String = ""): List<String>

    @Query("SELECT COALESCE(MAX(sort_order), -1) FROM habits")
    suspend fun maxSortOrder(): Int

    @Insert
    suspend fun insert(habit: HabitEntity)

    @Update
    suspend fun update(habit: HabitEntity)

    @Query("UPDATE habits SET sort_order = :sortOrder WHERE id = :id")
    suspend fun setSortOrder(id: String, sortOrder: Int)
}

@Dao
interface PeriodDao {
    @Query("SELECT * FROM habit_periods")
    fun observeAll(): Flow<List<HabitPeriodEntity>>

    @Query("SELECT * FROM habit_periods WHERE habit_id = :habitId ORDER BY start_date")
    suspend fun forHabit(habitId: String): List<HabitPeriodEntity>

    @Insert
    suspend fun insert(period: HabitPeriodEntity): Long

    @Update
    suspend fun update(period: HabitPeriodEntity)

    @Delete
    suspend fun delete(period: HabitPeriodEntity)
}

@Dao
interface CompletionDao {
    @Query("SELECT * FROM completions")
    fun observeAll(): Flow<List<CompletionEntity>>

    @Query("SELECT * FROM completions WHERE habit_id = :habitId AND date = :date")
    suspend fun get(habitId: String, date: LocalDate): CompletionEntity?

    /** Returns -1 when a row for (habit, date) already exists. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(completion: CompletionEntity): Long

    @Query("DELETE FROM completions WHERE habit_id = :habitId AND date = :date")
    suspend fun delete(habitId: String, date: LocalDate): Int

    @Query("SELECT COUNT(*) FROM completions")
    suspend fun count(): Int
}

@Dao
interface FocusSessionDao {
    /** The session the Focus screen should show: active, or completed and not yet dismissed. */
    @Query(
        "SELECT * FROM focus_sessions WHERE status IN ('RUNNING', 'PAUSED') " +
            "OR (status = 'COMPLETED' AND acknowledged = 0) ORDER BY started_at DESC LIMIT 1",
    )
    fun observeCurrent(): Flow<FocusSessionEntity?>

    @Query("SELECT * FROM focus_sessions WHERE status IN ('RUNNING', 'PAUSED')")
    suspend fun active(): List<FocusSessionEntity>

    @Query("SELECT * FROM focus_sessions WHERE status = 'RUNNING' AND ends_at <= :now")
    suspend fun due(now: Instant): List<FocusSessionEntity>

    @Query("SELECT * FROM focus_sessions WHERE id = :id")
    suspend fun get(id: String): FocusSessionEntity?

    @Insert
    suspend fun insert(session: FocusSessionEntity)

    @Update
    suspend fun update(session: FocusSessionEntity)

    /** Compare-and-set: only one caller can move a session from RUNNING to COMPLETED. */
    @Query(
        "UPDATE focus_sessions SET status = 'COMPLETED', finished_at = :finishedAt, completion_date = :date " +
            "WHERE id = :id AND status = 'RUNNING'",
    )
    suspend fun markCompleted(id: String, finishedAt: Instant, date: LocalDate): Int

    @Query("UPDATE focus_sessions SET acknowledged = 1 WHERE id = :id")
    suspend fun acknowledge(id: String)
}
