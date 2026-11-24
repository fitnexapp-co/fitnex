package com.fitnex.feature.profile

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.fitnex.core.datastore.SurfaceStyle
import com.fitnex.core.datastore.ThemeMode
import com.fitnex.core.datastore.UserPreferences
import com.fitnex.core.designsystem.component.FitnexBottomSheet
import com.fitnex.core.designsystem.component.FitnexPrimaryButton
import com.fitnex.core.designsystem.component.FitnexSegmentedControl
import com.fitnex.core.designsystem.component.FitnexSwitchRow
import com.fitnex.core.designsystem.component.GlassCard
import com.fitnex.core.designsystem.component.SettingsDivider
import com.fitnex.core.designsystem.component.SettingsGroup
import com.fitnex.core.designsystem.icon.FitnexIcons
import com.fitnex.core.designsystem.theme.FitnexAccents
import com.fitnex.core.designsystem.theme.FitnexSignature
import com.fitnex.core.designsystem.theme.FitnexMotion
import com.fitnex.core.designsystem.theme.FitnexTheme
import com.fitnex.core.designsystem.theme.Spacing

private val presetNames = listOf(
    R.string.accent_violet, R.string.accent_blue, R.string.accent_cyan, R.string.accent_coral,
    R.string.accent_orange, R.string.accent_pink, R.string.accent_red, R.string.accent_green,
)

