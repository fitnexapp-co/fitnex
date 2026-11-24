package com.fitnex.core.data.ai

import com.fitnex.core.common.result.AppError
import com.fitnex.core.common.result.AppResult
import com.fitnex.core.common.time.IdGenerator
import com.fitnex.core.data.profile.UserProfileRepository
import com.fitnex.core.data.training.ExerciseRepository
import com.fitnex.core.data.training.RoutineRepository
import com.fitnex.core.model.AiFailure
import com.fitnex.core.model.Equipment
import com.fitnex.core.model.ExerciseListItem
import com.fitnex.core.model.ExperienceLevel
import com.fitnex.core.model.ProposedExercise
import com.fitnex.core.model.Routine
import com.fitnex.core.model.RoutineExercise
import com.fitnex.core.model.RoutineLimits
import com.fitnex.core.model.RoutineProposal
import com.fitnex.core.model.RoutineRequest
import com.fitnex.core.network.BuildConfig
import com.fitnex.core.network.CatalogExerciseDto
import com.fitnex.core.network.FitnexBackendApi
import com.fitnex.core.network.RoutineRequestDto
import com.fitnex.core.network.safeApiCall
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * "Creación inteligente de rutinas": FITNEX AI proposes a session using only catalogue exercises the
 * user can do with their equipment; the user reviews it and only then is it saved as a normal routine.
 */
