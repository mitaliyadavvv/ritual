package app.ritualhabits.ui.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.ritualhabits.core.rules.CalendarMonth
import app.ritualhabits.core.rules.DayStatus
import app.ritualhabits.core.rules.HabitMonth
import app.ritualhabits.core.rules.MonthOverview
import app.ritualhabits.core.rules.ProgressCalculator
import app.ritualhabits.core.rules.StreakCalculator
import app.ritualhabits.data.local.HabitEntity
import app.ritualhabits.data.repository.HabitRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.datetime.LocalDate

enum class ProgressView { MONTH, HABITS }

/** Lifecycle label shown beside a habit's name in history. */
enum class HabitState { ACTIVE, ARCHIVED, DELETED }

data class ProgressHabit(
    val id: String,
    val name: String,
    val state: HabitState,
    val month: HabitMonth,
    val currentStreak: Int,
    val longestStreak: Int,
)

data class DayDetailItem(val habitId: String, val name: String, val state: HabitState, val done: Boolean)

data class DayDetail(
    val date: LocalDate,
    val status: DayStatus,
    /** Habits due that day; the focused habit (if any) first. */
    val items: List<DayDetailItem>,
    val focusHabitId: String?,
)

sealed interface ProgressUiState {
    data object Loading : ProgressUiState
    data object Error : ProgressUiState
    /** No habit has ever been tracked. */
    data object Empty : ProgressUiState
    data class Ready(
        val month: CalendarMonth,
        val today: LocalDate,
        val canGoBack: Boolean,
        val canGoForward: Boolean,
        val overview: MonthOverview,
        /** Habits due at some point this month (active first, then archived/deleted), in saved order. */
        val habits: List<ProgressHabit>,
        val currentStreak: Int,
        val longestStreak: Int,
    ) : ProgressUiState
}

/**
 * Progress for one month. Every number is derived from stored eligibility periods and completions
 * through core's [ProgressCalculator] and [StreakCalculator] — the same rules as Today's streak.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProgressViewModel(
    private val habits: HabitRepository,
    private val today: StateFlow<LocalDate>,
) : ViewModel() {

    /** null = follow the current month. */
    private val selectedMonth = MutableStateFlow<CalendarMonth?>(null)
    private val _view = MutableStateFlow(ProgressView.MONTH)
    val view: StateFlow<ProgressView> = _view.asStateFlow()

    private val selectedDay = MutableStateFlow<Pair<LocalDate, String?>?>(null)
    private val retry = MutableStateFlow(0)

    val uiState: StateFlow<ProgressUiState> = retry
        .flatMapLatest { observe() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProgressUiState.Loading)

    /** The open day sheet, if any. */
    val dayDetail: StateFlow<DayDetail?> =
        combine(selectedDay, habits.observeHistory(), habits.observeAll()) { selection, history, all ->
            selection?.let { (date, focus) ->
                val byId = all.associateBy { it.id }
                val items = ProgressCalculator.dayDetail(history, date)
                    .map { DayDetailItem(it.habitId, byId[it.habitId]?.name ?: "Habit", byId[it.habitId].state(), it.done) }
                    .sortedWith(compareBy<DayDetailItem>({ it.habitId != focus }, { byId[it.habitId]?.sortOrder ?: Int.MAX_VALUE }))
                val status = ProgressCalculator.month(history, CalendarMonth.of(date), today.value).days[date.dayOfMonth - 1].status
                DayDetail(date, status, items, focus)
            }
        }
            .catch { emit(null) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private fun observe(): Flow<ProgressUiState> =
        combine(selectedMonth, today, habits.observeHistory(), habits.observeAll()) { selected, date, history, all ->
            val first = history.firstTrackedDate
            if (first == null || first > date) return@combine ProgressUiState.Empty
            val current = CalendarMonth.of(date)
            val earliest = CalendarMonth.of(first)
            val month = (selected ?: current).coerceIn(earliest, current)
            val summary = StreakCalculator.summarize(history, date)
            val inMonth = ProgressCalculator.habitsInMonth(history, month, date)
            val rows = all
                .filter { it.id in inMonth }
                .sortedWith(compareBy({ it.state().ordinal }, { it.sortOrder }, { it.createdAt }))
                .map { habit ->
                    val stats = StreakCalculator.habitStats(history, habit.id, date)
                    ProgressHabit(
                        id = habit.id,
                        name = habit.name,
                        state = habit.state(),
                        month = ProgressCalculator.habitMonth(history, habit.id, month, date),
                        currentStreak = stats.currentStreak,
                        longestStreak = stats.longestStreak,
                    )
                }
            ProgressUiState.Ready(
                month = month,
                today = date,
                canGoBack = month > earliest,
                canGoForward = month < current,
                overview = ProgressCalculator.month(history, month, date),
                habits = rows,
                currentStreak = summary.currentStreak,
                longestStreak = summary.longestStreak,
            ) as ProgressUiState
        }
            .onStart { emit(ProgressUiState.Loading) }
            .catch { emit(ProgressUiState.Error) }

    fun retry() = retry.update { it + 1 }

    fun setView(view: ProgressView) {
        _view.value = view
    }

    fun previousMonth() = shiftMonth(-1)

    fun nextMonth() = shiftMonth(1)

    private fun shiftMonth(by: Int) {
        val state = uiState.value as? ProgressUiState.Ready ?: return
        if ((by < 0 && !state.canGoBack) || (by > 0 && !state.canGoForward)) return
        val target = state.month.plus(by)
        // Returning to the current month resumes following it across midnight / month changes.
        selectedMonth.value = if (target == CalendarMonth.of(state.today)) null else target
        selectedDay.value = null
    }

    /** Open the day sheet; [habitId] focuses one habit (from the Habits view). Future days are ignored. */
    fun openDay(date: LocalDate, habitId: String? = null) {
        if (date > today.value) return
        selectedDay.value = date to habitId
    }

    fun closeDay() {
        selectedDay.value = null
    }
}

private fun HabitEntity?.state(): HabitState = when {
    this == null -> HabitState.DELETED
    isDeleted -> HabitState.DELETED
    isArchived -> HabitState.ARCHIVED
    else -> HabitState.ACTIVE
}

private fun CalendarMonth.coerceIn(min: CalendarMonth, max: CalendarMonth): CalendarMonth =
    if (this < min) min else if (this > max) max else this
