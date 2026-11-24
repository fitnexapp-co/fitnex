package com.fitnex.feature.nutrition

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fitnex.core.data.nutrition.PlannedItem
import com.fitnex.core.designsystem.component.FitnexConfirmDialog
import com.fitnex.core.designsystem.component.FitnexHeader
import com.fitnex.core.designsystem.component.FitnexPrimaryButton
import com.fitnex.core.designsystem.component.GlassCard
import com.fitnex.core.designsystem.component.LoadingState
import com.fitnex.core.designsystem.locale.rememberAppLocale
import com.fitnex.core.designsystem.theme.FitnexTheme
import com.fitnex.core.designsystem.theme.Spacing
import com.fitnex.core.model.MealType
import com.fitnex.feature.nutrition.ui.fmt
import com.fitnex.feature.nutrition.ui.label
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

/**
 * Plan de comidas: what to eat each weekday, by meal. It repeats every week; one button copies
 * the selected day into today's diary.
 */
@Composable
internal fun MealPlanScreen(
    state: MealPlanUiState,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onSelectDay: (DayOfWeek) -> Unit,
    onAdd: (DayOfWeek, MealType) -> Unit,
    onRemove: (String) -> Unit,
    onCopyToToday: () -> Unit,
    onDismissCopied: () -> Unit,
) {
    val colors = FitnexTheme.colors
    val locale = rememberAppLocale()
    var confirmCopy by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(contentPadding)) {
        FitnexHeader(stringResource(R.string.plan_title), overline = stringResource(R.string.plan_overline), onBack = onBack)
        Column(Modifier.padding(horizontal = Spacing.gutter), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Text(stringResource(R.string.plan_body), style = FitnexTheme.typography.bodyMedium, color = colors.textSecondary)
            DayStrip(state, locale, onSelectDay)
            if (state.loading) {
                LoadingState()
                return@Column
            }
            val dayName = state.day.getDisplayName(TextStyle.FULL, locale).replaceFirstChar { it.titlecase(locale) }
            DayTotals(state, dayName, locale)
            state.copied?.let { n ->
                GlassCard(Modifier.fillMaxWidth().testTag("plan_copied"), contentPadding = PaddingValues(Spacing.md)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (n > 0) pluralStringResource(R.plurals.plan_copied, n, n) else stringResource(R.string.plan_copied_none),
                            style = FitnexTheme.typography.bodyMedium, color = colors.textPrimary, modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = onDismissCopied) { Text(stringResource(R.string.plan_ok), color = colors.accent) }
                    }
                }
            }
            MealType.entries.forEach { type ->
                PlanMealCard(type, state.dayItems.filter { it.mealType == type }, state, locale, onAdd = { onAdd(state.day, type) }, onRemove = onRemove)
            }
            FitnexPrimaryButton(
                stringResource(R.string.plan_copy_today, dayName.lowercase(locale)), { confirmCopy = true },
                Modifier.fillMaxWidth().testTag("plan_copy"), enabled = state.dayItems.isNotEmpty(),
            )
            Text(stringResource(R.string.estimates_note), style = FitnexTheme.typography.bodySmall, color = colors.textDisabled)
        }
    }
    if (confirmCopy) {
        FitnexConfirmDialog(
            title = stringResource(R.string.plan_copy_title),
            message = androidx.compose.ui.res.pluralStringResource(R.plurals.plan_copy_body, state.dayItems.size, state.dayItems.size),
            confirmLabel = stringResource(R.string.plan_copy_confirm),
            dismissLabel = stringResource(R.string.cancel),
            onConfirm = { confirmCopy = false; onCopyToToday() },
            onDismiss = { confirmCopy = false },
        )
    }
}

@Composable
private fun DayStrip(state: MealPlanUiState, locale: Locale, onSelect: (DayOfWeek) -> Unit) {
    val colors = FitnexTheme.colors
    Row(Modifier.fillMaxWidth().testTag("plan_days"), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        DayOfWeek.entries.forEach { d ->
            val selected = d == state.day
            val isToday = d == state.today.dayOfWeek
            val full = d.getDisplayName(TextStyle.FULL, locale)
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(14.dp))
                    .background(if (selected) colors.accent else colors.glassFill)
                    .clickable { onSelect(d) }
                    .semantics { role = Role.Tab; this.selected = selected; contentDescription = full }
                    .padding(vertical = Spacing.sm)
                    .testTag("plan_day_${d.value}"),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    d.getDisplayName(TextStyle.NARROW, locale).uppercase(locale),
                    style = FitnexTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = if (selected) colors.onAccent else colors.textPrimary,
                )
                val n = state.count(d)
                Box(
                    Modifier.padding(top = Spacing.xxs).size(6.dp).clip(CircleShape)
                        .background(
                            when {
                                n == 0 -> androidx.compose.ui.graphics.Color.Transparent
                                selected -> colors.onAccent
                                else -> colors.accent
                            },
                        ),
                )
                if (isToday) {
                    Text(stringResource(R.string.today), style = FitnexTheme.typography.labelSmall, color = if (selected) colors.onAccent else colors.textSecondary)
                }
            }
        }
    }
}

