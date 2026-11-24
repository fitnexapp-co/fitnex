package com.fitnex.core.model

import kotlinx.serialization.Serializable

/**
 * Domain enums shared by database, network and UI layers.
 * Persisted by NAME: never rename a constant without a Room migration.
 */

@Serializable
enum class ExperienceLevel { BEGINNER, INTERMEDIATE, ADVANCED }

@Serializable
enum class ActivityLevel { SEDENTARY, LIGHT, MODERATE, ACTIVE, VERY_ACTIVE }

@Serializable
enum class Difficulty { BEGINNER, INTERMEDIATE, ADVANCED }

@Serializable
enum class Equipment {
    BODYWEIGHT, DUMBBELL, BARBELL, KETTLEBELL, MACHINE, CABLE, RESISTANCE_BAND, BENCH, PULL_UP_BAR, OTHER,
    EZ_BAR, DIP_BARS, RACK, MEDICINE_BALL, ROPE, SLED, SANDBAG, CARDIO_MACHINE,
}

@Serializable
enum class ExerciseType { STRENGTH, HYPERTROPHY, CARDIO, MOBILITY, CALISTHENICS, FUNCTIONAL, STRETCHING }

/**
 * How a set of the exercise is logged: weight × reps (most strength work), reps only (body weight),
 * time (planks, holds, stretches; with weight when the exercise uses a load) or time and distance (cardio).
 */
@Serializable
enum class Tracking { WEIGHT_REPS, REPS, TIME, TIME_DISTANCE }

object TrackingRules {
    /** A user-created exercise is logged the way its type suggests. */
    fun forCustom(type: ExerciseType, equipment: Set<Equipment>): Tracking = when {
        type == ExerciseType.CARDIO -> Tracking.TIME_DISTANCE
        type == ExerciseType.STRETCHING || type == ExerciseType.MOBILITY -> Tracking.TIME
        (equipment - Equipment.BODYWEIGHT).isEmpty() -> Tracking.REPS
        else -> Tracking.WEIGHT_REPS
    }

    /** Timed sets ask for a weight only when the exercise carries a load (farmer's walk, weighted plank). */
    fun timedWithWeight(tracking: Tracking, equipment: Set<Equipment>): Boolean =
        tracking == Tracking.TIME && equipment.any { it in LOADS }

    private val LOADS = setOf(Equipment.DUMBBELL, Equipment.BARBELL, Equipment.KETTLEBELL, Equipment.SANDBAG, Equipment.EZ_BAR, Equipment.MEDICINE_BALL)
}

@Serializable
enum class AnatomicalRegion { CHEST, BACK, SHOULDERS, ARMS, CORE, LEGS, GLUTES, NECK }

/** How much a muscle participates in an exercise. [weight] feeds muscle-map statistics. */
@Serializable
enum class MuscleInvolvement(val weight: Float) { PRIMARY(1.0f), SECONDARY(0.5f), STABILIZER(0.25f) }

@Serializable
enum class MealType { BREAKFAST, LUNCH, DINNER, SNACK, DRINK }

@Serializable
enum class AnalysisStatus { NOT_REQUESTED, PENDING, COMPLETED, NEEDS_MORE_INFO, FAILED, MANUAL }

@Serializable
enum class ScheduledStatus { SCHEDULED, COMPLETED, CANCELLED, MISSED }

@Serializable
enum class AiRole { USER, ASSISTANT, SYSTEM }

/** IN_PROGRESS sessions are restored after a crash or process death (spec §18). */
@Serializable
enum class SessionStatus { IN_PROGRESS, PAUSED, FINISHED, ABANDONED }

/** Where a set's repetition count came from. Camera counts are always user-editable. */
@Serializable
enum class SetSource { MANUAL, CAMERA }

/** Local-first sync lifecycle of every user-owned row. */
enum class SyncStatus { PENDING_UPSERT, PENDING_DELETE, SYNCED }
