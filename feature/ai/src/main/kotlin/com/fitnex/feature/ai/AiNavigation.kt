package com.fitnex.feature.ai

import com.fitnex.core.designsystem.component.fitnexScreen

import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import com.fitnex.core.model.AiScreenContext
import kotlinx.serialization.Serializable

/**
 * FITNEX AI chat. [sourceScreen] is an [AiScreenContext.key] identifying where it was opened
 * from, so only that screen's data is attached to the request (spec §19).
 */
@Serializable
data class AiChatRoute(
    val sourceScreen: String,
    /** True when the user tapped an action such as "Analizar mi progreso": its question is sent at once. */
    val ask: Boolean = false,
)

/** [ask]: send the context's suggested question right away (e.g. "Analizar mi progreso"). */
fun NavController.navigateToAiChat(context: AiScreenContext, ask: Boolean = false, navOptions: NavOptions? = null) =
    navigate(AiChatRoute(context.key, ask), navOptions)

fun NavGraphBuilder.aiChatScreen(onBack: () -> Unit) {
    fitnexScreen<AiChatRoute> {
        val vm: AiChatViewModel = hiltViewModel()
        val state by vm.state.collectAsStateWithLifecycle()
        AiChatScreen(state, vm, onBack)
    }
}

internal fun AiScreenContext.labelRes(): Int = when (this) {
    AiScreenContext.PROGRESS -> R.string.ai_ctx_progress
    AiScreenContext.NUTRITION -> R.string.ai_ctx_nutrition
    AiScreenContext.WORKOUT -> R.string.ai_ctx_workout
    AiScreenContext.CALENDAR -> R.string.ai_ctx_calendar
    AiScreenContext.STATISTICS -> R.string.ai_ctx_statistics
    AiScreenContext.EXERCISE_LIBRARY -> R.string.ai_ctx_library
    AiScreenContext.EXERCISE_DETAIL -> R.string.ai_ctx_exercise
    AiScreenContext.MUSCLE_DETAIL -> R.string.ai_ctx_muscle
}
