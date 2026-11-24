package com.fitnex.feature.profile

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.fitnex.core.data.session.SessionRepository
import com.fitnex.core.datastore.ProfilePhotoStore
import com.fitnex.core.designsystem.component.CardEmphasis
import com.fitnex.core.designsystem.component.FitnexAvatar
import com.fitnex.core.designsystem.component.FitnexSecondaryButton
import com.fitnex.core.designsystem.component.GlassCard
import com.fitnex.core.designsystem.theme.FitnexTheme
import com.fitnex.core.designsystem.theme.Spacing
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Picked image waiting for the user's confirmation. */
data class PhotoPreview(val uri: Uri, val image: ImageBitmap)

@HiltViewModel
class ProfilePhotoViewModel @Inject constructor(
    private val session: SessionRepository,
    private val photos: ProfilePhotoStore,
) : ViewModel() {
    @OptIn(ExperimentalCoroutinesApi::class)
    val photo: StateFlow<ImageBitmap?> = session.accountUserId
        .flatMapLatest { uid -> uid?.let(photos::path) ?: flowOf(null) }
        .map { path -> path?.let { photos.load(it, 512)?.asImageBitmap() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _preview = MutableStateFlow<PhotoPreview?>(null)
    val preview: StateFlow<PhotoPreview?> = _preview.asStateFlow()

    private val _error = MutableStateFlow(false)
    val error: StateFlow<Boolean> = _error.asStateFlow()

    fun picked(uri: Uri) = viewModelScope.launch {
        val image = photos.decodeScaled(uri, 512)
        _error.value = image == null
        _preview.value = image?.let { PhotoPreview(uri, it.asImageBitmap()) }
    }

    fun confirm() = viewModelScope.launch {
        val p = _preview.value ?: return@launch
        val uid = session.accountUserId.first() ?: return@launch
        _error.value = !photos.save(uid, p.uri)
        _preview.value = null
    }

    fun cancel() { _preview.value = null }

    fun remove() = viewModelScope.launch { session.accountUserId.first()?.let { photos.remove(it) } }
}

/** Top of Perfil: avatar (tap to change), name, e-mail and the profile actions. */
@Composable
internal fun ProfileHero(
    name: String,
    email: String?,
    onEditProfile: () -> Unit,
    vm: ProfilePhotoViewModel = hiltViewModel(),
) {
    val colors = FitnexTheme.colors
    val photo by vm.photo.collectAsStateWithLifecycle()
    val preview by vm.preview.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    // System photo picker: no storage permission needed on any Android version.
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let(vm::picked) }
    val openPicker = { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }

    GlassCard(Modifier.fillMaxWidth().testTag("profile_hero"), emphasis = CardEmphasis.Featured) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(contentAlignment = Alignment.BottomEnd) {
                FitnexAvatar(
                    photo,
                    size = 96.dp,
                    contentDescription = stringResource(R.string.profile_photo_cd),
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(role = Role.Button, onClickLabel = stringResource(if (photo == null) R.string.photo_add else R.string.photo_change), onClick = openPicker)
                        .testTag("profile_avatar"),
                )
                Box(
                    Modifier
                        .offset(x = 2.dp, y = 2.dp)
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(colors.accent)
                        .border(2.dp, colors.surface, CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.Edit, contentDescription = null, tint = colors.onAccent, modifier = Modifier.size(15.dp)) }
            }
            Text(
                name.ifBlank { "—" },
                style = FitnexTheme.typography.titleLarge,
                color = colors.textPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = Spacing.md),
            )
            email?.let { Text(it, style = FitnexTheme.typography.bodyMedium, color = colors.textSecondary) }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), modifier = Modifier.padding(top = Spacing.xs)) {
                TextButton(onClick = openPicker, modifier = Modifier.testTag("photo_pick")) {
                    Text(stringResource(if (photo == null) R.string.photo_add else R.string.photo_change), color = colors.accent, style = FitnexTheme.typography.labelLarge)
                }
                if (photo != null) {
                    TextButton(onClick = vm::remove, modifier = Modifier.testTag("photo_remove")) {
                        Text(stringResource(R.string.photo_remove), color = colors.textSecondary, style = FitnexTheme.typography.labelLarge)
                    }
                }
            }
            if (error) Text(stringResource(R.string.photo_error), style = FitnexTheme.typography.bodySmall, color = colors.error)
            FitnexSecondaryButton(
                stringResource(R.string.account_edit_profile), onEditProfile,
                Modifier.fillMaxWidth().padding(top = Spacing.sm).testTag("account_edit_profile"),
            )
        }
    }

    preview?.let { p ->
        PhotoPreviewDialog(p.image, onConfirm = vm::confirm, onDismiss = vm::cancel)
    }
}

@Composable
private fun PhotoPreviewDialog(image: ImageBitmap, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = FitnexTheme.colors.surfaceRaised,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(com.fitnex.core.designsystem.theme.Radii.xl),
        title = { Text(stringResource(R.string.photo_preview_title), color = FitnexTheme.colors.textPrimary, style = FitnexTheme.typography.titleLarge) },
        text = {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                FitnexAvatar(image, size = 160.dp, contentDescription = stringResource(R.string.profile_photo_cd))
                Text(stringResource(R.string.photo_preview_body), color = FitnexTheme.colors.textSecondary, style = FitnexTheme.typography.bodyMedium, textAlign = TextAlign.Center)
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, modifier = Modifier.testTag("photo_confirm")) {
                Text(stringResource(R.string.photo_use), color = FitnexTheme.colors.accent, style = FitnexTheme.typography.labelLarge)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel), color = FitnexTheme.colors.textSecondary, style = FitnexTheme.typography.labelLarge)
            }
        },
    )
}

