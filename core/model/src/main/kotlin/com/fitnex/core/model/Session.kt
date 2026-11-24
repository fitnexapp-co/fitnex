package com.fitnex.core.model

import java.time.Duration
import java.time.Instant

data class LoggedSet(
    val id: String,
    val sessionId: String,
    val exerciseId: String,
    val exerciseName: String,
    val exercisePosition: Int,
    val setNumber: Int,
    val reps: Int,
    val weightKg: Float,
    val completedAt: Instant,
    val source: SetSource,
    /** Timed sets (planks, stretches, cardio): seconds. Null for weight × reps sets. */
    val durationSeconds: Int? = null,
    /** Cardio distance in metres, when recorded. */
    val distanceMeters: Float? = null,
)

/** A workout in progress (or paused). Everything here is persisted, so it survives process death. */
data class ActiveSession(
    val id: String,
    val routineId: String?,
    val routineName: String,
    val startedAt: Instant,
    val pausedSeconds: Long,
    val pausedAt: Instant?,
    val currentExerciseIndex: Int,
    /** Snapshot of the routine at start: targets and rest per exercise. */
    val plan: List<RoutineExercise>,
    val sets: List<LoggedSet>,
) {
    val isPaused: Boolean get() = pausedAt != null

    /** Active training time: wall-clock since start minus every pause (including the current one). */
    fun elapsed(now: Instant): Duration {
        val running = pausedAt?.let { Duration.between(startedAt, it) } ?: Duration.between(startedAt, now)
        return running.minusSeconds(pausedSeconds).coerceAtLeast(Duration.ZERO)
    }

    fun setsFor(exerciseId: String): List<LoggedSet> = sets.filter { it.exerciseId == exerciseId }.sortedBy { it.setNumber }

    val plannedSetCount: Int get() = plan.sumOf { it.sets }
}

private fun Duration.coerceAtLeast(min: Duration): Duration = if (this < min) min else this

/** What the user did on an exercise the last time (shown as "Última sesión"). */
data class PreviousPerformance(val date: Instant, val sets: List<LoggedSet>)

enum class RecordType { MAX_WEIGHT, ESTIMATED_1RM, MAX_REPS }

/** A new personal best achieved in a session, compared only with sessions BEFORE it. */
data class PersonalRecord(
    val exerciseId: String,
    val exerciseName: String,
    val type: RecordType,
    val value: Float,
    val previous: Float?,
)

data class SessionSummary(
    val sessionId: String,
    val routineName: String,
    val duration: Duration,
    val exercisesCompleted: Int,
    val sets: Int,
    val totalReps: Int,
    /** Σ reps × kg for weighted sets; null when nothing had external load (body-weight sessions). */
    val volumeKg: Float?,
    val records: List<PersonalRecord>,
    /** Muscles with at least one set, primary first. */
    val musclesWorked: List<Muscle>,
    /** Seconds logged in timed sets (planks, stretches, cardio). */
    val timedSeconds: Int = 0,
    /** Cardio distance logged, in metres. */
    val distanceMeters: Float = 0f,
)
