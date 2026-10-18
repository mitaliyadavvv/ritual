package app.ritualhabits.data

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import app.ritualhabits.core.model.ThemeMode
import app.ritualhabits.core.rules.StreakCalculator
import app.ritualhabits.core.time.ManualClock
import app.ritualhabits.data.local.RitualDatabase
import app.ritualhabits.data.local.SyncTriggers
import app.ritualhabits.data.reminders.ReminderCoordinator
import app.ritualhabits.data.repository.CompletionRepository
import app.ritualhabits.data.repository.HabitRepository
import app.ritualhabits.data.repository.HabitWriteResult
import app.ritualhabits.data.repository.PreferencesRepository
import app.ritualhabits.data.repository.ReminderSetting
import app.ritualhabits.data.sync.Attach
import app.ritualhabits.data.sync.CloudCompletion
import app.ritualhabits.data.sync.CloudHabit
import app.ritualhabits.data.sync.CloudPage
import app.ritualhabits.data.sync.CloudPeriod
import app.ritualhabits.data.sync.CloudPreferences
import app.ritualhabits.data.sync.CloudStore
import app.ritualhabits.data.sync.CloudUnavailableException
import app.ritualhabits.data.sync.SyncEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * In-memory stand-in for Firestore. Data is partitioned by user id exactly like the real layout
 * (users/{uid}/...), and every write gets a strictly increasing server time.
 */
class FakeCloud : CloudStore {
    class Account {
        val habits = linkedMapOf<String, CloudHabit>()
        val completions = linkedMapOf<String, CloudCompletion>()
        var preferences: CloudPreferences? = null
    }

    val accounts = mutableMapOf<String, Account>()
    var online = true
    /** Simulates a dropped connection after the upload reached the server but before the reply. */
    var failAfterNextWrite = false
    var writes = 0
    private var serverClock = 1_000L

    fun account(uid: String) = accounts.getOrPut(uid) { Account() }

    private fun checkOnline() {
        if (!online) throw CloudUnavailableException()
    }

    override suspend fun habitsSince(uid: String, since: Long?): CloudPage<CloudHabit> {
        checkOnline()
        val items = account(uid).habits.values.filter { since == null || it.serverTime >= since }
        return CloudPage(items, items.maxOfOrNull { it.serverTime })
    }

    override suspend fun completionsSince(uid: String, since: Long?): CloudPage<CloudCompletion> {
        checkOnline()
        val items = account(uid).completions.values.filter { since == null || it.serverTime >= since }
        return CloudPage(items, items.maxOfOrNull { it.serverTime })
    }

    override suspend fun write(uid: String, habits: List<CloudHabit>, completions: List<CloudCompletion>) {
        checkOnline()
        writes++
        val account = account(uid)
        habits.forEach { account.habits[it.id] = it.copy(serverTime = ++serverClock) }
        completions.forEach { account.completions["${it.habitId}_${it.date}"] = it.copy(serverTime = ++serverClock) }
        if (failAfterNextWrite) {
            failAfterNextWrite = false
            throw CloudUnavailableException()
        }
    }

    override suspend fun preferences(uid: String): CloudPreferences? {
        checkOnline()
        return account(uid).preferences
    }

    override suspend fun writePreferences(uid: String, preferences: CloudPreferences) {
        checkOnline()
        account(uid).preferences = preferences
    }
}

