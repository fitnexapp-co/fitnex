package com.fitnex.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.fitnex.core.designsystem.theme.FitnexMotion
import com.fitnex.core.designsystem.theme.FitnexTheme
import com.fitnex.core.designsystem.theme.pressFeedback
import com.fitnex.core.designsystem.theme.Radii
import com.fitnex.core.designsystem.theme.Spacing

private val ButtonShape = RoundedCornerShape(Radii.md)

/** Primary call to action. Shows an inline spinner while [loading] and ignores taps meanwhile. */
@Composable
fun FitnexPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: ImageVector? = null,
) {
    val colors = FitnexTheme.colors
    val interaction = remember { MutableInteractionSource() }
    // Disabled is neutral grey, not a dimmed violet: a faded accent still looked tappable.
    val container by animateColorAsState(if (enabled) colors.accent else colors.surfaceRaised, FitnexMotion.spec(), label = "btn")
    // Violet gradient with a soft glow underneath (flat and dimmed when disabled).
    val fill = if (enabled) {
        androidx.compose.ui.graphics.Brush.horizontalGradient(
            listOf(androidx.compose.ui.graphics.lerp(colors.accent, androidx.compose.ui.graphics.Color.Black, 0.12f), androidx.compose.ui.graphics.lerp(colors.accent, androidx.compose.ui.graphics.Color.White, 0.14f)),
        )
    } else {
        androidx.compose.ui.graphics.SolidColor(container)
    }
    Button(
        onClick = { if (!loading) onClick() },
        enabled = enabled,
        interactionSource = interaction,
        shape = ButtonShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
            contentColor = colors.onAccent,
            disabledContainerColor = androidx.compose.ui.graphics.Color.Transparent,
            disabledContentColor = colors.textDisabled,
        ),
        elevation = null,
        contentPadding = PaddingValues(horizontal = Spacing.xl, vertical = Spacing.md),
        modifier = modifier
            .heightIn(min = 52.dp)
            .liquidInteraction(ButtonShape, interaction, enabled = enabled)
            .then(
                if (enabled && colors.isDark) {
                    Modifier.shadow(14.dp, ButtonShape, ambientColor = colors.accent, spotColor = colors.accent)
                } else Modifier,
            )
            .background(fill, ButtonShape)
            .liquidRim(colors, ButtonShape),
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(20.dp), color = colors.onAccent, strokeWidth = 2.dp)
        } else {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(Spacing.sm))
            }
            Text(text, style = FitnexTheme.typography.labelLarge, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
    }
}

/** Quiet, filled-tonal action (no outline): grey surface, primary text. */
@Composable
fun FitnexSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = FitnexTheme.colors
    val interaction = remember { MutableInteractionSource() }
    Button(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interaction,
        shape = ButtonShape,

        colors = ButtonDefaults.buttonColors(
            containerColor = if (colors.isDark) colors.surfaceRaised else colors.background,
            contentColor = colors.textPrimary,
            disabledContainerColor = if (colors.isDark) colors.surfaceRaised else colors.background,
            disabledContentColor = colors.textDisabled,
        ),
        elevation = null,
        contentPadding = PaddingValues(horizontal = Spacing.xl, vertical = Spacing.md),
        modifier = modifier.heightIn(min = 52.dp).liquidInteraction(ButtonShape, interaction, enabled = enabled).liquidRim(colors, ButtonShape),
    ) {
        // Never cut mid-word without a sign: a label too long for its button ends in "…".
        Text(text, style = FitnexTheme.typography.labelLarge, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
    }
}

@Composable
fun FitnexTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    errorText: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    singleLine: Boolean = true,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingIcon: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    readOnly: Boolean = false,
) {
    val colors = FitnexTheme.colors
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        isError = errorText != null,
        supportingText = (errorText ?: supportingText)?.let { msg -> { Text(msg) } },
        keyboardOptions = keyboardOptions,
        singleLine = singleLine,
        visualTransformation = visualTransformation,
        trailingIcon = trailingIcon,
        enabled = enabled,
        readOnly = readOnly,
        shape = ButtonShape,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = colors.accent,
            unfocusedBorderColor = colors.glassBorder,
            disabledBorderColor = colors.glassBorder,
            focusedContainerColor = if (colors.isDark) colors.surfaceRaised else colors.background,
            unfocusedContainerColor = if (colors.isDark) colors.surfaceRaised else colors.background,
            disabledContainerColor = if (colors.isDark) colors.surfaceRaised else colors.background,
            focusedTextColor = colors.textPrimary,
            unfocusedTextColor = colors.textPrimary,
            errorBorderColor = colors.error,
            focusedLabelColor = colors.accent,
            unfocusedLabelColor = colors.textSecondary,
            cursorColor = colors.accent,
        ),
        modifier = modifier.fillMaxWidth(),
    )
}

/** Circular progress indicator for goals/consistency. [progress] is clamped to 0..1. */
@Composable
fun FitnexProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    size: Dp = 96.dp,
    strokeWidth: Dp = 8.dp,
    accessibilityLabel: String,
    center: @Composable () -> Unit = {},
) {
    // Goal progress glides to its new value (it starts at the target, so nothing replays on entry).
    val animated by animateFloatAsState(progress.coerceIn(0f, 1f), FitnexMotion.spec(500), label = "ring")
    FitnexProgressRing(progress = { animated }, modifier, size, strokeWidth, accessibilityLabel, center)
}

/**
 * Same ring, but [progress] is read while drawing, so a caller can update it every frame (a
 * countdown that moves continuously) without recomposing anything.
 */
@Composable
fun FitnexProgressRing(
    progress: () -> Float,
    modifier: Modifier = Modifier,
    size: Dp = 96.dp,
    strokeWidth: Dp = 8.dp,
    accessibilityLabel: String,
    center: @Composable () -> Unit = {},
) {
    val colors = FitnexTheme.colors
    Box(modifier.size(size).clearAndSetSemantics { contentDescription = accessibilityLabel }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val stroke = Stroke(strokeWidth.toPx(), cap = StrokeCap.Round)
            drawArc(colors.accent.copy(alpha = 0.18f), -90f, 360f, useCenter = false, style = stroke)
            val value = progress().coerceIn(0f, 1f)
            if (value > 0f) {
                drawArc(
                    brush = Brush.sweepGradient(listOf(colors.accent, colors.accentStrong, colors.accent)),
                    startAngle = -90f, sweepAngle = 360f * value, useCenter = false, style = stroke,
                )
            }
        }
        center()
    }
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth().padding(vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(title, style = FitnexTheme.typography.labelMedium, color = FitnexTheme.colors.textSecondary)
        action?.invoke()
    }
}

@Composable
fun LoadingState(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(Spacing.xxl), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = FitnexTheme.colors.accent)
    }
}

/** Empty or error state. Pass [actionLabel]/[onAction] only when a real action exists. */
@Composable
fun MessageState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val colors = FitnexTheme.colors
    Column(
        modifier.fillMaxWidth().padding(Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        if (icon != null) {
            Box(
                Modifier.size(64.dp).background(colors.accentMuted, RoundedCornerShape(Radii.pill)),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, contentDescription = null, tint = colors.accent, modifier = Modifier.size(28.dp)) }
        }
        Text(title, style = FitnexTheme.typography.titleLarge, color = colors.textPrimary, textAlign = TextAlign.Center)
        Text(message, style = FitnexTheme.typography.bodyMedium, color = colors.textSecondary, textAlign = TextAlign.Center)
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.size(Spacing.sm))
            FitnexPrimaryButton(actionLabel, onAction)
        }
    }
}
