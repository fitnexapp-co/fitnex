package com.fitnex.feature.nutrition

import com.fitnex.core.designsystem.component.fitnexScreen

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.fitnex.feature.nutrition.ui.label
import kotlinx.serialization.Serializable

@Serializable
data object NutritionRoute

/** Search the catalog and add to one meal of one day. [date] is ISO, [mealType] a MealType name. */
@Serializable
data class AddFoodRoute(val date: String, val mealType: String, val planDay: Int? = null)

@Serializable
data class CustomFoodRoute(val date: String, val mealType: String)

/** [recipeId] null = new recipe. */
@Serializable
data class RecipeEditorRoute(val recipeId: String? = null)

@Serializable
data object NutritionWeekRoute

/** Weekly meal plan. */
@Serializable
data object MealPlanRoute

fun NavGraphBuilder.nutritionGraph(
    nav: NavController,
    tabPadding: PaddingValues,
    secondaryPadding: PaddingValues,
    onOpenSettings: () -> Unit,
) {
    fitnexScreen<NutritionRoute> {
        val vm: NutritionViewModel = hiltViewModel()
        val state by vm.state.collectAsStateWithLifecycle()
        DiaryScreen(
            state = state, vm = vm, contentPadding = tabPadding, onOpenSettings = onOpenSettings,
            onAddFood = { type -> nav.navigate(AddFoodRoute(state.date.toString(), type.name)) },
            onOpenWeek = { nav.navigate(NutritionWeekRoute) },
            onNewRecipe = { nav.navigate(RecipeEditorRoute()) },
            onOpenPlan = { nav.navigate(MealPlanRoute) },
        )
    }
    fitnexScreen<MealPlanRoute> {
        val vm: MealPlanViewModel = hiltViewModel()
        val state by vm.state.collectAsStateWithLifecycle()
        MealPlanScreen(
            state, secondaryPadding, onBack = nav::popBackStack, onSelectDay = vm::selectDay,
            onAdd = { day, type -> nav.navigate(AddFoodRoute(state.today.toString(), type.name, planDay = day.value)) },
            onRemove = vm::remove, onCopyToToday = vm::copyToToday, onDismissCopied = vm::dismissCopied,
        )
    }
    fitnexScreen<RecipeEditorRoute> {
        val vm: RecipeEditorViewModel = hiltViewModel()
        val state by vm.state.collectAsStateWithLifecycle()
        LaunchedEffect(vm) { vm.finished.collect { nav.popBackStack() } }
        RecipeEditorScreen(
            state, secondaryPadding, onBack = nav::popBackStack, onName = vm::setName, onServings = vm::setServings,
            onAddItem = vm::addItem, onUpdateItem = vm::updateItem, onRemoveItem = vm::removeItem, onSave = vm::save, onDelete = vm::delete,
        )
    }
    fitnexScreen<NutritionWeekRoute> {
        val vm: NutritionWeekViewModel = hiltViewModel()
        val state by vm.state.collectAsStateWithLifecycle()
        WeekScreen(state, secondaryPadding, onBack = nav::popBackStack)
    }
    fitnexScreen<AddFoodRoute> {
        val vm: AddFoodViewModel = hiltViewModel()
        val state by vm.state.collectAsStateWithLifecycle()
        LaunchedEffect(vm) { vm.finished.collect { nav.popBackStack() } }
        AddFoodScreen(
            state = state,
            mealName = vm.planDay?.let { d -> androidx.compose.ui.res.stringResource(R.string.plan_add_meal, vm.mealType.label(), d.getDisplayName(java.time.format.TextStyle.FULL, com.fitnex.core.designsystem.locale.rememberAppLocale())) } ?: vm.mealType.label(),
            contentPadding = secondaryPadding,
            onBack = nav::popBackStack, onQuery = vm::setQuery, onCategory = vm::setCategory, onAdd = vm::add,
            onCustom = if (vm.planDay != null) null else ({ nav.navigate(CustomFoodRoute(vm.date.toString(), vm.mealType.name)) }),
            onAddRecipe = vm::addRecipe,
            onNewRecipe = { nav.navigate(RecipeEditorRoute()) },
            onEditRecipe = { nav.navigate(RecipeEditorRoute(it)) },
            barcodeLookup = vm.barcode.takeIf { vm.planDay == null },
        )
    }
    fitnexScreen<CustomFoodRoute> {
        val vm: CustomFoodViewModel = hiltViewModel()
        val showKcal by vm.showKcal.collectAsStateWithLifecycle()
        LaunchedEffect(vm) {
            // Back to the diary: drop the search screen too.
            vm.finished.collect { nav.popBackStack<NutritionRoute>(inclusive = false) }
        }
        CustomFoodScreen(vm.mealType.label(), showKcal, secondaryPadding, onBack = nav::popBackStack, onSave = vm::save)
    }
}
