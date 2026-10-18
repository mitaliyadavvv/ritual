package app.ritualhabits.data.local

import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * SQLite triggers that record every local change to habits, eligibility periods and completions in
 * `sync_outbox`, so cloud backup sees every write path without the repositories knowing about it.
 *
 * They only record while this phone's data belongs to an account (`sync_meta.owner_uid` exists) and
 * not while a remote change is being applied (`sync_meta.applying`), so nothing is queued for people
 * who never sign in, and downloaded data isn't echoed back. Installed on every open (idempotent).
 */
object SyncTriggers : RoomDatabase.Callback() {
    const val META_OWNER_UID = "owner_uid"
    const val META_APPLYING = "applying"

    private const val NOW_MS = "CAST((julianday('now') - 2440587.5) * 86400000 AS INTEGER)"
    private const val WHEN = "WHEN EXISTS (SELECT 1 FROM sync_meta WHERE `key` = '$META_OWNER_UID') " +
        "AND NOT EXISTS (SELECT 1 FROM sync_meta WHERE `key` = '$META_APPLYING')"

    // Delete + insert rather than INSERT OR REPLACE: inside a trigger, the outer statement's conflict
    // policy (Room uses OR ABORT) overrides the trigger's own.
    private fun trigger(name: String, event: String, table: String, entity: String, key: String) = name to
        "CREATE TRIGGER sync_$name AFTER $event ON $table $WHEN BEGIN " +
            "DELETE FROM sync_outbox WHERE entity = '$entity' AND `key` = $key; " +
            "INSERT INTO sync_outbox (entity, `key`, changed_at) VALUES ('$entity', $key, $NOW_MS); END"

    private val definitions = listOf(
        trigger("habit_insert", "INSERT", "habits", SyncEntity.HABIT, "NEW.id"),
        trigger("habit_update", "UPDATE", "habits", SyncEntity.HABIT, "NEW.id"),
        trigger("period_insert", "INSERT", "habit_periods", SyncEntity.HABIT, "NEW.habit_id"),
        trigger("period_update", "UPDATE", "habit_periods", SyncEntity.HABIT, "NEW.habit_id"),
        trigger("period_delete", "DELETE", "habit_periods", SyncEntity.HABIT, "OLD.habit_id"),
        trigger("completion_insert", "INSERT", "completions", SyncEntity.COMPLETION, "NEW.habit_id || '|' || NEW.date"),
        trigger("completion_update", "UPDATE", "completions", SyncEntity.COMPLETION, "NEW.habit_id || '|' || NEW.date"),
        trigger("completion_delete", "DELETE", "completions", SyncEntity.COMPLETION, "OLD.habit_id || '|' || OLD.date"),
    )

    /** Recreated on every open, so a changed definition always replaces the old one. */
    fun install(db: SupportSQLiteDatabase) {
        definitions.forEach { (name, sql) ->
            db.execSQL("DROP TRIGGER IF EXISTS sync_$name")
            db.execSQL(sql)
        }
    }

    override fun onOpen(db: SupportSQLiteDatabase) = install(db)
}
