package com.fitnex.feature.progress

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitnex.core.data.session.SessionRepository
import com.fitnex.core.data.stats.GlobalStatisticsRepository
import com.fitnex.core.data.training.ExerciseRepository
import com.fitnex.core.model.Exercise
import com.fitnex.core.model.ExerciseStats
import com.fitnex.core.model.GlobalStats
import com.fitnex.core.model.Muscle
import com.fitnex.core.model.MuscleInvolvement
import com.fitnex.core.model.MuscleStats
import com.fitnex.core.model.MuscleStatsCalculator
import com.fitnex.core.model.StatsPeriod
import java.time.Clock
import java.time.LocalDate
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ProgressUiState(
    val loading: Boolean = true,
    val period: StatsPeriod = StatsPeriod.MONTH,
    val stats: GlobalStats? = null,
    /** Catalog muscles by id: names and functions for the muscle map. */
    val muscles: Map<String, Muscle> = emptyMap(),
    /** Weekly goal, streak and badges (real finished workouts only). */
    val achievements: com.fitnex.core.model.Achievements? = null,
    /** All-time summary for the hero cards at the top ("Tu experiencia FITNEX"). */
    val experience: Experience? = null,
    /** Body measurements, newest first; null = not offered (minors or unknown age). */
    val measurements: List<com.fitnex.core.data.body.BodyMeasurement>? = null,
) {
    /** Map colouring for the period: 0..1 relative to the most-trained muscle. */
    val heat: Map<String, Float> get() = stats?.let { MuscleStatsCalculator.heat(it.muscles) }.orEmpty()
}