/** One installation of the app: its own database, settings file and clock. */
class Device(cloud: CloudStore, folder: java.io.File, zone: TimeZone = TimeZone.of("Asia/Kolkata")) {
    val clock = ManualClock(LocalDateTime.parse("2026-10-18T08:00").toInstant(zone), zone)
    val db: RitualDatabase = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), RitualDatabase::class.java)
        .addCallback(SyncTriggers)
        .build()
    private val prefsScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    val preferences = PreferencesRepository(PreferenceDataStoreFactory.create(scope = prefsScope) { folder.resolve("settings.preferences_pb") })
    val habits = HabitRepository(db, clock)
    val completions = CompletionRepository(db, clock)
    val engine = SyncEngine(db, cloud, preferences)

    suspend fun create(name: String, reminder: ReminderSetting? = null) =
        (habits.create(name, null, reminder) as HabitWriteResult.Success).habitId

    suspend fun names() = habits.observeAll().first().filter { !it.isDeleted }.map { it.name }.sorted()
    suspend fun doneKeys() = db.completionDao().observeAll().first().map { it.habitId to it.date }.toSet()
    suspend fun summary() = StreakCalculator.summarize(habits.observeHistory().first(), clock.today())
    suspend fun outbox() = db.syncDao().outbox()

    fun close() {
        db.close()
        prefsScope.cancel()
    }
}

@RunWith(RobolectricTestRunner::class)
class CloudSyncTest {
    @get:Rule val tmp = TemporaryFolder()
    private val cloud = FakeCloud()
    private val devices = mutableListOf<Device>()

    private fun device(zone: TimeZone = TimeZone.of("Asia/Kolkata")) = Device(cloud, tmp.newFolder(), zone).also(devices::add)

    @After fun tearDown() = devices.forEach(Device::close)

    /** Next local change gets a strictly later timestamp (SQLite clock has millisecond resolution). */
    private fun tick() = Thread.sleep(5)

    @Test fun `nothing is queued for people who never sign in`() = runBlocking<Unit> {
        val phone = device()
        val id = phone.create("Read")
        phone.completions.toggle(id, phone.clock.today())
        assertTrue(phone.outbox().isEmpty())
        assertNull(phone.engine.ownerUid())
    }

    @Test fun `first sign-in keeps local habits and history and uploads them`() = runBlocking<Unit> {
        val phone = device()
        val read = phone.create("Read", ReminderSetting(true, LocalTime(21, 30)))
        phone.create("Walk")
        phone.completions.toggle(read, phone.clock.today())
        val before = phone.summary()

        assertEquals(Attach.Claimed, phone.engine.attach("alice", "alice@example.com"))
        phone.engine.sync("alice")

        // Local data untouched, nothing left to upload.
        assertEquals(listOf("Read", "Walk"), phone.names())
        assertEquals(before, phone.summary())
        assertTrue(phone.outbox().isEmpty())
        // Cloud has it all, under Alice only.
        val alice = cloud.account("alice")
        assertEquals(setOf("Read", "Walk"), alice.habits.values.map { it.name }.toSet())
        assertEquals(1, alice.habits.values.first { it.name == "Read" }.periods.size)
        assertEquals(21 * 60 + 30, alice.habits[read]!!.reminderMinutes)
        assertEquals(listOf(true), alice.completions.values.map { it.done })
        assertEquals(setOf("alice"), cloud.accounts.keys)
    }

    @Test fun `an empty cloud account never wipes local progress`() = runBlocking<Unit> {
        val phone = device()
        val id = phone.create("Read")
        phone.completions.toggle(id, phone.clock.today())
        cloud.account("alice") // exists, empty
        phone.engine.attach("alice", null)
        phone.engine.sync("alice")
        phone.engine.sync("alice")
        assertEquals(listOf("Read"), phone.names())
        assertEquals(1, phone.summary().currentStreak)
    }

