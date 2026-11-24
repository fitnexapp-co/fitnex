package com.fitnex.feature.nutrition

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fitnex.core.designsystem.component.FitnexBottomSheet
import com.fitnex.core.designsystem.component.FitnexConfirmDialog
import com.fitnex.core.designsystem.component.FitnexHeader
import com.fitnex.core.designsystem.component.FitnexPrimaryButton
import com.fitnex.core.designsystem.component.FitnexSecondaryButton
import com.fitnex.core.designsystem.component.FitnexTextField
import com.fitnex.core.designsystem.component.GlassCard
import com.fitnex.core.designsystem.component.LoadingState
import com.fitnex.core.designsystem.component.SectionHeader
import com.fitnex.core.designsystem.component.picker.FitnexWheelPicker
import com.fitnex.core.designsystem.locale.rememberAppLocale
import com.fitnex.core.designsystem.theme.FitnexTheme
import com.fitnex.core.designsystem.theme.Spacing
import com.fitnex.core.model.Food
import com.fitnex.core.model.Nutrients
import com.fitnex.core.model.NutritionMath
import com.fitnex.core.model.Recipe
import com.fitnex.core.model.RecipeMath
import com.fitnex.feature.nutrition.ui.allergenLabel
import com.fitnex.feature.nutrition.ui.fmt

// ---------------------------------------------------------------- Recipes tab (add to a meal)

@Composable
internal fun RecipeList(
    state: AddFoodUiState,
    contentPadding: PaddingValues,
    onAddRecipe: (Recipe, Float, String) -> Unit,
    onNewRecipe: () -> Unit,
    onEditRecipe: (String) -> Unit,
) {
    val colors = FitnexTheme.colors
    var picked by remember { mutableStateOf<Recipe?>(null) }
    LazyColumn(
        Modifier.fillMaxSize().testTag("recipe_list"),
        contentPadding = PaddingValues(start = Spacing.gutter, end = Spacing.gutter, bottom = contentPadding.calculateBottomPadding()),
    ) {
        item {
            FitnexSecondaryButton(stringResource(R.string.recipe_new), onNewRecipe, Modifier.fillMaxWidth().padding(vertical = Spacing.sm).testTag("recipe_new"))
        }
        if (state.recipes.isEmpty()) {
            item { Text(stringResource(R.string.recipes_empty), style = FitnexTheme.typography.bodyMedium, color = colors.textSecondary, modifier = Modifier.padding(vertical = Spacing.md)) }
        }
        items(state.recipes, key = { it.id }) { r ->
            val n = RecipeMath.perServing(r, state.foods)
            val conflicts = RecipeMath.allergens(r, state.foods).intersect(state.audience.allergies)
            Row(Modifier.fillMaxWidth().clickable { picked = r }.padding(vertical = Spacing.sm).testTag("recipe_${r.name}"), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(r.name, style = FitnexTheme.typography.titleMedium, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    NutrientLine(n, state.audience, prefix = stringResource(R.string.per_serving))
                    if (conflicts.isNotEmpty()) Text(stringResource(R.string.entry_allergen, conflicts.map { allergenLabel(it) }.joinToString()), style = FitnexTheme.typography.bodySmall, color = colors.warning)
                }
                TextButton(onClick = { onEditRecipe(r.id) }) { Text(stringResource(R.string.recipe_edit), color = colors.accent) }
            }
            HorizontalDivider(color = colors.divider)
        }
    }
    picked?.let { r -> ServingsSheet(r, state, onConfirm = { s, label -> onAddRecipe(r, s, label); picked = null }, onDismiss = { picked = null }) }
}

private val SERVING_STEPS = (1..8).map { it * 0.5f }

