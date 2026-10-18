package app.ritualhabits.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

class Converters {
    @TypeConverter fun instantToLong(value: Instant?): Long? = value?.toEpochMilliseconds()
    @TypeConverter fun longToInstant(value: Long?): Instant? = value?.let(Instant::fromEpochMilliseconds)

    // Reminder time as minutes after local midnight (0..1439): wall-clock, zone-independent.
    @TypeConverter fun timeToMinutes(value: LocalTime?): Int? = value?.let { it.hour * 60 + it.minute }
    @TypeConverter fun minutesToTime(value: Int?): LocalTime? = value?.let { LocalTime(it / 60, it % 60) }

    // ISO yyyy-MM-dd: sorts correctly and is readable in a DB inspector.
    @TypeConverter fun dateToString(value: LocalDate?): String? = value?.toString()
    @TypeConverter fun stringToDate(value: String?): LocalDate? = value?.let(LocalDate::parse)
}

@Database(
    entities = [
        HabitEntity::class, HabitPeriodEntity::class, CompletionEntity::class, FocusSessionEntity::class,
        SyncOutboxEntity::class, SyncMetaEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class RitualDatabase : RoomDatabase() {
    abstract fun habitDao(): HabitDao
    abstract fun periodDao(): PeriodDao
    abstract fun completionDao(): CompletionDao
    abstract fun focusSessionDao(): FocusSessionDao
    abstract fun syncDao(): SyncDao

    companion object {
        const val NAME = "ritual.db"

        /** v2: daily habit reminders. Additive only; existing habits get reminders off, no time. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE habits ADD COLUMN reminder_enabled INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE habits ADD COLUMN reminder_time INTEGER")
            }
        }

        /** v3: cloud backup bookkeeping. Two new tables; existing tables and rows untouched. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `sync_outbox` (`entity` TEXT NOT NULL, `key` TEXT NOT NULL, " +
                        "`changed_at` INTEGER NOT NULL, PRIMARY KEY(`entity`, `key`))",
                )
                db.execSQL("CREATE TABLE IF NOT EXISTS `sync_meta` (`key` TEXT NOT NULL, `value` TEXT NOT NULL, PRIMARY KEY(`key`))")
            }
        }

        // Schema changes ship explicit migrations (see docs/ARCHITECTURE.md §11). Never destructive.
        fun build(context: Context): RitualDatabase =
            Room.databaseBuilder(context, RitualDatabase::class.java, NAME)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .addCallback(SyncTriggers)
                .build()
    }
}
