package com.fitnex.core.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** One week of work on a muscle (Monday start). */
data class MuscleWeek(val start: LocalDate, val weightedSets: Float, val volumeKg: Float)

/** A finished session that trained the muscle, with only the sets that involved it. */
data class MuscleSessionEntry(
    val sessionId: String,
    val date: LocalDate,
    val routineName: String,
    val sets: Int,
    val weightedSets: Float,
    val exerciseNames: List<String>,
)

/** Spec §5 "Información de cada músculo": history, frequency, sessions and evolution — all from real sets. */
data class MuscleStats(
    val muscleId: String,
    val sessions: Int,
    val totalSets: Int,
    val totalWeightedSets: Float,
    val lastTrained: LocalDate?,
    /** Sessions per week over the last [MuscleStatsCalculator.FREQUENCY_WEEKS] weeks. */
    val sessionsPerWeek: Float,
    /** Oldest first, including empty weeks so the chart's time axis is honest. */
    val weeks: List<MuscleWeek>,
    /** Newest first. */
    val history: List<MuscleSessionEntry>,
)

object MuscleStatsCalculator {
    const val CHART_WEEKS = 12
    const val FREQUENCY_WEEKS = 4

    fun compute(
        muscleId: String,
        all: List<CompletedSession>,
        linksByExercise: Map<String, List<ExerciseMuscle>>,
        today: LocalDate,
        zone: ZoneId,
    ): MuscleStats {
        /** Involvement weight of this muscle in an exercise, or null if not involved. */
        fun weightOf(exerciseId: String): Float? =
            linksByExercise[exerciseId]?.firstOrNull { it.muscleId == muscleId }?.involvement?.weight

        val entries = all.mapNotNull { s ->
            val hits = s.sets.mapNotNull { set -> weightOf(set.exerciseId)?.let { set to it } }
            if (hits.isEmpty()) return@mapNotNull null
            Triple(s, s.startedAt.atZone(zone).toLocalDate(), hits)
        }

        val thisWeek = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val firstWeek = thisWeek.minusWeeks((CHART_WEEKS - 1).toLong())
        val weeks = (0 until CHART_WEEKS).map { i ->
            val start = firstWeek.plusWeeks(i.toLong())
            val inWeek = entries.filter { (_, d, _) -> !d.isBefore(start) && d.isBefore(start.plusWeeks(1)) }.flatMap { it.third }
            MuscleWeek(
                start = start,
                weightedSets = inWeek.sumOf { it.second.toDouble() }.toFloat(),
                volumeKg = inWeek.sumOf { (set, w) -> (set.reps * set.weightKg * w).toDouble() }.toFloat(),
            )
        }

        val frequencyStart = thisWeek.minusWeeks((FREQUENCY_WEEKS - 1).toLong())
        val recentSessions = entries.count { (_, d, _) -> !d.isBefore(frequencyStart) && !d.isAfter(today) }

        return MuscleStats(
            muscleId = muscleId,
            sessions = entries.size,
            totalSets = entries.sumOf { it.third.size },
            totalWeightedSets = entries.sumOf { e -> e.third.sumOf { it.second.toDouble() } }.toFloat(),
            lastTrained = entries.maxOfOrNull { it.second },
            sessionsPerWeek = recentSessions.toFloat() / FREQUENCY_WEEKS,
            weeks = weeks,
            history = entries.sortedByDescending { it.first.startedAt }.map { (s, d, hits) ->
                MuscleSessionEntry(
                    sessionId = s.id,
                    date = d,
                    routineName = s.routineName,
                    sets = hits.size,
                    weightedSets = hits.sumOf { it.second.toDouble() }.toFloat(),
                    exerciseNames = hits.map { it.first.exerciseName }.distinct(),
                )
            },
        )
    }

    /** Map heat 0..1 per muscle, relative to the most-trained muscle (0 = not trained → white). */
    fun heat(loads: List<MuscleLoad>): Map<String, Float> {
        val max = loads.maxOfOrNull { it.weightedSets } ?: return emptyMap()
        if (max <= 0f) return emptyMap()
        return loads.filter { it.weightedSets > 0f }.associate { it.muscleId to (it.weightedSets / max) }
    }
}
