package com.fitnex.feature.workout.editor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitnex.core.common.time.IdGenerator
import com.fitnex.core.data.session.SessionRepository
import com.fitnex.core.data.training.ExerciseRepository
import com.fitnex.core.data.training.RoutineRepository
import com.fitnex.core.datastore.UserPreferencesDataSource
import com.fitnex.core.model.Routine
import com.fitnex.core.model.RoutineExercise
import com.fitnex.core.model.RoutineLimits
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RoutineEditorUiState(
    val loading: Boolean = true,
    val isNew: Boolean = true,
    val name: String = "",
    val days: Set<DayOfWeek> = emptySet(),
    val isTemplate: Boolean = false,
    val items: List<RoutineExercise> = emptyList(),
    val showErrors: Boolean = false,
    val saving: Boolean = false,
    /** Set once saved or deleted: the screen pops back. */
    val finished: Boolean = false,
    val limitReached: Boolean = false,
    /** FITNEX AI's explanation when the routine was designed by it (kept on save). */
    val aiRationale: String? = null,
    /** How each exercise is logged (by exercise id): its target means reps, seconds or minutes. */
    val trackings: Map<String, com.fitnex.core.model.Tracking> = emptyMap(),
    /** Exercises whose target includes a weight (weight × reps, or loaded timed holds/carries). */
    val weighted: Set<String> = emptySet(),
    /** Photo folder of each exercise (for the thumbnails), when it has one. */
    val media: Map<String, String?> = emptyMap(),
) {
    fun trackingOf(exerciseId: String) = trackings[exerciseId] ?: com.fitnex.core.model.Tracking.WEIGHT_REPS
    val nameError: Boolean get() = showErrors && name.isBlank()
    val exercisesError: Boolean get() = showErrors && items.isEmpty()
}

/** The copy made by "Duplicar rutina": new ids for the routine and each of its exercises. */
internal fun duplicateOf(s: RoutineEditorUiState, suffix: String, id: String, now: Instant, newId: () -> String): Routine {
    val base = s.name.trim().take(RoutineLimits.NAME_MAX_LENGTH - suffix.length)
    return Routine(id, base + suffix, emptySet(), false, s.items.map { it.copy(id = newId()) }, now)
}

