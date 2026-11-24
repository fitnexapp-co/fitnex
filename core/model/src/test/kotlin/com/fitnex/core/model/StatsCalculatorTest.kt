package com.fitnex.core.model

import java.time.Duration
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StatsCalculatorTest {
    private val zone = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 11, 19) // Thursday

    private val muscles = listOf(
        Muscle("pectoralis_major", "Pectoral mayor", "", AnatomicalRegion.CHEST),
        Muscle("triceps_brachii", "Tríceps", "", AnatomicalRegion.ARMS),
        Muscle("quadriceps", "Cuádriceps", "", AnatomicalRegion.LEGS),
    ).associateBy { it.id }
    private val links = mapOf(
        "bench_press" to listOf(ExerciseMuscle("pectoralis_major", MuscleInvolvement.PRIMARY), ExerciseMuscle("triceps_brachii", MuscleInvolvement.SECONDARY)),
        "push_up" to listOf(ExerciseMuscle("pectoralis_major", MuscleInvolvement.PRIMARY)),
        "squat" to listOf(ExerciseMuscle("quadriceps", MuscleInvolvement.PRIMARY)),
    )

    private fun session(id: String, date: LocalDate, vararg sets: Triple<String, Int, Float>) = CompletedSession(
        id = id, routineName = "R-$id", startedAt = date.atTime(18, 0).toInstant(zone), duration = Duration.ofMinutes(45),
        sets = sets.mapIndexed { i, (ex, reps, kg) ->
            LoggedSet("$id-$i", id, ex, ex, 0, i + 1, reps, kg, date.atTime(18, i).toInstant(zone), SetSource.MANUAL)
        },
    )

    @Test
    fun rangesFollowCalendarPeriods() {
        assertEquals(DateRange(LocalDate.of(2026, 11, 16), LocalDate.of(2026, 11, 23)), StatsCalculator.range(StatsPeriod.WEEK, today, null))
        assertEquals(DateRange(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 12, 1)), StatsCalculator.range(StatsPeriod.MONTH, today, null))
        assertEquals(LocalDate.of(2026, 3, 2), StatsCalculator.range(StatsPeriod.ALL, today, LocalDate.of(2026, 3, 2)).start)
    }

    @Test
    fun weekStatsCountOnlyRealSessionsAndShowEveryDay() {
        val all = listOf(
            session("old", LocalDate.of(2026, 11, 2), Triple("squat", 5, 100f)), // previous week: excluded
            session("mon", LocalDate.of(2026, 11, 16), Triple("bench_press", 10, 25f), Triple("bench_press", 8, 30f)),
            session("wed", LocalDate.of(2026, 11, 18), Triple("push_up", 15, 0f)),
        )
        val s = StatsCalculator.compute(StatsPeriod.WEEK, today, zone, all, links, muscles)
        assertEquals(2, s.workouts)
        assertEquals(3, s.sets)
        assertEquals(33, s.reps)
        assertEquals(Duration.ofMinutes(90), s.duration)
        assertEquals(10 * 25f + 8 * 30f, s.volumeKg!!, 0.01f)
        assertEquals(2, s.activeDays)
        assertEquals(BucketUnit.DAY, s.bucketUnit)
        assertEquals(7, s.frequency.size) // empty days are shown, not hidden
        assertEquals(listOf(1, 0, 1, 0, 0, 0, 0), s.frequency.map { it.sessions })
        assertEquals(listOf("wed", "mon"), s.history.map { it.sessionId })
        assertTrue(s.hasAnyHistory)
    }

    @Test
    fun muscleLoadWeightsPrimaryAndSecondary() {
        val all = listOf(session("mon", LocalDate.of(2026, 11, 16), Triple("bench_press", 10, 25f), Triple("bench_press", 10, 25f)))
        val s = StatsCalculator.compute(StatsPeriod.WEEK, today, zone, all, links, muscles)
        val byId = s.muscles.associateBy { it.muscleId }
        assertEquals(2f, byId.getValue("pectoralis_major").weightedSets)
        assertEquals(1f, byId.getValue("triceps_brachii").weightedSets) // 2 sets × 0.5
        assertEquals(listOf(AnatomicalRegion.CHEST, AnatomicalRegion.ARMS), s.regions.map { it.region })
    }

    @Test
    fun regionCountsEachSetOnceAtStrongestInvolvement() {
        val arms = muscles + listOf(
            Muscle("biceps_brachii", "Bíceps", "", AnatomicalRegion.ARMS),
            Muscle("forearm", "Antebrazo", "", AnatomicalRegion.ARMS),
        ).associateBy { it.id }
        val curlLinks = links + ("hammer_curl" to listOf(
            ExerciseMuscle("biceps_brachii", MuscleInvolvement.PRIMARY),
            ExerciseMuscle("forearm", MuscleInvolvement.SECONDARY),
        ))
        val all = listOf(session("mon", LocalDate.of(2026, 11, 16), Triple("hammer_curl", 10, 12f), Triple("hammer_curl", 10, 12f), Triple("bench_press", 10, 25f)))
        val s = StatsCalculator.compute(StatsPeriod.WEEK, today, zone, all, curlLinks, arms)
        val byRegion = s.regions.associate { it.region to it.weightedSets }
        assertEquals(2.5f, byRegion.getValue(AnatomicalRegion.ARMS)) // 2 curls × 1 + bench's triceps 0.5; not 2 × 1.5 + 0.5
        assertEquals(1f, byRegion.getValue(AnatomicalRegion.CHEST))
        assertEquals(1f, s.muscles.single { it.muscleId == "forearm" }.weightedSets) // per-muscle load unchanged
    }

    @Test
    fun cardioAndTimedSetsAddUpTimeAndDistance() {
        val day = LocalDate.of(2026, 11, 18)
        val run = CompletedSession(
            "run", "Correr", day.atTime(8, 0).toInstant(zone), Duration.ofMinutes(35),
            listOf(
                LoggedSet("r1", "run", "squat", "Correr", 0, 1, 0, 0f, day.atTime(8, 30).toInstant(zone), SetSource.MANUAL, durationSeconds = 1800, distanceMeters = 5400f),
                LoggedSet("r2", "run", "push_up", "Plancha", 1, 1, 0, 0f, day.atTime(8, 35).toInstant(zone), SetSource.MANUAL, durationSeconds = 60),
            ),
        )
        val s = StatsCalculator.compute(StatsPeriod.WEEK, today, zone, listOf(run, session("mon", LocalDate.of(2026, 11, 16), Triple("bench_press", 10, 25f))), links, muscles)
        assertEquals(1860L, s.timedSeconds)
        assertEquals(5400f, s.distanceMeters, 0.01f)
        val strength = StatsCalculator.compute(StatsPeriod.WEEK, today, zone, listOf(session("mon", LocalDate.of(2026, 11, 16), Triple("bench_press", 10, 25f))), links, muscles)
        assertEquals("strength-only weeks show no cardio", 0L, strength.timedSeconds)
    }

    @Test
    fun bodyweightOnlyHasNoVolumeAndEmptyHistoryIsReported() {
        val bw = StatsCalculator.compute(StatsPeriod.WEEK, today, zone, listOf(session("w", LocalDate.of(2026, 11, 18), Triple("push_up", 20, 0f))), links, muscles)
        assertNull(bw.volumeKg)
        assertNull(bw.exercises.single().bestWeightKg)
        val empty = StatsCalculator.compute(StatsPeriod.MONTH, today, zone, emptyList(), links, muscles)
        assertFalse(empty.hasAnyHistory)
        assertEquals(0, empty.workouts)
        assertTrue(empty.frequency.all { it.sessions == 0 })
    }

    @Test
    fun exerciseStatsOnePointPerSessionOldestFirst() {
        val all = listOf(
            session("b", LocalDate.of(2026, 11, 18), Triple("bench_press", 8, 30f), Triple("bench_press", 6, 32.5f)),
            session("a", LocalDate.of(2026, 11, 11), Triple("bench_press", 10, 25f), Triple("squat", 5, 100f)),
        )
        val st = StatsCalculator.exerciseStats("bench_press", "Press", all, zone)
        assertEquals(listOf("a", "b"), st.points.map { it.sessionId })
        assertEquals(32.5f, st.bestWeightKg!!, 0.01f)
        assertEquals(10, st.bestReps)
        assertFalse(st.isBodyweight)
        assertEquals(1, st.points.first().sets.size) // squat sets not mixed in
    }

    @Test
    fun calendarStatusPriorities() {
        val plan = listOf(ScheduledWorkout("s", today, "r", "R", ScheduledStatus.SCHEDULED))
        assertEquals(DayStatus.COMPLETED, CalendarRules.status(today, today, true, plan, true))
        assertEquals(DayStatus.SCHEDULED, CalendarRules.status(today, today, false, plan, false))
        assertEquals(DayStatus.MISSED, CalendarRules.status(today, today.plusDays(1), false, plan, false))
        assertEquals(DayStatus.PLANNED_REST, CalendarRules.status(today, today, false, emptyList(), true))
        assertNull("no data is NOT rest", CalendarRules.status(today, today, false, emptyList(), false))
        val cancelled = listOf(plan.single().copy(status = ScheduledStatus.CANCELLED))
        assertNull(CalendarRules.status(today, today, false, cancelled, false))
    }
}
