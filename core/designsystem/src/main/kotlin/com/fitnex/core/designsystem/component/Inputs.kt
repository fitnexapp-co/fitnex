package com.fitnex.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.icons.rounded.Person
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import com.fitnex.core.designsystem.theme.FitnexMotion
import com.fitnex.core.designsystem.theme.pressFeedback
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitnex.core.designsystem.R
import com.fitnex.core.designsystem.icon.FitnexIcons
import com.fitnex.core.designsystem.theme.Elevation
import com.fitnex.core.designsystem.theme.FitnexTheme
import com.fitnex.core.designsystem.theme.Radii
import com.fitnex.core.designsystem.theme.Spacing

/** 44dp glass circle for header actions. Always has a content description. */
@Composable
fun GlassIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconSize: Dp = 22.dp,
) {
    val colors = FitnexTheme.colors
    val interaction = remember { MutableInteractionSource() }
    // Inside the header capsule the capsule is the glass: the button is just the icon.
    val inCapsule = LocalInCapsule.current
    IconButton(
        onClick = onClick,
        interactionSource = interaction,
        modifier = modifier.size(if (inCapsule) 44.dp else 46.dp).liquidInteraction(CircleShape, interaction)
            .then(if (inCapsule) Modifier else Modifier.chromeSurface(CircleShape, body = 0.7f)),
    ) {
        Icon(icon, contentDescription = contentDescription, tint = colors.textPrimary, modifier = Modifier.size(iconSize))
    }
}

/** The signed-in user's photo, provided at the root; null keeps the default person icon. */
val LocalProfilePhoto = staticCompositionLocalOf<ImageBitmap?> { null }

/** Name of the "Entrenar juntos" profile on screen (null = the account owner): its initial marks the avatar. */
val LocalCompanionName = staticCompositionLocalOf<String?> { null }

