package com.fitnex.feature.profile

import com.fitnex.core.designsystem.component.fitnexScreen

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.fitnex.core.datastore.ThemeMode
import com.fitnex.core.datastore.UserPreferences
import com.fitnex.core.designsystem.component.FitnexHeader
import com.fitnex.core.designsystem.component.FitnexSliderRow
import com.fitnex.core.designsystem.component.FitnexSwitchRow
import com.fitnex.core.designsystem.component.FitnexTextField
import com.fitnex.core.designsystem.component.LoadingState
import com.fitnex.core.designsystem.component.LocalProfilePhoto
import com.fitnex.core.designsystem.component.FitnexAvatar
import com.fitnex.core.designsystem.component.SettingsDivider
import com.fitnex.core.designsystem.component.SettingsGroup
import com.fitnex.core.designsystem.component.SettingsRow
import com.fitnex.core.designsystem.component.SettingsSectionLabel
import com.fitnex.core.designsystem.icon.FitnexIcons
import com.fitnex.core.designsystem.theme.FitnexTheme
import com.fitnex.core.designsystem.theme.Spacing
import com.fitnex.feature.profile.SettingsViewModel.Companion.REST_MAX_SECONDS
import com.fitnex.feature.profile.SettingsViewModel.Companion.REST_MIN_SECONDS
import com.fitnex.feature.profile.SettingsViewModel.Companion.REST_STEP_SECONDS
import kotlinx.serialization.Serializable

/** [page] opens straight on one page of Ajustes (a [SettingsPage] name), e.g. from the account menu. */
@Serializable
data class SettingsRoute(val page: String? = null)

/** Ajustes is one list; each row opens its own page (kept inside this screen, with its own back). */
enum class SettingsPage { HOME, ACCOUNT, TRAIN_TOGETHER, TRAINING, APPEARANCE, HEALTH, CLOUD, PRIVACY }

/** Appearance callbacks, grouped so the screen signature stays readable. */
internal class AppearanceActions(
    val onThemeMode: (ThemeMode) -> Unit,
    val onAccent: (Int) -> Unit = {},
    val onResetAccent: () -> Unit = {},
    val onPreviewAccent: (Int?) -> Unit = {},
    val onApplyCustom: (Int) -> Unit = {},
    val onLiquidGlass: (Boolean) -> Unit = {},
    val onReduceMotion: (Boolean) -> Unit = {},
    val onReduceBlur: (Boolean) -> Unit = {},
)

fun NavGraphBuilder.settingsScreen(contentPadding: PaddingValues, onBack: () -> Unit, onEditProfile: () -> Unit) {
    fitnexScreen<SettingsRoute> { entry ->
        val viewModel: SettingsViewModel = hiltViewModel()
        val state by viewModel.state.collectAsStateWithLifecycle()
        val start = entry.toRoute<SettingsRoute>().page?.let { p -> SettingsPage.entries.firstOrNull { it.name == p } } ?: SettingsPage.HOME
        SettingsScreen(
            state = state,
            contentPadding = contentPadding,
            onBack = onBack,
            startPage = start,
            profileSection = { ProfileSection(onEditProfile) },
            accountSection = { AccountSection() },
            cloudSection = { CloudSection() },
            healthSection = { HealthSection() },
            trainTogether = { TrainTogetherPage() },
            cloudStatus = { cloudStatusText() },
            appearance = AppearanceActions(
                onThemeMode = viewModel::setThemeMode,
                onAccent = viewModel::setAccent,
                onResetAccent = viewModel::resetAccent,
                onPreviewAccent = viewModel::previewAccent,
                onApplyCustom = viewModel::applyCustomAccent,
                onLiquidGlass = viewModel::setLiquidGlass,
                onReduceMotion = viewModel::setReduceMotion,
                onReduceBlur = viewModel::setReduceBlur,
            ),
            onRestSeconds = viewModel::setDefaultRestSeconds,
            onRestSound = viewModel::setRestTimerSound,
            onRestNotifications = viewModel::setRestTimerNotifications,
            onPreferMap3d = viewModel::setPreferMuscleMap3d,
            onReminderHour = viewModel::setReminderHour,
        )
    }
}