/**
 * Apariencia, laid out like a system settings page: Tema, color de acento, then the switches for
 * animations, blur and Liquid Glass (refracting glass; off = the light glass prototype).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AppearanceSection(
    prefs: UserPreferences,
    onThemeMode: (ThemeMode) -> Unit,
    onAccent: (Int) -> Unit,
    onResetAccent: () -> Unit,
    onPreviewAccent: (Int?) -> Unit,
    onApplyCustom: (Int) -> Unit,
    onLiquidGlass: (Boolean) -> Unit,
    onReduceMotion: (Boolean) -> Unit,
    onReduceBlur: (Boolean) -> Unit = {},
) {
    val colors = FitnexTheme.colors
    var showPicker by rememberSaveable { mutableStateOf(false) }
    val current = prefs.accentArgb ?: FitnexAccents.Default.toArgb()
    val isPreset = FitnexAccents.presets.any { it.toArgb() == current }

    Column(Modifier.testTag("appearance_section"), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        SettingsGroup {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = Spacing.md, bottom = Spacing.sm)) {
                Icon(FitnexIcons.Theme, null, tint = colors.textPrimary, modifier = Modifier.padding(end = Spacing.lg).size(24.dp))
                Text(stringResource(R.string.appearance_theme_title), style = FitnexTheme.typography.titleMedium, color = colors.textPrimary)
            }
            val labels = mapOf(
                ThemeMode.SYSTEM to stringResource(R.string.settings_theme_system),
                ThemeMode.LIGHT to stringResource(R.string.settings_theme_light),
                ThemeMode.DARK to stringResource(R.string.settings_theme_dark),
            )
            FitnexSegmentedControl(
                options = listOf(ThemeMode.SYSTEM, ThemeMode.LIGHT, ThemeMode.DARK),
                selected = prefs.themeMode,
                onSelect = onThemeMode,
                label = { labels.getValue(it) },
                modifier = Modifier.padding(bottom = Spacing.md),
            )
        }

        SettingsGroup {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = Spacing.md)) {
                Icon(FitnexIcons.Brush, null, tint = colors.textPrimary, modifier = Modifier.padding(end = Spacing.lg).size(24.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.appearance_accent_title), style = FitnexTheme.typography.titleMedium, color = colors.textPrimary)
                    Text(stringResource(R.string.appearance_accent_desc), style = FitnexTheme.typography.bodySmall, color = colors.textSecondary)
                }
            }
            FlowRow(
                Modifier.fillMaxWidth().padding(top = Spacing.md, bottom = Spacing.sm),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                FitnexAccents.presets.forEachIndexed { i, swatch ->
                    Swatch(
                        color = swatch,
                        selected = swatch.toArgb() == current,
                        label = stringResource(presetNames[i]),
                        onClick = { onAccent(swatch.toArgb()) },
                        modifier = Modifier.testTag("accent_$i"),
                        // The default is the multicolour FITNEX style, not just "violet".
                        brush = if (swatch == FitnexAccents.Default) SignatureSwatch else null,
                    )
                }
                CustomSwatch(
                    color = prefs.customAccentArgb?.let { Color(it) },
                    selected = !isPreset,
                    onClick = { showPicker = true },
                )
            }
            AnimatedVisibility(current != FitnexAccents.Default.toArgb(), enter = fadeIn(), exit = fadeOut()) {
                TextButton(onClick = onResetAccent, modifier = Modifier.testTag("accent_reset")) {
                    Text(stringResource(R.string.appearance_reset), color = colors.accent, style = FitnexTheme.typography.labelLarge)
                }
            }
        }

        SettingsGroup {
            FitnexSwitchRow(
                title = stringResource(R.string.appearance_reduce_motion),
                description = stringResource(R.string.appearance_reduce_motion_desc),
                checked = prefs.reduceMotion,
                onCheckedChange = onReduceMotion,
                icon = FitnexIcons.Motion,
                modifier = Modifier.testTag("appearance_reduce_motion"),
            )
            if (!prefs.reduceMotion && FitnexTheme.appearance.reduceMotion) {
                Text(stringResource(R.string.appearance_reduce_motion_system), style = FitnexTheme.typography.bodySmall, color = colors.textSecondary, modifier = Modifier.padding(start = 40.dp, bottom = Spacing.sm))
            }
            SettingsDivider()
            FitnexSwitchRow(
                title = stringResource(R.string.appearance_reduce_blur),
                description = stringResource(R.string.appearance_reduce_blur_desc),
                checked = prefs.reduceBlur,
                onCheckedChange = onReduceBlur,
                icon = FitnexIcons.Drop,
                modifier = Modifier.testTag("appearance_reduce_blur"),
            )
            SettingsDivider()
            val liquid = prefs.surfaceStyle == SurfaceStyle.LIQUID_GLASS
            FitnexSwitchRow(
                title = stringResource(R.string.appearance_liquid),
                description = stringResource(R.string.appearance_liquid_desc),
                checked = liquid,
                onCheckedChange = onLiquidGlass,
                icon = FitnexIcons.Sparkle,
                modifier = Modifier.testTag("appearance_liquid"),
            )
            if (liquid && Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                Text(stringResource(R.string.appearance_liquid_fallback), style = FitnexTheme.typography.bodySmall, color = colors.warning, modifier = Modifier.padding(start = 40.dp, bottom = Spacing.sm))
            }
        }
    }

    if (showPicker) {
        ColorPickerSheet(
            initial = Color(prefs.customAccentArgb ?: current),
            onPreview = { onPreviewAccent(it.toArgb()) },
            onApply = { onApplyCustom(it.toArgb()); showPicker = false },
            onDismiss = { onPreviewAccent(null); showPicker = false },
        )
    }
}

private val SignatureSwatch = Brush.linearGradient(listOf(FitnexSignature.Blue, FitnexSignature.Violet, FitnexSignature.Mint))

@Composable
private fun Swatch(color: Color, selected: Boolean, label: String, onClick: () -> Unit, modifier: Modifier = Modifier, brush: Brush? = null) {
    val ring by animateDpAsState(if (selected) 3.dp else 0.dp, FitnexMotion.spec(), label = "ring")
    val check by animateFloatAsState(if (selected) 1f else 0f, FitnexMotion.pressSpring(), label = "check")
    val description = if (selected) stringResource(R.string.accent_selected, label) else label
    Box(
        modifier
            .size(44.dp)
            .clip(CircleShape)
            .border(ring, FitnexTheme.colors.textPrimary.copy(alpha = 0.9f), CircleShape)
            .padding(5.dp)
            .clip(CircleShape)
            .then(if (brush != null) Modifier.background(brush) else Modifier.background(color))
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp).scale(check))
    }
}

/** Last custom colour (or a rainbow ring) + opens the picker. */
@Composable
private fun CustomSwatch(color: Color?, selected: Boolean, onClick: () -> Unit) {
    val ring by animateDpAsState(if (selected) 3.dp else 0.dp, FitnexMotion.spec(), label = "customRing")
    val label = stringResource(R.string.appearance_custom_choose)
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .border(ring, FitnexTheme.colors.textPrimary.copy(alpha = 0.9f), CircleShape)
            .padding(5.dp)
            .clip(CircleShape)
            .background(Brush.sweepGradient(HueStops))
            .selectable(selected = selected, onClick = onClick, role = Role.Button)
            .semantics { contentDescription = label }
            .testTag("accent_custom"),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(22.dp).clip(CircleShape).background(color ?: FitnexTheme.colors.surfaceRaised),
            contentAlignment = Alignment.Center,
        ) {
            if (color == null) Icon(Icons.Rounded.Add, contentDescription = null, tint = FitnexTheme.colors.textPrimary, modifier = Modifier.size(16.dp))
        }
    }
}

