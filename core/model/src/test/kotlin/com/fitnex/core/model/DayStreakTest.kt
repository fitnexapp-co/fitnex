package com.fitnex.core.model

import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class DayStreakTest {
    private val today = LocalDate.of(2026, 11, 20) // Friday
    private fun ago(n: Long) = today.minusDays(n)

    @Test
    fun noWorkoutsMeansNoStreak() {
        val s = DayStreakCalculator.compute(emptyList(), today)
        assertEquals(0, s.current)
        assertEquals(StreakState.NONE, s.state)
    }

    @Test
    fun oneRestDayBetweenWorkoutsKeepsTheStreakAndTwoEndIt() {
        // Trained: 9, 8, 6 (one rest), 5, 3 (one rest) days ago, then today → one chain of 6.
        val s = DayStreakCalculator.compute(listOf(ago(9), ago(8), ago(6), ago(5), ago(3), ago(2), today), today)
        assertEquals(7, s.current)
        assertEquals(StreakState.FED_TODAY, s.state)

        // Two rest days in a row (12 → 9: rest on 11 and 10) start a new chain.
        val broken = DayStreakCalculator.compute(listOf(ago(13), ago(12), ago(9)), today.minusDays(8))
        assertEquals("chain after the break: 1 day", 1, broken.current)
        assertEquals("longest ever kept", 2, broken.best)
    }

    @Test
    fun stateTellsWhenTheStreakIsAtRisk() {
        assertEquals(StreakState.CAN_GROW, DayStreakCalculator.compute(listOf(ago(1)), today).state)
        val last = DayStreakCalculator.compute(listOf(ago(3), ago(2)), today)
        assertEquals(StreakState.LAST_DAY, last.state)
        assertEquals("still alive with one rest day", 2, last.current)
        val gone = DayStreakCalculator.compute(listOf(ago(4), ago(3)), today)
        assertEquals(StreakState.NONE, gone.state)
        assertEquals(0, gone.current)
        assertEquals(2, gone.best)
    }

    @Test
    fun weekShowsOnlyThisWeeksTrainingDaysAndDuplicatesCountOnce() {
        val s = DayStreakCalculator.compute(listOf(ago(4), ago(4), ago(2), ago(5)), today) // Mon, Mon, Wed, previous Sun
        assertEquals(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY), s.weekTrained)
        assertEquals(DayOfWeek.FRIDAY, s.today)
        assertEquals("Sun, Mon, Wed (one rest between each)", 3, s.current)
    }
}
