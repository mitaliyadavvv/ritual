package app.ritualhabits.data

import android.Manifest
import android.app.AlarmManager
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import app.ritualhabits.core.rules.ReminderSchedule
import app.ritualhabits.core.time.ManualClock
import app.ritualhabits.data.local.RitualDatabase
import app.ritualhabits.data.reminders.AndroidReminderAlarms
import app.ritualhabits.data.reminders.AndroidReminderNotifier
import app.ritualhabits.data.reminders.ReminderAlarms
import app.ritualhabits.data.reminders.ReminderCoordinator
import app.ritualhabits.data.reminders.ReminderNotifier
import app.ritualhabits.data.reminders.ReminderOutcome
import app.ritualhabits.data.repository.CompletionRepository
import app.ritualhabits.data.repository.HabitRepository
import app.ritualhabits.data.repository.HabitWriteResult
import app.ritualhabits.data.repository.ReminderSetting
import app.ritualhabits.data.repository.ToggleResult
import app.ritualhabits.ui.habits.formatReminderTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

class FakeReminderAlarms : ReminderAlarms {
    /** One entry per habit, like the real AlarmManager identity. */
    val pending = linkedMapOf<String, Instant>()
    var scheduleCalls = 0
    override fun schedule(habitId: String, at: Instant) { pending[habitId] = at; scheduleCalls++ }
    override fun cancel(habitId: String) { pending.remove(habitId) }
}

class FakeReminderNotifier : ReminderNotifier {
    val shown = mutableListOf<Pair<String, String>>()
    val cleared = mutableListOf<String>()
    override fun show(habitId: String, habitName: String) { shown += habitId to habitName }
    override fun clear(habitId: String) { cleared += habitId }
    override val canNotify: Boolean = true
}

