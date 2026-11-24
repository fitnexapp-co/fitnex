package com.fitnex.core.model

import java.time.LocalDate

/**
 * What a calendar day shows (spec §17). A day with NO status means "no activity recorded" and
 * is never presented as rest: only [PLANNED_REST] (explicitly set by the user) is rest.
 */
enum class DayStatus { COMPLETED, SCHEDULED, MISSED, PLANNED_REST }

data class ScheduledWorkout(
    val id: String,
    val date: LocalDate,
    val routineId: String?,
    val routineName: String,
    val status: ScheduledStatus,
)

data class CalendarDay(
    val date: LocalDate,
    val status: DayStatus?,
    val sessions: List<CompletedSession>,
    val scheduled: List<ScheduledWorkout>,
    val isPlannedRest: Boolean,
    /** Routines whose weekdays (set in the routine editor) include this day; today and later only. */
    val weeklyRoutines: List<String> = emptyList(),
)

object CalendarRules {
    /**
     * Priority: what actually happened beats plans; a past plan that didn't happen is "missed".
     * [weeklyPlan]: a routine is assigned to this weekday. It only marks today and later as
     * scheduled (never "missed": a weekday plan is a habit, not an appointment), and an explicit
     * plan or a planned rest for that date wins over it.
     */
    fun status(
        date: LocalDate,
        today: LocalDate,
        hasSession: Boolean,
        scheduled: List<ScheduledWorkout>,
        plannedRest: Boolean,
        weeklyPlan: Boolean = false,
    ): DayStatus? {
        val pending = scheduled.filter { it.status == ScheduledStatus.SCHEDULED }
        return when {
            hasSession -> DayStatus.COMPLETED
            pending.isNotEmpty() && !date.isBefore(today) -> DayStatus.SCHEDULED
            pending.isNotEmpty() -> DayStatus.MISSED
            plannedRest -> DayStatus.PLANNED_REST
            weeklyPlan && scheduled.isEmpty() && !date.isBefore(today) -> DayStatus.SCHEDULED
            else -> null
        }
    }

    /** Names of the routines assigned to [date]'s weekday that have exercises. */
    fun weeklyRoutinesOn(date: LocalDate, routines: List<Routine>): List<String> =
        routines.filter { date.dayOfWeek in it.days && it.exercises.isNotEmpty() }.map { it.name }
}
