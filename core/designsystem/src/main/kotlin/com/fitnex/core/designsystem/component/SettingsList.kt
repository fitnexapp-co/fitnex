package com.fitnex.core.designsystem.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fitnex.core.designsystem.theme.FitnexTheme
import com.fitnex.core.designsystem.theme.Spacing
import com.fitnex.core.designsystem.theme.pressFeedback

/** Small uppercase label above a settings group ("ENTRENAMIENTO"). */
@Composable
fun SettingsSectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = FitnexTheme.typography.labelMedium,
        color = FitnexTheme.colors.textSecondary,
        modifier = modifier.padding(start = Spacing.xs, top = Spacing.md, bottom = Spacing.xs),
    )
}

/** Rounded group of settings rows; rows are separated by hairlines inset past the icon. */
@Composable
fun SettingsGroup(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    GlassCard(modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.xs), content = content)
}

/** Hairline between rows of a [SettingsGroup], starting after the icon column. */
@Composable
fun SettingsDivider(withIcon: Boolean = true) {
    HorizontalDivider(color = FitnexTheme.colors.divider, modifier = Modifier.padding(start = if (withIcon) 40.dp else 0.dp))
}

/**
 * One row of a settings list: icon (or any [leading]), title, optional subtitle, optional value
 * on the right and a chevron when it opens something. The whole row is the touch target.
 */
@Composable
fun SettingsRow(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    leading: (@Composable () -> Unit)? = null,
    subtitle: String? = null,
    value: String? = null,
    chevron: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val colors = FitnexTheme.colors
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier.pressFeedback(interaction).clickable(interaction, indication = null, role = Role.Button, onClick = onClick)
                } else Modifier,
            )
            .heightIn(min = 60.dp)
            .padding(vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            leading != null -> Row(Modifier.padding(end = Spacing.lg)) { leading() }
            icon != null -> Icon(icon, contentDescription = null, tint = colors.textPrimary, modifier = Modifier.padding(end = Spacing.lg).size(24.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = FitnexTheme.typography.titleMedium, color = colors.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(subtitle, style = FitnexTheme.typography.bodySmall, color = colors.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        if (value != null) {
            Text(value, style = FitnexTheme.typography.bodyMedium, color = colors.textSecondary, maxLines = 1, modifier = Modifier.padding(start = Spacing.sm))
        }
        trailing?.invoke()
        if (chevron && onClick != null) {
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = colors.textDisabled,
                modifier = Modifier.padding(start = Spacing.xs).size(22.dp),
            )
        }
    }
}
