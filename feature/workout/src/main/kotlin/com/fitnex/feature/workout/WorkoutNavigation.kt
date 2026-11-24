package com.fitnex.feature.workout

import com.fitnex.core.designsystem.component.fitnexScreen

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.fitnex.feature.workout.editor.RoutineEditorScreen
import com.fitnex.feature.workout.editor.RoutineEditorViewModel
import com.fitnex.feature.workout.live.LiveWorkoutScreen
import com.fitnex.feature.workout.live.LiveWorkoutViewModel
import com.fitnex.feature.workout.live.WorkoutSummaryScreen
import com.fitnex.feature.workout.live.WorkoutSummaryViewModel
import kotlinx.serialization.Serializable

@Serializable data object WorkoutRoute

/** [pickMode] = choosing exercises for the routine editor (returns ids to the previous entry). */
@Serializable data class ExerciseLibraryRoute(val pickMode: Boolean = false)

@Serializable data class ExerciseDetailRoute(val exerciseId: String)

/** [routineId] null = new routine. */
@Serializable data class RoutineEditorRoute(val routineId: String? = null)

/** Full-screen live mode. [routineId] null = resume the active session. */
@Serializable data class LiveWorkoutRoute(val routineId: String? = null)

@Serializable data class WorkoutSummaryRoute(val sessionId: String)

/** Every Workout screen. The tab root keeps the floating bar padding; secondary screens their own. */
fun NavGraphBuilder.workoutGraph(
    nav: NavController,
    tabPadding: PaddingValues,
    secondaryPadding: PaddingValues,
    onOpenCalendar: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenMuscle: (String) -> Unit,
) {
    fitnexScreen<WorkoutRoute> {
        val vm: WorkoutHomeViewModel = hiltViewModel()
        val state by vm.state.collectAsStateWithLifecycle()
        WorkoutHomeScreen(
            state = state,
            contentPadding = tabPadding,
            onOpenCalendar = onOpenCalendar,
            onOpenSettings = onOpenSettings,
            onOpenLibrary = { nav.navigate(ExerciseLibraryRoute()) },
            onNewRoutine = { nav.navigate(RoutineEditorRoute()) },
            onOpenRoutine = { nav.navigate(RoutineEditorRoute(it)) },
            onStartRoutine = { nav.navigate(LiveWorkoutRoute(it)) { launchSingleTop = true } },
            onResumeWorkout = { nav.navigate(LiveWorkoutRoute()) { launchSingleTop = true } },
        )
    }
    fitnexScreen<LiveWorkoutRoute> {
        val vm: LiveWorkoutViewModel = hiltViewModel()
        val state by vm.state.collectAsStateWithLifecycle()
        val notifications by vm.notificationsEnabled.collectAsStateWithLifecycle()
        LiveWorkoutScreen(
            state = state,
            vm = vm,
            notificationsEnabled = notifications,
            onClose = nav::popBackStack,
            onFinished = { sessionId ->
                nav.navigate(WorkoutSummaryRoute(sessionId)) {
                    popUpTo<LiveWorkoutRoute> { inclusive = true }
                }
            },
        )
    }
    fitnexScreen<WorkoutSummaryRoute> {
        val vm: WorkoutSummaryViewModel = hiltViewModel()
        val summary by vm.summary.collectAsStateWithLifecycle()
        val streak by vm.streak.collectAsStateWithLifecycle()
        WorkoutSummaryScreen(summary, onDone = nav::popBackStack, streak = streak)
    }
    fitnexScreen<ExerciseLibraryRoute> {
        val vm: LibraryViewModel = hiltViewModel()
        val state by vm.state.collectAsStateWithLifecycle()
        ExerciseLibraryScreen(
            state = state,
            contentPadding = secondaryPadding,
            onBack = nav::popBackStack,
            onFilter = vm::updateFilter,
            onClearFilters = vm::clearFilters,
            onOpenExercise = { nav.navigate(ExerciseDetailRoute(it)) },
            onToggleSelection = vm::toggleSelection,
            onConfirmSelection = { ids ->
                nav.previousBackStackEntry?.savedStateHandle?.set(RoutineEditorViewModel.PICKED_EXERCISES_KEY, ArrayList(ids))
                nav.popBackStack()
            },
        )
    }
    fitnexScreen<ExerciseDetailRoute> {
        val vm: ExerciseDetailViewModel = hiltViewModel()
        val state by vm.state.collectAsStateWithLifecycle()
        ExerciseDetailScreen(
            state, secondaryPadding,
            onBack = nav::popBackStack,
            onToggleFavorite = { vm.toggleFavorite() },
            onOpenMuscle = onOpenMuscle,
            onDelete = { vm.deleteCustom { nav.popBackStack() } },
        )
    }
    fitnexScreen<RoutineEditorRoute> { entry ->
        val vm: RoutineEditorViewModel = hiltViewModel()
        val state by vm.state.collectAsStateWithLifecycle()
        // Result from the library picker, delivered to this entry's handle; consume it exactly once.
        val picked by entry.savedStateHandle
            .getStateFlow<ArrayList<String>?>(RoutineEditorViewModel.PICKED_EXERCISES_KEY, null)
            .collectAsStateWithLifecycle()
        LaunchedEffect(picked) {
            picked?.let { ids ->
                entry.savedStateHandle[RoutineEditorViewModel.PICKED_EXERCISES_KEY] = null
                vm.onExercisesPicked(ids)
            }
        }
        RoutineEditorScreen(
            state = state,
            onBack = nav::popBackStack,
            onName = vm::setName,
            onToggleDay = vm::toggleDay,
            onDuplicate = vm::duplicate,
            onAddExercises = { nav.navigate(ExerciseLibraryRoute(pickMode = true)) },
            onMove = vm::move,
            onRemove = vm::remove,
            onConfigure = vm::configure,
            onSave = vm::save,
            onDelete = vm::delete,
        )
    }
}
