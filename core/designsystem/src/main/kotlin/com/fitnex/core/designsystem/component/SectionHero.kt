package com.fitnex.core.designsystem.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitnex.core.designsystem.R
import com.fitnex.core.designsystem.theme.FitnexTheme
import com.fitnex.core.designsystem.theme.Spacing

/** The FITNEX mark (F + violet dumbbell): the app icon's artwork, with an ink F on light backgrounds. */
@Composable
fun FitnexLogo(modifier: Modifier = Modifier, size: Dp = 40.dp) {
    // The drawable is a 108 dp adaptive-icon canvas whose art fills the middle ~60 %: scale the margin away.
    Box(modifier.size(size).clipToBounds(), contentAlignment = Alignment.Center) {
        Image(
            painter = painterResource(if (FitnexTheme.colors.isDark) R.drawable.ic_fitnex_mark else R.drawable.ic_fitnex_mark_ink),
            contentDescription = stringResource(R.string.ds_logo),
            modifier = Modifier.size(size).scale(1.6f),
        )
    }
}

/**
 * Top of every main tab, identical in Progreso, Nutrición and Entrenamiento: the logo and the
 * header buttons, then the overline, a large title and one line saying what the section is for.
 */
@Composable
fun FitnexSectionHero(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    overline: String? = null,
    overlineIcon: ImageVector? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val colors = FitnexTheme.colors
    Column(modifier.fillMaxWidth().padding(horizontal = Spacing.gutter).padding(top = Spacing.sm, bottom = Spacing.lg)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            FitnexLogo(size = 46.dp)
            Box(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically, content = actions)
        }
        if (overline != null) {
            Row(Modifier.padding(top = Spacing.md), verticalAlignment = Alignment.CenterVertically) {
                overlineIcon?.let { Icon(it, null, tint = colors.accent, modifier = Modifier.size(20.dp).padding(end = Spacing.xs)) }
                Text(overline, style = FitnexTheme.typography.labelLarge, color = colors.accent)
            }
        }
        Text(
            title,
            style = FitnexTheme.typography.headlineLarge.copy(fontSize = 36.sp, lineHeight = 42.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.6).sp),
            color = colors.textPrimary, maxLines = 1,
            modifier = Modifier.semantics { heading() },
        )
        Text(subtitle, style = FitnexTheme.typography.bodyMedium, color = colors.textSecondary, modifier = Modifier.padding(top = Spacing.xxs))
    }
}