private val HueStops = listOf(
    Color(0xFFFF0000), Color(0xFFFFFF00), Color(0xFF00FF00), Color(0xFF00FFFF),
    Color(0xFF0000FF), Color(0xFFFF00FF), Color(0xFFFF0000),
)

/**
 * Hue / saturation / brightness picker. Every change previews live across the whole app;
 * "Aplicar" saves it, closing the sheet restores the saved colour.
 */
@Composable
private fun ColorPickerSheet(initial: Color, onPreview: (Color) -> Unit, onApply: (Color) -> Unit, onDismiss: () -> Unit) {
    val hsv = remember { FloatArray(3).also { android.graphics.Color.colorToHSV(initial.toArgb(), it) } }
    var hue by remember { mutableFloatStateOf(hsv[0]) }
    var sat by remember { mutableFloatStateOf(hsv[1].coerceAtLeast(0.25f)) }
    var value by remember { mutableFloatStateOf(hsv[2].coerceAtLeast(0.35f)) }
    val color = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, value)))
    val update = { onPreview(Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, value)))) }

    FitnexBottomSheet(onDismiss = onDismiss, title = stringResource(R.string.picker_title), dragToDismiss = false) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.lg)) {
            Box(Modifier.size(64.dp).clip(CircleShape).background(color).testTag("picker_preview"))
            Text(
                "#%06X".format(color.toArgb() and 0xFFFFFF),
                style = FitnexTheme.typography.headlineMedium,
                color = FitnexTheme.colors.textPrimary,
            )
        }
        PickerTrack(
            stringResource(R.string.picker_hue), hue / 360f, Brush.horizontalGradient(HueStops),
            onChange = { hue = it * 360f; update() },
        )
        PickerTrack(
            stringResource(R.string.picker_saturation), sat,
            Brush.horizontalGradient(listOf(Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 0f, value))), Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 1f, value))))),
            onChange = { sat = it.coerceAtLeast(0.15f); update() },
        )
        PickerTrack(
            stringResource(R.string.picker_brightness), value,
            Brush.horizontalGradient(listOf(Color.Black, Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, 1f))))),
            onChange = { value = it.coerceAtLeast(0.25f); update() },
        )
        Text(
            stringResource(R.string.picker_contrast_note),
            style = FitnexTheme.typography.bodySmall, color = FitnexTheme.colors.textSecondary,
            modifier = Modifier.padding(top = Spacing.md),
        )
        FitnexPrimaryButton(
            stringResource(R.string.picker_apply), { onApply(color) },
            Modifier.fillMaxWidth().padding(top = Spacing.lg).testTag("picker_apply"),
        )
    }
}

/** A gradient track with a draggable round thumb; [fraction] in 0..1. */
@Composable
private fun PickerTrack(label: String, fraction: Float, brush: Brush, onChange: (Float) -> Unit) {
    val colors = FitnexTheme.colors
    Column(Modifier.fillMaxWidth().padding(top = Spacing.lg)) {
        Text(label, style = FitnexTheme.typography.labelMedium, color = colors.textSecondary, modifier = Modifier.padding(bottom = Spacing.sm))
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .height(32.dp)
                .semantics { stateDescription = "${(fraction * 100).toInt()} %" }
                .pointerInput(Unit) {
                    detectTapGestures { onChange((it.x / size.width).coerceIn(0f, 1f)) }
                }
                .pointerInput(Unit) {
                    detectDragGestures { change, _ -> onChange((change.position.x / size.width).coerceIn(0f, 1f)) }
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            Canvas(Modifier.fillMaxWidth().height(14.dp)) {
                drawRoundRect(brush, cornerRadius = CornerRadius(size.height / 2))
            }
            val thumb = 26.dp
            Box(
                Modifier
                    .offset(x = (maxWidth - thumb) * fraction.coerceIn(0f, 1f))
                    .size(thumb)
                    .clip(CircleShape)
                    .background(Color.White)
                    .border(2.dp, Color.Black.copy(alpha = 0.15f), CircleShape),
            )
        }
    }
}

