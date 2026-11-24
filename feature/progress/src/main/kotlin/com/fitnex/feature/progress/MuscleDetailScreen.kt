package com.fitnex.feature.progress

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.fitnex.core.anatomy.MapLegend
import com.fitnex.core.anatomy.map2d.BodyAtlas2D
import com.fitnex.core.anatomy.map2d.MuscleMap2D
import com.fitnex.core.designsystem.component.FitnexChipGroup
import com.fitnex.core.designsystem.component.FitnexHeader
import com.fitnex.core.designsystem.component.FitnexSegmentedControl
import com.fitnex.core.designsystem.component.GlassCard
import com.fitnex.core.designsystem.component.LoadingState
import com.fitnex.core.designsystem.component.MessageState
import com.fitnex.core.designsystem.component.SectionHeader
import com.fitnex.core.designsystem.component.StatTile
import com.fitnex.core.designsystem.component.chart.ChartPoint
import com.fitnex.core.designsystem.component.chart.FitnexBarChart
import com.fitnex.core.designsystem.locale.rememberAppLocale
import com.fitnex.core.designsystem.theme.FitnexTheme
import com.fitnex.core.designsystem.theme.Spacing
import com.fitnex.core.model.BucketUnit
import com.fitnex.core.model.Exercise
import com.fitnex.core.model.MuscleWeek
import java.util.Locale

private enum class MuscleMetric { SETS, VOLUME }

