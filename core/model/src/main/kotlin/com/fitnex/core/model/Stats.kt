package com.fitnex.core.model

import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/** Statistics periods (spec §20). */
enum class StatsPeriod { WEEK, MONTH, THREE_MONTHS, YEAR, ALL }

enum class BucketUnit { DAY, WEEK, MONTH }

data class DateRange(val start: LocalDate, val endExclusive: LocalDate) {
    operator fun contains(date: LocalDate): Boolean = !date.isBefore(start) && date.isBefore(endExclusive)
    val days: Long get() = ChronoUnit.DAYS.between(start, endExclusive)
}

/** A finished workout with its (non-deleted) sets: the only input statistics are built from. */
data class CompletedSession(
    val id: String,
    val routineName: String,
    val startedAt: Instant,
    val duration: Duration,
    val sets: List<LoggedSet>,
)

data class FrequencyBucket(val start: LocalDate, val sessions: Int)
data class RegionLoad(val region: AnatomicalRegion, val weightedSets: Float)
data class MuscleLoad(val muscleId: String, val weightedSets: Float, val sessions: Int)

data class ExerciseProgressItem(
    val exerciseId: String,
    val name: String,
    val sessions: Int,
    val lastDone: LocalDate,
    /** Null for body-weight-only exercises. */
    val bestWeightKg: Float?,
    val bestReps: Int,
)

data class SessionHistoryItem(
    val sessionId: String,
    val date: LocalDate,
    val routineName: String,
    val duration: Duration,
    val sets: Int,
    val exercises: Int,
)

data class GlobalStats(
    val period: StatsPeriod,
    val range: DateRange,
    val workouts: Int,
    val sets: Int,
    val reps: Int,
    val duration: Duration,
    /** Null when nothing in the period had external load. */
    val volumeKg: Float?,
    val activeDays: Int,
    val bucketUnit: BucketUnit,
    val frequency: List<FrequencyBucket>,
    val regions: List<RegionLoad>,
    val muscles: List<MuscleLoad>,
    val exercises: List<ExerciseProgressItem>,
    val history: List<SessionHistoryItem>,
    /** False when the user has never finished a workout (not just none in this period). */
    val hasAnyHistory: Boolean,
    /** Same figures for the period just before (previous week, month…); null for "Todo". */
    val previous: PeriodTotals? = null,
    /** Day of every workout in the range, to regroup the frequency chart by day, week or month. */
    val sessionDates: List<LocalDate> = emptyList(),
    /** Seconds logged in timed sets in the range (planks, stretches, cardio). */
    val timedSeconds: Long = 0,
    /** Cardio distance logged in the range, in metres. */
    val distanceMeters: Float = 0f,
)

/** Totals of one period, for the "↑ +2" comparisons. */
data class PeriodTotals(val workouts: Int, val sets: Int, val reps: Int, val duration: Duration, val volumeKg: Float)

data class ExerciseSessionPoint(
    val sessionId: String,
    val date: LocalDate,
    val sets: List<LoggedSet>,
    val bestWeightKg: Float,
    val bestEstimated1Rm: Float?,
    val volumeKg: Float,
    val totalReps: Int,
    val bestReps: Int,
)

data class ExerciseStats(
    val exerciseId: String,
    val name: String,
    val isBodyweight: Boolean,
    /** Oldest first, one point per session. */
    val points: List<ExerciseSessionPoint>,
) {
    val bestWeightKg: Float? get() = points.maxOfOrNull { it.bestWeightKg }?.takeIf { !isBodyweight }
    val bestEstimated1Rm: Float? get() = points.mapNotNull { it.bestEstimated1Rm }.maxOrNull()
    val bestReps: Int get() = points.maxOfOrNull { it.bestReps } ?: 0
}

/** Pure statistics engine: everything is derived from recorded sets — no estimates of steps or calories. */
object StatsCalculator {

