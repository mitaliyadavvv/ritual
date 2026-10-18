package app.ritualhabits.ui.manage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.ritualhabits.core.time.AppClock
import app.ritualhabits.core.time.HabitDay
import app.ritualhabits.data.local.HabitEntity
import app.ritualhabits.data.repository.HabitRepository
import app.ritualhabits.data.repository.HabitWriteResult
import app.ritualhabits.ui.habits.HabitFormState
import app.ritualhabits.ui.theme.HapticEvent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

enum class ManageTab { ACTIVE, ARCHIVED }

data class ManagedHabit(
    val id: String,
    val name: String,
    val description: String?,
    val createdOn: LocalDate,
    val archivedOn: LocalDate?,
)

sealed interface ManageUiState {
    data object Loading : ManageUiState
    data object Error : ManageUiState
    data class Ready(val active: List<ManagedHabit>, val archived: List<ManagedHabit>) : ManageUiState
}

/** The add/edit sheet. [habitId] == null means "create". */
data class HabitEditor(val habitId: String?, val form: HabitFormState) {
    val isNew: Boolean get() = habitId == null
}

/** Reversible action offered in a snackbar after it happens. */
sealed interface UndoAction {
    data class Unarchive(val habitId: String) : UndoAction
    data class Undelete(val habitId: String) : UndoAction
}

sealed interface ManageEvent {
    data class Haptic(val event: HapticEvent) : ManageEvent
    data class Message(val text: String, val undo: UndoAction? = null) : ManageEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
class ManageViewModel(
    private val habits: HabitRepository,
    private val clock: AppClock,
) : ViewModel() {

    private val retry = MutableStateFlow(0)

    val uiState: StateFlow<ManageUiState> = retry
        .flatMapLatest {
            // One query, split here, so a habit can never appear in both lists mid-update.
            habits.observeAll().map { all ->
                ManageUiState.Ready(
                    active = all.filter { it.isActive }.map { it.toManaged() },
                    archived = all.filter { it.isArchived }.sortedByDescending { it.archivedAt }.map { it.toManaged() },
                ) as ManageUiState
            }
                .onStart { emit(ManageUiState.Loading) }
                .catch { emit(ManageUiState.Error) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ManageUiState.Loading)

    private val _tab = MutableStateFlow(ManageTab.ACTIVE)
    val tab: StateFlow<ManageTab> = _tab.asStateFlow()

    private val _reordering = MutableStateFlow(false)
    val reordering: StateFlow<Boolean> = _reordering.asStateFlow()

    private val _editor = MutableStateFlow<HabitEditor?>(null)
    val editor: StateFlow<HabitEditor?> = _editor.asStateFlow()

    /** Habit awaiting delete confirmation. */
    private val _pendingDelete = MutableStateFlow<ManagedHabit?>(null)
    val pendingDelete: StateFlow<ManagedHabit?> = _pendingDelete.asStateFlow()

    private val _events = Channel<ManageEvent>(Channel.BUFFERED)
    val events: Flow<ManageEvent> = _events.receiveAsFlow()

    private fun HabitEntity.toManaged() = ManagedHabit(
        id = id,
        name = name,
        description = description,
        createdOn = HabitDay.of(createdAt, clock.zone()),
        archivedOn = archivedAt?.let { HabitDay.of(it, clock.zone()) },
    )

    fun retry() = retry.update { it + 1 }

    fun selectTab(tab: ManageTab) {
        _tab.value = tab
        _reordering.value = false
    }

    fun setReordering(enabled: Boolean) {
        _reordering.value = enabled
    }

    // --- Create / edit -----------------------------------------------------------------------

    fun openCreate() {
        _editor.value = HabitEditor(habitId = null, form = HabitFormState())
    }

    fun openEdit(habit: ManagedHabit) {
        _editor.value = HabitEditor(habit.id, HabitFormState(name = habit.name, description = habit.description.orEmpty()))
    }

    fun dismissEditor() {
        _editor.value = null
    }

    fun onNameChange(name: String) = _editor.update { it?.copy(form = it.form.copy(name = name, error = null)) }

    fun onDescriptionChange(text: String) = _editor.update { it?.copy(form = it.form.copy(description = text, error = null)) }

    fun saveEditor() {
        val editor = _editor.value ?: return
        if (editor.form.saving) return
        _editor.value = editor.copy(form = editor.form.copy(saving = true))
        viewModelScope.launch {
            val result = if (editor.isNew) {
                habits.create(editor.form.name, editor.form.description)
            } else {
                habits.update(editor.habitId!!, editor.form.name, editor.form.description)
            }
            when (result) {
                is HabitWriteResult.Success -> {
                    _editor.value = null
                    _events.send(ManageEvent.Haptic(HapticEvent.Confirm))
                }
                is HabitWriteResult.Invalid -> {
                    _editor.update { it?.copy(form = it.form.copy(saving = false, error = result.error)) }
                    _events.send(ManageEvent.Haptic(HapticEvent.Reject))
                }
                HabitWriteResult.NotFound -> {
                    _editor.value = null
                    _events.send(ManageEvent.Message("That habit no longer exists."))
                }
            }
        }
    }

    // --- Lifecycle ---------------------------------------------------------------------------

    fun archive(habit: ManagedHabit) = viewModelScope.launch {
        if (habits.archive(habit.id) is HabitWriteResult.Success) {
            _events.send(ManageEvent.Haptic(HapticEvent.ToggleOff))
            _events.send(ManageEvent.Message("Archived “${habit.name}”. Its history is kept.", UndoAction.Unarchive(habit.id)))
        }
    }

    fun restore(habit: ManagedHabit) = viewModelScope.launch {
        when (val result = habits.restore(habit.id)) {
            is HabitWriteResult.Success -> {
                _events.send(ManageEvent.Haptic(HapticEvent.ToggleOn))
                _events.send(ManageEvent.Message("Restored “${habit.name}”. It's due again from today."))
            }
            is HabitWriteResult.Invalid -> {
                _events.send(ManageEvent.Haptic(HapticEvent.Reject))
                _events.send(ManageEvent.Message("Can't restore “${habit.name}”: an active habit already uses that name."))
            }
            HabitWriteResult.NotFound -> Unit
        }
    }

    fun requestDelete(habit: ManagedHabit) {
        _pendingDelete.value = habit
    }

    fun cancelDelete() {
        _pendingDelete.value = null
    }

    fun confirmDelete() {
        val habit = _pendingDelete.value ?: return
        _pendingDelete.value = null
        viewModelScope.launch {
            if (habits.delete(habit.id) is HabitWriteResult.Success) {
                _events.send(ManageEvent.Haptic(HapticEvent.Confirm))
                _events.send(ManageEvent.Message("Deleted “${habit.name}”. Past days keep their records.", UndoAction.Undelete(habit.id)))
            }
        }
    }

    fun undo(action: UndoAction) = viewModelScope.launch {
        val result = when (action) {
            is UndoAction.Unarchive -> habits.restore(action.habitId)
            is UndoAction.Undelete -> habits.undoDelete(action.habitId)
        }
        if (result is HabitWriteResult.Invalid) {
            _events.send(ManageEvent.Message("Couldn't undo: another habit now uses that name."))
        }
    }

    // --- Reorder -----------------------------------------------------------------------------

    fun move(habitId: String, by: Int) = viewModelScope.launch {
        if (habits.move(habitId, by)) _events.send(ManageEvent.Haptic(HapticEvent.Selection))
    }
}