@HiltViewModel
class RoutineEditorViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    private val session: SessionRepository,
    private val routines: RoutineRepository,
    private val exercises: ExerciseRepository,
    private val preferences: UserPreferencesDataSource,
    private val ids: IdGenerator,
    private val clock: Clock,
) : ViewModel() {
    private val existingId: String? = savedState.get<String>("routineId")
    private val routineId: String = existingId ?: ids.newId()
    private var createdAt: Instant = clock.instant()
    private var defaultRest = 90

    private val _state = MutableStateFlow(RoutineEditorUiState(isNew = existingId == null))
    val state: StateFlow<RoutineEditorUiState> = _state.asStateFlow()

    /** Completed after the initial load. Declared BEFORE init: the init coroutine may run immediately. */
    private val loaded = CompletableDeferred<Unit>()

    init {
        viewModelScope.launch {
            defaultRest = preferences.preferences.first().defaultRestSeconds
            val routine = existingId?.let { routines.getRoutine(it) }
            if (routine != null) {
                createdAt = routine.createdAt
                val info = exercises.getExercises(routine.exercises.map { it.exerciseId })
                _state.update {
                    it.copy(
                        loading = false, name = routine.name, days = routine.days, isTemplate = routine.isTemplate, items = routine.exercises, aiRationale = routine.aiRationale,
                        trackings = info.associate { e -> e.id to e.tracking }, weighted = info.filter(::showsWeight).map { e -> e.id }.toSet(), media = info.associate { e -> e.id to e.mediaUri },
                    )
                }
            } else {
                _state.update { it.copy(loading = false) }
            }
            loaded.complete(Unit)
        }
    }

    /**
     * Exercises chosen in the library. The screen reads them from its NavBackStackEntry's
     * SavedStateHandle (that is NOT this ViewModel's handle) and forwards them here. Waits for
     * the initial load so an existing routine is never overwritten by a race.
     */
    fun onExercisesPicked(exerciseIds: List<String>) {
        viewModelScope.launch {
            loaded.await()
            add(exerciseIds)
        }
    }

    fun setName(value: String) = _state.update { it.copy(name = value.take(RoutineLimits.NAME_MAX_LENGTH)) }
    fun toggleDay(day: DayOfWeek) = _state.update { it.copy(days = if (day in it.days) it.days - day else it.days + day) }

    suspend fun add(exerciseIds: List<String>) {
        val present = _state.value.items.map { it.exerciseId }.toSet()
        val fresh = exercises.getExercises(exerciseIds.distinct().filterNot { it in present })
        _state.update { s ->
            val room = RoutineLimits.MAX_EXERCISES - s.items.size
            val toAdd = fresh.take(room.coerceAtLeast(0)).map { e ->
                RoutineExercise(
                    id = ids.newId(), exerciseId = e.id, exerciseName = e.name,
                    // Cardio (time + distance) is usually one continuous block.
                    sets = if (e.tracking == com.fitnex.core.model.Tracking.TIME_DISTANCE) 1 else RoutineLimits.DEFAULT_SETS, reps = RoutineLimits.defaultTarget(e.tracking),
                    restSeconds = nearestRest(defaultRest), targetWeightKg = null,
                )
            }
            s.copy(
                items = s.items + toAdd, limitReached = fresh.size > room,
                trackings = s.trackings + fresh.associate { e -> e.id to e.tracking },
                weighted = s.weighted + fresh.filter(::showsWeight).map { e -> e.id },
                media = s.media + fresh.associate { e -> e.id to e.mediaUri },
            )
        }
    }

    private fun showsWeight(e: com.fitnex.core.model.Exercise): Boolean =
        e.tracking == com.fitnex.core.model.Tracking.WEIGHT_REPS || com.fitnex.core.model.TrackingRules.timedWithWeight(e.tracking, e.equipment)

    fun move(index: Int, delta: Int) = _state.update { s ->
        val target = index + delta
        if (index !in s.items.indices || target !in s.items.indices) return@update s
        val list = s.items.toMutableList()
        val item = list.removeAt(index)
        list.add(target, item)
        s.copy(items = list)
    }

    fun remove(index: Int) = _state.update { s ->
        if (index !in s.items.indices) s else s.copy(items = s.items.filterIndexed { i, _ -> i != index }, limitReached = false)
    }

    fun configure(index: Int, sets: Int, reps: Int, restSeconds: Int, weightKg: Float?) = _state.update { s ->
        if (index !in s.items.indices) return@update s
        s.copy(
            items = s.items.mapIndexed { i, e ->
                if (i != index) e else e.copy(
                    sets = sets.coerceIn(RoutineLimits.SETS), reps = RoutineLimits.clampTarget(s.trackingOf(e.exerciseId), reps),
                    restSeconds = nearestRest(restSeconds), targetWeightKg = weightKg?.takeIf { it > 0f },
                )
            },
        )
    }

    /**
     * "Duplicar rutina": saves a copy of what is on screen as a new routine ("Name (copia)", same
     * exercises and targets, no weekdays so it is not scheduled twice) and goes back to the list,
     * where the copy appears. The original stays as it was saved.
     */
    fun duplicate(suffix: String) {
        val s = _state.value
        if (s.saving) return
        if (s.name.isBlank() || s.items.isEmpty()) {
            _state.update { it.copy(showErrors = true) }
            return
        }
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val uid = session.currentUserId.filterNotNull().first()
            routines.save(uid, duplicateOf(s, suffix, ids.newId(), clock.instant()) { ids.newId() })
            _state.update { it.copy(saving = false, finished = true) }
        }
    }

    fun save() {
        val s = _state.value
        if (s.saving) return
        if (s.name.isBlank() || s.items.isEmpty()) {
            _state.update { it.copy(showErrors = true) }
            return
        }
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val uid = session.currentUserId.filterNotNull().first()
            routines.save(uid, Routine(routineId, s.name.trim(), s.days, s.isTemplate, s.items, createdAt))
            _state.update { it.copy(saving = false, finished = true) }
        }
    }

    fun delete() {
        val id = existingId ?: return
        viewModelScope.launch {
            routines.delete(id)
            _state.update { it.copy(finished = true) }
        }
    }

    companion object {
        const val PICKED_EXERCISES_KEY = "picked_exercise_ids"

        fun nearestRest(seconds: Int): Int = RoutineLimits.REST_SECONDS.minBy { kotlin.math.abs(it - seconds) }
    }
}