@RunWith(RobolectricTestRunner::class)
class HabitRemindersTest {
    private val zone = TimeZone.of("Asia/Kolkata")
    private val clock = ManualClock(LocalDateTime.parse("2026-10-18T07:00").toInstant(zone), zone)
    private val alarms = FakeReminderAlarms()
    private val notifier = FakeReminderNotifier()
    private lateinit var db: RitualDatabase
    private lateinit var habits: HabitRepository
    private lateinit var completions: CompletionRepository
    private lateinit var reminders: ReminderCoordinator

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), RitualDatabase::class.java).build()
        habits = HabitRepository(db, clock)
        completions = CompletionRepository(db, clock)
        reminders = ReminderCoordinator(habits, alarms, notifier, clock)
    }

    @After fun tearDown() = db.close()

    private fun at(text: String) = LocalDateTime.parse(text).toInstant(zone)
    private suspend fun create(name: String, reminder: ReminderSetting? = null) =
        (habits.create(name, null, reminder) as HabitWriteResult.Success).habitId
    private suspend fun sync() = reminders.sync(habits.observeAll().first())
    private suspend fun habit(id: String) = habits.get(id)!!

    // --- Persistence -------------------------------------------------------------------------

    @Test fun `new habits default to reminders off`() = runBlocking<Unit> {
        val id = create("Read")
        assertFalse(habit(id).reminderEnabled)
        assertNull(habit(id).reminderTime)
        sync()
        assertTrue(alarms.pending.isEmpty())
    }

    @Test fun `saving, editing and disabling a reminder`() = runBlocking<Unit> {
        val id = create("Read", ReminderSetting(true, LocalTime(9, 0)))
        assertEquals(LocalTime(9, 0), habit(id).reminderTime)
        assertTrue(habit(id).reminderEnabled)

        habits.update(id, "Read", null, ReminderSetting(true, LocalTime(20, 30)))
        assertEquals(LocalTime(20, 30), habit(id).reminderTime)

        // Editing without touching reminders (null) leaves them as they are.
        habits.update(id, "Read 20 pages", null)
        assertEquals(LocalTime(20, 30), habit(id).reminderTime)
        assertTrue(habit(id).reminderEnabled)

        habits.update(id, "Read 20 pages", null, ReminderSetting(false, LocalTime(20, 30)))
        assertFalse(habit(id).reminderEnabled)
        assertEquals(LocalTime(20, 30), habit(id).reminderTime) // remembered for next time
    }

    // --- Scheduling ---------------------------------------------------------------------------

    @Test fun `schedules the next occurrence and replaces it when the time changes`() = runBlocking<Unit> {
        val id = create("Read", ReminderSetting(true, LocalTime(9, 0)))
        sync()
        assertEquals(mapOf(id to at("2026-10-18T09:00")), alarms.pending)

        habits.update(id, "Read", null, ReminderSetting(true, LocalTime(6, 0))) // already passed today
        sync()
        assertEquals(mapOf(id to at("2026-10-19T06:00")), alarms.pending) // one alarm, replaced

        val calls = alarms.scheduleCalls
        sync(); sync() // nothing changed: no redundant AlarmManager calls
        assertEquals(calls, alarms.scheduleCalls)

        habits.update(id, "Read", null, ReminderSetting(false, LocalTime(6, 0)))
        sync()
        assertTrue(alarms.pending.isEmpty())
        assertTrue(id in notifier.cleared)
    }

    @Test fun `archive, restore, delete and undo delete follow the habit`() = runBlocking<Unit> {
        val id = create("Read", ReminderSetting(true, LocalTime(9, 0)))
        sync()
        habits.archive(id); sync()
        assertTrue(alarms.pending.isEmpty())
        habits.update(id, "Read", null) ; sync() // editing an archived habit doesn't revive it
        assertTrue(alarms.pending.isEmpty())
        habits.restore(id); sync()
        assertEquals(setOf(id), alarms.pending.keys)
        habits.delete(id); sync()
        assertTrue(alarms.pending.isEmpty())
        habits.undoDelete(id); sync()
        assertEquals(setOf(id), alarms.pending.keys)
        // Habits with reminders off never get one, whatever their state.
        val quiet = create("Walk")
        habits.archive(quiet); habits.restore(quiet); sync()
        assertFalse(quiet in alarms.pending)
    }

    @Test fun `no active habits means no reminders`() = runBlocking<Unit> {
        val a = create("Read", ReminderSetting(true, LocalTime(9, 0)))
        val b = create("Walk", ReminderSetting(true, LocalTime(18, 0)))
        sync()
        habits.archive(a); habits.delete(b); sync()
        assertTrue(alarms.pending.isEmpty())
    }

    @Test fun `follows the habits table automatically`() = runBlocking<Unit> {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            reminders.start(scope)
            val id = create("Read", ReminderSetting(true, LocalTime(9, 0)))
            withTimeout(5_000) { while (id !in alarms.pending) delay(10) }
            habits.archive(id)
            withTimeout(5_000) { while (id in alarms.pending) delay(10) }
        } finally {
            scope.cancel()
        }
    }

    // --- Delivery -------------------------------------------------------------------------------

    @Test fun `delivery shows the nudge, then arms tomorrow`() = runBlocking<Unit> {
        val id = create("Read 20 pages", ReminderSetting(true, LocalTime(9, 0)))
        sync()
        clock.instant = at("2026-10-18T09:00")
        assertEquals(ReminderOutcome.SHOWN, reminders.onReminder(id, at("2026-10-18T09:00")))
        assertEquals(listOf(id to "Read 20 pages"), notifier.shown)
        assertEquals(at("2026-10-19T09:00"), alarms.pending[id])
        // A repeated delivery of the same alarm can't notify twice.
        assertEquals(ReminderOutcome.DUPLICATE, reminders.onReminder(id, at("2026-10-18T09:00")))
        assertEquals(1, notifier.shown.size)
        // A reminder never completes the habit.
        assertEquals(0, db.completionDao().count())
    }

    @Test fun `a habit already done today is not reminded`() = runBlocking<Unit> {
        val id = create("Read", ReminderSetting(true, LocalTime(9, 0)))
        assertEquals(ToggleResult.COMPLETED, completions.toggle(id, clock.today()))
        clock.instant = at("2026-10-18T09:00")
        assertEquals(ReminderOutcome.ALREADY_DONE, reminders.onReminder(id, at("2026-10-18T09:00")))
        assertTrue(notifier.shown.isEmpty())
        assertEquals(at("2026-10-19T09:00"), alarms.pending[id]) // tomorrow still armed
    }

    @Test fun `stale, inactive and not-due deliveries are dropped`() = runBlocking<Unit> {
        val id = create("Read", ReminderSetting(true, LocalTime(9, 0)))
        // Delivered the next morning (device was off): no backlog notification.
        clock.instant = at("2026-10-19T07:30")
        assertEquals(ReminderOutcome.STALE, reminders.onReminder(id, at("2026-10-18T09:00")))
        assertEquals(at("2026-10-19T09:00"), alarms.pending[id])

        habits.archive(id)
        clock.instant = at("2026-10-19T09:00")
        assertEquals(ReminderOutcome.INACTIVE, reminders.onReminder(id, at("2026-10-19T09:00")))
        assertFalse(id in alarms.pending)

        assertEquals(ReminderOutcome.INACTIVE, reminders.onReminder("no-such-habit", at("2026-10-19T09:00")))

        // Due-ness comes from eligibility: remove today's period while the reminder stays on.
        val walk = create("Walk", ReminderSetting(true, LocalTime(10, 0)))
        db.periodDao().delete(db.periodDao().forHabit(walk).single())
        clock.instant = at("2026-10-19T10:00")
        assertEquals(ReminderOutcome.NOT_DUE, reminders.onReminder(walk, at("2026-10-19T10:00")))
        assertTrue(notifier.shown.isEmpty())
    }

    @Test fun `midnight, time-zone change and reboot recompute from local time`() = runBlocking<Unit> {
        val id = create("Read", ReminderSetting(true, LocalTime(0, 15)))
        clock.instant = at("2026-10-18T23:59")
        reminders.resyncAll()
        assertEquals(at("2026-10-19T00:15"), alarms.pending[id])

        // Fly to Los Angeles: 00:15 local there, not 00:15 Kolkata.
        val la = TimeZone.of("America/Los_Angeles")
        clock.timeZone = la
        reminders.resyncAll() // what the TIMEZONE_CHANGED / BOOT_COMPLETED receiver does
        assertEquals(LocalDateTime.parse("2026-10-19T00:15").toInstant(la), alarms.pending[id])

        // Reboot clears all alarms; resync restores them.
        alarms.pending.clear()
        reminders.resyncAll()
        assertEquals(setOf(id), alarms.pending.keys)
    }

    @Test fun `reminder time shows AM and PM or 24-hour`() {
        assertEquals("9:00 AM", formatReminderTime(LocalTime(9, 0), is24Hour = false, Locale.US))
        assertEquals("8:30 PM", formatReminderTime(LocalTime(20, 30), is24Hour = false, Locale.US))
        assertEquals("20:30", formatReminderTime(LocalTime(20, 30), is24Hour = true, Locale.US))
        assertEquals(LocalTime(9, 0), ReminderSchedule.DEFAULT_TIME)
    }
}