@Composable
internal fun MuscleDetailScreen(
    state: MuscleDetailUiState,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onOpenMuscle: (String) -> Unit,
    onOpenExercise: (String) -> Unit,
) {
    val colors = FitnexTheme.colors
    val locale = rememberAppLocale()
    val muscle = state.muscle
    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(contentPadding)) {
        FitnexHeader(
            title = muscle?.name.orEmpty(),
            overline = muscle?.let { regionLabel(it.region) }.orEmpty(),
            onBack = onBack,
        )
        Column(Modifier.padding(horizontal = Spacing.gutter), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            when {
                state.loading -> LoadingState()
                muscle == null -> MessageState(title = "", message = stringResource(R.string.muscle_not_found))
                else -> {
                    val stats = state.stats
                    GlassCard(Modifier.fillMaxWidth().testTag("muscle_detail_map")) {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            MuscleMap2D(
                                view = BodyAtlas2D.preferredView(muscle.id),
                                levels = mapOf(muscle.id to 1f),
                                selected = muscle.id,
                                onSelect = null,
                                contentDescription = stringResource(R.string.muscle_map_a11y, muscle.name),
                                modifier = Modifier.height(260.dp),
                            )
                        }
                        SectionHeader(stringResource(R.string.muscle_function))
                        Text(muscle.function, style = FitnexTheme.typography.bodyLarge, color = colors.textPrimary)
                        MapLegend(Modifier.padding(top = Spacing.md))
                    }

                    if (stats != null) {
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            StatTile(stringResource(R.string.muscle_sessions), stats.sessions.toString(), Modifier.weight(1f).testTag("muscle_sessions"))
                            StatTile(stringResource(R.string.muscle_weighted_sets), formatKg(stats.totalWeightedSets), Modifier.weight(1f).testTag("muscle_sets"))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            StatTile(
                                stringResource(R.string.muscle_last),
                                stats.lastTrained?.let { shortDate(it, locale) } ?: stringResource(R.string.muscle_never),
                                Modifier.weight(1f).testTag("muscle_last"),
                            )
                            StatTile(
                                stringResource(R.string.muscle_frequency),
                                stringResource(R.string.muscle_frequency_value, formatKg(stats.sessionsPerWeek)),
                                Modifier.weight(1f),
                                caption = stringResource(R.string.muscle_frequency_caption),
                            )
                        }
                        Evolution(stats.weeks, locale)
                    }

                    ExerciseList(stringResource(R.string.muscle_primary_exercises), state.primaryExercises, onOpenExercise, "primary")
                    ExerciseList(stringResource(R.string.muscle_secondary_exercises), state.secondaryExercises, onOpenExercise, "secondary")

                    GlassCard(Modifier.fillMaxWidth().testTag("muscle_history")) {
                        SectionHeader(stringResource(R.string.muscle_history))
                        val history = stats?.history.orEmpty()
                        if (history.isEmpty()) {
                            Text(stringResource(R.string.muscle_history_empty), style = FitnexTheme.typography.bodyMedium, color = colors.textSecondary)
                        }
                        history.take(15).forEachIndexed { i, h ->
                            if (i > 0) HorizontalDivider(color = colors.divider)
                            Column(Modifier.padding(vertical = Spacing.sm)) {
                                Text("${shortDate(h.date, locale)} · ${h.routineName}", style = FitnexTheme.typography.titleMedium, color = colors.textPrimary)
                                Text(
                                    stringResource(
                                        R.string.muscle_history_row,
                                        pluralStringResource(R.plurals.sets_count, h.sets, h.sets),
                                        h.exerciseNames.joinToString(),
                                    ),
                                    style = FitnexTheme.typography.bodySmall, color = colors.textSecondary,
                                )
                            }
                        }
                    }

                    if (state.sameRegion.isNotEmpty()) {
                        GlassCard(Modifier.fillMaxWidth()) {
                            SectionHeader(stringResource(R.string.muscle_same_region, regionLabel(muscle.region).lowercase(locale)))
                            FitnexChipGroup(
                                options = state.sameRegion,
                                isSelected = { false },
                                onToggle = { onOpenMuscle(it.id) },
                                label = { it.name },
                                modifier = Modifier.testTag("muscle_same_region"),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Evolution(weeks: List<MuscleWeek>, locale: Locale) {
    val colors = FitnexTheme.colors
    var metric by rememberSaveable { mutableStateOf(MuscleMetric.SETS) }
    val names = mapOf(MuscleMetric.SETS to stringResource(R.string.muscle_metric_sets), MuscleMetric.VOLUME to stringResource(R.string.muscle_metric_volume))
    GlassCard(Modifier.fillMaxWidth().testTag("muscle_evolution")) {
        SectionHeader(stringResource(R.string.muscle_evolution))
        FitnexSegmentedControl(MuscleMetric.entries, metric, { metric = it }, { names.getValue(it) })
        val bars = weeks.map { w ->
            ChartPoint(bucketLabel(w.start, BucketUnit.WEEK, locale), if (metric == MuscleMetric.SETS) w.weightedSets else w.volumeKg)
        }
        if (bars.all { it.value == 0f }) {
            Text(stringResource(R.string.muscle_evolution_empty), style = FitnexTheme.typography.bodyMedium, color = colors.textSecondary, modifier = Modifier.padding(vertical = Spacing.sm))
        }
        FitnexBarChart(
            bars,
            stringResource(R.string.muscle_evolution_a11y, names.getValue(metric), bars.joinToString { "${it.label} ${formatKg(it.value)}" }),
            Modifier.padding(top = Spacing.sm),
            wholeNumbers = metric == MuscleMetric.SETS,
            maxLabels = 6, // 12 weekly "d/M" labels do not fit on a phone
        )
        if (metric == MuscleMetric.VOLUME) {
            Text(stringResource(R.string.muscle_volume_note), style = FitnexTheme.typography.bodySmall, color = colors.textDisabled)
        }
    }
}

@Composable
private fun ExerciseList(title: String, exercises: List<Exercise>, onOpen: (String) -> Unit, tag: String) {
    val colors = FitnexTheme.colors
    GlassCard(Modifier.fillMaxWidth().testTag("muscle_exercises_$tag")) {
        SectionHeader(title)
        if (exercises.isEmpty()) {
            Text(stringResource(R.string.muscle_no_exercises), style = FitnexTheme.typography.bodyMedium, color = colors.textSecondary)
        }
        exercises.forEachIndexed { i, e ->
            if (i > 0) HorizontalDivider(color = colors.divider)
            Text(
                e.name, style = FitnexTheme.typography.titleMedium, color = colors.textPrimary,
                modifier = Modifier.fillMaxWidth().clickable { onOpen(e.id) }.padding(vertical = Spacing.sm).testTag("muscle_exercise_${e.id}"),
            )
        }
    }
}