/** Round avatar with a name's first letter, for profiles without a photo. */
@Composable
fun InitialAvatar(name: String, modifier: Modifier = Modifier, size: Dp = 42.dp) {
    val colors = FitnexTheme.colors
    Box(
        modifier.size(size).clip(CircleShape).background(colors.accent.copy(alpha = 0.22f)).border(1.dp, colors.accent.copy(alpha = 0.5f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name.trim().firstOrNull()?.uppercase() ?: "?",
            style = FitnexTheme.typography.titleMedium.copy(fontSize = (size.value * 0.42f).sp),
            color = colors.accent,
        )
    }
}

/** Circular avatar: the photo cropped to a circle, or a neutral person glyph. Never stretched. */
@Composable
fun FitnexAvatar(photo: ImageBitmap?, modifier: Modifier = Modifier, size: Dp = 64.dp, contentDescription: String? = null) {
    val colors = FitnexTheme.colors
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(colors.accentMuted)
            .border(1.dp, colors.glassBorder, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (photo != null) {
            Image(photo, contentDescription, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
        } else {
            Icon(Icons.Rounded.Person, contentDescription, tint = colors.accent, modifier = Modifier.size(size * 0.5f))
        }
    }
}

/** Header shortcut to the calendar (reachable from Progress and Workout, spec §22). */
@Composable
fun CalendarHeaderAction(onClick: () -> Unit, modifier: Modifier = Modifier) =
    GlassIconButton(FitnexIcons.Calendar, stringResource(R.string.ds_open_calendar), onClick, modifier)

/** Header shortcut to profile and settings (spec §2). */
@Composable
fun ProfileHeaderAction(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val photo = LocalProfilePhoto.current
    val companion = LocalCompanionName.current
    val interaction = remember { MutableInteractionSource() }
    when {
        photo != null -> IconButton(onClick = onClick, interactionSource = interaction, modifier = modifier.size(44.dp).pressFeedback(interaction)) {
            FitnexAvatar(photo, size = 40.dp, contentDescription = stringResource(R.string.ds_open_profile))
        }
        companion != null -> IconButton(onClick = onClick, interactionSource = interaction, modifier = modifier.size(44.dp).pressFeedback(interaction)) {
            InitialAvatar(companion, size = 40.dp)
        }
        else -> GlassIconButton(Icons.Rounded.Person, stringResource(R.string.ds_open_profile), onClick, modifier)
    }
}

/** Pill-shaped segmented control, e.g. the statistics period selector (spec §20). */
@Composable
fun <T> FitnexSegmentedControl(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier,
    /** Smaller pills for secondary toggles (map view, period filter). */
    compact: Boolean = false,
) {
    val colors = FitnexTheme.colors
    val neutralThumb = colors.signature && colors.isDark
    val pillHeight = if (compact) 32.dp else 38.dp
    val index = options.indexOf(selected).coerceAtLeast(0)
    // Slide a finger across the options (iPhone style) or tap one.
    val slide = rememberSlideSelectState()
    val thumbPos by animatedPillPosition(index, slide)
    val grow by animateFloatAsState(if (slide.dragging) 1f else 0f, FitnexMotion.pressSpring(), label = "thumbGrow")
    val shape = RoundedCornerShape(Radii.pill)
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .liquidInteraction(shape, pressable = false, shimmerKey = index)
            .chromeSurface(shape, fallback = { c -> clip(shape).background(if (c.isDark) c.surfaceRaised else c.divider).liquidRim(c, shape) })
            .padding(3.dp)
            .slideToSelect(slide, options.size) { onSelect(options[it]) },
    ) {
        val segment = maxWidth / options.size.coerceAtLeast(1)
        // The selected pill slides between options instead of jumping, and swells under the finger.
        Box(
            Modifier
                // Lambda offset: the sliding pill moves at layout time without recomposing each frame.
                .offset { androidx.compose.ui.unit.IntOffset((segment * thumbPos).roundToPx(), 0) }
                .width(segment)
                .height(pillHeight)
                .graphicsLayer {
                    scaleX = 1f + 0.06f * grow
                    scaleY = 1f + 0.10f * grow
                }
                .shadow(if (colors.isDark) 0.dp else 2.dp, shape)
                .clip(shape)
                .then(
                    // FITNEX style: a lighter smoked-glass pill with white text; a picked accent fills it.
                    if (neutralThumb) {
                        Modifier
                            .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.20f), Color.White.copy(alpha = 0.10f))))
                            .border(1.dp, Color.White.copy(alpha = 0.14f), shape)
                    } else {
                        Modifier.background(colors.accent)
                    },
                ),
        )
        val hover = slide.hoverIndex
        Row(Modifier.fillMaxWidth().selectableGroup()) {
            options.forEachIndexed { i, option ->
                val isSelected = option == selected
                val lit = if (hover != null) hover == i else isSelected
                val fg by animateColorAsState(if (lit) (if (neutralThumb) colors.textPrimary else colors.onAccent) else colors.textSecondary, FitnexMotion.spec(), label = "segmentText")
                Text(
                    text = label(option),
                    style = if (compact) FitnexTheme.typography.labelSmall.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold) else FitnexTheme.typography.labelMedium,
                    color = fg,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier
                        .weight(1f)
                        .height(pillHeight)
                        .clip(shape)
                        .selectable(selected = isSelected, onClick = { onSelect(option) }, role = Role.Tab)
                        .wrapContentHeight(),
                )
            }
        }
    }
}

/** A settings row with a switch; the whole row is the touch target. */
@Composable
fun FitnexSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    icon: ImageVector? = null,
) {
    val colors = FitnexTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .toggleable(value = checked, onValueChange = onCheckedChange, role = Role.Switch)
            .heightIn(min = 56.dp)
            .padding(vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = colors.textPrimary, modifier = Modifier.padding(end = Spacing.lg).size(24.dp))
        }
        Column(Modifier.weight(1f).padding(end = Spacing.md)) {
            Text(title, style = FitnexTheme.typography.titleMedium, color = colors.textPrimary)
            if (description != null) {
                Text(description, style = FitnexTheme.typography.bodySmall, color = colors.textSecondary)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = null, // handled by the row's toggleable
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = colors.accent,
                checkedBorderColor = Color.Transparent,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = if (colors.isDark) Color(0xFF3A3A40) else Color(0xFFE3E3E8),
                uncheckedBorderColor = Color.Transparent,
            ),
        )
    }
}