/** Existing installs: schema 1 -> 2 must keep every habit and record, with reminders off. */
@RunWith(RobolectricTestRunner::class)
class ReminderMigrationTest {
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), RitualDatabase::class.java)

    @Test fun `migrating from version 1 keeps all data and turns reminders off`() = runBlocking<Unit> {
        val name = "migration-test.db"
        helper.createDatabase(name, 1).apply {
            execSQL("INSERT INTO habits (id, name, description, sort_order, created_at, archived_at, deleted_at) VALUES ('h1', 'Read', 'Before bed', 0, 1760000000000, NULL, NULL)")
            execSQL("INSERT INTO habits (id, name, description, sort_order, created_at, archived_at, deleted_at) VALUES ('h2', 'Walk', NULL, 1, 1760000000000, 1760100000000, NULL)")
            execSQL("INSERT INTO habit_periods (habit_id, start_date, end_date) VALUES ('h1', '2026-10-01', NULL)")
            execSQL("INSERT INTO completions (habit_id, date, completed_at, source, focus_session_id) VALUES ('h1', '2026-10-02', 1760000000000, 'MANUAL', NULL)")
            close()
        }
        helper.runMigrationsAndValidate(name, 2, true, RitualDatabase.MIGRATION_1_2).close()

        val db = Room.databaseBuilder(ApplicationProvider.getApplicationContext<Context>(), RitualDatabase::class.java, name)
            .addMigrations(RitualDatabase.MIGRATION_1_2, RitualDatabase.MIGRATION_2_3)
            .build()
        try {
            val all = db.habitDao().observeAll().first()
            assertEquals(listOf("Read", "Walk"), all.map { it.name })
            assertTrue(all.none { it.reminderEnabled })
            assertTrue(all.all { it.reminderTime == null })
            assertEquals("Before bed", all.first().description)
            assertEquals(1, db.completionDao().count())
            assertEquals(1, db.periodDao().forHabit("h1").size)
        } finally {
            db.close()
        }
    }
}