@Composable
internal fun SettingsScreen(
    state: SettingsUiState,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    appearance: AppearanceActions,
    onRestSeconds: (Int) -> Unit,
    onRestSound: (Boolean) -> Unit,
    onRestNotifications: (Boolean) -> Unit,
    onPreferMap3d: (Boolean) -> Unit,
    onReminderHour: (Int?) -> Unit = {},
    startPage: SettingsPage = SettingsPage.HOME,
    profileSection: @Composable () -> Unit = {},
    accountSection: @Composable () -> Unit = {},
    cloudSection: @Composable () -> Unit = {},
    healthSection: @Composable () -> Unit = {},
    trainTogether: @Composable () -> Unit = {},
    /** Short backup status for the Nube row ("Guardado hace 5 min"); null = unknown. */
    cloudStatus: @Composable () -> String? = { null },
) {
    var page by rememberSaveable { mutableStateOf(startPage) }
    // Back inside Ajustes returns to the list, unless the page was opened directly from outside.
    val back = { if (page != SettingsPage.HOME && startPage == SettingsPage.HOME) page = SettingsPage.HOME else onBack() }
    BackHandler(enabled = page != SettingsPage.HOME && startPage == SettingsPage.HOME) { page = SettingsPage.HOME }
    val reduce = FitnexTheme.appearance.reduceMotion

    AnimatedContent(
        targetState = page,
        transitionSpec = {
            if (reduce) {
                fadeIn(tween(90)) togetherWith fadeOut(tween(60))
            } else {
                val forward = targetState != SettingsPage.HOME
                (slideInHorizontally(tween(320)) { if (forward) it / 4 else -it / 4 } + fadeIn(tween(240))) togetherWith
                    (slideOutHorizontally(tween(260)) { if (forward) -it / 6 else it / 6 } + fadeOut(tween(180)))
            }
        },
        label = "settingsPage",
    ) { shown ->
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(contentPadding),
        ) {
            FitnexHeader(title = pageTitle(shown), onBack = back)
            Column(Modifier.padding(horizontal = Spacing.gutter), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                when (state) {
                    SettingsUiState.Loading -> LoadingState()
                    is SettingsUiState.Ready -> when (shown) {
                        SettingsPage.HOME -> SettingsHome(state.preferences, onOpen = { page = it }, cloudStatus = cloudStatus())
                        SettingsPage.ACCOUNT -> { profileSection(); accountSection() }
                        SettingsPage.TRAIN_TOGETHER -> trainTogether()
                        SettingsPage.TRAINING -> TrainingPage(state.preferences, onRestSeconds, onRestSound, onRestNotifications, onPreferMap3d, onReminderHour)
                        SettingsPage.APPEARANCE -> AppearanceSection(
                            prefs = state.preferences,
                            onThemeMode = appearance.onThemeMode,
                            onAccent = appearance.onAccent,
                            onResetAccent = appearance.onResetAccent,
                            onPreviewAccent = appearance.onPreviewAccent,
                            onApplyCustom = appearance.onApplyCustom,
                            onLiquidGlass = appearance.onLiquidGlass,
                            onReduceMotion = appearance.onReduceMotion,
                            onReduceBlur = appearance.onReduceBlur,
                        )
                        SettingsPage.HEALTH -> healthSection()
                        SettingsPage.CLOUD -> cloudSection()
                        SettingsPage.PRIVACY -> PrivacyPage(onOpen = { page = it })
                    }
                }
            }
        }
    }
}

@Composable
private fun pageTitle(page: SettingsPage): String = stringResource(
    when (page) {
        SettingsPage.HOME -> R.string.settings_title
        SettingsPage.ACCOUNT -> R.string.settings_row_account
        SettingsPage.TRAIN_TOGETHER -> R.string.tt_title
        SettingsPage.TRAINING -> R.string.settings_training
        SettingsPage.APPEARANCE -> R.string.settings_appearance
        SettingsPage.HEALTH -> R.string.health_title
        SettingsPage.CLOUD -> R.string.cloud_title
        SettingsPage.PRIVACY -> R.string.privacy_title
    },
)

private class SettingsEntry(val page: SettingsPage, val title: String, val subtitle: String?, val icon: ImageVector?, val keywords: String = "")

