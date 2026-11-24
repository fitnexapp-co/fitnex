package com.fitnex.feature.nutrition.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.fitnex.core.model.FoodCategory
import com.fitnex.core.model.MealType
import com.fitnex.feature.nutrition.R
import java.util.Locale

@Composable
internal fun MealType.label(): String = stringResource(
    when (this) {
        MealType.BREAKFAST -> R.string.meal_breakfast
        MealType.LUNCH -> R.string.meal_lunch
        MealType.DINNER -> R.string.meal_dinner
        MealType.SNACK -> R.string.meal_snack
        MealType.DRINK -> R.string.meal_drink
    },
)

@Composable
internal fun FoodCategory.label(): String = stringResource(
    when (this) {
        FoodCategory.FRUIT -> R.string.cat_fruit
        FoodCategory.VEGETABLE -> R.string.cat_vegetable
        FoodCategory.LEGUME -> R.string.cat_legume
        FoodCategory.NUT -> R.string.cat_nut
        FoodCategory.CEREAL -> R.string.cat_cereal
        FoodCategory.BREAD -> R.string.cat_bread
        FoodCategory.DAIRY -> R.string.cat_dairy
        FoodCategory.EGG -> R.string.cat_egg
        FoodCategory.POULTRY -> R.string.cat_poultry
        FoodCategory.MEAT -> R.string.cat_meat
        FoodCategory.FISH -> R.string.cat_fish
        FoodCategory.SHELLFISH -> R.string.cat_shellfish
        FoodCategory.FAT -> R.string.cat_fat
        FoodCategory.SWEET -> R.string.cat_sweet
        FoodCategory.DRINK -> R.string.cat_drink
        FoodCategory.SNACK -> R.string.cat_snack
        FoodCategory.DISH -> R.string.cat_dish
        FoodCategory.SAUCE -> R.string.cat_sauce
    },
)

/** Profile allergy keys (onboarding) → readable name; unknown keys are custom allergies typed by the user. */
@Composable
internal fun allergenLabel(key: String): String = when (key) {
    "nuts" -> stringResource(R.string.allergen_nuts)
    "peanut" -> stringResource(R.string.allergen_peanut)
    "milk" -> stringResource(R.string.allergen_milk)
    "egg" -> stringResource(R.string.allergen_egg)
    "gluten" -> stringResource(R.string.allergen_gluten)
    "soy" -> stringResource(R.string.allergen_soy)
    "fish" -> stringResource(R.string.allergen_fish)
    "shellfish" -> stringResource(R.string.allergen_shellfish)
    "sesame" -> stringResource(R.string.allergen_sesame)
    else -> key
}

/** 12.0 → "12", 12.34 → "12,3" (Spanish decimal comma). */
internal fun fmt(v: Float, locale: Locale): String =
    if (v >= 100f || v % 1f == 0f) "%.0f".format(locale, v) else "%.1f".format(locale, v)
