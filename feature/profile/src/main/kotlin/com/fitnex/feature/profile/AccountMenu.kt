package com.fitnex.feature.profile

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fitnex.core.designsystem.component.SettingsRow
import com.fitnex.core.designsystem.component.backdropBlur
import com.fitnex.core.designsystem.component.liquidGlass
import com.fitnex.core.designsystem.component.glass
import com.fitnex.core.designsystem.icon.FitnexIcons
import com.fitnex.core.designsystem.theme.Elevation
import com.fitnex.core.designsystem.theme.FitnexTheme
import com.fitnex.core.designsystem.theme.Radii
import com.fitnex.core.designsystem.theme.Spacing

/**
 * The menu behind the round avatar in every header: switch between your profile and the
 * "Entrenar juntos" ones, add one, manage them, or open Ajustes. It grows out of the avatar.
 */
@Composable
fun AccountMenu(
    visible: Boolean,
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit,
    onManageProfiles: () -> Unit,
) {
    val transition = remember { MutableTransitionState(false) }
    transition.targetState = visible
    val reduce = FitnexTheme.appearance.reduceMotion
    var creating by rememberSaveable { mutableStateOf(false) }
    val vm: TrainingProfilesViewModel = hiltViewModel()
    val account: AccountViewModel = hiltViewModel()

    if (visible) BackHandler(onBack = onDismiss)

    AnimatedVisibility(transition, enter = fadeIn(tween(if (reduce) 60 else 180)), exit = fadeOut(tween(if (reduce) 60 else 160))) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = if (FitnexTheme.colors.isDark) 0.6f else 0.3f))
                .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onDismiss)
                .semantics { contentDescription = "" },
        )
    }
    AnimatedVisibility(
        transition,
        // Grows a little from the avatar (0.92, never from "nothing") with no bounce, and leaves faster
        // than it came.
        enter = if (reduce) fadeIn(tween(80)) else scaleIn(tween(200, easing = com.fitnex.core.designsystem.theme.FitnexMotion.Easing), initialScale = 0.92f, transformOrigin = TransformOrigin(0.93f, 0f)) + fadeIn(tween(140)),
        exit = if (reduce) fadeOut(tween(60)) else scaleOut(tween(140), targetScale = 0.95f, transformOrigin = TransformOrigin(0.93f, 0f)) + fadeOut(tween(120)),
        modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(top = Spacing.lg, start = Spacing.gutter, end = Spacing.gutter),
    ) {
        val state by vm.state.collectAsStateWithLifecycle()
        val info by account.account.collectAsStateWithLifecycle()
        val colors = FitnexTheme.colors
        val shape = RoundedCornerShape(Radii.xl)
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopEnd) {
            Column(
                Modifier
                    .widthIn(max = 420.dp)
                    .fillMaxWidth()
                    .then(
                        // Frosted rather than refracting: a menu full of text must stay perfectly legible.
                        if (FitnexTheme.appearance.liquidGlass && !FitnexTheme.appearance.reduceBlur) {
                            Modifier.backdropBlur(shape, 40.dp).liquidGlass(colors, shape, Elevation.floating, body = 0.93f)
                        } else {
                            Modifier.glass(colors, shape, Elevation.floating)
                        },
                    )
                    .clickable(remember { MutableInteractionSource() }, indication = null) { }
                    .padding(horizontal = Spacing.lg, vertical = Spacing.lg)
                    .testTag("account_menu"),
            ) {
                Text(stringResource(R.string.menu_title), style = FitnexTheme.typography.titleLarge, color = colors.textPrimary)
                info?.email?.let {
                    Text(it, style = FitnexTheme.typography.bodyMedium, color = colors.textSecondary, modifier = Modifier.padding(top = Spacing.xs, bottom = Spacing.xs))
                }
                state.entries.forEach { e ->
                    SettingsRow(
                        title = e.name.ifBlank { "—" },
                        leading = { ProfileAvatar(e, 46.dp) },
                        subtitle = stringResource(if (e.isOwner) R.string.tt_you else R.string.tt_companion),
                        chevron = false,
                        trailing = { if (e.active) Icon(Icons.Rounded.Check, null, tint = colors.textPrimary, modifier = Modifier.size(24.dp)) },
                        onClick = { vm.switchTo(e.id); onDismiss() },
                        modifier = Modifier.testTag("menu_profile_${e.name}"),
                    )
                }
                if (state.canAdd) {
                    SettingsRow(
                        title = stringResource(R.string.menu_add), leading = { AddIcon() }, chevron = false,
                        onClick = { creating = true }, modifier = Modifier.testTag("menu_add"),
                    )
                }
                SettingsRow(
                    title = stringResource(R.string.menu_manage), icon = FitnexIcons.People, chevron = false,
                    onClick = { onDismiss(); onManageProfiles() }, modifier = Modifier.testTag("menu_manage"),
                )
                SettingsRow(
                    title = stringResource(R.string.menu_settings), icon = FitnexIcons.Gear, chevron = false,
                    onClick = { onDismiss(); onOpenSettings() }, modifier = Modifier.testTag("menu_settings"),
                )
            }
        }
    }

    if (creating) {
        NewProfileSheet(onCreate = { vm.create(it); creating = false; onDismiss() }, onDismiss = { creating = false })
    }
}
