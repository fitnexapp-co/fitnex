package com.fitnex.core.data.today

import com.fitnex.core.data.calendar.WorkoutCalendarRepository
import com.fitnex.core.data.training.RoutineRepository
import com.fitnex.core.model.Routine
import com.fitnex.core.model.ScheduledStatus
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

/** What today looks like, for the quick-start card, the reminder and the home-screen widget. */
data class TodayPlan(
    /** The routine to do today (scheduled in the calendar, or whose weekdays include today). */
    val routineId: String?,
    val routineName: String?,
    /** A workout was already finished today. */
    val doneToday: Boolean,
    /** The user planned today as a rest day. */
    val restDay: Boolean,
    /** Next day (after today) with a routine on its weekdays, if any. */
    val nextDate: LocalDate?,
    val nextRoutineName: String?,
)

@Singleton
class TodayPlanRepository @Inject constructor(
    private val calendar: WorkoutCalendarRepository,
    private val routines: RoutineRepository,
    private val clock: Clock,
) {
    fun observe(userId: String): Flow<TodayPlan> {
        val today = LocalDate.now(clock)
        return combine(calendar.observeDay(userId, today), routines.observeRoutines(userId)) { day, all ->
            val scheduled = day.scheduled.firstOrNull { it.status == ScheduledStatus.SCHEDULED }
            val byWeekday = all.firstOrNull { today.dayOfWeek in it.days && it.exercises.isNotEmpty() }
            val (nextDate, nextRoutine) = nextPlanned(all, today)
            TodayPlan(
                routineId = scheduled?.routineId ?: byWeekday?.id,
                routineName = scheduled?.routineName ?: byWeekday?.name,
                doneToday = day.sessions.isNotEmpty(),
                restDay = day.isPlannedRest,
                nextDate = nextDate,
                nextRoutineName = nextRoutine?.name,
            )
        }
    }

    suspend fun get(userId: String): TodayPlan = observe(userId).first()

    private fun nextPlanned(all: List<Routine>, today: LocalDate): Pair<LocalDate?, Routine?> {
        for (i in 1..7L) {
            val d = today.plusDays(i)
            all.firstOrNull { d.dayOfWeek in it.days && it.exercises.isNotEmpty() }?.let { return d to it }
        }
        return null to null
    }
}
