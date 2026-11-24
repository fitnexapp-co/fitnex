package com.fitnex.core.model

import java.time.DayOfWeek
import java.time.Instant

/** A muscle as drawn on the 2D/3D maps. [id] is the shared key for SVG paths and GLB nodes. */
data class Muscle(
    val id: String,
    val name: String,
    val function: String,
    val region: AnatomicalRegion,
)

data class ExerciseMuscle(val muscleId: String, val involvement: MuscleInvolvement)

data class Exercise(
    val id: String,
    val name: String,
    val description: String,
    val instructions: List<String>,
    val techniqueTips: List<String>,
    val commonMistakes: List<String>,
    val equipment: Set<Equipment>,
    val type: ExerciseType,
    val difficulty: Difficulty,
    /** Licensed image/animation; null until media assets are added (never a fake picture). */
    val mediaUri: String?,
    val cameraRepSupported: Boolean,
    val isCustom: Boolean,
    val muscles: List<ExerciseMuscle>,
    /** How sets are logged (weight × reps, reps, time, time + distance). */
    val tracking: Tracking = Tracking.WEIGHT_REPS,
    /** Other names it is searched by: English name, common names, tags. */
    val aliases: List<String> = emptyList(),
) {
    val primaryMuscleIds: List<String> get() = muscles.filter { it.involvement == MuscleInvolvement.PRIMARY }.map { it.muscleId }
    val secondaryMuscleIds: List<String> get() = muscles.filter { it.involvement != MuscleInvolvement.PRIMARY }.map { it.muscleId }

    /** Equipment that must be available; body weight needs nothing. */
    val requiredEquipment: Set<Equipment> get() = equipment - Equipment.BODYWEIGHT
}

/** Library row: the exercise plus what the list needs to render it. */
data class ExerciseListItem(
    val exercise: Exercise,
    val primaryMuscles: List<Muscle>,
    val isFavorite: Boolean,
)

data class RoutineExercise(
    /** Stable row id (kept across edits so sync sees updates, not delete+insert). */
    val id: String,
    val exerciseId: String,
    val exerciseName: String,
    val sets: Int,
    val reps: Int,
    val restSeconds: Int,
    val targetWeightKg: Float?,
)

data class Routine(
    val id: String,
    val name: String,
    val days: Set<DayOfWeek>,
    val isTemplate: Boolean,
    val exercises: List<RoutineExercise>,
    val createdAt: Instant,
    /** Set when FITNEX AI designed the routine: its explanation, shown to the user. */
    val aiRationale: String? = null,
)

object RoutineLimits {
    val SETS = 1..10
    val REPS = 1..50
    /** 15 s steps from 15 s to 5 min. */
    val REST_SECONDS = (15..300 step 15).toList()
    const val NAME_MAX_LENGTH = 40
    const val MAX_EXERCISES = 20
    const val DEFAULT_SETS = 3
    const val DEFAULT_REPS = 10

    /** Timed exercises keep their target in the routine's reps field as SECONDS (5 s steps, up to 10 min). */
    val TIME_SECONDS = (5..600 step 5).toList()
    /** Cardio keeps its target in the reps field as MINUTES. */
    val CARDIO_MINUTES = (1..180).toList()

    fun targetOptions(tracking: Tracking): List<Int> = when (tracking) {
        Tracking.TIME -> TIME_SECONDS
        Tracking.TIME_DISTANCE -> CARDIO_MINUTES
        else -> REPS.toList()
    }

    fun defaultTarget(tracking: Tracking): Int = when (tracking) {
        Tracking.TIME -> 30
        Tracking.TIME_DISTANCE -> 20
        else -> DEFAULT_REPS
    }

    /** Nearest valid target for the tracking mode. */
    fun clampTarget(tracking: Tracking, value: Int): Int = targetOptions(tracking).minBy { kotlin.math.abs(it - value) }
}
