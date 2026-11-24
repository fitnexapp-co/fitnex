package com.fitnex.app

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitnex.core.data.session.SessionRepository
import com.fitnex.core.data.session.SessionState
import com.fitnex.core.datastore.ProfilePhotoStore
import com.fitnex.core.datastore.SurfaceStyle
import com.fitnex.core.datastore.ThemeMode
import com.fitnex.core.datastore.UserPreferencesDataSource
import com.fitnex.core.designsystem.theme.FitnexAccents
import com.fitnex.core.designsystem.theme.FitnexAppearance
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

sealed interface MainUiState {
    data object Loading : MainUiState
    data class Ready(
        val themeMode: ThemeMode,
        val session: SessionState,
        val appearance: FitnexAppearance = FitnexAppearance(),
        val profilePhoto: ImageBitmap? = null,
        /** Name of the "Entrenar juntos" profile on screen; null while it is the account owner. */
        val companionName: String? = null,
    ) : MainUiState
}

@HiltViewModel
class MainViewModel @Inject constructor(
    preferences: UserPreferencesDataSource,
    session: SessionRepository,
    photos: ProfilePhotoStore,
    profiles: com.fitnex.core.data.profile.UserProfileRepository,
) : ViewModel() {
    private val appearance = combine(preferences.preferences, preferences.previewAccent) { p, preview ->
        FitnexAppearance(
            accent = (preview ?: p.accentArgb)?.let { Color(it) } ?: FitnexAccents.Default,
            liquidGlass = p.surfaceStyle == SurfaceStyle.LIQUID_GLASS,
            reduceMotion = p.reduceMotion,
            reduceBlur = p.reduceBlur,
        )
    }

    /** Small decode (256 px) for the header avatar; the profile screen loads its own larger copy. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val photo = session.currentUserId
        .flatMapLatest { uid -> uid?.let(photos::path) ?: flowOf(null) }
        .map { path -> path?.let { photos.load(it, 256)?.asImageBitmap() } }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val companion = session.currentUserId.flatMapLatest { id ->
        if (id == null || com.fitnex.core.database.sync.PROFILE_SEPARATOR !in id) flowOf(null)
        else profiles.observe(id).map { it?.name }
    }

    private val avatar = combine(photo, companion, ::Pair)

    /** The splash stays up until both theme and session are known, so nothing flickers. */
    val uiState: StateFlow<MainUiState> =
        combine(preferences.preferences, session.state, appearance, avatar) { prefs, s, look, (picture, companionName) ->
            MainUiState.Ready(prefs.themeMode, s, look, picture, companionName) as MainUiState
        }.stateIn(viewModelScope, SharingStarted.Eagerly, MainUiState.Loading)
}
