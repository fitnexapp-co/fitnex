package com.fitnex.app.navigation

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector
import com.fitnex.app.R
import com.fitnex.core.designsystem.icon.FitnexIcons
import com.fitnex.core.model.AiScreenContext
import com.fitnex.feature.nutrition.NutritionRoute
import com.fitnex.feature.progress.ProgressRoute
import com.fitnex.feature.workout.WorkoutRoute
import kotlin.reflect.KClass

/**
 * Exactly three main sections (spec §2, §22). Calendar, statistics, profile and settings are
 * secondary screens reached from these — never a fourth tab.
 */
enum class TopLevelDestination(
    val route: Any,
    val routeClass: KClass<*>,
    val icon: ImageVector,
    @StringRes val labelRes: Int,
    val aiContext: AiScreenContext,
    val testTag: String,
) {
    PROGRESS(ProgressRoute, ProgressRoute::class, FitnexIcons.Progress, R.string.tab_progress, AiScreenContext.PROGRESS, "tab_progress"),
    NUTRITION(NutritionRoute, NutritionRoute::class, FitnexIcons.Nutrition, R.string.tab_nutrition, AiScreenContext.NUTRITION, "tab_nutrition"),
    WORKOUT(WorkoutRoute, WorkoutRoute::class, FitnexIcons.Workout, R.string.tab_workout, AiScreenContext.WORKOUT, "tab_workout"),
}