@Composable
private fun DayTotals(state: MealPlanUiState, dayName: String, locale: Locale) {
    val colors = FitnexTheme.colors
    val t = state.dayTotals
    GlassCard(Modifier.fillMaxWidth().testTag("plan_totals"), contentPadding = PaddingValues(Spacing.lg)) {
        Text(stringResource(R.string.plan_day_totals, dayName.lowercase(locale)), style = FitnexTheme.typography.labelMedium, color = colors.textSecondary)
        Row(Modifier.fillMaxWidth().padding(top = Spacing.sm)) {
            Total(stringResource(R.string.protein), fmt(t.proteinG, locale) + " g", MacroColors.Protein, Modifier.weight(1f))
            Total(stringResource(R.string.carbs_long), fmt(t.carbsG, locale) + " g", MacroColors.Carbs, Modifier.weight(1f))
            Total(stringResource(R.string.fat), fmt(t.fatG, locale) + " g", MacroColors.Fat, Modifier.weight(1f))
        }
        if (state.audience.showKcal) {
            Text(stringResource(R.string.kcal_estimate, fmt(t.kcal, locale)), style = FitnexTheme.typography.bodySmall, color = colors.textSecondary, modifier = Modifier.padding(top = Spacing.sm).testTag("plan_kcal"))
        }
    }
}

@Composable
private fun Total(label: String, value: String, color: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    val colors = FitnexTheme.colors
    Column(modifier.semantics(mergeDescendants = true) { }) {
        Text(value, style = FitnexTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = colors.textPrimary)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(color))
            Text(label, style = FitnexTheme.typography.bodySmall, color = colors.textSecondary, modifier = Modifier.padding(start = Spacing.xs))
        }
    }
}

@Composable
private fun PlanMealCard(
    type: MealType,
    items: List<PlannedItem>,
    state: MealPlanUiState,
    locale: Locale,
    onAdd: () -> Unit,
    onRemove: (String) -> Unit,
) {
    val colors = FitnexTheme.colors
    GlassCard(Modifier.fillMaxWidth().testTag("plan_meal_${type.name}"), contentPadding = PaddingValues(start = Spacing.lg, end = Spacing.sm, top = Spacing.sm, bottom = Spacing.md)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(type.label(), style = FitnexTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold), color = colors.textPrimary, modifier = Modifier.weight(1f))
            TextButton(onClick = onAdd, modifier = Modifier.testTag("plan_add_${type.name}")) {
                Icon(Icons.Rounded.Add, null, tint = colors.accent)
                Text(stringResource(R.string.meal_add), color = colors.accent)
            }
        }
        if (items.isEmpty()) {
            Text(stringResource(R.string.plan_meal_empty), style = FitnexTheme.typography.bodySmall, color = colors.textDisabled)
        }
        items.forEachIndexed { i, item ->
            if (i > 0) HorizontalDivider(color = colors.divider)
            val n = state.nutrientsOf(item)
            Row(Modifier.fillMaxWidth().height(56.dp).testTag("plan_item_${item.id}"), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(item.name, style = FitnexTheme.typography.titleMedium, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        if (n == null) stringResource(R.string.plan_item_missing)
                        else item.portion + " · " + stringResource(R.string.entry_macros, fmt(n.proteinG, locale), fmt(n.carbsG, locale), fmt(n.fatG, locale)),
                        style = FitnexTheme.typography.bodySmall, color = if (n == null) colors.warning else colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = { onRemove(item.id) }, modifier = Modifier.testTag("plan_remove_${item.id}")) {
                    Icon(Icons.Rounded.Close, stringResource(R.string.plan_remove_cd, item.name), tint = colors.textSecondary)
                }
            }
        }
    }
}
