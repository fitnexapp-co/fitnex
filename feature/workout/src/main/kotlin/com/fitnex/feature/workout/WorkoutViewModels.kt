package com.fitnex.feature.workout

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitnex.core.data.profile.UserProfileRepository
import com.fitnex.core.data.session.SessionRepository
import com.fitnex.core.data.training.ExerciseRepository
import com.fitnex.core.data.training.RoutineRepository
import com.fitnex.core.data.training.WorkoutSessionRepository
import com.fitnex.core.model.ActiveSession
import com.fitnex.core.model.Equipment
import com.fitnex.core.model.Exercise
import com.fitnex.core.model.ExerciseListItem
import com.fitnex.core.model.Muscle
import com.fitnex.core.model.Routine
import com.fitnex.feature.workout.library.ExerciseFilter
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private val SHARING = SharingStarted.WhileSubscribed(5_000)

data class WorkoutHomeUiState(
    val loading: Boolean = true,
    val routines: List<Routine> = emptyList(),
    val libraryCount: Int = 0,
    /** Unfinished workout (e.g. after the app was closed): offered as "Continuar". */
    val active: ActiveSession? = null,
    /** Today's planned routine (calendar or weekdays): one tap to start it. */
    val today: com.fitnex.core.data.today.TodayPlan? = null,
    /** Per routine id: main muscle groups, estimated minutes and the last time it was done. */
    val routineInfo: Map<String, RoutineInfo> = emptyMap(),
    /** Finished workouts this week (Monday–Sunday) and the weekly target. */
    val weekDone: Int = 0,
    val weekTarget: Int = 0,
) {
    /** The routine the hero card offers: today's, otherwise the first one. */
    val featured: Routine? get() = today?.routineId?.let { id -> routines.firstOrNull { it.id == id } } ?: routines.firstOrNull()
    val featuredIsToday: Boolean get() = featured != null && featured?.id == today?.routineId
}

