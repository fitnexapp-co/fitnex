package com.fitnex.feature.workout.live

import com.fitnex.core.model.ActiveSession
import com.fitnex.core.model.PreviousPerformance
import com.fitnex.core.model.RoutineExercise
import java.time.Duration
import java.time.Instant

/**
 * Rest countdown based on wall-clock end time (not on ticks), so it stays correct if the UI
 * stops updating for a while (screen off, slow device). Immutable: every action returns a copy.
 */
data class RestTimer(
    val totalSeconds: Int,
    /** When the rest ends; null while paused. */
    val endsAt: Instant?,
    /** Remaining seconds frozen at pause time; null while running. */
    val pausedRemaining: Long?,
) {
    val isPaused: Boolean get() = endsAt == null

    fun remaining(now: Instant): Long =
        pausedRemaining ?: Duration.between(now, endsAt!!).seconds.coerceAtLeast(0) +
            if (Duration.between(now, endsAt).toMillis() % 1000 > 0) 1 else 0

    fun isFinished(now: Instant): Boolean = !isPaused && !now.isBefore(endsAt!!)
    fun pause(now: Instant) = if (isPaused) this else copy(endsAt = null, pausedRemaining = remaining(now))
    fun resume(now: Instant) = if (!isPaused) this else copy(endsAt = now.plusSeconds(pausedRemaining ?: 0), pausedRemaining = null)

    /** +15 s / -15 s; never below zero, and the ring's total grows with it so it stays consistent. */
    fun add(seconds: Long, now: Instant): RestTimer {
        val newRemaining = (remaining(now) + seconds).coerceAtLeast(0)
        val total = maxOf(totalSeconds.toLong(), newRemaining).toInt()
        return if (isPaused) copy(totalSeconds = total, pausedRemaining = newRemaining)
        else copy(totalSeconds = total, endsAt = now.plusSeconds(newRemaining))
    }

    companion object {
        fun start(seconds: Int, now: Instant) = RestTimer(seconds, now.plusSeconds(seconds.toLong()), null)
    }
}

/**
 * Bests of one exercise from earlier workouts, shown while training it: heaviest weight, most reps
 * in a set and the estimated 1RM (Epley, sets of 1–12 reps). Null fields when they don't apply.
 */
data class ExerciseBests(val bestKg: Float?, val bestReps: Int?, val estimated1rm: Float?) {
    companion object {
        fun from(sets: List<com.fitnex.core.model.LoggedSet>): ExerciseBests? {
            if (sets.isEmpty()) return null
            val bestKg = sets.maxOfOrNull { it.weightKg }?.takeIf { it > 0f }
            val bestReps = sets.maxOfOrNull { it.reps }?.takeIf { it > 0 }
            val rm = sets.filter { it.weightKg > 0f && it.reps in 1..12 }.maxOfOrNull { it.weightKg * (1f + it.reps / 30f) }
            return ExerciseBests(bestKg, bestReps, rm).takeIf { bestKg != null || bestReps != null }
        }
    }
}

/** Values proposed for the next set of an exercise, so logging is usually a single tap. */
data class SetDraft(
    val reps: Int,
    val weightKg: Float,
    /** CAMERA when the reps came from the camera counter (the user can still change them). */
    val source: com.fitnex.core.model.SetSource = com.fitnex.core.model.SetSource.MANUAL,
    /** Timed and cardio sets. */
    val durationSeconds: Int? = null,
    val distanceMeters: Float? = null,
    /** Kilos added on top of last time because every set and rep was completed (progressive overload). */
    val increaseKg: Float? = null,
)

object DraftLogic {
    /**
     * Priority: 1) what you just did in this session (same exercise), 2) the same set number in
     * your last session of this exercise, 3) its last set, 4) the routine's targets (0 kg if none).
     */
    fun nextDraft(
        item: RoutineExercise, session: ActiveSession, previous: PreviousPerformance?,
        tracking: com.fitnex.core.model.Tracking = com.fitnex.core.model.Tracking.WEIGHT_REPS,
    ): SetDraft {
        fun from(s: com.fitnex.core.model.LoggedSet) = SetDraft(s.reps, s.weightKg, durationSeconds = s.durationSeconds, distanceMeters = s.distanceMeters)
        val done = session.setsFor(item.exerciseId)
        done.lastOrNull()?.let { return from(it) }
        val nextNumber = done.size + 1
        previous?.sets?.let { prev ->
            (prev.firstOrNull { it.setNumber == nextNumber } ?: prev.lastOrNull())?.let { base ->
                // Progressive overload: last time every planned set reached its reps → a little more weight.
                val step = overloadStep(item, prev, tracking)
                return if (step != null) from(base).copy(weightKg = base.weightKg + step, increaseKg = step) else from(base)
            }
        }
        // Routine targets: the reps field holds seconds for timed exercises and minutes for cardio.
        return when (tracking) {
            com.fitnex.core.model.Tracking.TIME -> SetDraft(0, item.targetWeightKg ?: 0f, durationSeconds = item.reps)
            com.fitnex.core.model.Tracking.TIME_DISTANCE -> SetDraft(0, 0f, durationSeconds = item.reps * 60, distanceMeters = 0f)
            else -> SetDraft(item.reps, item.targetWeightKg ?: 0f)
        }
    }

    /**
     * +2.5 kg (or +1 kg under 10 kg, e.g. dumbbell raises) when the last session of a weighted
     * exercise completed at least the planned sets, every one at or above the target reps.
     */
    fun overloadStep(item: RoutineExercise, previous: List<com.fitnex.core.model.LoggedSet>, tracking: com.fitnex.core.model.Tracking): Float? {
        if (tracking != com.fitnex.core.model.Tracking.WEIGHT_REPS || previous.isEmpty()) return null
        if (previous.size < item.sets || previous.any { it.weightKg <= 0f || it.reps < item.reps }) return null
        return if (previous.maxOf { it.weightKg } < 10f) 1f else 2.5f
    }
}

/** Personal records as they happen, against earlier workouts AND the sets already done today. */
object RecordLogic {
    fun recordFor(
        item: RoutineExercise, draft: SetDraft,
        doneThisSession: List<com.fitnex.core.model.LoggedSet>, history: List<com.fitnex.core.model.LoggedSet>,
    ): com.fitnex.core.model.PersonalRecord? {
        if (draft.durationSeconds != null) return null // timed sets: no weight or rep records
        val set = com.fitnex.core.model.LoggedSet(
            "new", "", item.exerciseId, item.exerciseName, 0, doneThisSession.size + 1, draft.reps, draft.weightKg,
            Instant.EPOCH, draft.source,
        )
        val before = history.filter { it.exerciseId == item.exerciseId } + doneThisSession.filter { it.exerciseId == item.exerciseId }
        val found = com.fitnex.core.model.TrainingMath.newRecords(listOf(set), before)
        return found.firstOrNull { it.type == com.fitnex.core.model.RecordType.MAX_WEIGHT }
            ?: found.firstOrNull { it.type == com.fitnex.core.model.RecordType.MAX_REPS }
            ?: found.firstOrNull()
    }
}

/** 754 s → "12:34", 3_725 s → "1:02:05". */
fun formatClock(totalSeconds: Long): String {
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
