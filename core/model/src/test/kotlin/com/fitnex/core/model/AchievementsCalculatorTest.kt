package com.fitnex.core.model

import java.time.Duration
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AchievementsCalculatorTest {
    private val zone = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 11, 20) // Friday

    private fun session(date: LocalDate, minutes: Long = 45, exercises: List<String> = listOf("push_up")) = CompletedSession(
        id = date.toString() + exercises.joinToString(), routineName = "R", startedAt = date.atTime(18, 0).toInstant(zone),
        duration = Duration.ofMinutes(minutes),
        sets = exercises.mapIndexed { i, e -> LoggedSet("$date-$i", "s", e, e, 0, i + 1, 10, 0f, date.atTime(18, i).toInstant(zone), SetSource.MANUAL) },
    )

    @Test
    fun weeklyGoalCountsMondayToSunday() {
        val a = AchievementsCalculator.compute(
            listOf(session(today), session(today.minusDays(4)), session(today.minusDays(5))), // Fri, Mon, previous Sun
            weeklyTarget = 3, today = today, zone = zone,
        )
        assertEquals(2, a.weekDone)
        assertEquals(2f / 3f, a.weekProgress, 0.001f)
        assertEquals("only this week's days", setOf(java.time.DayOfWeek.FRIDAY, java.time.DayOfWeek.MONDAY), a.trainedDays)
        assertEquals(java.time.DayOfWeek.FRIDAY, a.today)
    }

    @Test
    fun streakCountsConsecutiveWeeksMeetingTheGoal() {
        val sessions = (1..3).flatMap { w -> listOf(session(today.minusWeeks(w.toLong())), session(today.minusWeeks(w.toLong()).minusDays(1))) } +
            listOf(session(today.minusWeeks(5))) // a gap breaks the streak
        val a = AchievementsCalculator.compute(sessions, weeklyTarget = 2, today = today, zone = zone)
        assertEquals("this week not met yet: counts up to last week", 3, a.streakWeeks)
        val metNow = AchievementsCalculator.compute(sessions + session(today) + session(today.minusDays(1)), 2, today, zone)
        assertEquals(4, metNow.streakWeeks)
        assertTrue(metNow.badges.first { it.badge == Badge.STREAK_4_WEEKS }.unlocked)
    }

    @Test
    fun badgesFromRealSessionsOnly() {
        val none = AchievementsCalculator.compute(emptyList(), 3, today, zone)
        assertTrue(none.badges.none { it.unlocked })
        assertEquals(0, none.weekDone)

        val a = AchievementsCalculator.compute(listOf(session(today, exercises = listOf("a", "b", "c"))), 3, today, zone)
        assertTrue(a.badges.first { it.badge == Badge.FIRST_WORKOUT }.unlocked)
        assertFalse(a.badges.first { it.badge == Badge.WORKOUTS_10 }.unlocked)
        assertEquals(3, a.badges.first { it.badge == Badge.VARIETY_15 }.current)
        assertEquals("closest locked badge: 3 of 15 exercises", Badge.VARIETY_15, a.next?.badge)
    }
}
