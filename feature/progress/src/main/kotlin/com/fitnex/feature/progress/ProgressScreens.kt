package com.fitnex.feature.progress

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.fitnex.core.designsystem.component.FitnexTonalButton
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.fitnex.core.anatomy.MuscleMapCard
import com.fitnex.core.designsystem.component.CalendarHeaderAction
import com.fitnex.core.designsystem.component.FitnexHeader
import com.fitnex.core.designsystem.component.FitnexPrimaryButton
import com.fitnex.core.designsystem.component.FitnexSegmentedControl
import com.fitnex.core.designsystem.component.GlassCard
import com.fitnex.core.designsystem.component.LoadingState
import com.fitnex.core.designsystem.component.MessageState
import com.fitnex.core.designsystem.component.ProfileHeaderAction
import com.fitnex.core.designsystem.component.SectionHeader
import com.fitnex.core.designsystem.component.StatTile
import com.fitnex.core.designsystem.component.chart.ChartPoint
import com.fitnex.core.designsystem.component.chart.FitnexBarChart
import com.fitnex.core.designsystem.component.chart.FitnexLineChart
import com.fitnex.core.designsystem.icon.FitnexIcons
import com.fitnex.core.designsystem.locale.rememberAppLocale
import com.fitnex.core.designsystem.theme.FitnexTheme
import com.fitnex.core.designsystem.theme.Spacing
import com.fitnex.core.model.AnatomicalRegion
import com.fitnex.core.model.BucketUnit
import com.fitnex.core.model.ExerciseStats
import com.fitnex.core.model.GlobalStats
import com.fitnex.core.model.StatsPeriod
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

// ---------------------------------------------------------------- Dashboard

@Composable
internal fun ProgressScreen(
    state: ProgressUiState,
    contentPadding: PaddingValues,
    onOpenCalendar: () -> Unit,
    onOpenSettings: () -> Unit,
    onPeriod: (StatsPeriod) -> Unit,
    onOpenExercise: (String) -> Unit,
    onOpenMuscle: (String) -> Unit,
    onGoTrain: () -> Unit,
    onAnalyzeProgress: () -> Unit = {},
    onSetGoal: (Int) -> Unit = {},
    onAddMeasurement: (com.fitnex.core.data.body.BodyMeasurement) -> Unit = {},
    onDeleteMeasurement: (String) -> Unit = {},
) {
    var showStreak by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    if (showStreak) state.achievements?.let { a -> com.fitnex.core.designsystem.component.StreakDialog(a.dayStreak, onDismiss = { showStreak = false }) }
    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(contentPadding)) {
        com.fitnex.core.designsystem.component.FitnexSectionHero(
            title = stringResource(R.string.progress_title),
            subtitle = stringResource(R.string.progress_subtitle),
            overline = stringResource(R.string.progress_overline),
            overlineIcon = FitnexIcons.Progress,
            actions = {
                state.achievements?.let { a -> com.fitnex.core.designsystem.component.StreakChip(a.dayStreak, onClick = { showStreak = true }) }
                CalendarHeaderAction(onOpenCalendar, Modifier.testTag("open_calendar"))
                ProfileHeaderAction(onOpenSettings, Modifier.testTag("open_settings"))
            },
        )
        val stats = state.stats
        // Hero cards edge to edge (the next one peeks in), like a wallet of cards.
        state.experience?.let { e ->
            ExperienceCards(e, state.achievements)
            androidx.compose.foundation.layout.Spacer(Modifier.height(Spacing.md))
        }
        Column(Modifier.padding(horizontal = Spacing.gutter), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            if (state.loading || stats == null) {
                LoadingState()
            } else {
                // A new account still sees the whole dashboard, at real zeros, with a nudge on top
                // that disappears once the first workout is finished.
                if (!stats.hasAnyHistory) FirstWorkoutNotice(onGoTrain)
                state.achievements?.let { GoalsCard(it, onSetGoal) }
                Dashboard(stats, state, onPeriod, onOpenExercise, onOpenMuscle)
                state.measurements?.let { BodyMeasurementsCard(it, onAddMeasurement, onDeleteMeasurement) }
                // Spec §20: opens FITNEX AI with this screen's statistics as context. Tonal, so it
                // never competes with the primary action.
                FitnexTonalButton(
                    stringResource(R.string.analyze_progress), onAnalyzeProgress,
                    Modifier.fillMaxWidth().testTag("analyze_progress"), icon = FitnexIcons.Sparkle,
                )
            }
        }
    }
}