/** Labelled slider with the formatted current value on the right. */
@Composable
fun FitnexSliderRow(
    title: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    valueLabel: String,
    modifier: Modifier = Modifier,
    onValueChangeFinished: (() -> Unit)? = null,
) {
    val colors = FitnexTheme.colors
    Column(modifier.fillMaxWidth().padding(vertical = Spacing.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = FitnexTheme.typography.titleMedium, color = colors.textPrimary, modifier = Modifier.weight(1f))
            Text(valueLabel, style = FitnexTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"), color = colors.accent)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = valueRange,
            steps = steps,
            colors = SliderDefaults.colors(
                thumbColor = colors.accent,
                activeTrackColor = colors.accent,
                // Visible on both the dark and the white card (surfaceRaised vanished in light mode).
                inactiveTrackColor = colors.textDisabled.copy(alpha = 0.35f),
                // Steps still snap, but tick dots are hidden: 17 dots cluttered the track.
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent,
            ),
        )
    }
}

/**
 * Metric tile: the number is the hero, the label and unit stay quiet. Fixed minimum height so
 * tiles in a grid line up (never a fabricated delta).
 */
@Composable
fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    unit: String? = null,
    caption: String? = null,
    /** Small violet line icon before the label. */
    icon: ImageVector? = null,
    /** Change against the previous period, e.g. "+2" / "−1"; null = no comparison shown. */
    delta: String? = null,
    /** True = went up (green arrow), false = went down, null = unchanged (neutral). */
    deltaUp: Boolean? = null,
) {
    val colors = FitnexTheme.colors
    // One accessibility unit: TalkBack reads "Series, 4" instead of two separate fragments.
    GlassCard(
        modifier.heightIn(min = 96.dp).semantics(mergeDescendants = true) { },
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = Spacing.lg, vertical = Spacing.md),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = colors.accent, modifier = Modifier.padding(end = Spacing.sm).size(22.dp))
            }
            Text(label, style = FitnexTheme.typography.labelMedium, color = colors.textSecondary, maxLines = 1)
        }
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = Spacing.xs)) {
            Text(
                value,
                style = FitnexTheme.typography.displayMedium.copy(fontSize = 30.sp, lineHeight = 34.sp),
                color = colors.textPrimary,
                maxLines = 1,
            )
            if (unit != null) {
                Text(
                    " $unit",
                    style = FitnexTheme.typography.titleMedium,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            if (delta != null) {
                val tint = when (deltaUp) { true -> colors.success; false -> colors.warning; null -> colors.textSecondary }
                val arrow = when (deltaUp) { true -> "↑ "; false -> "↓ "; null -> "" }
                Text(
                    arrow + delta,
                    style = FitnexTheme.typography.labelMedium,
                    color = tint,
                    maxLines = 1,
                    modifier = Modifier.padding(start = Spacing.sm, bottom = 6.dp),
                )
            }
        }
        if (caption != null) {
            Text(caption, style = FitnexTheme.typography.bodySmall, color = colors.textDisabled, maxLines = 1)
        }
    }
}

/**
 * Secondary action tinted with the accent (e.g. "Analizar mi progreso"): clearly tappable, but it
 * never competes with the screen's primary button.
 */
@Composable
fun FitnexTonalButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val colors = FitnexTheme.colors
    val interaction = remember { MutableInteractionSource() }
    androidx.compose.material3.Button(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interaction,
        shape = RoundedCornerShape(Radii.md),
        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
            containerColor = colors.accent.copy(alpha = if (colors.isDark) 0.16f else 0.11f),
            contentColor = colors.accent,
            disabledContainerColor = colors.accent.copy(alpha = 0.06f),
            disabledContentColor = colors.textDisabled,
        ),
        elevation = null,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = Spacing.lg, vertical = Spacing.sm),
        modifier = modifier.heightIn(min = 46.dp).liquidInteraction(RoundedCornerShape(Radii.md), interaction, enabled = enabled).liquidRim(colors, RoundedCornerShape(Radii.md)),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            androidx.compose.foundation.layout.Spacer(Modifier.width(Spacing.sm))
        }
        Text(text, style = FitnexTheme.typography.labelLarge, maxLines = 1)
    }
}