@Singleton
class AiRoutineBuilder @Inject constructor(
    private val api: FitnexBackendApi,
    private val profiles: UserProfileRepository,
    private val exercises: ExerciseRepository,
    private val routines: RoutineRepository,
    private val ids: IdGenerator,
    private val clock: Clock,
    private val stats: com.fitnex.core.data.stats.GlobalStatisticsRepository,
) {
    suspend fun propose(userId: String, request: RoutineRequest): AiResult<RoutineProposal> {
        val profile = profiles.get(userId) ?: return AiResult.Failed(AiFailure.NOT_SIGNED_IN)
        if (!profiles.hasAiConsent(userId)) return AiResult.Failed(AiFailure.NO_CONSENT)
        if (BuildConfig.BACKEND_URL.contains(".invalid")) return AiResult.Failed(AiFailure.NOT_CONFIGURED)
        val library = exercises.observeLibrary(userId).first()
        val usable = usableExercises(library, profile.equipment)
        val names = usable.associate { it.exercise.id to it.exercise.name }
        val dto = RoutineRequestDto(
            goal = request.goal.name,
            sessionMinutes = request.sessionMinutes,
            daysPerWeek = profile.availableDays.size.coerceIn(1, 7),
            experience = (profile.experience ?: ExperienceLevel.BEGINNER).name,
            ageProfile = profile.ageProfile(LocalDate.now(clock)).name,
            focus = request.focus.trim().take(300),
            catalog = usable.map { item ->
                CatalogExerciseDto(item.exercise.id, item.exercise.name, item.primaryMuscles.map { it.name }, item.exercise.difficulty.name)
            },
        )
        return when (val r = safeApiCall { api.routine(dto) }) {
            is AppResult.Success -> {
                val picked = r.data.exercises.filter { it.exerciseId in names } // defence in depth
                if (picked.isEmpty()) {
                    AiResult.Failed(AiFailure.UNAVAILABLE)
                } else {
                    AiResult.Ok(
                        RoutineProposal(
                            name = r.data.name.take(RoutineLimits.NAME_MAX_LENGTH),
                            rationale = r.data.rationale,
                            exercises = picked.map {
                                ProposedExercise(it.exerciseId, names.getValue(it.exerciseId), it.sets, it.reps, it.restSeconds, it.note)
                            },
                        ),
                    )
                }
            }
            is AppResult.Failure -> AiResult.Failed(r.error.toAiFailure())
        }
    }

    /**
     * Weekly plan: one session per training day (2–6), adjusted to a plain summary of the last four
     * weeks of REAL training (sessions and best sets only).
     */
    suspend fun proposePlan(userId: String, request: RoutineRequest): AiResult<com.fitnex.core.model.RoutinePlanProposal> {
        val profile = profiles.get(userId) ?: return AiResult.Failed(AiFailure.NOT_SIGNED_IN)
        if (!profiles.hasAiConsent(userId)) return AiResult.Failed(AiFailure.NO_CONSENT)
        if (BuildConfig.BACKEND_URL.contains(".invalid")) return AiResult.Failed(AiFailure.NOT_CONFIGURED)
        val usable = usableExercises(exercises.observeLibrary(userId).first(), profile.equipment)
        val names = usable.associate { it.exercise.id to it.exercise.name }
        val days = profile.availableDays.size.coerceIn(2, 6)
        val dto = com.fitnex.core.network.PlanRequestDto(
            goal = request.goal.name, sessionMinutes = request.sessionMinutes, daysPerWeek = days,
            experience = (profile.experience ?: ExperienceLevel.BEGINNER).name,
            ageProfile = profile.ageProfile(LocalDate.now(clock)).name, focus = request.focus.trim().take(300),
            catalog = usable.map { CatalogExerciseDto(it.exercise.id, it.exercise.name, it.primaryMuscles.map { m -> m.name }, it.exercise.difficulty.name) },
            sessions = days, history = history(userId),
        )
        return when (val r = safeApiCall { api.plan(dto) }) {
            is AppResult.Success -> {
                val sessions = r.data.sessions.map { s ->
                    RoutineProposal(
                        name = s.name.take(RoutineLimits.NAME_MAX_LENGTH), rationale = r.data.rationale,
                        exercises = s.exercises.filter { it.exerciseId in names }.map { ProposedExercise(it.exerciseId, names.getValue(it.exerciseId), it.sets, it.reps, it.restSeconds, it.note) },
                    )
                }.filter { it.exercises.isNotEmpty() }
                if (sessions.size < 2) AiResult.Failed(AiFailure.UNAVAILABLE)
                else AiResult.Ok(com.fitnex.core.model.RoutinePlanProposal(r.data.name.take(RoutineLimits.NAME_MAX_LENGTH), r.data.rationale, sessions))
            }
            is AppResult.Failure -> AiResult.Failed(r.error.toAiFailure())
        }
    }

    /** Saves every session as its own routine, each on one of the user's training days (in order). */
    suspend fun savePlan(userId: String, plan: com.fitnex.core.model.RoutinePlanProposal): List<String> {
        val days = profiles.get(userId)?.availableDays.orEmpty().sorted()
        return plan.sessions.mapIndexed { i, s ->
            saveRoutine(userId, s.copy(rationale = plan.rationale), days.getOrNull(i)?.let { setOf(it) }.orEmpty())
        }
    }

    /** Last 4 weeks in a few words: how often, and the best set of the most frequent exercises. */
    private suspend fun history(userId: String): String {
        val repo = stats
        val since = clock.instant().minus(java.time.Duration.ofDays(28))
        val recent = repo.observeCompletedSessions(userId).first().filter { it.startedAt.isAfter(since) }
        if (recent.isEmpty()) return ""
        val byExercise = recent.flatMap { it.sets }.groupBy { it.exerciseName }
        val tops = byExercise.entries.sortedByDescending { it.value.size }.take(8).joinToString("; ") { (name, sets) ->
            val best = sets.maxWith(compareBy({ it.weightKg }, { it.reps }))
            if (best.weightKg > 0f) "$name: mejor ${best.reps}×${best.weightKg.toInt()} kg" else "$name: mejor ${best.reps} reps"
        }
        return "${recent.size} entrenamientos en 4 semanas. $tops".take(1100)
    }

    /** Saves an accepted proposal as an ordinary, editable routine (with its explanation). */
    suspend fun save(userId: String, proposal: RoutineProposal): String =
        saveRoutine(userId, proposal, profiles.get(userId)?.availableDays.orEmpty())

    private suspend fun saveRoutine(userId: String, proposal: RoutineProposal, days: Set<java.time.DayOfWeek>): String {
        val routine = Routine(
            id = ids.newId(),
            name = proposal.name.ifBlank { "Rutina FITNEX AI" },
            days = days,
            isTemplate = false,
            exercises = proposal.exercises.take(RoutineLimits.MAX_EXERCISES).map {
                RoutineExercise(
                    id = ids.newId(), exerciseId = it.exerciseId, exerciseName = it.exerciseName,
                    sets = it.sets.coerceIn(RoutineLimits.SETS), reps = it.reps.coerceIn(RoutineLimits.REPS),
                    restSeconds = RoutineLimits.REST_SECONDS.minBy { s -> kotlin.math.abs(s - it.restSeconds) },
                    targetWeightKg = null,
                )
            },
            createdAt = clock.instant(),
            aiRationale = proposal.rationale.ifBlank { null },
        )
        routines.save(userId, routine)
        return routine.id
    }

    companion object {
        /** Exercises whose required equipment the user has; with no equipment listed, body weight only. */
        /** The server accepts at most this many catalogue exercises per request. */
        const val AI_CATALOG_MAX = 80

        /**
         * Strength exercises the user can do with their equipment, balanced across muscles: with ~600
         * exercises the server's 80-exercise limit would otherwise only ever see the first few groups.
         * Round-robin by primary muscle; within a muscle, the classic photographed exercises first.
         */
        fun usableExercises(library: List<ExerciseListItem>, equipment: Set<Equipment>): List<ExerciseListItem> {
            val usable = library.filter {
                val e = it.exercise
                !e.isCustom && equipment.containsAll(e.requiredEquipment) &&
                    e.tracking in setOf(com.fitnex.core.model.Tracking.WEIGHT_REPS, com.fitnex.core.model.Tracking.REPS) &&
                    e.type !in setOf(com.fitnex.core.model.ExerciseType.MOBILITY, com.fitnex.core.model.ExerciseType.STRETCHING, com.fitnex.core.model.ExerciseType.CARDIO)
            }
            if (usable.size <= AI_CATALOG_MAX) return usable
            val queues = usable.groupBy { it.exercise.primaryMuscleIds.firstOrNull() ?: "" }
                .mapValues { (_, list) -> ArrayDeque(list.sortedWith(compareBy({ it.exercise.mediaUri.let { m -> m == null || m.endsWith(com.fitnex.core.data.catalog.CatalogSeeder.REFERENCE_SUFFIX) } }, { it.exercise.difficulty.ordinal }, { it.exercise.name }))) }
                .toSortedMap()
            val out = ArrayList<ExerciseListItem>(AI_CATALOG_MAX)
            while (out.size < AI_CATALOG_MAX && queues.values.any { it.isNotEmpty() }) {
                for (q in queues.values) { if (out.size < AI_CATALOG_MAX) q.removeFirstOrNull()?.let(out::add) }
            }
            return out
        }
    }
}

internal fun AppError.toAiFailure(): AiFailure = when (this) {
    AppError.NoConnection -> AiFailure.NO_CONNECTION
    is AppError.RateLimited -> AiFailure.RATE_LIMITED
    AppError.Unauthorized -> AiFailure.NOT_SIGNED_IN
    else -> AiFailure.UNAVAILABLE
}