@Composable
private fun ServingsSheet(recipe: Recipe, state: AddFoodUiState, onConfirm: (Float, String) -> Unit, onDismiss: () -> Unit) {
    val locale = rememberAppLocale()
    var index by rememberSaveable { mutableIntStateOf(1) }
    val servings = SERVING_STEPS[index]
    val n = RecipeMath.perServing(recipe, state.foods).scaled(servings)
    val label = stringResource(R.string.servings_label, fmt(servings, locale))
    FitnexBottomSheet(onDismiss = onDismiss, title = recipe.name, dragToDismiss = false) {
        val res = androidx.compose.ui.platform.LocalContext.current.resources
        FitnexWheelPicker(SERVING_STEPS, index, { index = it }, { res.getString(R.string.servings_label, fmt(it, locale)) }, Modifier.fillMaxWidth(), testTag = "servings_wheel")
        NutrientLine(n, state.audience)
        FitnexPrimaryButton(stringResource(R.string.portion_add), { onConfirm(servings, label) }, Modifier.fillMaxWidth().padding(top = Spacing.md).testTag("servings_confirm"))
    }
}

@Composable
internal fun NutrientLine(n: Nutrients, audience: NutritionAudience, prefix: String? = null) {
    val locale = rememberAppLocale()
    Text(
        listOfNotNull(prefix, stringResource(R.string.entry_macros, fmt(n.proteinG, locale), fmt(n.carbsG, locale), fmt(n.fatG, locale)),
            "${stringResource(R.string.fiber)} ${fmt(n.fiberG, locale)} g",
            if (audience.showKcal) "≈ ${fmt(n.kcal, locale)} kcal" else null).joinToString(" · "),
        style = FitnexTheme.typography.bodySmall, color = FitnexTheme.colors.textSecondary,
    )
}

// ---------------------------------------------------------------- Recipe editor

@Composable
internal fun RecipeEditorScreen(
    state: RecipeEditorUiState,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onName: (String) -> Unit,
    onServings: (Int) -> Unit,
    onAddItem: (Food, Float) -> Unit,
    onUpdateItem: (Int, Float) -> Unit,
    onRemoveItem: (Int) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = FitnexTheme.colors
    val locale = rememberAppLocale()
    var choosing by remember { mutableStateOf(false) }
    var portionFor by remember { mutableStateOf<Food?>(null) }
    var editingIndex by remember { mutableStateOf<Int?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(contentPadding)) {
        FitnexHeader(
            stringResource(if (state.isNew) R.string.recipe_new_title else R.string.recipe_edit_title),
            overline = stringResource(R.string.recipe_overline), onBack = onBack,
        )
        if (state.loading) { LoadingState(); return@Column }
        Column(Modifier.padding(horizontal = Spacing.gutter), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            FitnexTextField(value = state.name, onValueChange = onName, label = stringResource(R.string.recipe_name), modifier = Modifier.fillMaxWidth().testTag("recipe_name"))
            GlassCard(Modifier.fillMaxWidth()) {
                SectionHeader(stringResource(R.string.recipe_servings))
                FitnexWheelPicker(RecipeMath.SERVINGS.toList(), state.servings - 1, { onServings(it + 1) }, { "$it" }, Modifier.fillMaxWidth(), visibleCount = 3, testTag = "recipe_servings")
            }
            GlassCard(Modifier.fillMaxWidth().testTag("recipe_items")) {
                SectionHeader(stringResource(R.string.recipe_ingredients))
                if (state.items.isEmpty()) Text(stringResource(R.string.recipe_no_items), style = FitnexTheme.typography.bodyMedium, color = colors.textSecondary)
                state.items.forEachIndexed { i, item ->
                    val food = state.foods[item.foodId]
                    if (i > 0) HorizontalDivider(color = colors.divider)
                    Row(Modifier.fillMaxWidth().clickable(enabled = food != null) { editingIndex = i }.padding(vertical = Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                        Text(food?.name ?: item.foodId, style = FitnexTheme.typography.titleMedium, color = colors.textPrimary, modifier = Modifier.weight(1f))
                        Text("${fmt(item.grams, locale)} g", style = FitnexTheme.typography.bodyMedium, color = colors.textSecondary)
                        TextButton(onClick = { onRemoveItem(i) }) { Text(stringResource(R.string.entry_delete), color = colors.error) }
                    }
                }
                FitnexSecondaryButton(stringResource(R.string.recipe_add_item), { choosing = true }, Modifier.fillMaxWidth().padding(top = Spacing.sm).testTag("recipe_add_item"))
            }
            if (state.items.isNotEmpty()) {
                GlassCard(Modifier.fillMaxWidth()) {
                    SectionHeader(stringResource(R.string.per_serving))
                    NutrientLine(RecipeMath.perServing(state.recipe, state.foods), state.audience)
                    val conflicts = RecipeMath.allergens(state.recipe, state.foods).intersect(state.audience.allergies)
                    if (conflicts.isNotEmpty()) Text(stringResource(R.string.portion_allergen_warning, conflicts.map { allergenLabel(it) }.joinToString()), style = FitnexTheme.typography.bodyMedium, color = colors.warning)
                }
            }
            FitnexPrimaryButton(stringResource(R.string.recipe_save), onSave, Modifier.fillMaxWidth().testTag("recipe_save"), enabled = state.canSave)
            if (!state.isNew) {
                TextButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.recipe_delete), color = colors.error) }
            }
        }
    }

    if (choosing) FoodChooserSheet(state.catalog, state.audience, onPick = { choosing = false; portionFor = it }, onDismiss = { choosing = false })
    portionFor?.let { f ->
        PortionSheet(f, null, state.audience, stringResource(R.string.portion_add), onConfirm = { g, _ -> onAddItem(f, g); portionFor = null }, onDismiss = { portionFor = null })
    }
    editingIndex?.let { i ->
        val item = state.items.getOrNull(i)
        val f = item?.let { state.foods[it.foodId] }
        if (f != null) {
            PortionSheet(f, item.grams, state.audience, stringResource(R.string.portion_save), onConfirm = { g, _ -> onUpdateItem(i, g); editingIndex = null }, onDismiss = { editingIndex = null })
        }
    }
    if (confirmDelete) {
        FitnexConfirmDialog(
            title = stringResource(R.string.recipe_delete_title, state.name), message = stringResource(R.string.recipe_delete_message),
            confirmLabel = stringResource(R.string.recipe_delete), dismissLabel = stringResource(R.string.cancel),
            onConfirm = { confirmDelete = false; onDelete() }, onDismiss = { confirmDelete = false }, destructive = true,
        )
    }
}

