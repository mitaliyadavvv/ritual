package app.ritualhabits.ui

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.ritualhabits.core.time.ManualClock
import app.ritualhabits.data.local.RitualDatabase
import app.ritualhabits.data.repository.CompletionRepository
import app.ritualhabits.data.repository.HabitRepository
import app.ritualhabits.data.repository.HabitWriteResult
import app.ritualhabits.data.repository.ReminderSetting
import app.ritualhabits.ui.manage.ManageEvent
import app.ritualhabits.ui.manage.ManageUiState
import app.ritualhabits.ui.manage.ManageViewModel
import app.ritualhabits.ui.today.TodayEvent
import app.ritualhabits.ui.today.TodayUiState
import app.ritualhabits.ui.today.TodayViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** The reminder controls in the Add/Edit sheet, through the existing Today and Manage ViewModels. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ReminderEditorTest {
    private val zone = TimeZone.of("Asia/Kolkata")
    private val clock = ManualClock(LocalDateTime.parse("2026-10-18T07:00").toInstant(zone), zone)
    private lateinit var db: RitualDatabase
    private lateinit var habits: HabitRepository

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), RitualDatabase::class.java).build()
        habits = HabitRepository(db, clock)
    }

    @After fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    @Test fun `adding from Today starts with reminders off at 9 AM, and saves what was chosen`() = runBlocking<Unit> {
        val vm = TodayViewModel(habits, CompletionRepository(db, clock), MutableStateFlow(clock.today()))
        withTimeout(5_000) { vm.uiState.first { it is TodayUiState.Ready } }
        vm.openAddHabit()
        assertFalse(vm.addHabit.value!!.reminderEnabled)
        assertEquals(LocalTime(9, 0), vm.addHabit.value!!.reminderTime)

        vm.onNameChange("Read")
        vm.onReminderEnabledChange(true)
        vm.onReminderTimeChange(LocalTime(21, 15))
        vm.saveHabit()
        assertIs<TodayEvent.Haptic>(withTimeout(5_000) { vm.events.first() })
        val saved = habits.observeActive().first().single()
        assertTrue(saved.reminderEnabled)
        assertEquals(LocalTime(21, 15), saved.reminderTime)
    }

    @Test fun `edit shows the saved reminder, cancel changes nothing, save replaces it`() = runBlocking<Unit> {
        val id = (habits.create("Read", null, ReminderSetting(true, LocalTime(7, 30))) as HabitWriteResult.Success).habitId
        val vm = ManageViewModel(habits, clock)
        val read = (withTimeout(5_000) { vm.uiState.first { it is ManageUiState.Ready && it.active.isNotEmpty() } } as ManageUiState.Ready).active.single()

        vm.openEdit(read)
        assertTrue(vm.editor.value!!.form.reminderEnabled)
        assertEquals(LocalTime(7, 30), vm.editor.value!!.form.reminderTime)

        vm.onReminderTimeChange(LocalTime(22, 0))
        vm.onReminderEnabledChange(false)
        vm.dismissEditor() // cancel
        assertTrue(habits.get(id)!!.reminderEnabled)
        assertEquals(LocalTime(7, 30), habits.get(id)!!.reminderTime)

        vm.openEdit(read)
        vm.onReminderTimeChange(LocalTime(22, 0))
        vm.saveEditor()
        assertIs<ManageEvent.Haptic>(withTimeout(5_000) { vm.events.first() })
        assertEquals(LocalTime(22, 0), habits.get(id)!!.reminderTime)
        assertTrue(habits.get(id)!!.reminderEnabled)

        // Edit a habit that never had a reminder: off, with the 9 AM default ready.
        val walk = (habits.create("Walk", null) as HabitWriteResult.Success).habitId
        val walkRow = (withTimeout(5_000) { vm.uiState.first { it is ManageUiState.Ready && it.active.size == 2 } } as ManageUiState.Ready)
            .active.first { it.id == walk }
        vm.openEdit(walkRow)
        assertFalse(vm.editor.value!!.form.reminderEnabled)
        assertEquals(LocalTime(9, 0), vm.editor.value!!.form.reminderTime)
    }
}
