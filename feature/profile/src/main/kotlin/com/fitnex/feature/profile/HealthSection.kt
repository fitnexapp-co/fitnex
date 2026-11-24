package com.fitnex.feature.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.WeightRecord
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.fitnex.core.data.health.HealthAvailability
import com.fitnex.core.data.health.HealthConnectRepository
import com.fitnex.core.data.health.HealthToday
import com.fitnex.core.data.profile.UserProfileRepository
import com.fitnex.core.data.session.SessionRepository
import com.fitnex.core.designsystem.component.FitnexPrimaryButton
import com.fitnex.core.designsystem.component.FitnexSecondaryButton
import com.fitnex.core.designsystem.component.GlassCard
import com.fitnex.core.designsystem.component.SectionHeader
import com.fitnex.core.designsystem.theme.FitnexTheme
import com.fitnex.core.designsystem.theme.Spacing
import com.fitnex.core.model.AgeProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class HealthUiState(
    val availability: HealthAvailability = HealthAvailability.UNSUPPORTED,
    val connected: Boolean = false,
    val today: HealthToday? = null,
    val adult: Boolean = false,
)

@HiltViewModel
class HealthViewModel @Inject constructor(
    private val health: HealthConnectRepository,
    private val session: SessionRepository,
    private val profiles: UserProfileRepository,
    private val clock: Clock,
) : ViewModel() {
    private val _state = MutableStateFlow(HealthUiState())
    val state: StateFlow<HealthUiState> = _state.asStateFlow()

    /** Minors are never asked for (nor shown) body weight. */
    fun requested(adult: Boolean): Set<String> =
        if (adult) health.permissions else health.permissions - HealthPermission.getReadPermission(WeightRecord::class)

    fun refresh() = viewModelScope.launch {
        val uid = session.currentUserId.first()
        val adult = uid?.let { profiles.get(it) }?.ageProfile(LocalDate.now(clock)) == AgeProfile.ADULT
        val connected = health.isConnected()
        _state.value = HealthUiState(health.availability(), connected, if (connected) health.today() else null, adult)
    }
}

/** "Health Connect": connect once, then steps/heart rate come in and workouts go out. */
@Composable
internal fun HealthSection(vm: HealthViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val colors = FitnexTheme.colors
    LaunchedEffect(Unit) { vm.refresh() }
    val request = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { vm.refresh() }

    GlassCard(Modifier.fillMaxWidth().testTag("health_section")) {
        SectionHeader(stringResource(R.string.health_title))
        Text(stringResource(R.string.health_desc), style = FitnexTheme.typography.bodySmall, color = colors.textSecondary)
        when {
            state.availability == HealthAvailability.UNSUPPORTED ->
                Text(stringResource(R.string.health_unsupported), style = FitnexTheme.typography.bodyMedium, color = colors.textSecondary, modifier = Modifier.padding(top = Spacing.sm))
            state.availability == HealthAvailability.NEEDS_INSTALL_OR_UPDATE ->
                Text(stringResource(R.string.health_install), style = FitnexTheme.typography.bodyMedium, color = colors.warning, modifier = Modifier.padding(top = Spacing.sm))
            !state.connected -> FitnexPrimaryButton(
                stringResource(R.string.health_connect), { request.launch(vm.requested(state.adult)) },
                Modifier.fillMaxWidth().padding(top = Spacing.md).testTag("health_connect"),
            )
            else -> {
                val t = state.today
                Text(stringResource(R.string.health_connected), style = FitnexTheme.typography.titleMedium, color = colors.success, modifier = Modifier.padding(top = Spacing.sm))
                t?.steps?.let { Text(stringResource(R.string.health_steps, it), style = FitnexTheme.typography.bodyMedium, color = colors.textPrimary) }
                t?.avgHeartRate?.let { Text(stringResource(R.string.health_heart, it), style = FitnexTheme.typography.bodyMedium, color = colors.textPrimary) }
                if (state.adult) t?.latestWeightKg?.let { Text(stringResource(R.string.health_weight, "%.1f".format(it)), style = FitnexTheme.typography.bodyMedium, color = colors.textPrimary) }
                if (t?.steps == null && t?.avgHeartRate == null) {
                    Text(stringResource(R.string.health_no_data), style = FitnexTheme.typography.bodySmall, color = colors.textSecondary)
                }
                FitnexSecondaryButton(stringResource(R.string.health_permissions), { request.launch(vm.requested(state.adult)) }, Modifier.fillMaxWidth().padding(top = Spacing.md))
            }
        }
    }
}
