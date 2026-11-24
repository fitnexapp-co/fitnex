package com.fitnex.core.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/**
 * Daily training streak, from REAL finished workouts. Rest is part of training: one rest day
 * between workouts keeps the streak alive; two rest days in a row end it. The number counts the
 * training days in the chain (rest days keep it, they don't add to it).
 */
data class DayStreak(
    /** Training days in the current chain; 0 when there is no live streak. */
    val current: Int,
    /** Longest chain ever. */
    val best: Int,
    val state: StreakState,
    /** Days of this week (Monday–Sunday) with a workout. */
    val weekTrained: Set<DayOfWeek>,
    val today: DayOfWeek,
)

enum class StreakState {
    /** No live streak: the next workout starts one. */
    NONE,
    /** Trained today: the streak is fed. */
    FED_TODAY,
    /** Trained yesterday: today is a free rest day, or a chance to grow it. */
    CAN_GROW,
    /** Rested yesterday: train today or the streak ends tomorrow. */
    LAST_DAY,
}

object DayStreakCalculator {
    /** At most this many days between two workouts (1 = consecutive, 2 = one rest day between). */
    const val MAX_GAP_DAYS = 2L

    fun compute(trainingDays: Collection<LocalDate>, today: LocalDate): DayStreak {
        val days = trainingDays.filter { !it.isAfter(today) }.toSortedSet().toList() // oldest → newest
        val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val weekTrained = days.filter { !it.isBefore(weekStart) }.map { it.dayOfWeek }.toSet()

        // Lengths of every chain, oldest first.
        val chains = mutableListOf<Int>()
        var prev: LocalDate? = null
        for (d in days) {
            if (prev != null && ChronoUnit.DAYS.between(prev, d) <= MAX_GAP_DAYS) chains[chains.lastIndex] += 1 else chains += 1
            prev = d
        }
        val best = chains.maxOrNull() ?: 0

        val last = days.lastOrNull()
        val sinceLast = last?.let { ChronoUnit.DAYS.between(it, today) }
        val state = when (sinceLast) {
            null -> StreakState.NONE
            0L -> StreakState.FED_TODAY
            1L -> StreakState.CAN_GROW
            in 2L..MAX_GAP_DAYS -> StreakState.LAST_DAY
            else -> StreakState.NONE
        }
        val current = if (state == StreakState.NONE) 0 else chains.lastOrNull() ?: 0
        return DayStreak(current, best, state, weekTrained, today.dayOfWeek)
    }
}
