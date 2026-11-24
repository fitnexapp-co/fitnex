package com.fitnex.feature.workout.live

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitnex.core.data.training.WorkoutSessionRepository
import com.fitnex.core.designsystem.component.FitnexChipGroup
import com.fitnex.core.designsystem.component.FitnexHeader
import com.fitnex.core.designsystem.component.FitnexPrimaryButton
import com.fitnex.core.designsystem.component.GlassCard
import com.fitnex.core.designsystem.component.LoadingState
import com.fitnex.core.designsystem.component.SectionHeader
import com.fitnex.core.designsystem.component.StatTile
import com.fitnex.core.designsystem.theme.FitnexTheme
import com.fitnex.core.designsystem.theme.Spacing
import com.fitnex.core.model.PersonalRecord
import com.fitnex.core.model.RecordType
import com.fitnex.core.model.SessionSummary
import com.fitnex.feature.workout.R
import com.fitnex.feature.workout.ui.formatKg
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@HiltViewModel
class WorkoutSummaryViewModel @Inject constructor(
    savedState: SavedStateHandle,
    sessions: WorkoutSessionRepository,
    session: com.fitnex.core.data.session.SessionRepository,
    stats: com.fitnex.core.data.stats.GlobalStatisticsRepository,
    clock: java.time.Clock,
) : ViewModel() {
    private val _summary = MutableStateFlow<SessionSummary?>(null)
    val summary: StateFlow<SessionSummary?> = _summary.asStateFlow()
    private val _streak = MutableStateFlow<com.fitnex.core.model.DayStreak?>(null)
    /** The daily streak including the workout just finished. */
    val streak: StateFlow<com.fitnex.core.model.DayStreak?> = _streak.asStateFlow()

    init {
        val id = checkNotNull(savedState.get<String>("sessionId"))
        viewModelScope.launch { _summary.value = sessions.summary(id) }
        viewModelScope.launch {
            val uid = session.currentUserId.filterNotNull().first()
            val done = stats.observeCompletedSessions(uid).first()
            _streak.value = com.fitnex.core.model.DayStreakCalculator.compute(
                done.map { it.startedAt.atZone(clock.zone).toLocalDate() }, java.time.LocalDate.now(clock),
            )
        }
    }
}

/** End-of-workout summary (spec §18 "Finalización"); all numbers come from the saved sets. */
@Composable
internal fun WorkoutSummaryScreen(summary: SessionSummary?, onDone: () -> Unit, streak: com.fitnex.core.model.DayStreak? = null) {
    val colors = FitnexTheme.colors
    // The streak moment first (once), then the summary underneath.
    var streakSeen by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    if (streak != null && streak.current > 0 && !streakSeen) {
        com.fitnex.core.designsystem.component.StreakDialog(streak, onDismiss = { streakSeen = true })
    }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        FitnexHeader(stringResource(R.string.workout_summary_title), overline = summary?.routineName)
        if (summary == null) {
            Box(Modifier.weight(1f)) { LoadingState() }
        } else {
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.gutter),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    StatTile(stringResource(R.string.summary_duration), formatClock(summary.duration.seconds), Modifier.weight(1f).testTag("summary_duration"))
                    StatTile(stringResource(R.string.summary_sets), summary.sets.toString(), Modifier.weight(1f).testTag("summary_sets"))
                }
                // Only the figures this workout actually has: a run shows time and distance, not "0 reps".
                if (summary.totalReps > 0 || summary.volumeKg != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        if (summary.totalReps > 0) StatTile(stringResource(R.string.summary_reps), summary.totalReps.toString(), Modifier.weight(1f).testTag("summary_reps"))
                        summary.volumeKg?.let {
                            StatTile(stringResource(R.string.summary_volume), formatKg(it), Modifier.weight(1f).testTag("summary_volume"), unit = "kg")
                        }
                    }
                }
                if (summary.timedSeconds > 0 || summary.distanceMeters > 0f) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        if (summary.timedSeconds > 0) {
                            StatTile(stringResource(R.string.summary_timed), com.fitnex.feature.workout.ui.formatSeconds(summary.timedSeconds), Modifier.weight(1f).testTag("summary_timed"))
                        }
                        if (summary.distanceMeters > 0f) {
                            StatTile(stringResource(R.string.summary_distance), com.fitnex.feature.workout.ui.formatDistance(summary.distanceMeters), Modifier.weight(1f).testTag("summary_distance"))
                        }
                    }
                }
                Text(stringResource(R.string.summary_exercises, summary.exercisesCompleted), style = FitnexTheme.typography.bodyMedium, color = colors.textSecondary)

                GlassCard(Modifier.fillMaxWidth().testTag("summary_records")) {
                    SectionHeader(stringResource(R.string.summary_records))
                    if (summary.records.isEmpty()) {
                        Text(stringResource(R.string.summary_no_records), style = FitnexTheme.typography.bodyMedium, color = colors.textSecondary)
                    } else {
                        summary.records.forEach { RecordRow(it) }
                        if (summary.records.any { it.type == RecordType.ESTIMATED_1RM }) {
                            Text(stringResource(R.string.summary_1rm_note), style = FitnexTheme.typography.bodySmall, color = colors.textDisabled)
                        }
                    }
                }
                if (summary.musclesWorked.isNotEmpty()) {
                    GlassCard(Modifier.fillMaxWidth()) {
                        SectionHeader(stringResource(R.string.summary_muscles))
                        FitnexChipGroup(summary.musclesWorked, { true }, { }, { it.name })
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth().padding(Spacing.gutter)) {
            FitnexPrimaryButton(stringResource(R.string.summary_done), onDone, Modifier.fillMaxWidth().testTag("summary_done"), enabled = summary != null)
        }
    }
}

@Composable
private fun RecordRow(r: PersonalRecord) {
    val text = when (r.type) {
        RecordType.MAX_WEIGHT -> stringResource(R.string.record_weight, formatKg(r.value), formatKg(r.previous ?: 0f))
        RecordType.ESTIMATED_1RM -> stringResource(R.string.record_1rm, formatKg(r.value), formatKg(r.previous ?: 0f))
        RecordType.MAX_REPS -> stringResource(R.string.record_reps, r.value.toInt().toString(), (r.previous ?: 0f).toInt().toString())
    }
    Column(Modifier.padding(vertical = Spacing.xs)) {
        Text("🏆 " + r.exerciseName, style = FitnexTheme.typography.titleMedium, color = FitnexTheme.colors.textPrimary)
        Text(text, style = FitnexTheme.typography.bodyMedium, color = FitnexTheme.colors.accent)
    }
}
