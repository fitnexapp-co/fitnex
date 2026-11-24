package com.fitnex.core.database

import com.fitnex.core.database.entity.ExerciseEntity
import com.fitnex.core.database.entity.ExerciseMuscleEntity
import com.fitnex.core.database.entity.ExerciseSetEntity
import com.fitnex.core.database.entity.MuscleEntity
import com.fitnex.core.database.entity.SyncMeta
import com.fitnex.core.database.entity.WorkoutEntity
import com.fitnex.core.database.entity.WorkoutSessionEntity
import com.fitnex.core.model.AnatomicalRegion
import com.fitnex.core.model.Difficulty
import com.fitnex.core.model.Equipment
import com.fitnex.core.model.ExerciseType
import com.fitnex.core.model.MuscleInvolvement
import com.fitnex.core.model.SessionStatus
import com.fitnex.core.model.SetSource
import java.time.Instant

internal object Fixtures {
    const val USER = "user-1"
    val T0: Instant = Instant.parse("2026-11-10T18:00:00Z")
    fun sync(at: Instant = T0) = SyncMeta(updatedAt = at)

    fun muscle(id: String, region: AnatomicalRegion) =
        MuscleEntity(id, id, "fn", region, model2dId = "2d_$id", model3dId = "3d_$id")

    fun exercise(id: String) = ExerciseEntity(
        id = id, name = id, description = "", instructions = listOf("step"), techniqueTips = emptyList(),
        commonMistakes = emptyList(), equipment = setOf(Equipment.BARBELL), type = ExerciseType.STRENGTH,
        difficulty = Difficulty.BEGINNER, mediaUri = null, cameraRepSupported = false, ownerUserId = null,
        sync = sync(),
    )

    fun link(exercise: String, muscle: String, involvement: MuscleInvolvement) =
        ExerciseMuscleEntity(exercise, muscle, involvement)

    fun workout(id: String, name: String) = WorkoutEntity(
        id = id, userId = USER, name = name, scheduledDays = emptySet(), isTemplate = false,
        aiRationale = null, createdAt = T0, sync = sync(),
    )

    fun session(
        id: String,
        workoutId: String?,
        status: SessionStatus = SessionStatus.FINISHED,
        startedAt: Instant = T0,
    ) = WorkoutSessionEntity(
        id = id, userId = USER, workoutId = workoutId, workoutNameSnapshot = "Push day",
        startedAt = startedAt, finishedAt = if (status == SessionStatus.FINISHED) startedAt.plusSeconds(3600) else null,
        durationSeconds = if (status == SessionStatus.FINISHED) 3600 else null, pausedSeconds = 0,
        status = status, currentExerciseIndex = 0, sync = sync(),
    )

    fun set(id: String, sessionId: String, exerciseId: String, n: Int, reps: Int, kg: Float, at: Instant = T0) =
        ExerciseSetEntity(
            id = id, sessionId = sessionId, exerciseId = exerciseId, exerciseNameSnapshot = exerciseId,
            exercisePosition = 0, setNumber = n, repetitions = reps, weightKg = kg, restSeconds = 90,
            source = SetSource.MANUAL, completedAt = at, sync = sync(at),
        )
}
