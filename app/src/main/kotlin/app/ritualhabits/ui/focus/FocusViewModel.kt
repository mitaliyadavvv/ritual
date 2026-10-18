package app.ritualhabits.ui.focus

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.ritualhabits.core.model.FocusSession
import app.ritualhabits.core.model.FocusStatus
import app.ritualhabits.core.rules.FocusTimer
import app.ritualhabits.core.time.AppClock
import app.ritualhabits.data.focus.FocusAlarms
import app.ritualhabits.data.repository.FocusOutcome
import app.ritualhabits.data.repository.FocusRepository
import app.ritualhabits.data.repository.FocusStartResult
import app.ritualhabits.data.repository.HabitRepository
import app.ritualhabits.data.repository.PreferencesRepository
import app.ritualhabits.ui.theme.HapticEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

enum class FocusPhase { IDLE, RUNNING, PAUSED, COMPLETED }

data class FocusHabitOption(val id: String, val name: String, val doneToday: Boolean)

sealed interface FocusUiState {
    data object Loading : FocusUiState
    data object Error : FocusUiState
    /** No active habits are due today, and no session is in progress. */
    data object NoHabits : FocusUiState
    data class Ready(
        val phase: FocusPhase,
        val habits: List<FocusHabitOption>,
        /** The habit shown on screen: the session's habit while a session exists, else the selection. */
        val habitId: String?,
        val habitName: String?,
        /** Selected preset (idle) or the session's planned length. */
        val duration: Duration,
        val remaining: Duration,
        /** 0 at start → 1 at the end. */
        val progress: Float,
        val sessionId: String?,
        /** Set once a session has completed. */
        val outcome: FocusOutcome?,
    ) : FocusUiState {
        val canStart: Boolean get() = phase == FocusPhase.IDLE && habitId != null && habits.any { it.id == habitId }
        val canChangeSetup: Boolean get() = phase == FocusPhase.IDLE
    }
}

sealed interface FocusEvent {
    data class Haptic(val event: HapticEvent) : FocusEvent
    data class Message(val text: String) : FocusEvent
}