@Composable
private fun SettingsHome(prefs: UserPreferences, onOpen: (SettingsPage) -> Unit, cloudStatus: String? = null) {
    val colors = FitnexTheme.colors
    var query by rememberSaveable { mutableStateOf("") }
    val account = SettingsEntry(SettingsPage.ACCOUNT, stringResource(R.string.settings_row_account), stringResource(R.string.settings_account), null, "perfil cuenta correo contraseña eliminar cerrar sesión foto ia")
    val together = SettingsEntry(SettingsPage.TRAIN_TOGETHER, stringResource(R.string.tt_title), stringResource(R.string.tt_row_desc), FitnexIcons.People, "amigos perfiles juntos")
    val training = SettingsEntry(SettingsPage.TRAINING, stringResource(R.string.settings_training), stringResource(R.string.settings_row_training_desc), FitnexIcons.Timer, "descanso sonido avisos recordatorio mapa muscular 3d 2d")
    val appearance = SettingsEntry(SettingsPage.APPEARANCE, stringResource(R.string.settings_appearance), stringResource(R.string.settings_row_appearance_desc), FitnexIcons.Brush, "tema oscuro claro color acento liquid glass cristal animaciones desenfoque")
    val health = SettingsEntry(SettingsPage.HEALTH, stringResource(R.string.health_title), stringResource(R.string.settings_row_health_desc), FitnexIcons.Heart, "pasos pulso salud")
    val cloud = SettingsEntry(SettingsPage.CLOUD, stringResource(R.string.cloud_title), cloudStatus ?: stringResource(R.string.settings_row_cloud_desc), FitnexIcons.Cloud, "sincronizar copia exportar datos")
    val privacy = SettingsEntry(SettingsPage.PRIVACY, stringResource(R.string.privacy_title), stringResource(R.string.privacy_row_desc), FitnexIcons.Shield, "privacidad datos borrar ia menores política")

    FitnexTextField(
        value = query, onValueChange = { query = it }, label = stringResource(R.string.settings_search),
        modifier = Modifier.fillMaxWidth().testTag("settings_search"),
        trailingIcon = { Icon(Icons.Rounded.Search, null, tint = colors.textSecondary) },
    )

    val q = query.trim().lowercase()
    if (q.isNotEmpty()) {
        val all = listOf(account, together, training, appearance, health, cloud, privacy)
        val hits = all.filter { e -> listOfNotNull(e.title, e.subtitle, e.keywords).any { q in it.lowercase() } }
        if (hits.isEmpty()) {
            Text(stringResource(R.string.settings_no_results, query.trim()), style = FitnexTheme.typography.bodyMedium, color = colors.textSecondary)
        } else {
            SettingsGroup { hits.forEachIndexed { i, e -> if (i > 0) SettingsDivider(); EntryRow(e, onOpen) } }
        }
        return
    }

    // Grouped by what people come here to do: who they are, how they train, how the app looks,
    // what happens to their data, and what this app is.
    SettingsSectionLabel(stringResource(R.string.settings_label_account))
    SettingsGroup {
        val photo = LocalProfilePhoto.current
        SettingsRow(
            title = account.title, subtitle = account.subtitle,
            leading = { if (photo != null) FitnexAvatar(photo, size = 32.dp) else Icon(FitnexIcons.Shield, null, tint = colors.textPrimary) },
            onClick = { onOpen(SettingsPage.ACCOUNT) },
            modifier = Modifier.testTag("settings_row_account"),
        )
        SettingsDivider()
        EntryRow(together, onOpen)
    }

    SettingsSectionLabel(stringResource(R.string.settings_label_training))
    SettingsGroup {
        SettingsRow(
            title = training.title, subtitle = training.subtitle, icon = training.icon,
            value = formatRest(prefs.defaultRestSeconds),
            onClick = { onOpen(SettingsPage.TRAINING) }, modifier = Modifier.testTag("settings_row_training"),
        )
    }

    SettingsSectionLabel(stringResource(R.string.settings_label_personal))
    SettingsGroup {
        EntryRow(appearance, onOpen)
    }

    SettingsSectionLabel(stringResource(R.string.settings_label_data))
    SettingsGroup {
        EntryRow(health, onOpen)
        SettingsDivider()
        EntryRow(cloud, onOpen)
        SettingsDivider()
        EntryRow(privacy, onOpen)
    }
    Text(stringResource(R.string.settings_local_note), style = FitnexTheme.typography.bodySmall, color = colors.textDisabled)

    SettingsSectionLabel(stringResource(R.string.settings_label_about))
    SettingsGroup {
        val context = androidx.compose.ui.platform.LocalContext.current
        val version = androidx.compose.runtime.remember(context) {
            runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
        }
        SettingsRow(title = stringResource(R.string.settings_version), value = version, icon = FitnexIcons.Sparkle, chevron = false, modifier = Modifier.testTag("settings_version"))
    }
    // Credits required by the licences of the bundled assets (CC BY 4.0 for the 3D body).
    Text(stringResource(R.string.credits_3d), style = FitnexTheme.typography.bodySmall, color = colors.textDisabled, modifier = Modifier.testTag("credits"))
}

