package com.fitnex.core.data.training

import com.fitnex.core.database.dao.CatalogDao
import com.fitnex.core.database.dao.WorkoutDao
import com.fitnex.core.database.entity.SyncMeta
import com.fitnex.core.database.entity.WorkoutEntity
import com.fitnex.core.database.entity.WorkoutExerciseEntity
import com.fitnex.core.database.relation.WorkoutWithExercises
import com.fitnex.core.model.Routine
import com.fitnex.core.model.RoutineExercise
import com.fitnex.core.model.RoutineLimits
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

interface RoutineRepository {
    fun observeRoutines(userId: String): Flow<List<Routine>>
    suspend fun getRoutine(id: String): Routine?
    suspend fun save(userId: String, routine: Routine)
    /** Soft delete: the routine disappears, but every session performed with it keeps its history. */
    suspend fun delete(id: String)
}

@Singleton
class RoomRoutineRepository @Inject constructor(
    private val workouts: WorkoutDao,
    private val catalog: CatalogDao,
    private val clock: Clock,
) : RoutineRepository {

    override fun observeRoutines(userId: String): Flow<List<Routine>> =
        combine(workouts.observeWorkouts(userId), catalog.observeExerciseNames()) { rows, names ->
            val nameById = names.associate { it.id to it.name }
            rows.map { it.toModel(nameById) }
        }

    override suspend fun getRoutine(id: String): Routine? {
        val row = workouts.observeWorkout(id).first() ?: return null
        val names = catalog.observeExerciseNames().first().associate { it.id to it.name }
        return row.toModel(names)
    }

    override suspend fun save(userId: String, routine: Routine) {
        require(routine.name.isNotBlank()) { "Routine needs a name" }
        require(routine.exercises.size in 1..RoutineLimits.MAX_EXERCISES) { "Routine needs 1..${RoutineLimits.MAX_EXERCISES} exercises" }
        val now = clock.instant()
        val sync = SyncMeta(updatedAt = now)
        // Editing an AI routine in the normal editor keeps its explanation.
        val rationale = routine.aiRationale ?: workouts.observeWorkout(routine.id).first()?.workout?.aiRationale
        workouts.saveWorkout(
            WorkoutEntity(
                id = routine.id, userId = userId, name = routine.name.trim(), scheduledDays = routine.days,
                isTemplate = routine.isTemplate, aiRationale = rationale, createdAt = routine.createdAt, sync = sync,
            ),
            routine.exercises.mapIndexed { index, e ->
                WorkoutExerciseEntity(
                    id = e.id, workoutId = routine.id, exerciseId = e.exerciseId, position = index,
                    targetSets = e.sets.coerceIn(RoutineLimits.SETS), targetReps = e.reps.coerceIn(RoutineLimits.REPS),
                    targetWeightKg = e.targetWeightKg, restSeconds = e.restSeconds, sync = sync,
                )
            },
        )
    }

    override suspend fun delete(id: String) = workouts.softDelete(id, clock.instant())
}

internal fun WorkoutWithExercises.toModel(names: Map<String, String>) = Routine(
    id = workout.id,
    name = workout.name,
    days = workout.scheduledDays,
    isTemplate = workout.isTemplate,
    exercises = exercises.sortedBy { it.position }.map {
        RoutineExercise(
            id = it.id, exerciseId = it.exerciseId, exerciseName = names[it.exerciseId] ?: it.exerciseId,
            sets = it.targetSets, reps = it.targetReps, restSeconds = it.restSeconds, targetWeightKg = it.targetWeightKg,
        )
    },
    createdAt = workout.createdAt,
    aiRationale = workout.aiRationale,
)
