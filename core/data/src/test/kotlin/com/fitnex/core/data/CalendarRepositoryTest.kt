package com.fitnex.core.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fitnex.core.data.calendar.RoomWorkoutCalendarRepository
import com.fitnex.core.data.catalog.AssetCatalogSource
import com.fitnex.core.data.catalog.CatalogSeeder
import com.fitnex.core.data.stats.RoomGlobalStatisticsRepository
import com.fitnex.core.data.training.RoomRoutineRepository
import com.fitnex.core.data.training.RoomWorkoutSessionRepository
import com.fitnex.core.database.FitnexDatabase
import com.fitnex.core.datastore.UserPreferencesDataSource
import com.fitnex.core.model.DayStatus
import com.fitnex.core.model.Routine
import com.fitnex.core.model.RoutineExercise
import com.fitnex.core.model.ScheduledStatus
import com.fitnex.core.model.StatsPeriod
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CalendarRepositoryTest {
    @get:Rule val tmp = TemporaryFolder()

    private lateinit var db: FitnexDatabase
    private val clock = MutableClock(Instant.parse("2026-11-19T09:00:00Z")) // Thursday
    private val today = LocalDate.of(2026, 11, 19)
    private var nextId = 0
    private val user = "u1"
    private val routine = Routine("r1", "Pierna", emptySet(), false, listOf(RoutineExercise("re1", "back_squat", "Sentadilla con barra", 3, 8, 120, 60f)), clock.now)

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), FitnexDatabase::class.java).allowMainThreadQueries().build()
    }

    @After fun tearDown() = db.close()

    private fun TestScope.wire(): Triple<RoomWorkoutCalendarRepository, RoomWorkoutSessionRepository, RoomGlobalStatisticsRepository> {
        val seeder = CatalogSeeder(
            AssetCatalogSource(ApplicationProvider.getApplicationContext()), db.catalogDao(),
            UserPreferencesDataSource(PreferenceDataStoreFactory.create(scope = backgroundScope) { File(tmp.root, "p.preferences_pb") }), clock,
        )
        val stats = RoomGlobalStatisticsRepository(db.sessionDao(), db.catalogDao(), seeder, clock)
        val ids = { "id-${nextId++}" }
        return Triple(
            RoomWorkoutCalendarRepository(db.calendarDao(), stats, ids, clock, RoomRoutineRepository(db.workoutDao(), db.catalogDao(), clock)),
            RoomWorkoutSessionRepository(db.sessionDao(), db.catalogDao(), db.calendarDao(), ids, clock),
            stats,
        )
    }

    @Test
    fun scheduleThenTrainMarksTheDayCompletedAndStatsUpdate() = runTest {
        val (calendar, sessions, stats) = wire()
        RoomRoutineRepository(db.workoutDao(), db.catalogDao(), clock).save(user, routine)

        assertTrue(calendar.schedule(user, today, routine))
        assertEquals(DayStatus.SCHEDULED, calendar.observeMonth(user, YearMonth.of(2026, 11)).first()[today])

        val id = sessions.start(user, routine)
        sessions.logSet(id, routine.exercises.single(), 0, 8, 60f)
        clock.advance(1800)
        sessions.finish(id)

        val day = calendar.observeDay(user, today).first()
        assertEquals(DayStatus.COMPLETED, day.status)
        assertEquals(1, day.sessions.size)
        assertEquals(ScheduledStatus.COMPLETED, day.scheduled.single().status) // marked by finish()
        assertEquals(1, stats.observeStats(user, StatsPeriod.WEEK).first().workouts)
    }

    @Test
    fun pastScheduleIsMissedFutureCanBeCancelledAndPastCannotBeScheduled() = runTest {
        val (calendar, _, _) = wire()
        RoomRoutineRepository(db.workoutDao(), db.catalogDao(), clock).save(user, routine)
        val tomorrow = today.plusDays(1)
        assertTrue(calendar.schedule(user, tomorrow, routine))
        assertFalse("past dates can't be scheduled", calendar.schedule(user, today.minusDays(1), routine))

        clock.advance(2 * 86_400) // two days later, never trained
        assertEquals(DayStatus.MISSED, calendar.observeMonth(user, YearMonth.of(2026, 11)).first()[tomorrow])

        val plan = calendar.observeDay(user, tomorrow).first().scheduled.single()
        calendar.cancelScheduled(plan.id)
        assertNull(calendar.observeMonth(user, YearMonth.of(2026, 11)).first()[tomorrow])
    }

    @Test
    fun restDaysToggleAndEmptyDaysAreNotRest() = runTest {
        val (calendar, _, _) = wire()
        val sat = LocalDate.of(2026, 11, 21)
        calendar.setPlannedRest(user, sat, true)
        assertEquals(DayStatus.PLANNED_REST, calendar.observeMonth(user, YearMonth.of(2026, 11)).first()[sat])
        calendar.setPlannedRest(user, sat, false)
        calendar.setPlannedRest(user, sat, true) // revive the same row (unique user+date) without crashing
        assertTrue(calendar.observeDay(user, sat).first().isPlannedRest)
        assertNull("no records is not rest", calendar.observeMonth(user, YearMonth.of(2026, 11)).first()[LocalDate.of(2026, 11, 20)])
    }

    @Test
    fun weekdaysOfARoutineShowAsScheduledFromTodayOn() = runTest {
        val (calendar, _, _) = wire()
        // "Pierna" on Thursdays; today is Thursday 19.
        RoomRoutineRepository(db.workoutDao(), db.catalogDao(), clock)
            .save(user, routine.copy(days = setOf(java.time.DayOfWeek.THURSDAY)))
        val month = calendar.observeMonth(user, YearMonth.of(2026, 11)).first()
        assertEquals(DayStatus.SCHEDULED, month[today])
        assertEquals(DayStatus.SCHEDULED, month[LocalDate.of(2026, 11, 26)])
        assertNull("a past weekday is never 'missed'", month[LocalDate.of(2026, 11, 12)])
        assertEquals(listOf("Pierna"), calendar.observeDay(user, today).first().weeklyRoutines)

        // A planned rest that day wins over the weekly routine.
        calendar.setPlannedRest(user, LocalDate.of(2026, 11, 26), true)
        assertEquals(DayStatus.PLANNED_REST, calendar.observeMonth(user, YearMonth.of(2026, 11)).first()[LocalDate.of(2026, 11, 26)])
    }
}