/** Searchable catalog inside a sheet, to pick a recipe ingredient. */
@Composable
private fun FoodChooserSheet(catalog: List<Food>, audience: NutritionAudience, onPick: (Food) -> Unit, onDismiss: () -> Unit) {
    val colors = FitnexTheme.colors
    var query by rememberSaveable { mutableStateOf("") }
    val results = remember(query, catalog) { NutritionMath.search(catalog, query) }
    FitnexBottomSheet(onDismiss = onDismiss, title = stringResource(R.string.recipe_add_item)) {
        FitnexTextField(
            value = query, onValueChange = { query = it.take(40) }, label = stringResource(R.string.search_hint),
            modifier = Modifier.fillMaxWidth().testTag("recipe_food_search"),
            trailingIcon = { Icon(Icons.Rounded.Search, null, tint = colors.textSecondary) },
        )
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
            items(results, key = { it.id }) { f ->
                val conflicts = NutritionMath.conflicts(f, audience.allergies)
                Row(Modifier.fillMaxWidth().clickable { onPick(f) }.padding(vertical = Spacing.sm).testTag("choose_${f.id}"), verticalAlignment = Alignment.CenterVertically) {
                    Text(f.name, style = FitnexTheme.typography.titleMedium, color = colors.textPrimary, modifier = Modifier.weight(1f))
                    if (conflicts.isNotEmpty()) Text(stringResource(R.string.allergen_flag, conflicts.map { allergenLabel(it) }.joinToString()), style = FitnexTheme.typography.labelSmall, color = colors.warning)
                }
            }
        }
    }
}
