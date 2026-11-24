package com.fitnex.feature.progress

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitnex.core.designsystem.icon.FitnexIcons
import com.fitnex.core.designsystem.theme.FitnexTheme
import com.fitnex.core.designsystem.theme.Spacing
import java.time.format.DateTimeFormatter

private data class HeroCard(
    val label: String,
    val icon: ImageVector,
    val value: String,
    val valueLabel: String,
    val footer: String,
    val cornerLabel: String,
    val cornerValue: String,
    val tag: String,
)

/**
 * "Tu experiencia FITNEX": a swipeable row of brushed-graphite cards (the number is the hero,
 * the person's name in spaced capitals underneath). The next card peeks in to invite the swipe.
 */
@Composable
internal fun ExperienceCards(e: Experience, a: com.fitnex.core.model.Achievements?) {
    val monthYear = DateTimeFormatter.ofPattern("MM/yy")
    val dayMonth = DateTimeFormatter.ofPattern("dd/MM")
    val name = e.name.ifBlank { "FITNEX" }.uppercase()
    val cards = listOf(
        HeroCard(
            label = stringResource(R.string.exp_label),
            icon = FitnexIcons.Workout,
            value = "%,d".format(e.minutes),
            valueLabel = stringResource(R.string.exp_minutes),
            footer = androidx.compose.ui.res.pluralStringResource(R.plurals.exp_workouts_year, e.workouts, e.workouts, e.year),
            cornerLabel = stringResource(R.string.exp_member),
            cornerValue = e.memberSince?.format(monthYear) ?: "—",
            tag = "exp_card_minutes",
        ),
        HeroCard(
            label = stringResource(R.string.exp_streak_label),
            icon = FitnexIcons.Progress,
            value = (a?.streakWeeks ?: 0).toString(),
            valueLabel = stringResource(R.string.exp_streak),
            footer = stringResource(R.string.exp_sets_total, e.sets),
            cornerLabel = stringResource(R.string.exp_last),
            cornerValue = e.lastWorkout?.format(dayMonth) ?: "—",
            tag = "exp_card_streak",
        ),
    )
    BoxWithConstraints(Modifier.testTag("experience_cards")) {
        val cardWidth = (maxWidth - Spacing.gutter * 2) * 0.93f
        val listState = rememberLazyListState()
        LazyRow(
            state = listState,
            flingBehavior = rememberSnapFlingBehavior(listState),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            // Bleeds to the screen edge on the right so the next card peeks in.
            contentPadding = PaddingValues(horizontal = Spacing.gutter),
        ) {
            items(cards.size) { i -> ExperienceCard(cards[i], name, Modifier.width(cardWidth)) }
        }
    }
}

