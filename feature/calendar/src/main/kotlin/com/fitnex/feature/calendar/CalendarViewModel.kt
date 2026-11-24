package com.fitnex.feature.calendar

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitnex.core.data.calendar.WorkoutCalendarRepository
import com.fitnex.core.data.session.SessionRepository
import com.fitnex.core.data.training.RoutineRepository
import com.fitnex.core.designsystem.component.calendar.DayMarker
import com.fitnex.core.model.CalendarDay
import com.fitnex.core.model.DayStatus
import com.fitnex.core.model.Routine
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CalendarUiState(
    val month: YearMonth,
    val selected: LocalDate,
    val today: LocalDate,
    /** Only days with a real status; a missing entry means "no activity recorded" (never rest). */
    val markers: Map<LocalDate, DayMarker> = emptyMap(),
    val day: CalendarDay? = null,
    val routines: List<Routine> = emptyList(),
) {
    val canPlan: Boolean get() = !selected.isBefore(today)
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val clock: Clock,
    private val savedState: SavedStateHandle,
    private val session: SessionRepository,
    private val calendar: WorkoutCalendarRepository,
    routines: RoutineRepository,
) : ViewModel() {
    private val today = LocalDate.now(clock)
    private val month = MutableStateFlow(savedState.get<String>(KEY_MONTH)?.let(YearMonth::parse) ?: YearMonth.from(today))
    private val selected = MutableStateFlow(savedState.get<String>(KEY_SELECTED)?.let(LocalDate::parse) ?: today)
    private val userId = session.currentUserId.filterNotNull()

    private val markers = combine(userId, month, ::Pair).flatMapLatest { (uid, m) -> calendar.observeMonth(uid, m) }
    private val day = combine(userId, selected, ::Pair).flatMapLatest { (uid, d) -> calendar.observeDay(uid, d) }
    private val routineList = userId.flatMapLatest { routines.observeRoutines(it) }

    val state: StateFlow<CalendarUiState> = combine(month, selected, markers, day, routineList) { m, s, mk, d, r ->
        CalendarUiState(m, s, today, mk.mapValues { it.value.toMarker() }, d.takeIf { it.date == s }, r)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, CalendarUiState(month.value, selected.value, today))

    fun previousMonth() = setMonth(month.value.minusMonths(1))
    fun nextMonth() = setMonth(month.value.plusMonths(1))
    fun goToToday() {
        setMonth(YearMonth.from(today))
        select(today)
    }

    fun select(date: LocalDate) {
        selected.value = date
        savedState[KEY_SELECTED] = date.toString()
    }

    private fun setMonth(m: YearMonth) {
        month.value = m
        savedState[KEY_MONTH] = m.toString()
    }

    fun schedule(routine: Routine) = viewModelScope.launch {
        calendar.schedule(userId.first(), selected.value, routine)
    }

    fun cancelScheduled(id: String) = viewModelScope.launch { calendar.cancelScheduled(id) }

    fun setRest(rest: Boolean) = viewModelScope.launch { calendar.setPlannedRest(userId.first(), selected.value, rest) }

    private companion object {
        const val KEY_MONTH = "calendar_month"
        const val KEY_SELECTED = "calendar_selected"
    }
}

internal fun DayStatus.toMarker(): DayMarker = when (this) {
    DayStatus.COMPLETED -> DayMarker.COMPLETED
    DayStatus.SCHEDULED -> DayMarker.SCHEDULED
    DayStatus.MISSED -> DayMarker.MISSED
    DayStatus.PLANNED_REST -> DayMarker.PLANNED_REST
}