    @Test fun `fresh install restores habits, history, streaks and reminders, even in another time zone`() = runBlocking<Unit> {
        val old = device()
        val read = old.create("Read", ReminderSetting(true, LocalTime(7, 15)))
        val walk = old.create("Walk")
        // Two days of history: both done on the 18th, Read done on the 19th, then Walk archived.
        old.completions.toggle(read, old.clock.today())
        old.completions.toggle(walk, old.clock.today())
        old.clock.instant = LocalDateTime.parse("2026-10-19T09:00").toInstant(old.clock.timeZone)
        old.completions.toggle(read, old.clock.today())
        old.habits.archive(walk)
        old.engine.attach("alice", "alice@example.com")
        old.engine.sync("alice")

        // New phone, different time zone, same moment.
        val fresh = device(TimeZone.of("America/Los_Angeles"))
        fresh.clock.instant = LocalDateTime.parse("2026-10-19T09:00").toInstant(fresh.clock.timeZone)
        assertEquals(Attach.Claimed, fresh.engine.attach("alice", "alice@example.com"))
        fresh.engine.sync("alice")

        assertEquals(old.habits.observeAll().first(), fresh.habits.observeAll().first())
        assertEquals(
            old.db.periodDao().forHabit(walk).map { it.startDate to it.endDate },
            fresh.db.periodDao().forHabit(walk).map { it.startDate to it.endDate },
        )
        val freshHistory = fresh.habits.observeHistory().first()
        assertEquals(old.doneKeys(), fresh.doneKeys())
        assertEquals(3, fresh.doneKeys().size)
        // Habit days are calendar dates: the same history gives the same streaks on both phones.
        assertEquals(old.summary(), StreakCalculator.summarize(freshHistory, old.clock.today()))
        assertEquals(LocalTime(7, 15), fresh.habits.get(read)!!.reminderTime)
        assertTrue(fresh.habits.get(read)!!.reminderEnabled)
        assertTrue(fresh.habits.get(walk)!!.isArchived)
        assertTrue(fresh.outbox().isEmpty())
    }

    @Test fun `restored reminders are scheduled again`() = runBlocking<Unit> {
        val old = device()
        val read = old.create("Read", ReminderSetting(true, LocalTime(21, 0)))
        old.engine.attach("alice", null)
        old.engine.sync("alice")

        val fresh = device()
        val alarms = FakeReminderAlarms()
        val reminders = ReminderCoordinator(fresh.habits, alarms, FakeReminderNotifier(), fresh.clock)
        fresh.engine.attach("alice", null)
        fresh.engine.sync("alice")
        reminders.sync(fresh.habits.observeAll().first())
        assertEquals(setOf(read), alarms.pending.keys)
    }

    @Test fun `edits, un-ticks and lifecycle changes flow between phones`() = runBlocking<Unit> {
        val a = device()
        val read = a.create("Read")
        a.completions.toggle(read, a.clock.today())
        a.engine.attach("alice", null)
        a.engine.sync("alice")
        val b = device()
        b.engine.attach("alice", null)
        b.engine.sync("alice")

        tick()
        b.completions.toggle(read, b.clock.today()) // un-tick: a tombstone, not a lost update
        b.habits.update(read, "Read 20 pages", null)
        val walk = b.create("Walk")
        b.habits.delete(walk)
        b.engine.sync("alice")
        a.engine.sync("alice")

        assertEquals(listOf("Read 20 pages"), a.names())
        assertTrue(a.doneKeys().isEmpty())
        assertTrue(a.habits.get(walk)!!.isDeleted)
        assertEquals(b.db.periodDao().forHabit(walk).map { it.startDate to it.endDate }, a.db.periodDao().forHabit(walk).map { it.startDate to it.endDate })
        assertTrue(a.outbox().isEmpty())
        // Applying downloaded changes didn't queue them to be uploaded again.
        a.engine.sync("alice")
        assertTrue(a.outbox().isEmpty())
    }

    @Test fun `conflicting edits resolve to the latest change, whichever phone syncs first`() = runBlocking<Unit> {
        for (laterSyncsFirst in listOf(false, true)) {
            val a = device()
            val id = a.create("Habit $laterSyncsFirst")
            a.engine.attach("u$laterSyncsFirst", null)
            a.engine.sync("u$laterSyncsFirst")
            val b = device()
            b.engine.attach("u$laterSyncsFirst", null)
            b.engine.sync("u$laterSyncsFirst")

            tick()
            a.habits.update(id, "Earlier", null)
            tick()
            b.habits.update(id, "Later", null)
            if (laterSyncsFirst) {
                b.engine.sync("u$laterSyncsFirst")
                a.engine.sync("u$laterSyncsFirst")
            } else {
                a.engine.sync("u$laterSyncsFirst")
                b.engine.sync("u$laterSyncsFirst")
                a.engine.sync("u$laterSyncsFirst")
            }
            assertEquals(listOf("Later"), a.names())
            assertEquals(listOf("Later"), b.names())
            assertEquals("Later", cloud.account("u$laterSyncsFirst").habits[id]!!.name)
        }
    }