data class RoutineInfo(
    val groups: List<com.fitnex.feature.workout.library.MuscleGroup>,
    val estimatedMinutes: Int,
    val lastDone: com.fitnex.core.model.CompletedSession?,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class WorkoutHomeViewModel @Inject constructor(
    session: SessionRepository,
    routines: RoutineRepository,
    exercises: ExerciseRepository,
    sessions: WorkoutSessionRepository,
    today: com.fitnex.core.data.today.TodayPlanRepository,
    stats: com.fitnex.core.data.stats.GlobalStatisticsRepository,
    preferences: com.fitnex.core.datastore.UserPreferencesDataSource,
    profiles: UserProfileRepository,
    clock: java.time.Clock,
) : ViewModel() {
    val state: StateFlow<WorkoutHomeUiState> = session.currentUserId.filterNotNull()
        .flatMapLatest { uid ->
            val base = combine(routines.observeRoutines(uid), exercises.observeLibrary(uid), sessions.observeActive(uid), today.observe(uid)) { r, lib, active, plan ->
                WorkoutHomeUiState(loading = false, routines = r, libraryCount = lib.size, active = active, today = plan) to lib
            }
            combine(base, stats.observeCompletedSessions(uid), preferences.preferences, profiles.observe(uid)) { (s, lib), done, prefs, profile ->
                val byId = lib.associateBy { it.exercise.id }
                val info = s.routines.associate { r ->
                    val exercisesOf = r.exercises.mapNotNull { byId[it.exerciseId]?.exercise }
                    r.id to RoutineInfo(
                        groups = RoutineEstimate.mainGroups(exercisesOf.map { it.primaryMuscleIds }),
                        estimatedMinutes = RoutineEstimate.minutes(r.exercises, exercisesOf.associate { it.id to it.tracking }),
                        lastDone = done.filter { it.routineName == r.name }.maxByOrNull { it.startedAt },
                    )
                }
                val target = prefs.weeklyGoal ?: profile?.availableDays?.size?.takeIf { it > 0 } ?: com.fitnex.core.model.AchievementsCalculator.DEFAULT_WEEKLY_TARGET
                val a = com.fitnex.core.model.AchievementsCalculator.compute(done, target, java.time.LocalDate.now(clock), clock.zone)
                s.copy(routineInfo = info, weekDone = a.weekDone, weekTarget = a.weekTarget)
            }
        }
        .stateIn(viewModelScope, SHARING, WorkoutHomeUiState())
}

/** Pure helpers for the home cards (tested in RoutineEstimateTest). */
object RoutineEstimate {
    /** Seconds of work per set when the target is a rep count. */
    private const val SECONDS_PER_REP_SET = 40
    private const val CHANGE_OVER_SECONDS = 60

    /**
     * Rough session length: work + rest between sets + a minute to change exercise. The target
     * means reps, seconds or minutes depending on how the exercise is logged.
     */
    fun minutes(items: List<com.fitnex.core.model.RoutineExercise>, trackings: Map<String, com.fitnex.core.model.Tracking>): Int {
        val seconds = items.sumOf { e ->
            val work = when (trackings[e.exerciseId]) {
                com.fitnex.core.model.Tracking.TIME -> e.reps
                com.fitnex.core.model.Tracking.TIME_DISTANCE -> e.reps * 60
                else -> SECONDS_PER_REP_SET
            }
            e.sets * work + (e.sets - 1).coerceAtLeast(0) * e.restSeconds + CHANGE_OVER_SECONDS
        }
        return if (items.isEmpty()) 0 else ((seconds + 299) / 300) * 5 // rounded up to 5 min
    }

    /** Up to three muscle groups, the most trained first (primary muscles only). */
    fun mainGroups(primaryByExercise: List<List<String>>): List<com.fitnex.feature.workout.library.MuscleGroup> =
        primaryByExercise.flatMap { ids ->
            ids.mapNotNull { id -> com.fitnex.feature.workout.library.MuscleGroup.entries.firstOrNull { id in it.muscleIds } }.distinct()
        }.groupingBy { it }.eachCount().entries
            .sortedWith(compareByDescending<Map.Entry<com.fitnex.feature.workout.library.MuscleGroup, Int>> { it.value }.thenBy { it.key.ordinal })
            .take(3).map { it.key }
}

data class LibraryUiState(
    val loading: Boolean = true,
    val pickMode: Boolean = false,
    val filter: ExerciseFilter = ExerciseFilter(),
    val results: List<ExerciseListItem> = emptyList(),
    val selected: List<String> = emptyList(),
    /** How many exercises of the whole library work each muscle group (shown on the group tiles). */
    val groupCounts: Map<com.fitnex.feature.workout.library.MuscleGroup, Int> = emptyMap(),
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LibraryViewModel @Inject constructor(
    savedState: SavedStateHandle,
    session: SessionRepository,
    exercises: ExerciseRepository,
    profiles: UserProfileRepository,
) : ViewModel() {
    private val pickMode: Boolean = savedState.get<Boolean>("pickMode") ?: false
    private val filter = MutableStateFlow(ExerciseFilter())
    /** Ordered: exercises are added to the routine in the order they were tapped. */
    private val selected = MutableStateFlow<List<String>>(emptyList())

    private val userId = session.currentUserId.filterNotNull()
    private val myEquipment = userId.flatMapLatest { uid -> profiles.observe(uid).map { it?.equipment ?: emptySet<Equipment>() } }

    val state: StateFlow<LibraryUiState> = userId
        .flatMapLatest { uid ->
            combine(exercises.observeLibrary(uid), exercises.observeMuscles(), myEquipment, filter, selected) { lib, muscles, eq, f, sel ->
                val byId = muscles.associateBy(Muscle::id)
                val counts = com.fitnex.feature.workout.library.MuscleGroup.entries.associateWith { g ->
                    lib.count { item -> item.exercise.primaryMuscleIds.any { it in g.muscleIds } }
                }
                LibraryUiState(false, pickMode, f, f.apply(lib, byId, eq), sel, counts)
            }
        }
        .stateIn(viewModelScope, SHARING, LibraryUiState(pickMode = pickMode))

    fun updateFilter(transform: (ExerciseFilter) -> ExerciseFilter) = filter.update(transform)
    fun clearFilters() = filter.update { ExerciseFilter(query = it.query) }

    fun toggleSelection(exerciseId: String) = selected.update { if (exerciseId in it) it - exerciseId else it + exerciseId }
}

data class ExerciseDetailUiState(
    val loading: Boolean = true,
    val exercise: Exercise? = null,
    val muscles: Map<String, Muscle> = emptyMap(),
    val isFavorite: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ExerciseDetailViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val session: SessionRepository,
    private val exercises: ExerciseRepository,
) : ViewModel() {
    private val exerciseId: String = checkNotNull(savedState.get<String>("exerciseId")) { "exerciseId argument missing" }

    val state: StateFlow<ExerciseDetailUiState> = session.currentUserId.filterNotNull()
        .flatMapLatest { uid ->
            combine(
                exercises.observeExercise(exerciseId),
                exercises.observeMuscles(),
                exercises.observeIsFavorite(uid, exerciseId),
            ) { e, m, fav -> ExerciseDetailUiState(false, e, m.associateBy(Muscle::id), fav) }
        }
        .stateIn(viewModelScope, SHARING, ExerciseDetailUiState())

    fun toggleFavorite() = viewModelScope.launch {
        val uid = session.currentUserId.filterNotNull().first()
        exercises.setFavorite(uid, exerciseId, !state.value.isFavorite)
    }

    /** Only the user's own exercises can be deleted (soft delete: past sets stay in the history). */
    fun deleteCustom(onDone: () -> Unit) = viewModelScope.launch {
        if (state.value.exercise?.isCustom != true) return@launch
        exercises.deleteCustom(exerciseId)
        onDone()
    }
}
