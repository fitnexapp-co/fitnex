package com.fitnex.feature.calendar

import com.fitnex.core.designsystem.component.fitnexScreen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.fitnex.core.designsystem.component.FitnexBottomSheet
import com.fitnex.core.designsystem.component.FitnexHeader
import com.fitnex.core.designsystem.component.FitnexSecondaryButton
import com.fitnex.core.designsystem.component.GlassCard
import com.fitnex.core.designsystem.component.SectionHeader
import com.fitnex.core.designsystem.component.calendar.CalendarLegend
import com.fitnex.core.designsystem.component.calendar.FitnexMonthCalendar
import com.fitnex.core.designsystem.locale.rememberAppLocale
import com.fitnex.core.designsystem.theme.FitnexTheme
import com.fitnex.core.designsystem.theme.Spacing
import com.fitnex.core.model.DayStatus
import com.fitnex.core.model.Routine
import com.fitnex.core.model.ScheduledStatus
import java.time.Duration
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlinx.serialization.Serializable

@Serializable
data object CalendarRoute

fun NavGraphBuilder.calendarScreen(contentPadding: PaddingValues, onBack: () -> Unit) {
    fitnexScreen<CalendarRoute> {
        val viewModel: CalendarViewModel = hiltViewModel()
        val state by viewModel.state.collectAsStateWithLifecycle()
        CalendarScreen(state, contentPadding, onBack, viewModel)
    }
}

@Composable
internal fun CalendarScreen(state: CalendarUiState, contentPadding: PaddingValues, onBack: () -> Unit, vm: CalendarViewModel) {
    val colors = FitnexTheme.colors
    val locale = rememberAppLocale()
    var pickRoutine by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(contentPadding)) {
        FitnexHeader(
            title = stringResource(R.string.calendar_title),
            overline = stringResource(R.string.calendar_overline),
            onBack = onBack,
            actions = {
                TextButton(onClick = vm::goToToday) {
                    Text(stringResource(R.string.calendar_today), style = FitnexTheme.typography.labelLarge, color = colors.accent)
                }
            },
        )
        Column(Modifier.padding(horizontal = Spacing.gutter), verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
            GlassCard(contentPadding = PaddingValues(Spacing.md)) {
                FitnexMonthCalendar(
                    month = state.month,
                    selected = state.selected,
                    today = state.today,
                    onSelect = vm::select,
                    onPreviousMonth = vm::previousMonth,
                    onNextMonth = vm::nextMonth,
                    marker = { state.markers[it] },
                    locale = locale,
                )
            }
            DayDetail(state, locale, onSchedule = { pickRoutine = true }, onCancel = vm::cancelScheduled, onRest = vm::setRest)
            GlassCard {
                SectionHeader(stringResource(R.string.calendar_legend))
                CalendarLegend()
            }
        }
    }
    if (pickRoutine) {
        RoutinePicker(state.routines, onPick = { vm.schedule(it); pickRoutine = false }, onDismiss = { pickRoutine = false })
    }
}

@Composable
private fun DayDetail(
    state: CalendarUiState,
    locale: Locale,
    onSchedule: () -> Unit,
    onCancel: (String) -> Unit,
    onRest: (Boolean) -> Unit,
) {
    val colors = FitnexTheme.colors
    val day = state.day
    val title = state.selected.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale))
        .replaceFirstChar { it.titlecase(locale) }
    GlassCard(Modifier.fillMaxWidth().testTag("calendar_day_detail")) {
        Text(title, style = FitnexTheme.typography.titleLarge, color = colors.textPrimary)

        // What actually happened, set by set.
        day?.sessions?.forEach { s ->
            Column(Modifier.padding(top = Spacing.sm).testTag("calendar_session")) {
                Text(
                    stringResource(R.string.calendar_session, s.routineName, minutes(s.duration), s.sets.size),
                    style = FitnexTheme.typography.titleMedium, color = colors.accent,
                )
                s.sets.groupBy { it.exerciseName }.forEach { (name, sets) ->
                    Text(
                        "$name: " + sets.joinToString(" · ") { if (it.weightKg > 0f) "${it.reps}×${kg(it.weightKg)}" else "${it.reps}" },
                        style = FitnexTheme.typography.bodyMedium, color = colors.textPrimary,
                    )
                }
            }
        }
        // Plans for the day.
        day?.scheduled?.filter { it.status == ScheduledStatus.SCHEDULED }?.forEach { plan ->
            Row(Modifier.fillMaxWidth().padding(top = Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.calendar_scheduled, plan.routineName), style = FitnexTheme.typography.bodyLarge, color = colors.textPrimary, modifier = Modifier.weight(1f))
                if (state.canPlan) {
                    val cd = stringResource(R.string.calendar_cancel_cd, plan.routineName)
                    TextButton(onClick = { onCancel(plan.id) }, modifier = Modifier.testTag("cancel_${plan.routineName}")) {
                        Text(stringResource(R.string.calendar_cancel), color = colors.error)
                    }
                }
            }
        }
        // Routines assigned to this weekday in the routine editor.
        day?.weeklyRoutines?.forEach { name ->
            Text(
                stringResource(R.string.calendar_weekly, name), style = FitnexTheme.typography.bodyLarge, color = colors.textPrimary,
                modifier = Modifier.padding(top = Spacing.sm).testTag("calendar_weekly"),
            )
        }
        val message = when (day?.status) {
            null -> stringResource(R.string.calendar_no_activity)
            DayStatus.PLANNED_REST -> stringResource(R.string.calendar_planned_rest)
            DayStatus.MISSED -> stringResource(R.string.calendar_missed)
            else -> null
        }
        message?.let { Text(it, style = FitnexTheme.typography.bodyMedium, color = colors.textSecondary, modifier = Modifier.padding(top = Spacing.xs).testTag("calendar_day_message")) }

        // Planning is only for today and the future; the past is history, not editable here.
        if (state.canPlan && day != null) {
            // One above the other, full width: side by side the labels were cut ("Marcar como…").
            Column(Modifier.padding(top = Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                FitnexSecondaryButton(stringResource(R.string.calendar_schedule), onSchedule, Modifier.fillMaxWidth().testTag("calendar_schedule"))
                if (day.sessions.isEmpty()) {
                    FitnexSecondaryButton(
                        stringResource(if (day.isPlannedRest) R.string.calendar_unmark_rest else R.string.calendar_mark_rest),
                        { onRest(!day.isPlannedRest) }, Modifier.fillMaxWidth().testTag("calendar_rest"),
                    )
                }
            }
        }
    }
}

@Composable
private fun RoutinePicker(routines: List<Routine>, onPick: (Routine) -> Unit, onDismiss: () -> Unit) {
    val colors = FitnexTheme.colors
    FitnexBottomSheet(onDismiss = onDismiss, title = stringResource(R.string.calendar_schedule_title)) {
        if (routines.isEmpty()) {
            Text(stringResource(R.string.calendar_no_routines), style = FitnexTheme.typography.bodyMedium, color = colors.textSecondary)
        }
        routines.forEachIndexed { i, r ->
            if (i > 0) HorizontalDivider(color = colors.divider)
            Text(
                r.name, style = FitnexTheme.typography.titleMedium, color = colors.textPrimary,
                modifier = Modifier.fillMaxWidth().clickable { onPick(r) }.padding(vertical = Spacing.md).testTag("pick_${r.name}"),
            )
        }
    }
}

private fun minutes(d: Duration) = "${d.toMinutes()} min"
private fun kg(v: Float) = if (v % 1f == 0f) v.toInt().toString() else "%d,%d".format(v.toInt(), Math.round((v - v.toInt()) * 10))