@Composable
private fun ExperienceCard(c: HeroCard, name: String, modifier: Modifier = Modifier) {
    val dark = FitnexTheme.colors.isDark
    val shape = RoundedCornerShape(28.dp)
    // Deep violet glass with a sweeping beam of light, as in the reference: the number is the hero.
    val accent = FitnexTheme.colors.accent
    // Tinted with the user's accent (violet, orange…): deep and dark, never a flat colour.
    val base = if (dark) listOf(androidx.compose.ui.graphics.lerp(accent, Color.Black, 0.80f), androidx.compose.ui.graphics.lerp(accent, Color.Black, 0.88f), androidx.compose.ui.graphics.lerp(accent, Color.Black, 0.94f))
    else listOf(androidx.compose.ui.graphics.lerp(accent, Color.Black, 0.62f), androidx.compose.ui.graphics.lerp(accent, Color.Black, 0.74f), androidx.compose.ui.graphics.lerp(accent, Color.Black, 0.82f))
    val ink = Color.White
    val soft = Color.White.copy(alpha = 0.66f)
    // Liquid glass over the violet base: diagonal rim light, light sweep and spring press.
    com.fitnex.core.designsystem.component.LiquidGlassSurface(
        modifier = modifier
            .height(206.dp)
            .shadow(18.dp, shape, ambientColor = accent.copy(alpha = 0.5f), spotColor = accent.copy(alpha = 0.6f))
            // One TalkBack stop per card: "Tu experiencia FITNEX, 120, minutos entrenados…"
            .semantics(mergeDescendants = true) {}
            .testTag(c.tag),
        shape = shape,
        fill = Brush.linearGradient(base, start = Offset.Zero, end = Offset(900f, 700f)),
        elevation = 0.dp,
    ) {
    Box(
        Modifier
            .matchParentSize()
            .drawBehind {
                // Soft violet bloom bottom-right and a curved beam sweeping across the card.
                drawRect(Brush.radialGradient(listOf(accent.copy(alpha = 0.45f), Color.Transparent), Offset(size.width * 0.95f, size.height * 0.55f), size.width * 0.55f))
                drawRect(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.07f), Color.Transparent), Offset(size.width * 0.15f, 0f), size.width * 0.6f))
                val beam = androidx.compose.ui.graphics.Path().apply {
                    moveTo(size.width * 0.38f, size.height * 1.05f)
                    cubicTo(size.width * 0.6f, size.height * 0.62f, size.width * 0.82f, size.height * 0.42f, size.width * 1.05f, size.height * 0.30f)
                }
                drawPath(beam, Brush.linearGradient(listOf(Color.Transparent, accent.copy(alpha = 0.55f), Color.White.copy(alpha = 0.75f)), start = Offset(size.width * 0.4f, size.height), end = Offset(size.width, size.height * 0.3f)), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.2.dp.toPx()))
                drawPath(beam, Brush.linearGradient(listOf(Color.Transparent, accent.copy(alpha = 0.35f)), start = Offset(size.width * 0.4f, size.height), end = Offset(size.width, size.height * 0.3f)), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 14.dp.toPx()))
            }
            .border(1.dp, Brush.linearGradient(listOf(accent.copy(alpha = 0.45f), Color.Transparent, accent.copy(alpha = 0.25f)), start = Offset.Zero, end = Offset(900f, 700f)), shape)
            .padding(horizontal = 22.dp, vertical = 20.dp),
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(FitnexIcons.Progress, null, tint = accent, modifier = Modifier.size(26.dp))
                Text(
                    c.label.uppercase(), color = soft, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 2.4.sp),
                    modifier = Modifier.padding(start = Spacing.md).weight(1f),
                )
                Icon(c.icon, null, tint = ink, modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.weight(1f))
            // Staggered entrance: the figure, then its label, rise 14 dp into place inside a clip.
            com.fitnex.core.designsystem.component.StaggeredReveal(index = 0) {
                Text(
                    c.value, maxLines = 1,
                    style = TextStyle(
                        fontSize = 48.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp,
                        brush = Brush.verticalGradient(listOf(Color.White, androidx.compose.ui.graphics.lerp(accent, Color.White, 0.8f))),
                        shadow = Shadow(accent.copy(alpha = 0.55f), Offset(0f, 2f), 14f),
                    ),
                )
            }
            com.fitnex.core.designsystem.component.StaggeredReveal(index = 1) {
                Text(c.valueLabel.uppercase(), color = soft, style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 2.4.sp))
            }
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(FitnexIcons.Calendar, null, tint = accent, modifier = Modifier.size(22.dp))
                Column(Modifier.weight(1f).padding(start = Spacing.sm)) {
                    Text(name, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.2.sp))
                    Text(c.footer, color = soft, maxLines = 1, style = TextStyle(fontSize = 13.sp))
                }
                // Thin divider before the corner figure.
                Box(Modifier.padding(horizontal = Spacing.md).width(1.dp).height(36.dp).background(Color.White.copy(alpha = 0.18f)))
                Column(horizontalAlignment = Alignment.End) {
                    Text(c.cornerLabel.uppercase(), color = soft, style = TextStyle(fontSize = 9.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp))
                    Text(c.cornerValue, color = ink, style = TextStyle(fontSize = 19.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp))
                }
            }
        }
    }
    }
}
