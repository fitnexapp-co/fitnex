package com.fitnex.core.designsystem.component

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.fitnex.core.designsystem.theme.FitnexTheme
import com.fitnex.core.designsystem.theme.Radii
import com.fitnex.core.designsystem.theme.Spacing

/** Glass-styled bottom sheet for quick edits (weight, reps, meal portions…). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FitnexBottomSheet(
    onDismiss: () -> Unit,
    title: String? = null,
    /** False for sheets with scrollable wheels, so a fast swipe never dismisses the sheet. */
    dragToDismiss: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = FitnexTheme.colors
    val liquid = FitnexTheme.appearance.liquidGlass
    val shape = RoundedCornerShape(topStart = Radii.xl, topEnd = Radii.xl)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetGesturesEnabled = dragToDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        // Liquid Glass: translucent sheet with a luminous rim (a sheet is its own window, so no backdrop sampling).
        // Nearly opaque: without a blur behind it, a lower alpha let the screen's text show through the sheet.
        containerColor = colors.surfaceRaised,
        contentColor = colors.textPrimary,
        scrimColor = Color.Black.copy(alpha = if (colors.isDark) 0.6f else 0.3f),
        shape = shape,
        tonalElevation = 0.dp,
        dragHandle = { BottomSheetDefaults.DragHandle(color = colors.textDisabled, width = 36.dp, height = 5.dp) },
        // No border: a border around the whole sheet also drew its lower edge as a stray line
        // across the content.
    ) {
        if (title != null) {
            Text(
                title,
                style = FitnexTheme.typography.titleLarge,
                color = colors.textPrimary,
                modifier = Modifier.padding(horizontal = Spacing.gutter, vertical = Spacing.sm),
            )
        }
        androidx.compose.foundation.layout.Column(
            Modifier.padding(horizontal = Spacing.gutter).padding(bottom = Spacing.xl).navigationBarsPadding(),
            content = content,
        )
    }
}

/**
 * Confirmation dialog. Set [destructive] for irreversible actions (delete routine, delete
 * account) so the confirm action is drawn in red.
 */
@Composable
fun FitnexConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    dismissLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
) {
    val colors = FitnexTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        // Opaque: a translucent dialog let the screen's text show through and was hard to read.
        containerColor = colors.surfaceRaised,
        tonalElevation = 0.dp,
        titleContentColor = colors.textPrimary,
        textContentColor = colors.textSecondary,
        shape = RoundedCornerShape(Radii.xl),
        modifier = Modifier.liquidRim(colors, RoundedCornerShape(Radii.xl)),
        title = { Text(title, style = FitnexTheme.typography.titleLarge) },
        text = { Text(message, style = FitnexTheme.typography.bodyMedium) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel, style = FitnexTheme.typography.labelLarge, color = if (destructive) colors.error else colors.accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(dismissLabel, style = FitnexTheme.typography.labelLarge, color = colors.textSecondary)
            }
        },
    )
}
