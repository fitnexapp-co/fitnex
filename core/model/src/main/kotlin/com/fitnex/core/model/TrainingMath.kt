package com.fitnex.core.model

/**
 * Training formulas used by statistics and records. Pure functions, unit-tested.
 * All results derive ONLY from recorded sets — nothing is invented or extrapolated.
 */
object TrainingMath {
    /**
     * Epley estimated one-rep max: w × (1 + reps / 30). Only meaningful for loaded sets of
     * 1–12 reps; returns null otherwise (body weight or high-rep sets would be misleading).
     */
    fun estimatedOneRepMax(weightKg: Float, reps: Int): Float? =
        if (weightKg <= 0f || reps !in 1..12) null else if (reps == 1) weightKg else weightKg * (1f + reps / 30f)

    fun volume(sets: List<LoggedSet>): Float = sets.sumOf { (it.reps * it.weightKg).toDouble() }.toFloat()

    /**
     * New personal records in [session] compared with [history] (sets from EARLIER sessions only).
     * - Loaded exercises: max weight and best estimated 1RM.
     * - Body-weight exercises (all sets at 0 kg): max reps in a single set.
     * The very first time an exercise is done there is nothing to beat, so no record is claimed.
     */
    fun newRecords(session: List<LoggedSet>, history: List<LoggedSet>): List<PersonalRecord> =
        session.groupBy { it.exerciseId }.flatMap { (exerciseId, sets) ->
            val before = history.filter { it.exerciseId == exerciseId }
            if (before.isEmpty()) return@flatMap emptyList()
            val name = sets.first().exerciseName
            val loaded = sets.any { it.weightKg > 0f } || before.any { it.weightKg > 0f }
            buildList {
                if (loaded) {
                    val best = sets.maxOf { it.weightKg }
                    val prev = before.maxOf { it.weightKg }
                    if (best > prev) add(PersonalRecord(exerciseId, name, RecordType.MAX_WEIGHT, best, prev))
                    val e1rm = sets.mapNotNull { estimatedOneRepMax(it.weightKg, it.reps) }.maxOrNull()
                    val prevE1rm = before.mapNotNull { estimatedOneRepMax(it.weightKg, it.reps) }.maxOrNull()
                    // Only report 1RM when it is not just the same news as the weight record.
                    if (e1rm != null && prevE1rm != null && e1rm > prevE1rm && none { it.type == RecordType.MAX_WEIGHT }) {
                        add(PersonalRecord(exerciseId, name, RecordType.ESTIMATED_1RM, e1rm, prevE1rm))
                    }
                } else {
                    val best = sets.maxOf { it.reps }
                    val prev = before.maxOf { it.reps }
                    if (best > prev) add(PersonalRecord(exerciseId, name, RecordType.MAX_REPS, best.toFloat(), prev.toFloat()))
                }
            }
        }
}
