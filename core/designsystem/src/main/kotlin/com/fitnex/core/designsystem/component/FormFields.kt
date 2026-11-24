package com.fitnex.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.fitnex.core.designsystem.R
import com.fitnex.core.designsystem.theme.FitnexTheme
import com.fitnex.core.designsystem.theme.Radii
import com.fitnex.core.designsystem.theme.Spacing

/** Password field with a show/hide toggle (never shown in plain text by default). */
@Composable
fun FitnexPasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    errorText: String? = null,
    imeAction: ImeAction = ImeAction.Done,
) {
    var visible by rememberSaveable { mutableStateOf(false) }
    FitnexTextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        modifier = modifier,
        supportingText = supportingText,
        errorText = errorText,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = imeAction, autoCorrectEnabled = false),
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            // TextButton sizes to its label; an IconButton (48dp) wrapped "MOSTRAR" onto two lines.
            TextButton(onClick = { visible = !visible }, modifier = Modifier.padding(end = Spacing.xs)) {
                Text(
                    stringResource(if (visible) R.string.ds_password_hide else R.string.ds_password_show),
                    style = FitnexTheme.typography.labelSmall,
                    color = FitnexTheme.colors.textSecondary,
                    maxLines = 1,
                )
            }
        },
    )
}

/** Multi- or single-select chip group laid out in a flowing wrap. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> FitnexChipGroup(
    options: List<T>,
    isSelected: (T) -> Boolean,
    onToggle: (T) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier,
    icon: ((T) -> ImageVector?)? = null,
) {
    val colors = FitnexTheme.colors
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        options.forEach { option ->
            val selected = isSelected(option)
            FilterChip(
                selected = selected,
                onClick = { onToggle(option) },
                label = { Text(label(option), style = FitnexTheme.typography.bodyMedium) },
                leadingIcon = when {
                    selected -> { { Icon(Icons.Rounded.Check, contentDescription = null) } }
                    icon?.invoke(option) != null -> { { Icon(icon(option)!!, contentDescription = null) } }
                    else -> null
                },
                shape = androidx.compose.foundation.shape.RoundedCornerShape(Radii.pill),
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = colors.glassFill,
                    labelColor = colors.textPrimary,
                    selectedContainerColor = colors.accent,
                    selectedLabelColor = colors.onAccent,
                    selectedLeadingIconColor = colors.onAccent,
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = selected,
                    borderColor = colors.glassBorder.copy(alpha = 0.3f),
                    selectedBorderColor = colors.accent,
                    borderWidth = 1.dp,
                ),
            )
        }
    }
}
