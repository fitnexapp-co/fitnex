package com.fitnex.feature.workout.live

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitnex.core.data.session.SessionRepository
import com.fitnex.core.data.training.ExerciseRepository
import com.fitnex.core.data.training.RoutineRepository
import com.fitnex.core.data.training.WorkoutSessionRepository
import com.fitnex.core.datastore.UserPreferencesDataSource
import com.fitnex.core.model.ActiveSession
import com.fitnex.core.model.LoggedSet
import com.fitnex.core.model.PreviousPerformance
import com.fitnex.core.model.RoutineExercise
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class LiveUiState(
    val loading: Boolean = true,
    val session: ActiveSession? = null,
    val now: Instant = Instant.EPOCH,
    val index: Int = 0,
    val current: RoutineExercise? = null,
    val currentSets: List<LoggedSet> = emptyList(),
    val targetSets: Int = 0,
    val previous: PreviousPerformance? = null,
    /** Bests of the current exercise from earlier workouts (null when it was never done). */
    val bests: ExerciseBests? = null,
    val primaryMuscles: List<String> = emptyList(),
    /** The current exercise has a camera rep detector. */
    val cameraSupported: Boolean = false,
    /** How the current exercise is logged, and whether a timed exercise also takes a weight. */
    val tracking: com.fitnex.core.model.Tracking = com.fitnex.core.model.Tracking.WEIGHT_REPS,
    val timedWeight: Boolean = false,
    val draft: SetDraft = SetDraft(0, 0f),
    val rest: RestTimer? = null,
    val doneSets: Int = 0,
    val plannedSets: Int = 0,
    /** Navigation outcomes. */
    val finishedSummaryId: String? = null,
    val closed: Boolean = false,
)

