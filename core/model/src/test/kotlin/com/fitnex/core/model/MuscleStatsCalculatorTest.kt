package com.fitnex.core.model

import java.time.Duration
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MuscleStatsCalculatorTest {
    private val zone = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 11, 19) // Thursday
    private val links = mapOf(
        "bench_press" to listOf(ExerciseMuscle("pectoralis_major", MuscleInvolvement.PRIMARY), ExerciseMuscle("triceps_brachii", MuscleInvolvement.SECONDARY)),
        "squat" to listOf(ExerciseMuscle("quadriceps", MuscleInvolvement.PRIMARY)),
    )

    private fun session(id: String, date: LocalDate, vararg sets: Triple<String, Int, Float>) = CompletedSession(
        id, "R-$id", date.atTime(18, 0).toInstant(zone), Duration.ofMinutes(40),
        sets.mapIndexed { i, (ex, reps, kg) -> LoggedSet("$id-$i", id, ex, "Ex $ex", 0, i + 1, reps, kg, date.atTime(18, i).toInstant(zone), SetSource.MANUAL) },
    )

    private val all = listOf(
        session("a", LocalDate.of(2026, 11, 3), Triple("bench_press", 10, 50f), Triple("squat", 5, 100f)),
        session("b", LocalDate.of(2026, 11, 17), Triple("bench_press", 8, 60f), Triple("bench_press", 8, 60f)),
        session("legs", LocalDate.of(2026, 11, 18), Triple("squat", 5, 100f)),
    )

    @Test
    fun countsOnlySessionsAndSetsThatTrainedTheMuscle() {
        val s = MuscleStatsCalculator.compute("triceps_brachii", all, links, today, zone)
        assertEquals(2, s.sessions) // "legs" did not touch triceps
        assertEquals(3, s.totalSets)
        assertEquals(1.5f, s.totalWeightedSets, 0.001f) // secondary = 0.5 each
        assertEquals(LocalDate.of(2026, 11, 17), s.lastTrained)
        assertEquals(listOf("b", "a"), s.history.map { it.sessionId })
        assertEquals(listOf("Ex bench_press"), s.history.first().exerciseNames)
    }

    @Test
    fun weeksAreAlwaysTwelveWithEmptyOnesAndWeightedVolume() {
        val s = MuscleStatsCalculator.compute("pectoralis_major", all, links, today, zone)
        assertEquals(MuscleStatsCalculator.CHART_WEEKS, s.weeks.size)
        assertEquals(LocalDate.of(2026, 11, 16), s.weeks.last().start)
        assertEquals(2f, s.weeks.last().weightedSets)
        assertEquals(960f, s.weeks.last().volumeKg) // 2 × 8 × 60 × 1
        assertTrue(s.weeks.dropLast(3).all { it.weightedSets == 0f })
        assertEquals(2f / MuscleStatsCalculator.FREQUENCY_WEEKS, s.sessionsPerWeek)
    }

    @Test
    fun untrainedMuscleIsHonestlyEmpty() {
        val s = MuscleStatsCalculator.compute("calves", all, links, today, zone)
        assertEquals(0, s.sessions)
        assertNull(s.lastTrained)
        assertTrue(s.history.isEmpty())
        assertTrue(s.weeks.all { it.weightedSets == 0f })
    }

    @Test
    fun heatIsRelativeToTheMostTrainedMuscle() {
        val heat = MuscleStatsCalculator.heat(listOf(MuscleLoad("a", 4f, 2), MuscleLoad("b", 1f, 1), MuscleLoad("c", 0f, 0)))
        assertEquals(mapOf("a" to 1f, "b" to 0.25f), heat)
        assertTrue(MuscleStatsCalculator.heat(emptyList()).isEmpty())
    }
}