    fun range(period: StatsPeriod, today: LocalDate, firstSession: LocalDate?): DateRange {
        val end = today.plusDays(1)
        return when (period) {
            StatsPeriod.WEEK -> DateRange(today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)), today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).plusDays(7))
            StatsPeriod.MONTH -> DateRange(today.withDayOfMonth(1), today.withDayOfMonth(1).plusMonths(1))
            StatsPeriod.THREE_MONTHS -> DateRange(today.minusMonths(3).plusDays(1), end)
            StatsPeriod.YEAR -> DateRange(today.minusYears(1).plusDays(1), end)
            StatsPeriod.ALL -> DateRange(firstSession ?: today, end)
        }
    }

    /** The period just before [range], same length (previous week, previous month…); null for ALL. */
    fun previousRange(period: StatsPeriod, range: DateRange): DateRange? = when (period) {
        StatsPeriod.WEEK -> DateRange(range.start.minusWeeks(1), range.start)
        StatsPeriod.MONTH -> DateRange(range.start.minusMonths(1), range.start)
        StatsPeriod.THREE_MONTHS, StatsPeriod.YEAR -> DateRange(range.start.minusDays(range.days), range.start)
        StatsPeriod.ALL -> null
    }

    fun bucketUnit(period: StatsPeriod, range: DateRange): BucketUnit = when (period) {
        StatsPeriod.WEEK -> BucketUnit.DAY
        StatsPeriod.MONTH, StatsPeriod.THREE_MONTHS -> BucketUnit.WEEK
        StatsPeriod.YEAR -> BucketUnit.MONTH
        StatsPeriod.ALL -> if (range.days <= 92) BucketUnit.WEEK else BucketUnit.MONTH
    }

    fun bucketStart(date: LocalDate, unit: BucketUnit): LocalDate = when (unit) {
        BucketUnit.DAY -> date
        BucketUnit.WEEK -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        BucketUnit.MONTH -> date.withDayOfMonth(1)
    }

    /** Every bucket in the range, including empty ones, so the chart's time axis is honest. */
    fun buckets(range: DateRange, unit: BucketUnit): List<LocalDate> {
        val out = mutableListOf<LocalDate>()
        var d = bucketStart(range.start, unit)
        while (d.isBefore(range.endExclusive)) {
            out += d
            d = when (unit) {
                BucketUnit.DAY -> d.plusDays(1)
                BucketUnit.WEEK -> d.plusWeeks(1)
                BucketUnit.MONTH -> d.plusMonths(1)
            }
        }
        return out
    }

    fun compute(
        period: StatsPeriod,
        today: LocalDate,
        zone: ZoneId,
        all: List<CompletedSession>,
        linksByExercise: Map<String, List<ExerciseMuscle>>,
        muscles: Map<String, Muscle>,
    ): GlobalStats {
        fun CompletedSession.date(): LocalDate = startedAt.atZone(zone).toLocalDate()
        val first = all.minOfOrNull { it.date() }
        val range = range(period, today, first)
        val inRange = all.filter { it.date() in range }
        val sets = inRange.flatMap { it.sets }
        val unit = bucketUnit(period, range)

        val perBucket = inRange.groupingBy { bucketStart(it.date(), unit) }.eachCount()
        val frequency = buckets(range, unit).map { FrequencyBucket(it, perBucket[it] ?: 0) }

        // Muscle load: primary sets count 1, secondary 0.5, stabilisers 0.25 (MuscleInvolvement.weight).
        val muscleWeights = mutableMapOf<String, Float>()
        val muscleSessions = mutableMapOf<String, MutableSet<String>>()
        // A region counts each set once, at its strongest involvement: a hammer curl (biceps 1 + forearm 0.5)
        // is one arm set, not 1.5. Summing per-muscle loads would inflate regions with many small muscles.
        val regionWeights = mutableMapOf<AnatomicalRegion, Float>()
        sets.forEach { s ->
            val links = linksByExercise[s.exerciseId].orEmpty()
            links.forEach { link ->
                muscleWeights[link.muscleId] = (muscleWeights[link.muscleId] ?: 0f) + link.involvement.weight
                muscleSessions.getOrPut(link.muscleId) { mutableSetOf() } += s.sessionId
            }
            links.mapNotNull { link -> muscles[link.muscleId]?.region?.let { it to link.involvement.weight } }
                .groupBy({ it.first }, { it.second })
                .forEach { (region, weights) -> regionWeights[region] = (regionWeights[region] ?: 0f) + weights.max() }
        }
        val muscleLoads = muscleWeights.map { (id, w) -> MuscleLoad(id, w, muscleSessions[id]?.size ?: 0) }.sortedByDescending { it.weightedSets }
        val regions = regionWeights.map { (region, w) -> RegionLoad(region, w) }.sortedByDescending { it.weightedSets }

        val exercises = sets.groupBy { it.exerciseId }.map { (id, list) ->
            val loaded = list.any { it.weightKg > 0f }
            ExerciseProgressItem(
                exerciseId = id,
                name = list.first().exerciseName,
                sessions = list.map { it.sessionId }.distinct().size,
                lastDone = list.maxOf { it.completedAt }.atZone(zone).toLocalDate(),
                bestWeightKg = if (loaded) list.maxOf { it.weightKg } else null,
                bestReps = list.maxOf { it.reps },
            )
        }.sortedByDescending { it.lastDone }

        return GlobalStats(
            period = period,
            range = range,
            workouts = inRange.size,
            sets = sets.size,
            reps = sets.sumOf { it.reps },
            duration = inRange.fold(Duration.ZERO) { acc, s -> acc + s.duration },
            volumeKg = if (sets.any { it.weightKg > 0f }) TrainingMath.volume(sets) else null,
            activeDays = inRange.map { it.date() }.distinct().size,
            bucketUnit = unit,
            frequency = frequency,
            regions = regions,
            muscles = muscleLoads,
            exercises = exercises,
            history = inRange.sortedByDescending { it.startedAt }.map {
                SessionHistoryItem(it.id, it.date(), it.routineName, it.duration, it.sets.size, it.sets.map { s -> s.exerciseId }.distinct().size)
            },
            hasAnyHistory = all.isNotEmpty(),
            previous = previousRange(period, range)?.let { prev ->
                val before = all.filter { it.date() in prev }
                val bs = before.flatMap { it.sets }
                PeriodTotals(
                    workouts = before.size, sets = bs.size, reps = bs.sumOf { it.reps },
                    duration = before.fold(Duration.ZERO) { acc, s -> acc + s.duration },
                    volumeKg = if (bs.any { it.weightKg > 0f }) TrainingMath.volume(bs) else 0f,
                )
            },
            sessionDates = inRange.map { it.date() }.sorted(),
            timedSeconds = sets.sumOf { (it.durationSeconds ?: 0).toLong() },
            distanceMeters = sets.sumOf { (it.distanceMeters ?: 0f).toDouble() }.toFloat(),
        )
    }

    fun exerciseStats(exerciseId: String, fallbackName: String, sessions: List<CompletedSession>, zone: ZoneId): ExerciseStats {
        val points = sessions.mapNotNull { session ->
            val sets = session.sets.filter { it.exerciseId == exerciseId }.sortedBy { it.setNumber }
            if (sets.isEmpty()) null else ExerciseSessionPoint(
                sessionId = session.id,
                date = session.startedAt.atZone(zone).toLocalDate(),
                sets = sets,
                bestWeightKg = sets.maxOf { it.weightKg },
                bestEstimated1Rm = sets.mapNotNull { TrainingMath.estimatedOneRepMax(it.weightKg, it.reps) }.maxOrNull(),
                volumeKg = TrainingMath.volume(sets),
                totalReps = sets.sumOf { it.reps },
                bestReps = sets.maxOf { it.reps },
            )
        }.sortedBy { it.date }
        val name = points.lastOrNull()?.sets?.first()?.exerciseName ?: fallbackName
        return ExerciseStats(exerciseId, name, isBodyweight = points.all { it.bestWeightKg == 0f }, points = points)
    }
}