@Composable
private fun FirstWorkoutNotice(onGoTrain: () -> Unit) {
    val colors = FitnexTheme.colors
    // Compact and motivating: a glowing badge, one title, one line and a white pill to start.
    GlassCard(
        Modifier.fillMaxWidth().testTag("progress_first_workout_notice"),
        emphasis = com.fitnex.core.designsystem.component.CardEmphasis.Featured,
        contentPadding = PaddingValues(Spacing.lg),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            androidx.compose.foundation.layout.Box(
                Modifier
                    .size(56.dp)
                    .background(
                        androidx.compose.ui.graphics.Brush.radialGradient(
                            listOf(colors.accent.copy(alpha = 0.55f), androidx.compose.ui.graphics.lerp(colors.accent, androidx.compose.ui.graphics.Color.Black, 0.7f)),
                        ),
                        androidx.compose.foundation.shape.CircleShape,
                    )
                    .border(1.dp, colors.accentStrong.copy(alpha = 0.6f), androidx.compose.foundation.shape.CircleShape),
                contentAlignment = Alignment.Center,
            ) { Icon(FitnexIcons.Progress, contentDescription = null, tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(26.dp)) }
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.progress_empty_title), style = FitnexTheme.typography.titleMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold), color = colors.textPrimary)
                Text(stringResource(R.string.progress_empty_message), style = FitnexTheme.typography.bodySmall, color = colors.textSecondary)
            }
            // White pill on dark, like the reference; ink text keeps full contrast.
            Row(
                Modifier
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(50))
                    .background(if (colors.isDark) androidx.compose.ui.graphics.Color.White else colors.accent)
                    .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onGoTrain)
                    .padding(start = Spacing.lg, end = Spacing.sm, top = Spacing.sm + Spacing.xxs, bottom = Spacing.sm + Spacing.xxs)
                    .testTag("progress_go_train"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val ink = if (colors.isDark) androidx.compose.ui.graphics.Color(0xFF111114) else colors.onAccent
                Text(stringResource(R.string.progress_go_train), style = FitnexTheme.typography.labelLarge, color = ink)
                Icon(androidx.compose.material.icons.Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, tint = ink, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun Dashboard(
    s: GlobalStats,
    state: ProgressUiState,
    onPeriod: (StatsPeriod) -> Unit,
    onOpenExercise: (String) -> Unit,
    onOpenMuscle: (String) -> Unit,
) {
    val period = state.period
    val colors = FitnexTheme.colors
    val locale = rememberAppLocale()
    val labels = mapOf(
        StatsPeriod.WEEK to stringResource(R.string.period_week), StatsPeriod.MONTH to stringResource(R.string.period_month),
        StatsPeriod.THREE_MONTHS to stringResource(R.string.period_3m), StatsPeriod.YEAR to stringResource(R.string.period_year),
        StatsPeriod.ALL to stringResource(R.string.period_all),
    )
    FitnexSegmentedControl(StatsPeriod.entries, period, onPeriod, { labels.getValue(it) }, Modifier.testTag("period_selector"), compact = true)

    // Every tile compares with the period just before (previous week, previous month…).
    val prev = s.previous
    fun cmp(now: Long, before: Long?): Pair<String?, Boolean?> {
        if (before == null) return null to null
        val d = now - before
        return (if (d >= 0) "+$d" else "−${-d}") to (if (d > 0) true else if (d < 0) false else null)
    }
    // Four tiles, the essentials. Reps and active days go in the captions instead of tiles of their own.
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        val (dw, uw) = cmp(s.workouts.toLong(), prev?.workouts?.toLong())
        StatTile(
            stringResource(R.string.tile_workouts), s.workouts.toString(), Modifier.weight(1f).testTag("tile_workouts"), icon = FitnexIcons.Workout, delta = dw, deltaUp = uw,
            caption = androidx.compose.ui.res.pluralStringResource(R.plurals.tile_active_days_short, s.activeDays, s.activeDays),
        )
        val (dt, ut) = cmp(s.duration.toMinutes(), prev?.duration?.toMinutes())
        StatTile(stringResource(R.string.tile_time), formatHours(s.duration), Modifier.weight(1f).testTag("tile_time"), icon = FitnexIcons.Clock, delta = dt?.let { "$it min" }, deltaUp = ut)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        val (ds, us) = cmp(s.sets.toLong(), prev?.sets?.toLong())
        StatTile(
            stringResource(R.string.tile_sets), s.sets.toString(), Modifier.weight(1f).testTag("tile_sets"), icon = FitnexIcons.Layers, delta = ds, deltaUp = us,
            caption = pluralStringResource(R.plurals.tile_reps_caption, s.reps, s.reps),
        )
        // Zero when nothing with a load was lifted (bodyweight sets add no kilos).
        val (dv, uv) = cmp((s.volumeKg ?: 0f).toLong(), prev?.volumeKg?.toLong())
        StatTile(stringResource(R.string.tile_volume), formatKg(s.volumeKg ?: 0f), Modifier.weight(1f).testTag("tile_volume"), unit = "kg", icon = FitnexIcons.Kettlebell, delta = dv, deltaUp = uv)
    }
    // Cardio and timed work (planks, stretches): only when there is some, so strength-only users see no zeros.
    if (s.timedSeconds > 0 || s.distanceMeters > 0f) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            if (s.timedSeconds > 0) {
                StatTile(stringResource(R.string.tile_timed), formatHours(Duration.ofSeconds(s.timedSeconds)), Modifier.weight(1f).testTag("tile_timed"), icon = FitnexIcons.Timer, caption = stringResource(R.string.tile_timed_caption))
            }
            if (s.distanceMeters > 0f) {
                StatTile(stringResource(R.string.tile_distance), formatKm(s.distanceMeters), Modifier.weight(1f).testTag("tile_distance"), unit = "km", icon = FitnexIcons.Progress)
            }
        }
    }

    if (s.workouts == 0 && s.hasAnyHistory) {
        Text(stringResource(R.string.progress_no_sessions_period), style = FitnexTheme.typography.bodyMedium, color = colors.textSecondary)
    }

    FrequencyCard(s, locale)
    MuscleGroupsCard(s)

    // Spec §5/§20: the map shows what was worked in the selected period, from real sets only.
    val loads = s.muscles.associateBy { it.muscleId }
    MuscleMapCard(
        levels = state.heat,
        muscles = state.muscles,
        info = { id ->
            loads[id]?.let {
                stringResource(R.string.map_muscle_info, formatKg(it.weightedSets), pluralStringResource(R.plurals.sessions_count, it.sessions, it.sessions))
            }
                ?: stringResource(R.string.map_muscle_not_worked)
        },
        onOpenMuscle = onOpenMuscle,
    )

    GlassCard(Modifier.fillMaxWidth().testTag("section_exercises")) {
        SectionHeader(stringResource(R.string.section_exercises))
        if (s.exercises.isEmpty()) EmptyLine(R.string.exercises_empty)
        s.exercises.forEachIndexed { i, e ->
            if (i > 0) HorizontalDivider(color = colors.divider)
            Row(
                Modifier.fillMaxWidth().clickable { onOpenExercise(e.exerciseId) }.padding(vertical = Spacing.sm).testTag("exercise_stats_${e.exerciseId}"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(e.name, style = FitnexTheme.typography.titleMedium, color = colors.textPrimary)
                    Text(stringResource(R.string.exercise_row, e.sessions, shortDate(e.lastDone, locale)), style = FitnexTheme.typography.bodySmall, color = colors.textSecondary)
                }
                Text(
                    e.bestWeightKg?.let { stringResource(R.string.exercise_best_weight, formatKg(it)) } ?: stringResource(R.string.exercise_best_reps, e.bestReps),
                    style = FitnexTheme.typography.labelLarge, color = colors.accent,
                )
            }
        }
    }

    GlassCard(Modifier.fillMaxWidth().testTag("section_history")) {
        SectionHeader(stringResource(R.string.section_history))
        if (s.history.isEmpty()) EmptyLine(R.string.history_empty)
        s.history.take(10).forEachIndexed { i, h ->
            if (i > 0) HorizontalDivider(color = colors.divider)
            Column(Modifier.padding(vertical = Spacing.sm)) {
                Text("${shortDate(h.date, locale)} · ${h.routineName}", style = FitnexTheme.typography.titleMedium, color = colors.textPrimary)
                Text(stringResource(R.string.history_row, formatHours(h.duration), h.sets, h.exercises), style = FitnexTheme.typography.bodySmall, color = colors.textSecondary)
            }
        }
    }
}

