package com.fitnex.feature.nutrition

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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.fitnex.core.designsystem.component.FitnexHeader
import com.fitnex.core.designsystem.component.GlassCard
import com.fitnex.core.designsystem.component.LoadingState
import com.fitnex.core.designsystem.component.SectionHeader
import com.fitnex.core.designsystem.component.StatTile
import com.fitnex.core.designsystem.component.chart.ChartPoint
import com.fitnex.core.designsystem.component.chart.FitnexBarChart
import com.fitnex.core.designsystem.locale.rememberAppLocale
import com.fitnex.core.designsystem.theme.FitnexTheme
import com.fitnex.core.designsystem.theme.Spacing
import com.fitnex.core.model.FoodCategory
import com.fitnex.core.model.NutritionDayStat
import com.fitnex.core.model.NutritionWeek
import com.fitnex.feature.nutrition.ui.fmt
import com.fitnex.feature.nutrition.ui.label
import java.time.format.TextStyle
import java.util.Locale

/**
 * Spec §20 "Estadísticas de nutrición": history, variety, nutrients, hydration and habits of the
 * last 7 days. Descriptive only: no targets, scores or comparisons (spec §6, §20).
 */
@Composable
internal fun WeekScreen(state: WeekUiState, contentPadding: PaddingValues, onBack: () -> Unit) {
    val colors = FitnexTheme.colors
    val locale = rememberAppLocale()
    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(contentPadding)) {
        FitnexHeader(stringResource(R.string.week_title), overline = stringResource(R.string.week_overline), onBack = onBack)
        val week = state.week
        if (state.loading || week == null) { LoadingState(); return@Column }
        Column(Modifier.padding(horizontal = Spacing.gutter), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            if (week.loggedDays.isEmpty()) {
                Text(stringResource(R.string.week_empty), style = FitnexTheme.typography.bodyMedium, color = colors.textSecondary, modifier = Modifier.testTag("week_empty"))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                StatTile(stringResource(R.string.week_days_logged), "${week.loggedDays.size}/7", Modifier.weight(1f).testTag("week_days"))
                StatTile(stringResource(R.string.week_variety), week.distinctFoods.toString(), Modifier.weight(1f).testTag("week_variety"))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                StatTile(stringResource(R.string.week_breakfasts), "${week.daysWithBreakfast}/7", Modifier.weight(1f))
                StatTile(stringResource(R.string.week_groups), "${week.categoriesEaten.size}", Modifier.weight(1f), caption = stringResource(R.string.week_groups_caption))
            }

            Chart(stringResource(R.string.week_water), week, locale, "ml", "week_water_chart") { it.waterMl.toFloat() }
            Chart(stringResource(R.string.protein), week, locale, "g", "week_protein_chart") { it.nutrients.proteinG }
            Chart(stringResource(R.string.fiber), week, locale, "g", "week_fiber_chart") { it.nutrients.fiberG }

            GlassCard(Modifier.fillMaxWidth()) {
                SectionHeader(stringResource(R.string.week_averages))
                Text(stringResource(R.string.week_averages_note), style = FitnexTheme.typography.bodySmall, color = colors.textDisabled)
                val avg = { pick: (NutritionDayStat) -> Float -> week.averagePerLoggedDay(pick)?.let { fmt(it, locale) } ?: "—" }
                Line(stringResource(R.string.protein), avg { it.nutrients.proteinG } + " g")
                Line(stringResource(R.string.carbs), avg { it.nutrients.carbsG } + " g")
                Line(stringResource(R.string.fat), avg { it.nutrients.fatG } + " g")
                Line(stringResource(R.string.fiber), avg { it.nutrients.fiberG } + " g")
                Line(stringResource(R.string.nutrient_calcium), avg { it.nutrients.calciumMg } + " mg")
                Line(stringResource(R.string.nutrient_iron), avg { it.nutrients.ironMg } + " mg")
                Line(stringResource(R.string.nutrient_vitamin_c), avg { it.nutrients.vitaminCMg } + " mg")
                Line(stringResource(R.string.nutrient_potassium), avg { it.nutrients.potassiumMg } + " mg")
                Line(stringResource(R.string.week_water), avg { it.waterMl.toFloat() } + " ml")
                if (state.audience.showKcal) Line(stringResource(R.string.week_kcal), "≈ " + avg { it.nutrients.kcal } + " kcal")
            }

            GlassCard(Modifier.fillMaxWidth().testTag("week_top_foods")) {
                SectionHeader(stringResource(R.string.week_top_foods))
                if (week.topFoods.isEmpty()) Text(stringResource(R.string.week_no_foods), style = FitnexTheme.typography.bodyMedium, color = colors.textSecondary)
                week.topFoods.forEach { f -> Line(f.name, pluralStringResource(R.plurals.times, f.times, f.times)) }
            }

            GlassCard(Modifier.fillMaxWidth()) {
                SectionHeader(stringResource(R.string.week_groups_title))
                val eaten = week.categoriesEaten
                Text(
                    FoodCategory.entries.filter { it in eaten }.map { it.label() }.joinToString().ifEmpty { "—" },
                    style = FitnexTheme.typography.bodyMedium, color = colors.textPrimary,
                )
                Text(stringResource(R.string.week_variety_note), style = FitnexTheme.typography.bodySmall, color = colors.textSecondary, modifier = Modifier.padding(top = Spacing.xs))
            }
        }
    }
}

@Composable
private fun Chart(title: String, week: NutritionWeek, locale: Locale, unit: String, tag: String, value: (NutritionDayStat) -> Float) {
    GlassCard(Modifier.fillMaxWidth()) {
        SectionHeader(title)
        val bars = week.days.map { ChartPoint(it.date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale).take(3), value(it)) }
        FitnexBarChart(bars, "$title ($unit): " + bars.joinToString { "${it.label} ${fmt(it.value, locale)}" }, Modifier.testTag(tag), wholeNumbers = true)
    }
}

@Composable
private fun Line(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = Spacing.xs)) {
        Text(label, style = FitnexTheme.typography.bodyMedium, color = FitnexTheme.colors.textSecondary, modifier = Modifier.weight(1f))
        Text(value, style = FitnexTheme.typography.bodyMedium, color = FitnexTheme.colors.textPrimary)
    }
}