/**
 * Focus timer screen state. Time is always derived from the session's stored end time; the ticker
 * only redraws while running. Completion is performed by [FocusRepository.reconcile] (exactly once,
 * for the session's own habit, on the day it ended, only if due) — never by this ViewModel directly.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FocusViewModel(
    private val focus: FocusRepository,
    private val habits: HabitRepository,
    private val alarms: FocusAlarms,
    private val clock: AppClock,
    private val today: StateFlow<LocalDate>,
    private val preferences: PreferencesRepository? = null,
    private val tickInterval: Duration = 500.milliseconds,
) : ViewModel() {

    /** Whether Ritual has already asked for notification permission (asked at most once, on first Start). */
    val notificationsAsked: StateFlow<Boolean> = (preferences?.preferences?.map { it.notificationPermissionRequested } ?: flowOf(true))
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    fun markNotificationsAsked() {
        viewModelScope.launch { preferences?.markNotificationPermissionRequested() }
    }

    private val selectedHabit = MutableStateFlow<String?>(null)
    private val selectedDuration = MutableStateFlow(FocusTimer.DEFAULT_DURATION)
    private val retry = MutableStateFlow(0)

    private val _events = Channel<FocusEvent>(Channel.BUFFERED)
    val events: Flow<FocusEvent> = _events.receiveAsFlow()

    /** Session ids this screen ended itself (cancel), so an external cancel can be told apart. */
    private val cancelledHere = mutableSetOf<String>()
    private var lastActiveId: String? = null
    private var reconcileRequestedFor: String? = null

    init {
        // Catch up on anything that ended while the app was closed.
        viewModelScope.launch { runCatching { focus.reconcile() } }
    }

    /** Emits "now" every [tickInterval] while a session is running, once otherwise. */
    private val now: Flow<Instant> = focus.observeCurrent()
        .map { it?.status == FocusStatus.RUNNING }
        .distinctUntilChanged()
        .flatMapLatest { running ->
            if (!running) flowOf(clock.now())
            else flow {
                while (true) {
                    emit(clock.now())
                    delay(tickInterval)
                }
            }.flowOn(Dispatchers.Default) // only reads the clock; keeps ticking independently of the UI dispatcher
        }

    private data class Inputs(
        val session: FocusSession?,
        val options: List<FocusHabitOption>,
        val names: Map<String, String>,
        val selected: String?,
        val duration: Duration,
    )

    val uiState: StateFlow<FocusUiState> = retry
        .flatMapLatest {
            val inputs = combine(
                focus.observeCurrent(),
                combine(habits.observeActive(), habits.observeHistory(), today) { active, history, date ->
                    active.filter { history.isEligible(it.id, date) }
                        .map { FocusHabitOption(it.id, it.name, history.isCompleted(it.id, date)) }
                },
                habits.observeAll().map { all -> all.associate { it.id to it.name } },
                selectedHabit,
                selectedDuration,
            ) { session, options, names, selected, duration -> Inputs(session, options, names, selected, duration) }

            combine(inputs, now) { input, instant -> input to instant }
                .mapLatest { (input, instant) -> buildState(input, instant) }
                .onStart { emit(FocusUiState.Loading) }
                .catch { emit(FocusUiState.Error) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FocusUiState.Loading)

    private suspend fun buildState(input: Inputs, instant: Instant): FocusUiState {
        val session = input.session
        detectExternalEnd(session)
        if (session == null) {
            if (input.options.isEmpty()) return FocusUiState.NoHabits
            // Keep a valid selection only; a habit archived/deleted while selected is dropped.
            val selected = input.selected?.takeIf { id -> input.options.any { it.id == id } }
            return FocusUiState.Ready(
                phase = FocusPhase.IDLE,
                habits = input.options,
                habitId = selected,
                habitName = selected?.let(input.names::get),
                duration = input.duration,
                remaining = input.duration,
                progress = 0f,
                sessionId = null,
                outcome = null,
            )
        }
        val phase = when (session.status) {
            FocusStatus.RUNNING -> FocusPhase.RUNNING
            FocusStatus.PAUSED -> FocusPhase.PAUSED
            else -> FocusPhase.COMPLETED
        }
        if (FocusTimer.isDue(session, instant)) requestReconcile(session.id)
        return FocusUiState.Ready(
            phase = phase,
            habits = input.options,
            habitId = session.habitId,
            habitName = input.names[session.habitId] ?: "Habit",
            duration = session.plannedDuration,
            remaining = FocusTimer.remaining(session, instant),
            progress = FocusTimer.progress(session, instant),
            sessionId = session.id,
            outcome = if (phase == FocusPhase.COMPLETED) focus.outcome(session) else null,
        )
    }

    /** The end time passed while visible: complete it now (repository is idempotent). */
    private fun requestReconcile(sessionId: String) {
        if (reconcileRequestedFor == sessionId) return
        reconcileRequestedFor = sessionId
        viewModelScope.launch {
            if (focus.reconcile().any { it.id == sessionId }) {
                alarms.cancel()
                _events.send(FocusEvent.Haptic(HapticEvent.Confirm))
            }
        }
    }

    /**
     * A running/paused session vanished without this screen ending it: it was cancelled elsewhere
     * (its habit was archived or deleted). The marker only advances after the check completes, so a
     * state update that interrupts the check (mapLatest) re-runs it instead of losing it.
     */
    private suspend fun detectExternalEnd(session: FocusSession?) {
        val previous = lastActiveId
        val current = session?.id
        if (previous != null && previous != current && previous !in cancelledHere) {
            val ended = focus.get(previous)
            if (ended?.status == FocusStatus.CANCELLED) {
                alarms.cancel()
                val name = habits.get(ended.habitId)?.name ?: "This habit"
                _events.trySend(FocusEvent.Message("Session ended: “$name” was archived or deleted. Nothing was recorded."))
            }
        }
        lastActiveId = current
    }

    fun retry() = retry.update { it + 1 }

    /** Whether session-end alerts can be on time (exact alarms allowed). */
    val alertsCanBeExact: Boolean get() = alarms.canBeExact

    /** Screen came back to the foreground: catch up, and re-arm a running session's alarm
     *  (it becomes exact if the user just allowed precise alerts). */
    fun onResume() {
        viewModelScope.launch {
            runCatching { focus.reconcile() }
            focus.activeSessions().filter { it.status == FocusStatus.RUNNING }.forEach(alarms::schedule)
        }
    }

    fun selectHabit(id: String) {
        if ((uiState.value as? FocusUiState.Ready)?.canChangeSetup == false) return
        selectedHabit.value = id
    }

    fun selectDuration(duration: Duration) {
        if ((uiState.value as? FocusUiState.Ready)?.canChangeSetup == false) return
        require(duration in FocusTimer.PRESETS) { "Not a preset: $duration" }
        selectedDuration.value = duration
    }

    fun start() {
        val state = uiState.value as? FocusUiState.Ready ?: return
        if (!state.canStart) return
        val habitId = state.habitId ?: return
        viewModelScope.launch {
            when (val result = focus.start(habitId, state.duration)) {
                is FocusStartResult.Started -> {
                    alarms.schedule(result.session)
                    _events.send(FocusEvent.Haptic(HapticEvent.ToggleOn))
                }
                FocusStartResult.AlreadyActive -> _events.send(FocusEvent.Message("A session is already in progress."))
                FocusStartResult.NotEligible -> _events.send(FocusEvent.Message("That habit isn't due today."))
            }
        }
    }

    fun pause() = withSession { id ->
        focus.pause(id)
        alarms.cancel()
        _events.send(FocusEvent.Haptic(HapticEvent.ToggleOff))
    }

    fun resume() = withSession { id ->
        focus.resume(id)?.let(alarms::schedule)
        _events.send(FocusEvent.Haptic(HapticEvent.ToggleOn))
    }

    /** Ends the session without recording anything (if the end time already passed, it completes). */
    fun cancel() = withSession { id ->
        cancelledHere += id
        val session = focus.cancel(id)
        alarms.cancel()
        if (session?.status == FocusStatus.CANCELLED) {
            _events.send(FocusEvent.Message("Session cancelled. Nothing was recorded."))
        }
    }

    /** Dismiss the completed state and return to setup, keeping the same habit and length. */
    fun startAnother() {
        val state = uiState.value as? FocusUiState.Ready ?: return
        val id = state.sessionId ?: return
        if (state.phase != FocusPhase.COMPLETED) return
        state.habitId?.let { selectedHabit.value = it }
        if (state.duration in FocusTimer.PRESETS) selectedDuration.value = state.duration
        viewModelScope.launch { focus.acknowledge(id) }
    }

    private fun withSession(block: suspend (String) -> Unit) {
        val id = (uiState.value as? FocusUiState.Ready)?.sessionId ?: return
        viewModelScope.launch { block(id) }
    }
}