/**
 * Privacy in plain words: what FITNEX stores, where, and where to change each thing. No legal
 * text here (that belongs to the published privacy policy); just the facts of this app.
 */
@Composable
private fun PrivacyPage(onOpen: (SettingsPage) -> Unit) {
    val colors = FitnexTheme.colors
    listOf(
        R.string.privacy_where_title to R.string.privacy_where_body,
        R.string.privacy_ai_title to R.string.privacy_ai_body,
        R.string.privacy_minors_title to R.string.privacy_minors_body,
        R.string.privacy_food_title to R.string.privacy_food_body,
    ).forEach { (t, b) ->
        com.fitnex.core.designsystem.component.GlassCard(Modifier.fillMaxWidth()) {
            Text(stringResource(t), style = FitnexTheme.typography.titleMedium, color = colors.textPrimary)
            Text(stringResource(b), style = FitnexTheme.typography.bodyMedium, color = colors.textSecondary, modifier = Modifier.padding(top = Spacing.xs))
        }
    }
    SettingsSectionLabel(stringResource(R.string.privacy_manage))
    SettingsGroup {
        SettingsRow(title = stringResource(R.string.privacy_go_account), subtitle = stringResource(R.string.privacy_go_account_desc), icon = FitnexIcons.Shield, onClick = { onOpen(SettingsPage.ACCOUNT) }, modifier = Modifier.testTag("privacy_go_account"))
        SettingsDivider()
        SettingsRow(title = stringResource(R.string.privacy_go_cloud), subtitle = stringResource(R.string.privacy_go_cloud_desc), icon = FitnexIcons.Cloud, onClick = { onOpen(SettingsPage.CLOUD) }, modifier = Modifier.testTag("privacy_go_cloud"))
    }
}

@Composable
private fun EntryRow(e: SettingsEntry, onOpen: (SettingsPage) -> Unit) {
    SettingsRow(
        title = e.title, subtitle = e.subtitle, icon = e.icon ?: FitnexIcons.Shield,
        onClick = { onOpen(e.page) }, modifier = Modifier.testTag("settings_row_${e.page.name.lowercase()}"),
    )
}

@Composable
private fun TrainingPage(
    prefs: UserPreferences,
    onRestSeconds: (Int) -> Unit,
    onRestSound: (Boolean) -> Unit,
    onRestNotifications: (Boolean) -> Unit,
    onPreferMap3d: (Boolean) -> Unit,
    onReminderHour: (Int?) -> Unit,
) {
    SettingsGroup {
        // Local slider position while dragging; persisted only when the finger lifts.
        var dragValue by remember(prefs.defaultRestSeconds) { mutableFloatStateOf(prefs.defaultRestSeconds.toFloat()) }
        FitnexSliderRow(
            title = stringResource(R.string.settings_rest_default),
            value = dragValue,
            onValueChange = { dragValue = it },
            onValueChangeFinished = { onRestSeconds(dragValue.toInt()) },
            valueRange = REST_MIN_SECONDS.toFloat()..REST_MAX_SECONDS.toFloat(),
            steps = (REST_MAX_SECONDS - REST_MIN_SECONDS) / REST_STEP_SECONDS - 1,
            valueLabel = formatRest(dragValue.toInt()),
        )
        SettingsDivider(withIcon = false)
        FitnexSwitchRow(
            title = stringResource(R.string.settings_rest_sound),
            description = stringResource(R.string.settings_rest_sound_desc),
            checked = prefs.restTimerSound,
            onCheckedChange = onRestSound,
        )
        SettingsDivider(withIcon = false)
        FitnexSwitchRow(
            title = stringResource(R.string.settings_rest_notifications),
            description = stringResource(R.string.settings_rest_notifications_desc),
            checked = prefs.restTimerNotifications,
            onCheckedChange = onRestNotifications,
        )
        SettingsDivider(withIcon = false)
        ReminderSetting(prefs.reminderHour, onReminderHour)
    }
    SettingsSectionLabel(stringResource(R.string.settings_muscle_map))
    SettingsGroup {
        FitnexSwitchRow(
            title = stringResource(R.string.settings_prefer_3d),
            description = stringResource(R.string.settings_prefer_3d_desc),
            checked = prefs.preferMuscleMap3d,
            onCheckedChange = onPreferMap3d,
            icon = FitnexIcons.Body,
        )
    }
}

/** 90 → "1:30 min", 45 → "45 s". */
internal fun formatRest(seconds: Int): String =
    if (seconds < 60) "$seconds s" else "%d:%02d min".format(seconds / 60, seconds % 60)
