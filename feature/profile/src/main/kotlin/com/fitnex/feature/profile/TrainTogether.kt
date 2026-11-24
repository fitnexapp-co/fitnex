package com.fitnex.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.fitnex.core.data.profile.TrainingProfileDraft
import com.fitnex.core.data.profile.TrainingProfileRules
import com.fitnex.core.data.profile.TrainingProfilesRepository
import com.fitnex.core.datastore.ProfilePhotoStore
import com.fitnex.core.designsystem.component.FitnexAvatar
import com.fitnex.core.designsystem.component.FitnexBottomSheet
import com.fitnex.core.designsystem.component.FitnexConfirmDialog
import com.fitnex.core.designsystem.component.FitnexPrimaryButton
import com.fitnex.core.designsystem.component.FitnexTextField
import com.fitnex.core.designsystem.component.InitialAvatar
import com.fitnex.core.designsystem.component.SettingsDivider
import com.fitnex.core.designsystem.component.SettingsGroup
import com.fitnex.core.designsystem.component.SettingsRow
import com.fitnex.core.designsystem.component.picker.FitnexNumberPickerField
import com.fitnex.core.designsystem.theme.FitnexTheme
import com.fitnex.core.designsystem.theme.Spacing
import com.fitnex.core.model.ProfileLimits
import com.fitnex.core.model.UserProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import java.time.Period
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One row of the profile switcher. */
data class ProfileEntry(val id: String, val name: String, val isOwner: Boolean, val active: Boolean, val age: Int?, val photoPath: String?)

data class ProfilesUiState(val entries: List<ProfileEntry> = emptyList()) {
    val canAdd: Boolean get() = entries.count { !it.isOwner } < TrainingProfileRules.MAX_PROFILES
}

@HiltViewModel
class TrainingProfilesViewModel @Inject constructor(
    private val repository: TrainingProfilesRepository,
    private val photos: ProfilePhotoStore,
    private val clock: Clock,
) : ViewModel() {
    val state: StateFlow<ProfilesUiState> = combine(repository.profiles, repository.activeId) { list, active ->
        ProfilesUiState(list.map { it.toEntry(active) })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProfilesUiState())

    private suspend fun UserProfile.toEntry(active: String?) = ProfileEntry(
        id = id,
        name = name,
        isOwner = !TrainingProfileRules.isCompanion(id),
        active = id == active,
        age = birthDate?.let { Period.between(it, LocalDate.now(clock)).years },
        photoPath = photos.path(id).first(),
    )

    fun switchTo(id: String) = viewModelScope.launch { repository.switchTo(id) }

    fun create(draft: TrainingProfileDraft, onCreated: () -> Unit = {}) = viewModelScope.launch {
        val id = repository.create(draft)
        repository.switchTo(id)
        onCreated()
    }

    fun delete(id: String) = viewModelScope.launch { repository.delete(id) }
}

/** Avatar of a profile: its photo, else its initial. */
@Composable
internal fun ProfileAvatar(entry: ProfileEntry, size: androidx.compose.ui.unit.Dp = 44.dp) {
    val photo = entry.photoPath?.let { path ->
        androidx.compose.runtime.produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, path) {
            value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                android.graphics.BitmapFactory.decodeFile(path, android.graphics.BitmapFactory.Options().apply { inSampleSize = 4 })?.asImageBitmap()
            }
        }.value
    }
    if (photo != null) FitnexAvatar(photo, size = size) else InitialAvatar(entry.name.ifBlank { "?" }, size = size)
}

