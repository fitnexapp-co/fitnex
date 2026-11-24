package com.fitnex.feature.progress

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fitnex.core.designsystem.component.FitnexSegmentedControl
import com.fitnex.core.designsystem.component.GlassCard
import com.fitnex.core.designsystem.component.chart.ChartPoint
import com.fitnex.core.designsystem.component.chart.FitnexBarChart
import com.fitnex.core.designsystem.component.chart.FitnexDonutChart
import com.fitnex.core.designsystem.component.chart.FitnexRings
import com.fitnex.core.designsystem.component.chart.RingValue
import com.fitnex.core.designsystem.icon.FitnexIcons
import com.fitnex.core.designsystem.theme.FitnexTheme
import com.fitnex.core.designsystem.theme.Spacing
import com.fitnex.core.model.Achievements
import com.fitnex.core.model.BucketUnit
import com.fitnex.core.model.GlobalStats
import com.fitnex.core.model.StatsCalculator
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Card title row: violet line icon, title, optional control on the right. */
@Composable
internal fun CardTitle(icon: ImageVector, title: String, trailing: (@Composable () -> Unit)? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.sm)) {
        Icon(icon, contentDescription = null, tint = FitnexTheme.colors.accent, modifier = Modifier.size(26.dp))
        Text(
            title, style = FitnexTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold), color = FitnexTheme.colors.textPrimary,
            modifier = Modifier.padding(start = Spacing.md).weight(1f),
        )
        trailing?.invoke()
    }
}

/** Frequency of workouts, regrouped on demand by day, week or month over the selected period. */
@Composable
internal fun FrequencyCard(s: GlobalStats, locale: Locale) {
    val units = listOf(BucketUnit.DAY, BucketUnit.WEEK, BucketUnit.MONTH)
    var unit by rememberSaveable(s.period) { mutableStateOf(s.bucketUnit) }
    val labels = mapOf(
        BucketUnit.DAY to stringResource(R.string.freq_days),
        BucketUnit.WEEK to stringResource(R.string.freq_weeks),
        BucketUnit.MONTH to stringResource(R.string.freq_months),
    )
    GlassCard(Modifier.fillMaxWidth()) {
        CardTitle(FitnexIcons.Chart, stringResource(R.string.section_frequency))
        FitnexSegmentedControl(units, unit, { unit = it }, { labels.getValue(it) }, Modifier.padding(bottom = Spacing.sm).testTag("frequency_unit"), compact = true)
        val counts = s.sessionDates.groupingBy { StatsCalculator.bucketStart(it, unit) }.eachCount()
        val fmt = DateTimeFormatter.ofPattern(if (unit == BucketUnit.MONTH) "MMM" else "d/M", locale)
        val bars = StatsCalculator.buckets(s.range, unit).map { ChartPoint(if (unit == BucketUnit.MONTH) it.format(fmt).replaceFirstChar { c -> c.titlecase(locale) } else it.format(fmt), (counts[it] ?: 0).toFloat()) }
        val unitName = stringResource(when (unit) { BucketUnit.DAY -> R.string.unit_day; BucketUnit.WEEK -> R.string.unit_week; BucketUnit.MONTH -> R.string.unit_month })
        FitnexBarChart(
            bars, stringResource(R.string.frequency_a11y, unitName, bars.joinToString { "${it.label} ${it.value.toInt()}" }),
            Modifier.testTag("chart_frequency"), wholeNumbers = true, maxLabels = 6,
        )
    }
}

/** Where the work went: share of sets per body area, with the main one called out. */
@Composable
internal fun MuscleGroupsCard(s: GlobalStats) {
    val colors = FitnexTheme.colors
    GlassCard(Modifier.fillMaxWidth().testTag("section_muscles")) {
        CardTitle(FitnexIcons.Body, stringResource(R.string.groups_title))
        if (s.regions.isEmpty()) {
            Text(stringResource(R.string.muscles_empty), style = FitnexTheme.typography.bodyMedium, color = colors.textSecondary, modifier = Modifier.padding(vertical = Spacing.md))
        } else {
            val top = s.regions.take(5)
            val rest = s.regions.drop(5).sumOf { it.weightedSets.toDouble() }.toFloat()
            val slices = top.map { ChartPoint(regionLabel(it.region), it.weightedSets) } +
                if (rest > 0f) listOf(ChartPoint(stringResource(R.string.groups_other), rest)) else emptyList()
            val total = s.regions.sumOf { it.weightedSets.toDouble() }.toFloat()
            // Main group chip, like a highlighted tag.
            Row(
                Modifier.padding(bottom = Spacing.md).background(colors.accent.copy(alpha = 0.12f), RoundedCornerShape(14.dp)).padding(horizontal = Spacing.md, vertical = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(10.dp).background(colors.accent, CircleShape))
                Column(Modifier.padding(start = Spacing.sm)) {
                    Text(regionLabel(top.first().region), style = FitnexTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold), color = colors.textPrimary)
                    Text(stringResource(R.string.groups_main), style = FitnexTheme.typography.labelSmall, color = colors.textSecondary)
                }
            }
            FitnexDonutChart(
                slices = slices,
                centerValue = formatKg(total),
                centerLabel = stringResource(R.string.groups_center),
                accessibilitySummary = stringResource(R.string.muscles_a11y, slices.joinToString { "${it.label} ${formatKg(it.value)}" }),
                modifier = Modifier.testTag("chart_muscles"),
            )
        }
        Text(stringResource(R.string.muscles_note), style = FitnexTheme.typography.bodySmall, color = colors.textDisabled, modifier = Modifier.padding(top = Spacing.md))
    }
}
