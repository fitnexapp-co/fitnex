package com.fitnex.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitnex.app.R
import com.fitnex.core.data.session.SessionRepository
import com.fitnex.core.data.training.WorkoutSessionRepository
import com.fitnex.core.designsystem.component.chromeSurface
import com.fitnex.core.designsystem.component.liquidInteraction
import com.fitnex.core.designsystem.icon.FitnexIcons
import com.fitnex.core.designsystem.theme.Elevation
import com.fitnex.core.designsystem.theme.FitnexTheme
import com.fitnex.core.designsystem.theme.Spacing


import com.fitnex.core.model.ActiveSession
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class ActiveWorkoutViewModel @Inject constructor(
    session: SessionRepository,
    sessions: WorkoutSessionRepository,
) : ViewModel() {
    @OptIn(ExperimentalCoroutinesApi::class)
    val active: StateFlow<ActiveSession?> = session.currentUserId
        .flatMapLatest { uid -> uid?.let(sessions::observeActive) ?: flowOf(null) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

/**
 * Floating "now training" bar above the tab bar while a workout is open, like a music app's
 * mini player: routine, live time, and one tap to go back to it.
 */
@Composable
internal fun MiniWorkoutBar(active: ActiveSession, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val colors = FitnexTheme.colors
    val shape = RoundedCornerShape(26.dp)
    val interaction = remember { MutableInteractionSource() }
    val now by produceState(Instant.now(), active.id, active.isPaused) {
        while (!active.isPaused) { value = Instant.now(); delay(1_000) }
        value = Instant.now()
    }
    val seconds = active.elapsed(now).seconds
    Row(
        modifier
            .fillMaxWidth()
            .height(64.dp)
            .liquidInteraction(shape, interaction)
            .chromeSurface(shape, Elevation.floating, body = 0.66f)
            .clickable(interaction, indication = null, role = Role.Button, onClick = onOpen)
            .padding(horizontal = Spacing.sm)
            .testTag("mini_workout_bar"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(Brush.linearGradient(listOf(colors.accentStrong, colors.accent))),
            contentAlignment = Alignment.Center,
        ) { Icon(FitnexIcons.Workout, null, tint = colors.onAccent, modifier = Modifier.size(24.dp)) }
        Column(Modifier.weight(1f).padding(horizontal = Spacing.md)) {
            Text(
                active.routineName, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = FitnexTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold), color = colors.textPrimary,
            )
            Text(
                stringResource(if (active.isPaused) R.string.mini_workout_paused else R.string.mini_workout_running, if (seconds >= 3600) "%d:%02d:%02d".format(seconds / 3600, seconds % 3600 / 60, seconds % 60) else "%d:%02d".format(seconds / 60, seconds % 60), active.sets.size),
                style = FitnexTheme.typography.bodySmall, color = colors.textSecondary, maxLines = 1,
            )
        }
        Icon(
            Icons.Rounded.PlayArrow,
            contentDescription = stringResource(R.string.mini_workout_open),
            tint = colors.textPrimary,
            modifier = Modifier.padding(end = Spacing.sm).size(30.dp),
        )
    }
}