    @Test fun `offline changes stay queued and upload when the connection returns`() = runBlocking<Unit> {
        val phone = device()
        phone.engine.attach("alice", null)
        cloud.online = false
        val id = phone.create("Read")
        phone.completions.toggle(id, phone.clock.today())
        assertFailsWith<CloudUnavailableException> { phone.engine.sync("alice") }
        assertEquals(2, phone.outbox().size)
        assertEquals(listOf("Read"), phone.names())

        cloud.online = true
        phone.engine.sync("alice")
        assertTrue(phone.outbox().isEmpty())
        assertEquals(1, cloud.account("alice").habits.size)
        assertEquals(1, cloud.account("alice").completions.size)
    }

    @Test fun `a retried upload never creates duplicates`() = runBlocking<Unit> {
        val phone = device()
        phone.engine.attach("alice", null)
        val id = phone.create("Read")
        phone.completions.toggle(id, phone.clock.today())
        cloud.failAfterNextWrite = true
        assertFailsWith<CloudUnavailableException> { phone.engine.sync("alice") }
        assertEquals(2, phone.outbox().size) // reply lost: still queued
        phone.engine.sync("alice")
        phone.engine.sync("alice")
        assertEquals(1, cloud.account("alice").habits.size)
        assertEquals(1, cloud.account("alice").completions.size)
        assertEquals(1, phone.habits.observeAll().first().size)
        assertEquals(1, phone.doneKeys().size)
    }

    @Test fun `changes made while signed out sync when the same account signs back in`() = runBlocking<Unit> {
        val phone = device()
        phone.create("Read")
        phone.engine.attach("alice", "alice@example.com")
        phone.engine.sync("alice")
        // Signed out (owner kept): keep using the app.
        phone.create("Walk")
        assertEquals(1, phone.outbox().size)
        assertEquals(Attach.Resumed, phone.engine.attach("alice", "alice@example.com"))
        phone.engine.sync("alice")
        assertEquals(setOf("Read", "Walk"), cloud.account("alice").habits.values.map { it.name }.toSet())
    }

    @Test fun `another account never receives or merges this phone's data`() = runBlocking<Unit> {
        val bobsPhone = device()
        bobsPhone.create("Bob's habit")
        bobsPhone.engine.attach("bob", "bob@example.com")
        bobsPhone.engine.sync("bob")

        val phone = device()
        phone.create("Alice's habit")
        phone.engine.attach("alice", "alice@example.com")
        phone.engine.sync("alice")
        phone.create("Unsynced Alice habit")

        // Bob signs in on Alice's phone: nothing moves until a choice is made.
        val attach = phone.engine.attach("bob", "bob@example.com")
        assertEquals(Attach.OtherAccount("alice@example.com", unsyncedChanges = 1), attach)
        assertFailsWith<IllegalStateException> { phone.engine.sync("bob") }
        assertEquals(listOf("Alice's habit", "Unsynced Alice habit"), phone.names())
        assertEquals(listOf("Bob's habit"), cloud.account("bob").habits.values.map { it.name })

        // Switching: Alice's data leaves the phone, Bob's arrives; neither account is mixed.
        phone.engine.replaceLocalDataWith("bob", "bob@example.com")
        assertTrue(phone.names().isEmpty())
        phone.engine.sync("bob")
        assertEquals(listOf("Bob's habit"), phone.names())
        assertEquals(listOf("Bob's habit"), cloud.account("bob").habits.values.map { it.name })
        assertEquals(listOf("Alice's habit"), cloud.account("alice").habits.values.map { it.name })

        // And back to Alice later: her synced data returns, Bob's doesn't follow.
        assertIs<Attach.OtherAccount>(phone.engine.attach("alice", "alice@example.com"))
        phone.engine.replaceLocalDataWith("alice", "alice@example.com")
        phone.engine.sync("alice")
        assertEquals(listOf("Alice's habit"), phone.names())
        assertEquals(listOf("Bob's habit"), cloud.account("bob").habits.values.map { it.name })
    }