/** Ajustes › Entrenar juntos: the people trained from this account. */
@Composable
internal fun TrainTogetherPage(vm: TrainingProfilesViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val colors = FitnexTheme.colors
    var creating by rememberSaveable { mutableStateOf(false) }
    var deleting by rememberSaveable { mutableStateOf<String?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md), modifier = Modifier.testTag("train_together")) {
        Text(stringResource(R.string.tt_intro), style = FitnexTheme.typography.bodyMedium, color = colors.textSecondary)
        SettingsGroup {
            state.entries.forEachIndexed { i, e ->
                if (i > 0) SettingsDivider(withIcon = false)
                SettingsRow(
                    title = e.name.ifBlank { "—" },
                    leading = { ProfileAvatar(e) },
                    subtitle = listOfNotNull(
                        stringResource(if (e.isOwner) R.string.tt_you else R.string.tt_companion),
                        e.age?.let { androidx.compose.ui.res.pluralStringResource(R.plurals.tt_age_years, it, it) },
                        if (e.active) stringResource(R.string.tt_on_screen) else null,
                    ).joinToString(" · "),
                    chevron = false,
                    trailing = {
                        if (e.active) Icon(Icons.Rounded.Check, null, tint = colors.accent, modifier = Modifier.size(22.dp))
                        if (!e.isOwner) {
                            TextButton(onClick = { deleting = e.id }, modifier = Modifier.testTag("tt_delete_${e.name}")) {
                                Text(stringResource(R.string.tt_delete), color = colors.error, style = FitnexTheme.typography.labelLarge)
                            }
                        }
                    },
                    onClick = { vm.switchTo(e.id) },
                    modifier = Modifier.testTag("tt_profile_${e.name}"),
                )
            }
        }
        if (state.canAdd) {
            FitnexPrimaryButton(stringResource(R.string.tt_add), { creating = true }, Modifier.fillMaxWidth().testTag("tt_add"))
        } else {
            Text(stringResource(R.string.tt_limit, TrainingProfileRules.MAX_PROFILES), style = FitnexTheme.typography.bodySmall, color = colors.warning)
        }
        Text(stringResource(R.string.tt_note), style = FitnexTheme.typography.bodySmall, color = colors.textDisabled)
    }

    if (creating) NewProfileSheet(onCreate = { vm.create(it); creating = false }, onDismiss = { creating = false })
    deleting?.let { id ->
        val name = state.entries.firstOrNull { it.id == id }?.name.orEmpty()
        FitnexConfirmDialog(
            title = stringResource(R.string.tt_delete_title, name),
            message = stringResource(R.string.tt_delete_message),
            confirmLabel = stringResource(R.string.tt_delete),
            dismissLabel = stringResource(R.string.common_cancel),
            onConfirm = { vm.delete(id); deleting = null },
            onDismiss = { deleting = null },
            destructive = true,
        )
    }
}

/** Only what "Entrenar juntos" needs: name, age, height and (adults only) weight. */
@Composable
internal fun NewProfileSheet(onCreate: (TrainingProfileDraft) -> Unit, onDismiss: () -> Unit) {
    val colors = FitnexTheme.colors
    var name by rememberSaveable { mutableStateOf("") }
    var age by rememberSaveable { mutableStateOf<Float?>(null) }
    var height by rememberSaveable { mutableStateOf<Float?>(null) }
    var weight by rememberSaveable { mutableStateOf<Float?>(null) }
    var tried by rememberSaveable { mutableStateOf(false) }
    val nameOk = name.isNotBlank() && name.trim().length <= ProfileLimits.NAME_MAX_LENGTH
    val ageOk = age != null
    val adult = (age ?: 0f) >= 18f

    FitnexBottomSheet(onDismiss = onDismiss, title = stringResource(R.string.tt_new_title), dragToDismiss = false) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            FitnexTextField(
                value = name, onValueChange = { name = it.take(ProfileLimits.NAME_MAX_LENGTH) },
                label = stringResource(R.string.tt_name),
                modifier = Modifier.fillMaxWidth().testTag("tt_name"),
                errorText = if (tried && !nameOk) stringResource(R.string.tt_name_error) else null,
            )
            FitnexNumberPickerField(
                value = age, onValueChange = { age = it }, label = stringResource(R.string.tt_age), unit = "",
                range = TrainingProfileRules.AGE, defaultValue = 20f, optional = false,
                modifier = Modifier.fillMaxWidth().testTag("tt_age"),
                errorText = if (tried && !ageOk) stringResource(R.string.tt_age) else null,
            )
            FitnexNumberPickerField(
                value = height, onValueChange = { height = it }, label = stringResource(R.string.tt_height), unit = "cm",
                range = ProfileLimits.HEIGHT_CM.start.toInt()..ProfileLimits.HEIGHT_CM.endInclusive.toInt(), defaultValue = 170f,
                modifier = Modifier.fillMaxWidth().testTag("tt_height"),
            )
            if (adult) {
                FitnexNumberPickerField(
                    value = weight, onValueChange = { weight = it }, label = stringResource(R.string.tt_weight), unit = "kg",
                    range = ProfileLimits.WEIGHT_KG.start.toInt()..ProfileLimits.WEIGHT_KG.endInclusive.toInt(), defaultValue = 70f, withDecimal = true,
                    modifier = Modifier.fillMaxWidth().testTag("tt_weight"),
                )
            } else if (age != null) {
                Text(stringResource(R.string.tt_weight_minor), style = FitnexTheme.typography.bodySmall, color = colors.textSecondary)
            }
            FitnexPrimaryButton(
                stringResource(R.string.tt_create),
                {
                    tried = true
                    val a = age
                    if (nameOk && a != null) onCreate(TrainingProfileDraft(name.trim(), a.toInt(), height, if (adult) weight else null))
                },
                Modifier.fillMaxWidth().padding(top = Spacing.sm).testTag("tt_create"),
            )
        }
    }
}

/** Small "+" row used by the account menu. */
@Composable
internal fun AddIcon() = Icon(Icons.Rounded.Add, null, tint = FitnexTheme.colors.textPrimary, modifier = Modifier.size(24.dp))