@Composable
private fun EmptyLine(res: Int) {
    // Quiet, not an error: a small icon and one line.
    Row(Modifier.fillMaxWidth().padding(vertical = Spacing.md), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Icon(FitnexIcons.Progress, contentDescription = null, tint = FitnexTheme.colors.textDisabled, modifier = Modifier.size(18.dp))
        Text(stringResource(res), style = FitnexTheme.typography.bodyMedium, color = FitnexTheme.colors.textSecondary)
    }
}

// ---------------------------------------------------------------- Exercise stats

@Composable
internal fun ExerciseStatsScreen(
    state: ExerciseStatsUiState,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onMetric: (ExerciseMetric) -> Unit,
) {
    val colors = FitnexTheme.colors
    val locale = rememberAppLocale()
    val s = state.stats
    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(contentPadding)) {
        FitnexHeader(s?.name.orEmpty(), overline = stringResource(R.string.exstats_overline), onBack = onBack)
        Column(Modifier.padding(horizontal = Spacing.gutter), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            when {
                state.loading || s == null -> LoadingState()
                s.points.isEmpty() -> MessageState(title = "", message = stringResource(R.string.exstats_empty))
                else -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        StatTile(stringResource(R.string.exstats_sessions), s.points.size.toString(), Modifier.weight(1f).testTag("exstats_sessions"))
                        if (s.isBodyweight) {
                            StatTile(stringResource(R.string.exstats_best_reps), s.bestReps.toString(), Modifier.weight(1f), unit = "reps")
                        } else {
                            StatTile(stringResource(R.string.exstats_best_weight), formatKg(s.bestWeightKg ?: 0f), Modifier.weight(1f).testTag("exstats_best_weight"), unit = "kg")
                        }
                    }
                    s.bestEstimated1Rm?.let { StatTile(stringResource(R.string.exstats_best_1rm), formatKg(it), Modifier.fillMaxWidth(), unit = "kg") }

                    val metrics = ExerciseStatsViewModel.metricsFor(s)
                    val names = mapOf(
                        ExerciseMetric.WEIGHT to stringResource(R.string.metric_weight), ExerciseMetric.ONE_RM to stringResource(R.string.metric_1rm),
                        ExerciseMetric.VOLUME to stringResource(R.string.metric_volume), ExerciseMetric.REPS to stringResource(R.string.metric_reps),
                    )
                    if (metrics.size > 1) FitnexSegmentedControl(metrics, state.metric, onMetric, { names.getValue(it) })
                    GlassCard(Modifier.fillMaxWidth()) {
                        val points = s.points.map { p ->
                            ChartPoint(
                                shortDate(p.date, locale),
                                when (state.metric) {
                                    ExerciseMetric.WEIGHT -> p.bestWeightKg
                                    ExerciseMetric.ONE_RM -> p.bestEstimated1Rm ?: 0f
                                    ExerciseMetric.VOLUME -> p.volumeKg
                                    ExerciseMetric.REPS -> p.bestReps.toFloat()
                                },
                            )
                        }
                        if (points.size == 1) {
                            Text(stringResource(R.string.exstats_one_point), style = FitnexTheme.typography.bodyMedium, color = colors.textSecondary)
                        }
                        FitnexLineChart(
                            points,
                            stringResource(R.string.exstats_chart_a11y, names.getValue(state.metric), points.first().label, points.last().label),
                            Modifier.testTag("exstats_chart"),
                            unit = if (state.metric == ExerciseMetric.REPS) "" else " kg",
                        )
                        if (state.metric == ExerciseMetric.ONE_RM) {
                            Text(stringResource(R.string.exstats_1rm_note), style = FitnexTheme.typography.bodySmall, color = colors.textDisabled)
                        }
                    }
                    GlassCard(Modifier.fillMaxWidth()) {
                        SectionHeader(stringResource(R.string.exstats_history))
                        s.points.reversed().forEachIndexed { i, p ->
                            if (i > 0) HorizontalDivider(color = colors.divider)
                            Column(Modifier.padding(vertical = Spacing.sm)) {
                                Text(shortDate(p.date, locale), style = FitnexTheme.typography.labelMedium, color = colors.textSecondary)
                                Text(
                                    p.sets.joinToString("  ·  ") { if (it.weightKg > 0f) "${it.reps}×${formatKg(it.weightKg)}" else "${it.reps}" },
                                    style = FitnexTheme.typography.bodyLarge, color = colors.textPrimary,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Formatting

@Composable
internal fun regionLabel(r: AnatomicalRegion): String = stringResource(
    when (r) {
        AnatomicalRegion.CHEST -> R.string.region_chest
        AnatomicalRegion.BACK -> R.string.region_back
        AnatomicalRegion.SHOULDERS -> R.string.region_shoulders
        AnatomicalRegion.ARMS -> R.string.region_arms
        AnatomicalRegion.CORE -> R.string.region_core
        AnatomicalRegion.LEGS -> R.string.region_legs
        AnatomicalRegion.GLUTES -> R.string.region_glutes
        AnatomicalRegion.NECK -> R.string.region_neck
    },
)

internal fun bucketLabel(start: LocalDate, unit: BucketUnit, locale: Locale): String = when (unit) {
    BucketUnit.DAY -> if (locale.language == "es" && start.dayOfWeek == DayOfWeek.WEDNESDAY) "X"
        else start.dayOfWeek.getDisplayName(TextStyle.NARROW_STANDALONE, locale).uppercase(locale)
    BucketUnit.WEEK -> start.format(DateTimeFormatter.ofPattern("d/M", locale))
    BucketUnit.MONTH -> start.month.getDisplayName(TextStyle.SHORT_STANDALONE, locale).replaceFirstChar { it.titlecase(locale) }
}

internal fun shortDate(date: LocalDate, locale: Locale): String = date.format(DateTimeFormatter.ofPattern("d MMM", locale))

/** 90 min → "1 h 30 min", 45 min → "45 min". */
internal fun formatHours(d: Duration): String {
    val h = d.toHours()
    val m = d.toMinutes() % 60
    return if (h > 0) "$h h ${m} min" else "$m min"
}

/** 5400 m → "5,4" (km, one decimal). */
internal fun formatKm(meters: Float): String = formatKg(Math.round(meters / 100f) / 10f)

internal fun formatKg(kg: Float): String =
    if (kg % 1f == 0f) kg.toInt().toString() else "%d,%d".format(kg.toInt(), Math.round((kg - kg.toInt()) * 10))
