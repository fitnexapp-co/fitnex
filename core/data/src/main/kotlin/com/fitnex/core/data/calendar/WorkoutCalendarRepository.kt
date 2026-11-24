package com.fitnex.core.data.calendar

import com.fitnex.core.common.time.IdGenerator
import com.fitnex.core.data.stats.GlobalStatisticsRepository
import com.fitnex.core.database.dao.CalendarDao
import com.fitnex.core.database.entity.PlannedRestDayEntity
import com.fitnex.core.database.entity.ScheduledSessionEntity
import com.fitnex.core.database.entity.SyncMeta
import com.fitnex.core.model.CalendarDay
import com.fitnex.core.model.CalendarRules
import com.fitnex.core.model.DayStatus
import com.fitnex.core.model.Routine
import com.fitnex.core.model.ScheduledStatus
import com.fitnex.core.model.ScheduledWorkout
import com.fitnex.core.model.SyncStatus
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** Spec §17/§21: the calendar reads real sessions, scheduled routines and planned rest days. */
interface WorkoutCalendarRepository {
    fun observeMonth(userId: String, month: YearMonth): Flow<Map<LocalDate, DayStatus>>
    fun observeDay(userId: String, date: LocalDate): Flow<CalendarDay>

    /** Only today or future dates can be scheduled. Returns false if refused. */
    suspend fun schedule(userId: String, date: LocalDate, routine: Routine): Boolean
    suspend fun cancelScheduled(id: String)
    suspend fun setPlannedRest(userId: String, date: LocalDate, rest: Boolean)
}

@Singleton
class RoomWorkoutCalendarRepository @Inject constructor(
    private val calendar: CalendarDao,
    private val stats: GlobalStatisticsRepository,
    private val ids: IdGenerator,
    private val clock: Clock,
    private val routines: com.fitnex.core.data.training.RoutineRepository,
) : WorkoutCalendarRepository {

    private fun today() = LocalDate.now(clock)

    override fun observeMonth(userId: String, month: YearMonth): Flow<Map<LocalDate, DayStatus>> {
        // Pad by a week on each side: the grid shows neighbouring days too.
        val from = month.atDay(1).minusDays(7)
        val to = month.atEndOfMonth().plusDays(7)
        return combine(
            stats.observeCompletedSessions(userId),
            calendar.observeScheduled(userId, from, to),
            calendar.observeRestDays(userId, from, to),
            routines.observeRoutines(userId),
        ) { sessions, scheduled, rest, all ->
            val sessionDays = sessions.map { it.startedAt.atZone(clock.zone).toLocalDate() }.toSet()
            val scheduledByDay = scheduled.map { it.toModel() }.groupBy { it.date }
            val restDays = rest.map { it.date }.toSet()
            val today = today()
            generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(to) }
                .mapNotNull { d ->
                    val weekly = CalendarRules.weeklyRoutinesOn(d, all).isNotEmpty()
                    CalendarRules.status(d, today, d in sessionDays, scheduledByDay[d].orEmpty(), d in restDays, weekly)?.let { d to it }
                }.toMap()
        }
    }

    override fun observeDay(userId: String, date: LocalDate): Flow<CalendarDay> = combine(
        stats.observeCompletedSessions(userId),
        calendar.observeScheduled(userId, date, date),
        calendar.observeRestDays(userId, date, date),
        routines.observeRoutines(userId),
    ) { sessions, scheduled, rest, all ->
        val daySessions = sessions.filter { it.startedAt.atZone(clock.zone).toLocalDate() == date }
        val plans = scheduled.map { it.toModel() }.filter { it.status != ScheduledStatus.CANCELLED }
        val weekly = CalendarRules.weeklyRoutinesOn(date, all)
        val status = CalendarRules.status(
            date, today(), daySessions.isNotEmpty(), scheduled.map { it.toModel() }, rest.isNotEmpty(), weekly.isNotEmpty(),
        )
        CalendarDay(
            date = date,
            status = status,
            sessions = daySessions,
            scheduled = plans,
            isPlannedRest = rest.isNotEmpty(),
            // Shown only when it is what makes the day "scheduled" (no explicit plan, rest or workout).
            weeklyRoutines = if (status == DayStatus.SCHEDULED && plans.isEmpty()) weekly else emptyList(),
        )
    }

    override suspend fun schedule(userId: String, date: LocalDate, routine: Routine): Boolean {
        if (date.isBefore(today())) return false
        val now = clock.instant()
        calendar.upsertScheduled(
            ScheduledSessionEntity(
                id = ids.newId(), userId = userId, workoutId = routine.id, workoutNameSnapshot = routine.name,
                date = date, status = ScheduledStatus.SCHEDULED, completedSessionId = null, note = null,
                sync = SyncMeta(updatedAt = now),
            ),
        )
        // Scheduling a workout on a planned rest day replaces the rest plan.
        setPlannedRest(userId, date, rest = false)
        return true
    }

    override suspend fun cancelScheduled(id: String) {
        val row = calendar.getScheduled(id) ?: return
        val now = clock.instant()
        calendar.upsertScheduled(row.copy(status = ScheduledStatus.CANCELLED, sync = SyncMeta(updatedAt = now)))
    }

    override suspend fun setPlannedRest(userId: String, date: LocalDate, rest: Boolean) {
        val now = clock.instant()
        val existing = calendar.getRestDay(userId, date)
        when {
            rest && existing == null -> calendar.upsertRestDay(PlannedRestDayEntity(ids.newId(), userId, date, null, SyncMeta(updatedAt = now)))
            rest -> calendar.upsertRestDay(existing!!.copy(sync = SyncMeta(updatedAt = now)))
            existing != null && existing.sync.deletedAt == null -> calendar.upsertRestDay(
                existing.copy(sync = SyncMeta(updatedAt = now, syncStatus = SyncStatus.PENDING_DELETE, deletedAt = now)),
            )
        }
    }

    private fun ScheduledSessionEntity.toModel() = ScheduledWorkout(id, date, workoutId, workoutNameSnapshot, status)
}
