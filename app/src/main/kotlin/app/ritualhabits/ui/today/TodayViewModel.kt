package app.ritualhabits.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.ritualhabits.core.rules.StreakCalculator
import app.ritualhabits.data.repository.CompletionRepository
import app.ritualhabits.data.repository.HabitRepository
import app.ritualhabits.data.repository.HabitWriteResult
import app.ritualhabits.data.repository.ToggleResult
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

data class TodayHabit(
    val id: String,
    val name: String,
    val description: String?,
    val completed: Boolean,
)

sealed interface TodayUiState {
    data object Loading : TodayUiState
    data object Error : TodayUiState
    data class Ready(
        val date: LocalDate,
        val habits: List<TodayHabit>,
        /** Active habits exist but none is required today (only possible after a clock change). */
        val hasActiveHabits: Boolean,
        val currentStreak: Int,
    ) : TodayUiState {
        val total: Int get() = habits.size
        val completed: Int get() = habits.count { it.completed }
        val allDone: Boolean get() = total > 0 && completed == total
        val progress: Float get() = if (total == 0) 0f else completed.toFloat() / total
    }
}

sealed interface TodayEvent {
    data class Haptic(val event: HapticEvent) : TodayEvent
    data class Message(val text: String) : TodayEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModel(
    private val habits: HabitRepository,
    private val completions: CompletionRepository,
    private val today: StateFlow<LocalDate>,
) : ViewModel() {

    private val retry = MutableStateFlow(0)

    val uiState: StateFlow<TodayUiState> = retry
        .flatMapLatest { observeToday() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayUiState.Loading)

    private val _addHabit = MutableStateFlow<HabitFormState?>(null)
    val addHabit: StateFlow<HabitFormState?> = _addHabit.asStateFlow()

    private val _events = Channel<TodayEvent>(Channel.BUFFERED)
    val events: Flow<TodayEvent> = _events.receiveAsFlow()

    private fun observeToday(): Flow<TodayUiState> =
        combine(today, habits.observeActive(), habits.observeHistory()) { date, active, history ->
            val due = active.filter { history.isEligible(it.id, date) }
            TodayUiState.Ready(
                date = date,
                habits = due.map { TodayHabit(it.id, it.name, it.description, history.isCompleted(it.id, date)) },
                hasActiveHabits = active.isNotEmpty(),
                currentStreak = StreakCalculator.summarize(history, date).currentStreak,
            ) as TodayUiState
        }
            .onStart { emit(TodayUiState.Loading) }
            .catch { emit(TodayUiState.Error) }

    fun retry() = retry.update { it + 1 }

    /** Toggle a habit for the day the screen is showing. */
    fun toggle(habitId: String) {
        val shown = uiState.value as? TodayUiState.Ready ?: return
        val habit = shown.habits.firstOrNull { it.id == habitId } ?: return
        val completesDay = !habit.completed && shown.completed == shown.total - 1
        viewModelScope.launch {
            val event = when (completions.toggle(habitId, shown.date)) {
                ToggleResult.COMPLETED -> TodayEvent.Haptic(if (completesDay) HapticEvent.Confirm else HapticEvent.ToggleOn)
                ToggleResult.UNCOMPLETED -> TodayEvent.Haptic(HapticEvent.ToggleOff)
                ToggleResult.STALE_DATE -> TodayEvent.Message("A new day has started. Your checklist is up to date.")
                ToggleResult.NOT_ELIGIBLE -> TodayEvent.Message("This habit isn't due today.")
            }
            _events.send(event)
        }
    }

    fun openAddHabit() {
        _addHabit.value = HabitFormState()
    }

    fun dismissAddHabit() {
        _addHabit.value = null
    }

    fun onNameChange(name: String) = _addHabit.update { it?.copy(name = name, error = null) }

    fun onDescriptionChange(description: String) = _addHabit.update { it?.copy(description = description, error = null) }

    fun saveHabit() {
        val form = _addHabit.value ?: return
        if (form.saving) return
        _addHabit.value = form.copy(saving = true)
        viewModelScope.launch {
            when (val result = habits.create(form.name, form.description)) {
                is HabitWriteResult.Success -> {
                    _addHabit.value = null
                    _events.send(TodayEvent.Haptic(HapticEvent.Confirm))
                }
                is HabitWriteResult.Invalid -> {
                    _addHabit.update { it?.copy(saving = false, error = result.error) }
                    _events.send(TodayEvent.Haptic(HapticEvent.Reject))
                }
                HabitWriteResult.NotFound -> _addHabit.update { it?.copy(saving = false) }
            }
        }
    }
}
