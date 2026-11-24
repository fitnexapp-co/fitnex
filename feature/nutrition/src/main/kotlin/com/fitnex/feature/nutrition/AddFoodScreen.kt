package com.fitnex.feature.nutrition

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fitnex.core.designsystem.component.FitnexHeader
import com.fitnex.core.designsystem.component.FitnexSegmentedControl
import com.fitnex.core.designsystem.component.FitnexTextField
import com.fitnex.core.designsystem.component.LoadingState
import com.fitnex.core.designsystem.component.SectionHeader
import com.fitnex.core.designsystem.locale.rememberAppLocale
import com.fitnex.core.designsystem.theme.FitnexTheme
import com.fitnex.core.designsystem.theme.Spacing
import com.fitnex.core.model.Food
import com.fitnex.core.model.FoodCategory
import com.fitnex.core.model.NutritionMath
import com.fitnex.core.model.Recipe
import com.fitnex.feature.nutrition.ui.allergenLabel
import com.fitnex.feature.nutrition.ui.fmt
import com.fitnex.feature.nutrition.ui.label

/** Spec §6 "Buscador de alimentos": search the catalog, filter by category, re-add recent foods. */
@Composable
internal fun AddFoodScreen(
    state: AddFoodUiState,
    mealName: String,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onQuery: (String) -> Unit,
    onCategory: (FoodCategory?) -> Unit,
    onAdd: (Food, Float, String) -> Unit,
    /** Null in the meal plan: custom foods and barcode products are logged straight to the diary. */
    onCustom: (() -> Unit)?,
    onAddRecipe: (Recipe, Float, String) -> Unit = { _, _, _ -> },
    onNewRecipe: () -> Unit = {},
    onEditRecipe: (String) -> Unit = {},
    barcodeLookup: com.fitnex.core.data.nutrition.BarcodeFoodLookup? = null,
) {
    val colors = FitnexTheme.colors
    var picked by remember { mutableStateOf<Food?>(null) }
    var scanning by rememberSaveable { mutableStateOf(false) }
    var tab by rememberSaveable { mutableStateOf(AddTab.FOODS) }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        FitnexHeader(stringResource(R.string.add_title, mealName.lowercase()), overline = stringResource(R.string.add_overline), onBack = onBack)
        val tabLabels = mapOf(AddTab.FOODS to stringResource(R.string.tab_foods), AddTab.RECIPES to stringResource(R.string.tab_recipes))
        FitnexSegmentedControl(AddTab.entries, tab, { tab = it }, { tabLabels.getValue(it) }, Modifier.padding(horizontal = Spacing.gutter).padding(bottom = Spacing.sm).testTag("add_tabs"))
        if (tab == AddTab.RECIPES) {
            RecipeList(state, contentPadding, onAddRecipe, onNewRecipe, onEditRecipe)
            return@Column
        }
        Column(Modifier.padding(horizontal = Spacing.gutter)) {
            // Search and the barcode scanner side by side: one row, one glance.
            Row(verticalAlignment = Alignment.CenterVertically) {
                FitnexTextField(
                    value = state.query, onValueChange = onQuery, label = stringResource(R.string.search_hint),
                    modifier = Modifier.weight(1f).testTag("food_search"),
                    trailingIcon = { Icon(Icons.Rounded.Search, null, tint = colors.textSecondary) },
                )
                if (barcodeLookup != null) {
                    com.fitnex.core.designsystem.component.GlassIconButton(
                        com.fitnex.core.designsystem.icon.FitnexIcons.Barcode, stringResource(R.string.barcode_scan), { scanning = true },
                        Modifier.padding(start = Spacing.sm).testTag("barcode_scan"), iconSize = 24.dp,
                    )
                }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = Spacing.sm), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                CategoryChip(stringResource(R.string.all_categories), state.category == null) { onCategory(null) }
                FoodCategory.entries.forEach { c -> CategoryChip(c.label(), state.category == c, Modifier.testTag("cat_${c.name}")) { onCategory(if (state.category == c) null else c) } }
            }
        }
        if (state.loading) { LoadingState(); return@Column }
        LazyColumn(Modifier.fillMaxSize().testTag("food_results"), contentPadding = PaddingValues(start = Spacing.gutter, end = Spacing.gutter, bottom = contentPadding.calculateBottomPadding())) {
            val showRecent = state.query.isBlank() && state.category == null && state.recent.isNotEmpty()
            if (showRecent) {
                item { SectionHeader(stringResource(R.string.recent)) }
                items(state.recent, key = { "recent_${it.id}" }) { f -> FoodRow(f, state.audience) { picked = f } }
                item { HorizontalDivider(color = colors.divider, modifier = Modifier.padding(vertical = Spacing.sm)) }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(pluralStringResource(R.plurals.results, state.results.size, state.results.size), style = FitnexTheme.typography.labelMedium, color = colors.textSecondary, modifier = Modifier.weight(1f))
                    if (onCustom != null) TextButton(onClick = onCustom, modifier = Modifier.testTag("custom_food")) { Text(stringResource(R.string.custom_food), color = colors.accent) }
                }
            }
            if (state.results.isEmpty()) {
                item { Text(stringResource(R.string.no_results, state.query), style = FitnexTheme.typography.bodyMedium, color = colors.textSecondary, modifier = Modifier.padding(vertical = Spacing.md)) }
            }
            items(state.results, key = { it.id }) { f -> FoodRow(f, state.audience) { picked = f } }
            item { Text(stringResource(R.string.source_note), style = FitnexTheme.typography.bodySmall, color = colors.textDisabled, modifier = Modifier.padding(vertical = Spacing.lg)) }
        }
    }
    if (scanning && barcodeLookup != null) {
        com.fitnex.feature.nutrition.barcode.BarcodeScannerDialog(
            lookup = barcodeLookup,
            onFound = { food -> scanning = false; picked = food },
            onCreateCustom = { scanning = false; onCustom?.invoke() },
            onDismiss = { scanning = false },
        )
    }
    picked?.let { f ->
        PortionSheet(
            food = f, initialGrams = null, audience = state.audience, confirmLabel = stringResource(R.string.portion_add),
            onConfirm = { g, p -> onAdd(f, g, p); picked = null }, onDismiss = { picked = null },
        )
    }
}

private enum class AddTab { FOODS, RECIPES }

@Composable
private fun CategoryChip(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = FitnexTheme.colors
    FilterChip(
        selected = selected, onClick = onClick, label = { Text(text) }, modifier = modifier,
        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = colors.accent, selectedLabelColor = colors.onAccent, labelColor = colors.textPrimary),
    )
}

@Composable
private fun FoodRow(food: Food, audience: NutritionAudience, onClick: () -> Unit) {
    val colors = FitnexTheme.colors
    val locale = rememberAppLocale()
    val n = food.per100g
    val conflicts = NutritionMath.conflicts(food, audience.allergies)
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = Spacing.sm).testTag("food_${food.id}")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(food.name, style = FitnexTheme.typography.titleMedium, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (conflicts.isNotEmpty()) Text(stringResource(R.string.allergen_flag, conflicts.map { allergenLabel(it) }.joinToString()), style = FitnexTheme.typography.labelSmall, color = colors.warning)
        }
        Text(
            stringResource(R.string.food_row, stringResource(R.string.per_100g), fmt(n.proteinG, locale), fmt(n.carbsG, locale), fmt(n.fatG, locale)) +
                if (audience.showKcal) " · ${fmt(n.kcal, locale)} kcal" else "",
            style = FitnexTheme.typography.bodySmall, color = colors.textSecondary,
        )
    }
}
