package com.fitnex.feature.calendar

import androidx.lifecycle.SavedStateHandle
import com.fitnex.core.auth.AuthUser
import com.fitnex.core.data.calendar.WorkoutCalendarRepository
import com.fitnex.core.data.session.SessionRepository
import com.fitnex.core.data.training.RoutineRepository
import com.fitnex.core.designsystem.component.calendar.DayMarker
import com.fitnex.core.model.CalendarDay
import com.fitnex.core.model.DayStatus
import com.fitnex.core.model.Routine
import com.fitnex.core.testing.FakeAuthRepository
import com.fitnex.core.testing.FakeUserProfileRepository
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModelTest {
    private val clock = Clock.fixed(Instant.parse("2026-11-19T12:00:00Z"), ZoneId.of("Europe/Madrid"))
    private val today = LocalDate.of(2026, 11, 19)
    private val dispatcher = UnconfinedTestDispatcher()
    private val auth = FakeAuthRepository().apply { signInDirectly(AuthUser("u1", "a@b.c", true)) }
    private val calendar = FakeCalendar()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun vm(saved: SavedStateHandle = SavedStateHandle()) =
        CalendarViewModel(clock, saved, SessionRepository(auth, FakeUserProfileRepository(), com.fitnex.core.testing.FakeCloudSync()), calendar, NoRoutines)

    @Test
    fun startsOnTodayAndShowsOnlyRealMarkers() = runTest(dispatcher) {
        calendar.month.value = mapOf(today.minusDays(2) to DayStatus.COMPLETED, today.plusDays(1) to DayStatus.PLANNED_REST)
        val s = vm().state.value
        assertEquals(YearMonth.of(2026, 11), s.month)
        assertEquals(today, s.selected)
        assertEquals(DayMarker.COMPLETED, s.markers[today.minusDays(2)])
        assertEquals(DayMarker.PLANNED_REST, s.markers[today.plusDays(1)])
        assertEquals(2, s.markers.size) // every other day: no marker = no activity recorded
    }

    @Test
    fun planningOnlyForTodayAndFuture() = runTest(dispatcher) {
        val vm = vm()
        assertTrue(vm.state.value.canPlan)
        vm.select(today.minusDays(1))
        assertFalse(vm.state.value.canPlan)
        vm.select(today.plusDays(3))
        vm.setRest(true)
        assertEquals(listOf(today.plusDays(3) to true), calendar.restCalls)
    }

    @Test
    fun navigatesAcrossYearBoundaryAndRestoresAfterProcessDeath() = runTest(dispatcher) {
        val saved = SavedStateHandle()
        vm(saved).apply {
            nextMonth(); nextMonth()
            assertEquals(YearMonth.of(2027, 1), state.value.month)
            select(LocalDate.of(2027, 1, 5))
        }
        val restored = vm(saved).state.value
        assertEquals(YearMonth.of(2027, 1), restored.month)
        assertEquals(LocalDate.of(2027, 1, 5), restored.selected)
    }

    private class FakeCalendar : WorkoutCalendarRepository {
        val month = MutableStateFlow<Map<LocalDate, DayStatus>>(emptyMap())
        val restCalls = mutableListOf<Pair<LocalDate, Boolean>>()
        override fun observeMonth(userId: String, month: YearMonth) = this.month
        override fun observeDay(userId: String, date: LocalDate): Flow<CalendarDay> =
            month.map { CalendarDay(date, it[date], emptyList(), emptyList(), it[date] == DayStatus.PLANNED_REST) }
        override suspend fun schedule(userId: String, date: LocalDate, routine: Routine) = true
        override suspend fun cancelScheduled(id: String) = Unit
        override suspend fun setPlannedRest(userId: String, date: LocalDate, rest: Boolean) { restCalls += date to rest }
    }

    private object NoRoutines : RoutineRepository {
        override fun observeRoutines(userId: String): Flow<List<Routine>> = flowOf(emptyList())
        override suspend fun getRoutine(id: String): Routine? = null
        override suspend fun save(userId: String, routine: Routine) = Unit
        override suspend fun delete(id: String) = Unit
    }
}