/** Lifetime figures shown on the hero cards, from finished workouts only. */
data class Experience(
    val name: String,
    val minutes: Long,
    val workouts: Int,
    val sets: Int,
    val memberSince: java.time.LocalDate?,
    val lastWorkout: java.time.LocalDate?,
    val year: Int,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ProgressViewModel @Inject constructor(
    private val session: SessionRepository,
    stats: GlobalStatisticsRepository,
    exercises: ExerciseRepository,
    private val savedState: SavedStateHandle,
    private val preferences: com.fitnex.core.datastore.UserPreferencesDataSource,
    profiles: com.fitnex.core.data.profile.UserProfileRepository,
    private val clock: Clock,
    private val body: com.fitnex.core.data.body.BodyMeasurementRepository,
) : ViewModel() {
    private val period = MutableStateFlow(
        savedState.get<String>(KEY_PERIOD)?.let { runCatching { StatsPeriod.valueOf(it) }.getOrNull() } ?: StatsPeriod.MONTH,
    )

    val state: StateFlow<ProgressUiState> = combine(session.currentUserId.filterNotNull(), period, ::Pair)
        .flatMapLatest { (uid, p) ->
            combine(
                stats.observeStats(uid, p), exercises.observeMuscles(), stats.observeCompletedSessions(uid),
                preferences.preferences, profiles.observe(uid),
            ) { s, m, done, prefs, profile ->
                val target = prefs.weeklyGoal ?: profile?.availableDays?.size?.takeIf { it > 0 } ?: com.fitnex.core.model.AchievementsCalculator.DEFAULT_WEEKLY_TARGET
                val achievements = com.fitnex.core.model.AchievementsCalculator.compute(done, target, java.time.LocalDate.now(clock), clock.zone)
                val experience = Experience(
                    name = profile?.name.orEmpty(),
                    // Add the durations first, then round down once: the same total as the "Tiempo"
                    // tile (summing each workout's whole minutes lost up to a minute per workout).
                    minutes = done.fold(java.time.Duration.ZERO) { total, s -> total + s.duration }.toMinutes(),
                    workouts = done.size,
                    sets = done.sumOf { it.sets.size },
                    memberSince = profile?.createdAt?.atZone(clock.zone)?.toLocalDate(),
                    lastWorkout = done.maxOfOrNull { it.startedAt }?.atZone(clock.zone)?.toLocalDate(),
                    year = java.time.LocalDate.now(clock).year,
                )
                ProgressUiState(false, p, s, m.associateBy { it.id }, achievements, experience) to (profile?.ageProfile(java.time.LocalDate.now(clock))?.showCalorieEstimates == true)
            }.combine(body.observe(uid)) { (st, adult), list -> st.copy(measurements = list.takeIf { adult }) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProgressUiState(period = period.value))

    fun setWeeklyGoal(sessions: Int) = viewModelScope.launch { preferences.setWeeklyGoal(sessions) }

    fun addMeasurement(m: com.fitnex.core.data.body.BodyMeasurement) = viewModelScope.launch {
        session.currentUserId.filterNotNull().first().let { body.add(it, m) }
    }

    fun deleteMeasurement(id: String) = viewModelScope.launch { body.delete(id) }

    fun setPeriod(p: StatsPeriod) {
        period.value = p
        savedState[KEY_PERIOD] = p.name
    }

    private companion object { const val KEY_PERIOD = "progress_period" }
}

data class MuscleDetailUiState(
    val loading: Boolean = true,
    val muscle: Muscle? = null,
    val primaryExercises: List<Exercise> = emptyList(),
    val secondaryExercises: List<Exercise> = emptyList(),
    val stats: MuscleStats? = null,
    /** Other muscles of the same group, for moving between them. */
    val sameRegion: List<Muscle> = emptyList(),
)

/** Spec §5 "Información de cada músculo": catalog data + statistics from the user's real sets. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MuscleDetailViewModel @Inject constructor(
    savedState: SavedStateHandle,
    session: SessionRepository,
    stats: GlobalStatisticsRepository,
    exercises: ExerciseRepository,
    clock: Clock,
) : ViewModel() {
    private val muscleId: String = checkNotNull(savedState.get<String>("muscleId"))

    val state: StateFlow<MuscleDetailUiState> = session.currentUserId.filterNotNull()
        .flatMapLatest { uid ->
            combine(exercises.observeMuscles(), exercises.observeLibrary(uid), stats.observeCompletedSessions(uid)) { muscles, library, done ->
                val muscle = muscles.firstOrNull { it.id == muscleId }
                val all = library.map { it.exercise }
                fun involvement(e: Exercise) = e.muscles.firstOrNull { it.muscleId == muscleId }?.involvement
                MuscleDetailUiState(
                    loading = false,
                    muscle = muscle,
                    primaryExercises = all.filter { involvement(it) == MuscleInvolvement.PRIMARY }.sortedBy { it.name },
                    secondaryExercises = all.filter { involvement(it).let { i -> i != null && i != MuscleInvolvement.PRIMARY } }.sortedBy { it.name },
                    stats = MuscleStatsCalculator.compute(
                        muscleId = muscleId,
                        all = done,
                        linksByExercise = all.associate { it.id to it.muscles },
                        today = LocalDate.now(clock),
                        zone = clock.zone,
                    ),
                    sameRegion = muscle?.let { m -> muscles.filter { it.region == m.region && it.id != m.id } }.orEmpty(),
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MuscleDetailUiState())
}

enum class ExerciseMetric { WEIGHT, ONE_RM, VOLUME, REPS }

data class ExerciseStatsUiState(
    val loading: Boolean = true,
    val stats: ExerciseStats? = null,
    val metric: ExerciseMetric = ExerciseMetric.WEIGHT,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ExerciseStatsViewModel @Inject constructor(
    savedState: SavedStateHandle,
    session: SessionRepository,
    stats: GlobalStatisticsRepository,
) : ViewModel() {
    private val exerciseId: String = checkNotNull(savedState.get<String>("exerciseId"))
    private val metric = MutableStateFlow<ExerciseMetric?>(null)

    val state: StateFlow<ExerciseStatsUiState> = session.currentUserId.filterNotNull()
        .flatMapLatest { uid -> stats.observeExerciseStats(uid, exerciseId) }
        .let { flow -> combine(flow, metric) { s, m -> ExerciseStatsUiState(false, s, m ?: defaultMetric(s)) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExerciseStatsUiState())

    fun setMetric(m: ExerciseMetric) { metric.value = m }

    companion object {
        /** Body-weight exercises have no load: show reps by default. */
        fun defaultMetric(s: ExerciseStats) = if (s.isBodyweight) ExerciseMetric.REPS else ExerciseMetric.WEIGHT

        fun metricsFor(s: ExerciseStats): List<ExerciseMetric> =
            if (s.isBodyweight) listOf(ExerciseMetric.REPS)
            else listOfNotNull(
                ExerciseMetric.WEIGHT,
                ExerciseMetric.ONE_RM.takeIf { s.bestEstimated1Rm != null },
                ExerciseMetric.VOLUME,
                ExerciseMetric.REPS,
            )
    }
}
