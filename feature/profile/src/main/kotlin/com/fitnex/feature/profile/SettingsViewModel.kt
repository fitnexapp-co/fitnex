package com.fitnex.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitnex.core.datastore.SurfaceStyle
import com.fitnex.core.datastore.ThemeMode
import com.fitnex.core.datastore.UserPreferences
import com.fitnex.core.datastore.UserPreferencesDataSource
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface SettingsUiState {
    data object Loading : SettingsUiState
    data class Ready(val preferences: UserPreferences) : SettingsUiState
}

/** Every setting is persisted immediately in DataStore; the UI re-renders from the stored value. */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferences: UserPreferencesDataSource,
) : ViewModel() {
    val state: StateFlow<SettingsUiState> = preferences.preferences
        .map<UserPreferences, SettingsUiState> { SettingsUiState.Ready(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState.Loading)

    fun setThemeMode(mode: ThemeMode) = launch { preferences.setThemeMode(mode) }
    fun setRestTimerSound(enabled: Boolean) = launch { preferences.setRestTimerSound(enabled) }
    fun setRestTimerNotifications(enabled: Boolean) = launch { preferences.setRestTimerNotifications(enabled) }
    fun setPreferMuscleMap3d(enabled: Boolean) = launch { preferences.setPreferMuscleMap3d(enabled) }

    /** Preset tap: applied and saved at once. */
    fun setAccent(argb: Int) = launch { preferences.previewAccent.value = null; preferences.setAccent(argb) }
    fun resetAccent() = launch { preferences.previewAccent.value = null; preferences.setAccent(null) }

    /** While dragging the custom picker: the whole app follows live, nothing is written yet. */
    fun previewAccent(argb: Int?) { preferences.previewAccent.value = argb }

    /** "Aplicar" in the custom picker: persist and remember it as the custom colour. */
    fun applyCustomAccent(argb: Int) = launch { preferences.setAccent(argb, custom = true); preferences.previewAccent.value = null }

    fun setLiquidGlass(enabled: Boolean) = launch { preferences.setSurfaceStyle(if (enabled) SurfaceStyle.LIQUID_GLASS else SurfaceStyle.GLASS) }
    fun setReduceMotion(enabled: Boolean) = launch { preferences.setReduceMotion(enabled) }
    fun setReduceBlur(enabled: Boolean) = launch { preferences.setReduceBlur(enabled) }
    /** Null turns the daily training reminder off. */
    fun setReminderHour(hour: Int?) = launch { preferences.setReminderHour(hour) }

    override fun onCleared() {
        preferences.previewAccent.value = null // never leave an unconfirmed preview applied
    }

    /** Rest is stored in whole seconds, snapped to [REST_STEP_SECONDS] and kept in the allowed range. */
    fun setDefaultRestSeconds(seconds: Int) = launch {
        val snapped = (Math.round(seconds / REST_STEP_SECONDS.toFloat()) * REST_STEP_SECONDS)
            .coerceIn(REST_MIN_SECONDS, REST_MAX_SECONDS)
        preferences.setDefaultRestSeconds(snapped)
    }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    companion object {
        const val REST_MIN_SECONDS = 30
        const val REST_MAX_SECONDS = 300
        const val REST_STEP_SECONDS = 15
    }
}