    @Test fun `appearance settings follow the account, latest change wins`() = runBlocking<Unit> {
        val a = device()
        a.preferences.setThemeMode(ThemeMode.DARK)
        a.preferences.setHapticsEnabled(false)
        a.engine.attach("alice", null)
        a.engine.sync("alice")

        val b = device()
        b.engine.attach("alice", null)
        b.engine.sync("alice")
        val restored = b.preferences.preferences.first()
        assertEquals(ThemeMode.DARK, restored.themeMode)
        assertFalse(restored.hapticsEnabled)

        tick()
        b.preferences.setThemeMode(ThemeMode.LIGHT)
        b.engine.sync("alice")
        a.engine.sync("alice")
        assertEquals(ThemeMode.LIGHT, a.preferences.preferences.first().themeMode)
    }

    @Test fun `a completion that arrives before its habit is fetched again next time`() = runBlocking<Unit> {
        val phone = device()
        phone.engine.attach("alice", null)
        val habit = CloudHabit("h1", "Read", null, 0, 0, null, null, false, null, listOf(CloudPeriod("2026-10-18", null)), 10)
        cloud.write("alice", emptyList(), listOf(CloudCompletion("h1", "2026-10-18", true, 5, "MANUAL", null, 10)))
        phone.engine.sync("alice")
        assertTrue(phone.doneKeys().isEmpty())
        cloud.write("alice", listOf(habit), emptyList())
        phone.engine.sync("alice")
        assertEquals(1, phone.doneKeys().size)
    }
}

@RunWith(RobolectricTestRunner::class)
class SyncMigrationTest {
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), RitualDatabase::class.java)

    @Test fun `v2 to v3 keeps every row and adds empty sync tables`() {
        helper.createDatabase("sync-migration", 2).apply {
            execSQL(
                "INSERT INTO habits (id, name, description, sort_order, created_at, archived_at, deleted_at, reminder_enabled, reminder_time) " +
                    "VALUES ('h1', 'Read', NULL, 0, 1000, NULL, NULL, 1, 570)",
            )
            execSQL("INSERT INTO habit_periods (habit_id, start_date, end_date) VALUES ('h1', '2026-10-01', NULL)")
            execSQL("INSERT INTO completions (habit_id, date, completed_at, source, focus_session_id) VALUES ('h1', '2026-10-02', 2000, 'MANUAL', NULL)")
            close()
        }
        val db = helper.runMigrationsAndValidate("sync-migration", 3, true, RitualDatabase.MIGRATION_1_2, RitualDatabase.MIGRATION_2_3)
        db.query("SELECT name, reminder_enabled, reminder_time FROM habits").use {
            assertTrue(it.moveToFirst())
            assertEquals("Read", it.getString(0))
            assertEquals(1, it.getInt(1))
            assertEquals(570, it.getInt(2))
        }
        db.query("SELECT COUNT(*) FROM completions").use { it.moveToFirst(); assertEquals(1, it.getInt(0)) }
        db.query("SELECT COUNT(*) FROM habit_periods").use { it.moveToFirst(); assertEquals(1, it.getInt(0)) }
        db.query("SELECT COUNT(*) FROM sync_outbox").use { it.moveToFirst(); assertEquals(0, it.getInt(0)) }
        db.query("SELECT COUNT(*) FROM sync_meta").use { it.moveToFirst(); assertEquals(0, it.getInt(0)) }
    }
}