/**
 * Live workout (spec §18). The session itself lives in Room (survives process death); only the
 * rest countdown and unsaved draft values are in memory.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LiveWorkoutViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val session: SessionRepository,
    private val sessions: WorkoutSessionRepository,
    private val routines: RoutineRepository,
    private val exercises: ExerciseRepository,
    private val preferences: UserPreferencesDataSource,
    private val alerts: RestAlerts,
    private val clock: Clock,
    private val health: com.fitnex.core.data.health.HealthConnectRepository,
    private val memory: LiveSessionMemory,
) : ViewModel() {
    private val routineId: String? = savedState.get<String>("routineId")

    private val now = MutableStateFlow(clock.instant())
    // Kept in [memory] (not here) so they survive leaving the workout screen and coming back.
    private val rest = memory.rest
    private val drafts = memory.drafts
    private val extraSets = memory.extraSets
    private val previous = MutableStateFlow<Map<String, PreviousPerformance?>>(emptyMap())
    private val muscles = MutableStateFlow<Map<String, List<String>>>(emptyMap())
    private val cameraIds = MutableStateFlow<Set<String>>(emptySet())
    /** How to do each exercise of the plan, readable without leaving the session. */
    private val _guides = MutableStateFlow<Map<String, com.fitnex.core.model.Exercise>>(emptyMap())
    val guides: StateFlow<Map<String, com.fitnex.core.model.Exercise>> = _guides
    /** Sets from earlier workouts of the plan's exercises, to spot a personal record as it happens. */
    private var history: List<LoggedSet> = emptyList()
    private val _newRecord = MutableStateFlow<com.fitnex.core.model.PersonalRecord?>(null)
    /** Record set with the last completed set (shown once; cleared on the next set or when dismissed). */
    val newRecord: StateFlow<com.fitnex.core.model.PersonalRecord?> = _newRecord
    fun dismissRecord() { _newRecord.value = null }
    /** Id of the last completed set: the one the record banner and the rest countdown belong to. */
    private var lastSetId: String? = null
    private val trackings = MutableStateFlow<Map<String, Pair<com.fitnex.core.model.Tracking, Boolean>>>(emptyMap())
    private val outcome = MutableStateFlow<Pair<String?, Boolean>>(null to false)
    private var started = false
    private var ticker: Job? = null

    private val active = session.currentUserId.filterNotNull().flatMapLatest { sessions.observeActive(it) }

    val notificationsEnabled: StateFlow<Boolean> = preferences.preferences
        .map { it.restTimerNotifications }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val state: StateFlow<LiveUiState> = combine(
        combine(active, now, rest, ::Triple),
        combine(drafts, extraSets, previous, muscles, outcome) { d, e, p, m, o -> Inputs(d, e, p, m, o) },
        combine(cameraIds, trackings, ::Pair),
    ) { (s, t, r), inputs, (camera, tracks) -> buildState(s, t, r, inputs, tracks).let { st -> st.copy(cameraSupported = st.current?.exerciseId in camera) } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, LiveUiState())

    private data class Inputs(
        val drafts: Map<String, SetDraft>,
        val extra: Map<String, Int>,
        val previous: Map<String, PreviousPerformance?>,
        val muscles: Map<String, List<String>>,
        val outcome: Pair<String?, Boolean>,
    )

    init {
        // A rest that ended while the workout screen was closed already notified: no beep now.
        rest.value?.let { if (it.isFinished(clock.instant())) rest.value = null }
        viewModelScope.launch {
            val uid = session.currentUserId.filterNotNull().first()
            // Resume the active session if there is one; otherwise start the requested routine.
            val current = sessions.observeActive(uid).first()
            if (current == null) {
                val routine = routineId?.let { routines.getRoutine(it) }
                if (routine == null || routine.exercises.isEmpty()) {
                    outcome.value = null to true
                    return@launch
                }
                sessions.start(uid, routine)
            }
            started = true
            val active = sessions.observeActive(uid).filterNotNull().first()
            memory.bind(active.id)
            val plan = active.plan
            history = sessions.historyBefore(uid, plan.map { it.exerciseId }.distinct(), active.startedAt)
            val planExercises = exercises.getExercises(plan.map { it.exerciseId })
            cameraIds.value = planExercises.filter { it.cameraRepSupported }.map { it.id }.toSet()
            _guides.value = planExercises.associateBy { it.id }
            trackings.value = planExercises.associate { e -> e.id to (e.tracking to com.fitnex.core.model.TrackingRules.timedWithWeight(e.tracking, e.equipment)) }
            muscles.value = planExercises.associate { e ->
                e.id to e.primaryMuscleIds.let { ids -> exercises.observeMuscles().first().filter { it.id in ids }.map { it.name } }
            }
        }
        ticker = viewModelScope.launch {
            while (isActive) {
                val t = clock.instant()
                // Checked 4×/s so the rest ends on time, but the screen only rebuilds when the shown
                // second changes (once a second instead of four times).
                if (t.epochSecond != now.value.epochSecond) now.value = t
                rest.value?.let { r ->
                    if (r.isFinished(t)) {
                        rest.value = null
                        alerts.cancel() // we are on screen: no notification needed
                        alerts.playEnd(preferences.preferences.first().restTimerSound)
                    }
                }
                delay(TICK_MS)
            }
        }
    }

    private fun buildState(
        s: ActiveSession?, t: Instant, r: RestTimer?, i: Inputs,
        tracks: Map<String, Pair<com.fitnex.core.model.Tracking, Boolean>> = emptyMap(),
    ): LiveUiState {
        if (s == null) {
            // Session gone: finished (navigate to summary) or never started (close).
            return LiveUiState(loading = !started && !i.outcome.second, finishedSummaryId = i.outcome.first, closed = i.outcome.second, now = t)
        }
        val index = s.currentExerciseIndex.coerceIn(0, (s.plan.size - 1).coerceAtLeast(0))
        val item = s.plan.getOrNull(index)
        if (item != null && item.exerciseId !in i.previous) loadPrevious(item.exerciseId, s.startedAt)
        val prev = item?.let { i.previous[it.exerciseId] }
        val (tracking, timedWeight) = item?.let { tracks[it.exerciseId] } ?: (com.fitnex.core.model.Tracking.WEIGHT_REPS to false)
        return LiveUiState(
            tracking = tracking,
            timedWeight = timedWeight,
            loading = false,
            session = s,
            now = t,
            index = index,
            current = item,
            currentSets = item?.let { s.setsFor(it.exerciseId) }.orEmpty(),
            targetSets = item?.let { it.sets + (i.extra[it.exerciseId] ?: 0) } ?: 0,
            previous = prev,
            bests = item?.let { ExerciseBests.from(history.filter { h -> h.exerciseId == it.exerciseId }) },
            primaryMuscles = item?.let { i.muscles[it.exerciseId] }.orEmpty(),
            draft = item?.let { i.drafts[it.exerciseId] ?: DraftLogic.nextDraft(it, s, prev, tracking) } ?: SetDraft(0, 0f),
            rest = r,
            doneSets = s.sets.size,
            plannedSets = s.plannedSetCount + i.extra.values.sum(),
            finishedSummaryId = i.outcome.first,
            closed = i.outcome.second,
        )
    }

    private fun loadPrevious(exerciseId: String, before: Instant) {
        previous.update { it + (exerciseId to null) } // mark as loading, avoids duplicate queries
        viewModelScope.launch {
            val uid = session.currentUserId.filterNotNull().first()
            val perf = sessions.previousPerformance(uid, exerciseId, before)
            previous.update { it + (exerciseId to perf) }
        }
    }

    // ---- Sets

    fun setDraft(reps: Int, weightKg: Float, durationSeconds: Int? = null, distanceMeters: Float? = null) {
        val item = state.value.current ?: return
        drafts.update { it + (item.exerciseId to SetDraft(reps, weightKg, durationSeconds = durationSeconds, distanceMeters = distanceMeters)) }
    }

    /** Camera counter result: becomes the draft (weight kept), still editable before logging. */
    fun useCameraReps(reps: Int) {
        val s = state.value
        val item = s.current ?: return
        drafts.update { it + (item.exerciseId to SetDraft(reps.coerceAtLeast(1), s.draft.weightKg, com.fitnex.core.model.SetSource.CAMERA)) }
    }

    /** One tap: logs the proposed values (or [values]) and starts the rest timer. */
    fun completeSet(values: SetDraft? = null) {
        val s = state.value
        val session = s.session ?: return
        val item = s.current ?: return
        val draft = values ?: s.draft
        viewModelScope.launch {
            val logged = sessions.logSet(session.id, item, s.index, draft.reps, draft.weightKg, draft.source, draft.durationSeconds, draft.distanceMeters)
            lastSetId = logged.id
            _newRecord.value = RecordLogic.recordFor(item, draft, s.currentSets, history)
            drafts.update { it - item.exerciseId } // next draft follows what was just done
            startRest(item.restSeconds, item.exerciseName)
        }
    }

    /** "Repetir valores anteriores": logs a copy of the last set of this exercise. */
    fun repeatLastSet() {
        val last = state.value.currentSets.lastOrNull() ?: return
        // Values passed directly: reading them back from `state` would race with its async update.
        completeSet(SetDraft(last.reps, last.weightKg, durationSeconds = last.durationSeconds, distanceMeters = last.distanceMeters))
    }

    fun editSet(setId: String, reps: Int, weightKg: Float, durationSeconds: Int? = null, distanceMeters: Float? = null) =
        viewModelScope.launch { sessions.updateSet(setId, reps, weightKg, durationSeconds, distanceMeters) }
    /**
     * Deleting the set just completed also takes back what it triggered: its record banner and its
     * rest countdown (a set that no longer exists is no record and needs no rest).
     */
    fun deleteSet(setId: String) = viewModelScope.launch {
        sessions.deleteSet(setId)
        if (setId == lastSetId) {
            lastSetId = null
            _newRecord.value = null
            skipRest()
        }
    }

    fun addSet() {
        val item = state.value.current ?: return
        extraSets.update { it + (item.exerciseId to (it[item.exerciseId] ?: 0) + 1) }
    }

    // ---- Exercise navigation

    fun goTo(index: Int) {
        val s = state.value.session ?: return
        if (index !in s.plan.indices) return
        viewModelScope.launch { sessions.setCurrentExercise(s.id, index) }
    }

    fun next() = goTo(state.value.index + 1)
    fun previousExercise() = goTo(state.value.index - 1)

    // ---- Rest timer

    private suspend fun startRest(seconds: Int, exerciseName: String) {
        val t = clock.instant()
        val timer = RestTimer.start(seconds, t)
        rest.value = timer
        if (preferences.preferences.first().restTimerNotifications) alerts.schedule(timer.endsAt!!, exerciseName, t)
    }

    fun pauseRest() {
        rest.update { it?.pause(clock.instant()) }
        alerts.cancel()
    }

    fun resumeRest() {
        val t = clock.instant()
        val r = rest.value?.resume(t) ?: return
        rest.value = r
        scheduleIfEnabled(r, t)
    }

    fun addRest(seconds: Long) {
        val t = clock.instant()
        val r = rest.value?.add(seconds, t) ?: return
        rest.value = r
        if (!r.isPaused) scheduleIfEnabled(r, t)
    }

    fun skipRest() {
        rest.value = null
        alerts.cancel()
    }

    private fun scheduleIfEnabled(r: RestTimer, t: Instant) = viewModelScope.launch {
        if (preferences.preferences.first().restTimerNotifications) {
            alerts.schedule(r.endsAt!!, state.value.current?.exerciseName.orEmpty(), t)
        }
    }

    // ---- Session

    fun pauseSession() {
        val s = state.value.session ?: return
        viewModelScope.launch { sessions.pause(s.id) }
        pauseRest()
    }

    fun resumeSession() {
        val s = state.value.session ?: return
        viewModelScope.launch { sessions.resume(s.id) }
    }

    fun finish() {
        val s = state.value.session ?: return
        skipRest()
        viewModelScope.launch {
            val summary = sessions.finish(s.id)
            memory.clear()
            // Health Connect (if the user connected it): the workout also appears in their health app.
            if (summary != null) health.writeWorkout(summary.sessionId, s.routineName, s.startedAt, clock.instant())
            outcome.value = if (summary != null) summary.sessionId to false else null to true
        }
    }

    fun discard() {
        val s = state.value.session ?: return
        skipRest()
        viewModelScope.launch {
            sessions.discard(s.id)
            memory.clear()
            outcome.value = null to true
        }
    }

    override fun onCleared() {
        ticker?.cancel()
    }

    private companion object {
        const val TICK_MS = 250L
    }
}
