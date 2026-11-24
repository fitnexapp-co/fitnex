package com.fitnex.feature.progress

import com.fitnex.core.designsystem.component.fitnexScreen

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable
data object ProgressRoute

/** Per-exercise statistics page (spec §5: "cada ejercicio debe tener su propia página"). */
@Serializable
data class ExerciseStatsRoute(val exerciseId: String)

/** Muscle information page (spec §5 "Información de cada músculo"); opened from any muscle map. */
@Serializable
data class MuscleDetailRoute(val muscleId: String)

/** Opens a muscle page; moving between muscles replaces the page instead of stacking them. */
fun NavController.navigateToMuscle(muscleId: String) {
    val replacing = currentBackStackEntry?.destination?.hasRoute<MuscleDetailRoute>() == true
    navigate(MuscleDetailRoute(muscleId)) {
        if (replacing) popUpTo<MuscleDetailRoute> { inclusive = true }
    }
}

fun NavGraphBuilder.progressGraph(
    nav: NavController,
    tabPadding: PaddingValues,
    secondaryPadding: PaddingValues,
    onOpenCalendar: () -> Unit,
    onOpenSettings: () -> Unit,
    onGoTrain: () -> Unit,
    onOpenExerciseDetail: (String) -> Unit,
    onAnalyzeProgress: () -> Unit,
) {
    fitnexScreen<ProgressRoute> {
        val vm: ProgressViewModel = hiltViewModel()
        val state by vm.state.collectAsStateWithLifecycle()
        ProgressScreen(
            state = state,
            contentPadding = tabPadding,
            onOpenCalendar = onOpenCalendar,
            onOpenSettings = onOpenSettings,
            onPeriod = vm::setPeriod,
            onOpenExercise = { nav.navigate(ExerciseStatsRoute(it)) },
            onOpenMuscle = nav::navigateToMuscle,
            onGoTrain = onGoTrain,
            onAnalyzeProgress = onAnalyzeProgress,
            onSetGoal = vm::setWeeklyGoal,
            onAddMeasurement = { vm.addMeasurement(it) },
            onDeleteMeasurement = { vm.deleteMeasurement(it) },
        )
    }
    fitnexScreen<ExerciseStatsRoute> {
        val vm: ExerciseStatsViewModel = hiltViewModel()
        val state by vm.state.collectAsStateWithLifecycle()
        ExerciseStatsScreen(state, secondaryPadding, onBack = nav::popBackStack, onMetric = vm::setMetric)
    }
    fitnexScreen<MuscleDetailRoute> {
        val vm: MuscleDetailViewModel = hiltViewModel()
        val state by vm.state.collectAsStateWithLifecycle()
        MuscleDetailScreen(
            state = state,
            contentPadding = secondaryPadding,
            onBack = nav::popBackStack,
            onOpenMuscle = nav::navigateToMuscle,
            onOpenExercise = onOpenExerciseDetail,
        )
    }
}
