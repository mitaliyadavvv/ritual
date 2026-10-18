package app.ritualhabits.ui

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.ritualhabits.core.rules.HabitInputError
import app.ritualhabits.core.time.ManualClock
import app.ritualhabits.data.local.RitualDatabase
import app.ritualhabits.data.repository.CompletionRepository
import app.ritualhabits.data.repository.HabitRepository
import app.ritualhabits.ui.manage.ManageEvent
import app.ritualhabits.ui.manage.ManageTab
import app.ritualhabits.ui.manage.ManageUiState
import app.ritualhabits.ui.manage.ManageViewModel
import app.ritualhabits.ui.manage.UndoAction
import app.ritualhabits.ui.theme.HapticEvent
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
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ManageViewModelTest {
    private val zone = TimeZone.of("Asia/Kolkata")
    private val clock = ManualClock(LocalDateTime.parse("2026-10-18T09:00").toInstant(zone), zone)
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

    private fun vm() = ManageViewModel(habits, clock)

    private suspend fun ManageViewModel.ready(predicate: (ManageUiState.Ready) -> Boolean = { true }) =
        withTimeout(5_000) { uiState.first { it is ManageUiState.Ready && predicate(it) } as ManageUiState.Ready }

    private suspend fun ManageViewModel.nextEvent() = withTimeout(5_000) { events.first() }

    private suspend fun ManageViewModel.create(name: String) {
        openCreate(); onNameChange(name); saveEditor()
        assertEquals(ManageEvent.Haptic(HapticEvent.Confirm), nextEvent())
    }

    @Test fun `create and edit through the editor`() = runBlocking<Unit> {
        val vm = vm()
        assertEquals(ManageUiState.Ready(emptyList(), emptyList()), vm.ready())
        vm.create("Read")
        val read = vm.ready { it.active.size == 1 }.active.single()
        assertEquals(LocalDate(2026, 10, 18), read.createdOn)

        vm.openEdit(read)
        assertEquals("Read", vm.editor.value!!.form.name)
        vm.onNameChange("Read 20 pages")
        vm.onDescriptionChange("Before bed")
        vm.saveEditor()
        vm.nextEvent()
        assertNull(vm.editor.value)
        val edited = vm.ready { it.active.single().name == "Read 20 pages" }.active.single()
        assertEquals(read.id, edited.id) // same identity
        assertEquals("Before bed", edited.description)
    }

    @Test fun `editor validation keeps the sheet open`() = runBlocking<Unit> {
        val vm = vm()
        vm.ready()
        vm.create("Read")
        vm.create("Walk")
        val walk = vm.ready { it.active.size == 2 }.active.first { it.name == "Walk" }
        vm.openEdit(walk)
        vm.onNameChange("READ")
        vm.saveEditor()
        assertEquals(ManageEvent.Haptic(HapticEvent.Reject), vm.nextEvent())
        assertEquals(HabitInputError.NAME_DUPLICATE, vm.editor.value!!.form.error)
    }

    @Test fun `archive offers undo and moves the habit between lists`() = runBlocking<Unit> {
        val vm = vm()
        vm.ready()
        vm.create("Read")
        val read = vm.ready { it.active.size == 1 }.active.single()

        vm.archive(read)
        assertEquals(ManageEvent.Haptic(HapticEvent.ToggleOff), vm.nextEvent())
        val message = vm.nextEvent() as ManageEvent.Message
        assertEquals(UndoAction.Unarchive(read.id), message.undo)
        val archived = vm.ready { it.archived.size == 1 }
        assertEquals(LocalDate(2026, 10, 18), archived.archived.single().archivedOn)
        assertEquals(emptyList(), archived.active)

        vm.undo(message.undo!!)
        vm.ready { it.active.size == 1 && it.archived.isEmpty() }
    }

    @Test fun `restore from archived`() = runBlocking<Unit> {
        val vm = vm()
        vm.ready()
        vm.create("Read")
        val read = vm.ready { it.active.size == 1 }.active.single()
        vm.archive(read); vm.nextEvent(); vm.nextEvent()
        vm.selectTab(ManageTab.ARCHIVED)
        vm.restore(vm.ready { it.archived.size == 1 }.archived.single())
        assertEquals(ManageEvent.Haptic(HapticEvent.ToggleOn), vm.nextEvent())
        assertIs<ManageEvent.Message>(vm.nextEvent())
        vm.ready { it.active.size == 1 && it.archived.isEmpty() }
    }

    @Test fun `delete requires confirmation and can be undone`() = runBlocking<Unit> {
        val vm = vm()
        vm.ready()
        vm.create("Read")
        val read = vm.ready { it.active.size == 1 }.active.single()

        vm.requestDelete(read)
        assertEquals(read, vm.pendingDelete.value)
        vm.cancelDelete()
        assertNull(vm.pendingDelete.value)
        assertEquals(1, vm.ready().active.size) // nothing happened

        vm.requestDelete(read)
        vm.confirmDelete()
        vm.nextEvent()
        val message = vm.nextEvent() as ManageEvent.Message
        assertEquals(UndoAction.Undelete(read.id), message.undo)
        vm.ready { it.active.isEmpty() && it.archived.isEmpty() }

        vm.undo(message.undo!!)
        vm.ready { it.active.size == 1 }
    }

    @Test fun `deleting every habit leaves Today empty with no samples`() = runBlocking<Unit> {
        val vm = vm()
        vm.ready()
        vm.create("Read")
        vm.create("Walk")
        for (habit in vm.ready { it.active.size == 2 }.active) {
            vm.requestDelete(habit); vm.confirmDelete(); vm.nextEvent(); vm.nextEvent()
        }
        assertEquals(ManageUiState.Ready(emptyList(), emptyList()), vm.ready { it.active.isEmpty() })

        val today = TodayViewModel(habits, CompletionRepository(db, clock), MutableStateFlow(clock.today()))
        val state = withTimeout(5_000) { today.uiState.first { it is TodayUiState.Ready } } as TodayUiState.Ready
        assertEquals(emptyList(), state.habits)
        assertEquals(false, state.hasActiveHabits)
    }

    @Test fun `archived habits do not appear on Today`() = runBlocking<Unit> {
        val vm = vm()
        vm.ready()
        vm.create("Read")
        vm.create("Walk")
        vm.archive(vm.ready { it.active.size == 2 }.active.first { it.name == "Walk" })
        vm.nextEvent(); vm.nextEvent()
        val today = TodayViewModel(habits, CompletionRepository(db, clock), MutableStateFlow(clock.today()))
        val state = withTimeout(5_000) { today.uiState.first { it is TodayUiState.Ready && it.habits.size == 1 } } as TodayUiState.Ready
        assertEquals("Read", state.habits.single().name)
    }

    @Test fun `move persists order and switching tabs leaves reorder mode`() = runBlocking<Unit> {
        val vm = vm()
        vm.ready()
        vm.create("A"); vm.create("B")
        val b = vm.ready { it.active.size == 2 }.active.last()
        vm.setReordering(true)
        vm.move(b.id, -1)
        assertEquals(ManageEvent.Haptic(HapticEvent.Selection), vm.nextEvent())
        assertEquals(listOf("B", "A"), vm.ready { it.active.first().name == "B" }.active.map { it.name })
        vm.selectTab(ManageTab.ARCHIVED)
        assertEquals(false, vm.reordering.value)
    }

    @Test fun `database failure shows the error state`() = runBlocking<Unit> {
        db.close()
        assertEquals(ManageUiState.Error, withTimeout(5_000) { vm().uiState.first { it !is ManageUiState.Loading } })
    }
}