/** The real AlarmManager / NotificationManager integration, through Robolectric's shadows. */
@RunWith(RobolectricTestRunner::class)
class ReminderAndroidTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private val t = Instant.parse("2026-10-18T03:30:00Z")

    @Test fun `one alarm per habit, replaced not duplicated, and cancelled`() {
        val alarms = AndroidReminderAlarms(app)
        val shadow = shadowOf(app.getSystemService(Context.ALARM_SERVICE) as AlarmManager)
        alarms.schedule("a", t)
        alarms.schedule("b", t + 1.hours)
        alarms.schedule("a", t + 30.minutes) // replaces a's alarm
        assertEquals(2, shadow.scheduledAlarms.size)
        assertEquals(
            setOf(t.toEpochMilliseconds() + 30 * 60_000, (t + 1.hours).toEpochMilliseconds()),
            shadow.scheduledAlarms.map { it.triggerAtTime }.toSet(),
        )
        alarms.cancel("a")
        assertEquals(1, shadow.scheduledAlarms.size)
        alarms.cancel("never-scheduled") // harmless
        assertEquals(1, shadow.scheduledAlarms.size)
    }

    @Test fun `channel, nudge copy, one notification per habit`() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val notifier = AndroidReminderNotifier(app).apply { createChannel() }
        val manager = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        assertEquals("Habit reminders", manager.getNotificationChannel(AndroidReminderNotifier.CHANNEL_ID).name)

        notifier.show("h1", "Read 20 pages")
        notifier.show("h1", "Read 20 pages") // same habit: replaces
        notifier.show("h2", "Walk")
        val posted = shadowOf(manager).allNotifications
        assertEquals(2, posted.size)
        val read = posted.first { it.extras.getString("android.text") == "Time for Read 20 pages" }
        assertEquals("A little nudge from Ritual", read.extras.getString("android.title"))
        assertTrue(read.contentIntent != null)

        notifier.clear("h1")
        assertEquals(1, shadowOf(manager).allNotifications.size)
    }

    @Test fun `permission denied means no notification`() {
        shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val notifier = AndroidReminderNotifier(app).apply { createChannel() }
        notifier.show("h1", "Read")
        assertFalse(notifier.canNotify)
        val manager = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        assertTrue(shadowOf(manager).allNotifications.isEmpty())
    }
}
