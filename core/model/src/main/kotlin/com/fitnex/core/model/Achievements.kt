package com.fitnex.core.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/**
 * Goals and badges from REAL finished workouts only. Deliberately training-based (sessions, sets,
 * time, variety, consistency): nothing about body weight or calories, so they are safe for minors.
 */
enum class Badge(val target: Int) {
    FIRST_WORKOUT(1),
    WORKOUTS_10(10),
    WORKOUTS_50(50),
    WORKOUTS_100(100),
    STREAK_4_WEEKS(4),
    STREAK_12_WEEKS(12),
    SETS_500(500),
    HOURS_10(10),
    VARIETY_15(15),
}

data class BadgeProgress(val badge: Badge, val current: Int) {
    val unlocked: Boolean get() = current >= badge.target
    val fraction: Float get() = (current.toFloat() / badge.target).coerceIn(0f, 1f)
}

data class Achievements(
    /** Sessions this week (Monday–Sunday) and the user's weekly target. */
    val weekDone: Int,
    val weekTarget: Int,
    /** Consecutive weeks that met the target, ending this week (if already met) or last week. */
    val streakWeeks: Int,
    val badges: List<BadgeProgress>,
    /** Days of this week (Monday–Sunday) with at least one finished workout. */
    val trainedDays: Set<DayOfWeek> = emptySet(),
    val today: DayOfWeek = DayOfWeek.MONDAY,
    /** Daily streak (one rest day between workouts allowed). */
    val dayStreak: DayStreak = DayStreak(0, 0, StreakState.NONE, emptySet(), DayOfWeek.MONDAY),
) {
    val weekProgress: Float get() = if (weekTarget <= 0) 0f else (weekDone.toFloat() / weekTarget).coerceIn(0f, 1f)
    val unlocked: List<BadgeProgress> get() = badges.filter { it.unlocked }
    /** The locked badge closest to being earned: shown as the next goal. */
    val next: BadgeProgress? get() = badges.filterNot { it.unlocked }.maxByOrNull { it.fraction }
}

object AchievementsCalculator {
    const val DEFAULT_WEEKLY_TARGET = 3

    fun compute(sessions: List<CompletedSession>, weeklyTarget: Int, today: LocalDate, zone: ZoneId): Achievements {
        val target = weeklyTarget.coerceIn(1, 7)
        val weekOf = { d: LocalDate -> d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) }
        val perWeek = sessions.groupingBy { weekOf(it.startedAt.atZone(zone).toLocalDate()) }.eachCount()
        val thisWeek = weekOf(today)
        val weekDone = perWeek[thisWeek] ?: 0

        var streak = 0
        var week = if (weekDone >= target) thisWeek else thisWeek.minusWeeks(1)
        while ((perWeek[week] ?: 0) >= target) {
            streak++
            week = week.minusWeeks(1)
        }
        val bestStreak = longestStreak(perWeek, target).coerceAtLeast(streak)

        val totalSets = sessions.sumOf { it.sets.size }
        val hours = (sessions.sumOf { it.duration.seconds } / 3600).toInt()
        val variety = sessions.flatMap { s -> s.sets.map { it.exerciseId } }.toSet().size
        val count = sessions.size
        val current = mapOf(
            Badge.FIRST_WORKOUT to count, Badge.WORKOUTS_10 to count, Badge.WORKOUTS_50 to count, Badge.WORKOUTS_100 to count,
            Badge.STREAK_4_WEEKS to bestStreak, Badge.STREAK_12_WEEKS to bestStreak,
            Badge.SETS_500 to totalSets, Badge.HOURS_10 to hours, Badge.VARIETY_15 to variety,
        )
        val trainedDays = sessions.map { it.startedAt.atZone(zone).toLocalDate() }
            .filter { weekOf(it) == thisWeek }
            .map { it.dayOfWeek }.toSet()
        return Achievements(weekDone, target, streak, Badge.entries.map { BadgeProgress(it, current.getValue(it)) }, trainedDays, today.dayOfWeek,
            DayStreakCalculator.compute(sessions.map { it.startedAt.atZone(zone).toLocalDate() }, today),
        )
    }

    /** Badges for streaks are earned for good: the longest run ever counts. */
    private fun longestStreak(perWeek: Map<LocalDate, Int>, target: Int): Int {
        val weeks = perWeek.filterValues { it >= target }.keys.sorted()
        var best = 0
        var run = 0
        var prev: LocalDate? = null
        for (w in weeks) {
            run = if (prev != null && w == prev.plusWeeks(1)) run + 1 else 1
            best = maxOf(best, run)
            prev = w
        }
        return best
    }
}
