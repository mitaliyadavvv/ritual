package app.ritualhabits.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate

class Converters {
    @TypeConverter fun instantToLong(value: Instant?): Long? = value?.toEpochMilliseconds()
    @TypeConverter fun longToInstant(value: Long?): Instant? = value?.let(Instant::fromEpochMilliseconds)

    // ISO yyyy-MM-dd: sorts correctly and is readable in a DB inspector.
    @TypeConverter fun dateToString(value: LocalDate?): String? = value?.toString()
    @TypeConverter fun stringToDate(value: String?): LocalDate? = value?.let(LocalDate::parse)
}

@Database(
    entities = [HabitEntity::class, HabitPeriodEntity::class, CompletionEntity::class, FocusSessionEntity::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class RitualDatabase : RoomDatabase() {
    abstract fun habitDao(): HabitDao
    abstract fun periodDao(): PeriodDao
    abstract fun completionDao(): CompletionDao
    abstract fun focusSessionDao(): FocusSessionDao

    companion object {
        const val NAME = "ritual.db"

        // Schema changes ship explicit migrations (see docs/ARCHITECTURE.md §11). Never destructive.
        fun build(context: Context): RitualDatabase =
            Room.databaseBuilder(context, RitualDatabase::class.java, NAME).build()
    }
}
