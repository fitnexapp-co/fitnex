package com.fitnex.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.scale
import com.fitnex.core.designsystem.theme.FitnexMotion
import com.fitnex.core.designsystem.theme.pressFeedback
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.fitnex.core.designsystem.R
import com.fitnex.core.designsystem.icon.FitnexIcons
import com.fitnex.core.designsystem.theme.Elevation
import com.fitnex.core.designsystem.theme.FitnexTheme
import com.fitnex.core.designsystem.theme.Radii
import com.fitnex.core.designsystem.theme.Spacing

/** Option under the finger while the bar is being swiped (null = not dragging). */
private val LocalBarHover = androidx.compose.runtime.compositionLocalOf<Int?> { null }

/**
 * Floating glass bottom bar. FITNEX has exactly three destinations (spec §2). A pill marks the
 * current tab; sliding a finger along the bar drags it (a magnifying glass lens with Liquid Glass)
 * and lifting the finger opens that tab. Tapping works as always.
 */
@Composable
fun FitnexBottomBar(
    selectedIndex: Int,
    itemCount: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val colors = FitnexTheme.colors
    val shape = RoundedCornerShape(Radii.xl)
    val slide = rememberSlideSelectState()
    val pillPos by animatedPillPosition(selectedIndex, slide)
    val lensOn = liquidLensAvailable()
    val grow by animateFloatAsState(if (slide.dragging) 1f else 0f, FitnexMotion.pressSpring(), label = "lensGrow")
    // The optional trailing element (FITNEX AI) sits beside the bar, never on top of the content.
    Row(
        modifier = modifier
            .navigationBarsPadding()
            .padding(horizontal = Spacing.gutter, vertical = Spacing.md)
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Liquid glass dock: real backdrop blur of what scrolls behind it, diagonal specular rim and
        // a sweep of light each time the tab changes.
        LiquidGlassSurface(
            modifier = Modifier.weight(1f),
            shape = shape,
            blur = true,
            body = 0.6f,
            elevation = Elevation.floating,
            pressable = false,
            shimmerKey = selectedIndex,
        ) {
        androidx.compose.foundation.layout.BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .slideToSelect(slide, itemCount, onSelect),
            contentAlignment = Alignment.CenterStart,
        ) {
            val inner = maxWidth - Spacing.xs * 2
            val segment = inner / itemCount.coerceAtLeast(1)
            if (selectedIndex >= 0 || slide.dragging) {
                val pillShape = RoundedCornerShape(50)
                Box(
                    Modifier
                        .padding(start = Spacing.xs)
                        // Lambda offset: moves at layout time, without recomposing the bar each frame.
                        .offset { androidx.compose.ui.unit.IntOffset((segment * pillPos).roundToPx(), 0) }
                        .width(segment)
                        .height(56.dp)
                        .padding(horizontal = 3.dp)
                        .graphicsLayer {
                            val s = 1f + 0.14f * grow
                            // Liquid stretch: the pill elongates while it travels between tabs and
                            // settles back with the spring's subtle bounce.
                            val stretch = if (slide.dragging) 0f else kotlin.math.abs(selectedIndex - pillPos).coerceAtMost(1f)
                            scaleX = s * (1f + 0.06f * stretch)
                            scaleY = s * (1f - 0.02f * stretch)
                        }
                        .then(
                            if (lensOn && grow > 0.01f) {
                                // While dragging: a small lens that magnifies what passes under it.
                                Modifier
                                    .liquidLens(pillShape, refraction = 20.dp, bevel = 26.dp, blur = 0.dp)
                                    .liquidGlass(colors, pillShape, Elevation.none, body = 0.06f, tint = colors.accent.copy(alpha = 0.10f))
                            } else {
                                Modifier.clip(pillShape).background(colors.accent.copy(alpha = if (colors.isDark) 0.18f else 0.13f))
                            },
                        ),
                )
            }
            if (selectedIndex >= 0 || slide.dragging) {
                // Small glowing violet line under the selected tab; it slides with the pill.
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = Spacing.xs, bottom = 4.dp)
                        .offset { androidx.compose.ui.unit.IntOffset((segment * pillPos + (segment - 30.dp) / 2).roundToPx(), 0) }
                        .width(30.dp)
                        .height(3.dp)
                        .shadow(6.dp, RoundedCornerShape(50), ambientColor = colors.accent, spotColor = colors.accent)
                        .background(colors.accent, RoundedCornerShape(50)),
                )
            }
            androidx.compose.runtime.CompositionLocalProvider(LocalBarHover provides slide.hoverIndex) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.xs).selectableGroup(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                    content = content,
                )
            }
        }
        }
        trailing?.invoke()
    }
}

@Composable
fun RowScope.FitnexBottomBarItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    /** Position in the bar, so the item lights up while a finger slides over it. */
    index: Int = -1,
) {
    val colors = FitnexTheme.colors
    val hover = LocalBarHover.current
    val lit = if (hover != null) hover == index else selected
    val tint by animateColorAsState(if (lit) colors.accent else colors.textSecondary, FitnexMotion.spec(), label = "tint")
    val iconScale by animateFloatAsState(if (lit) 1f else 0.92f, FitnexMotion.pressSpring(), label = "iconScale")
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier = modifier
            .weight(1f)
            .clip(RoundedCornerShape(Radii.lg))
            .selectable(selected = selected, onClick = onClick, role = Role.Tab, interactionSource = interaction, indication = null)
            .pressFeedback(interaction)
            .padding(vertical = Spacing.sm - 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.width(52.dp).height(30.dp), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp).scale(iconScale))
        }
        Spacer(Modifier.height(Spacing.xxs))
        // Tab labels grow with the system font only up to ×1.15, so "Entrenamiento" always fits
        // (TalkBack reads the full label regardless).
        val fontScale = androidx.compose.ui.platform.LocalDensity.current.fontScale
        val style = FitnexTheme.typography.labelSmall
        val capped = if (fontScale > 1.15f) style.copy(fontSize = style.fontSize * (1.15f / fontScale)) else style
        Text(label, style = capped, color = tint, maxLines = 1, softWrap = false)
    }
}

/** Global FITNEX AI entry point (spec §19): discreet, always reachable, red-glow accent. */
/** Set once the FITNEX AI halo has played in this app session. */
private object AiHaloOnce { @Volatile var played = false }

@Composable
fun FitnexAiButton(onClick: () -> Unit, contentDescription: String, modifier: Modifier = Modifier, diameter: androidx.compose.ui.unit.Dp = 52.dp) {
    val colors = FitnexTheme.colors
    val liquid = FitnexTheme.appearance.liquidGlass
    val lens = liquidLensAvailable()
    val reduce = FitnexTheme.appearance.reduceMotion
    val interaction = remember { MutableInteractionSource() }
    // Micro-interaction: one soft halo the first time the button appears in the app session — not on
    // every screen it reappears on (it lost all meaning when it played on each push).
    val playHalo = remember { !reduce && !AiHaloOnce.played }
    val halo = remember { androidx.compose.animation.core.Animatable(if (playHalo) 1f else 0f) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (playHalo) {
            AiHaloOnce.played = true
            halo.animateTo(0f, androidx.compose.animation.core.tween(1100, delayMillis = 250, easing = FitnexMotion.Easing))
        }
    }
    Box(
        modifier = modifier
            .size(diameter)
            .drawBehind {
                if (halo.value > 0f) {
                    drawCircle(colors.accent.copy(alpha = 0.35f * halo.value), radius = size.minDimension / 2f * (1f + 0.45f * (1f - halo.value)))
                }
            }
            .liquidInteraction(CircleShape, interaction)
            .shadow(Elevation.floating, CircleShape, ambientColor = colors.accent, spotColor = colors.accent)
            .then(
                if (liquid) {
                    // Glass orb tinted with the accent; the icon keeps the accent colour.
                    when {
                        FitnexTheme.appearance.reduceBlur -> Modifier.liquidGlass(colors, CircleShape, Elevation.none, body = 0.85f, tint = colors.accent.copy(alpha = 0.22f))
                        lens -> Modifier.liquidLens(CircleShape, refraction = 12.dp, bevel = 20.dp).liquidGlass(colors, CircleShape, Elevation.none, body = 0.22f, tint = colors.accent.copy(alpha = 0.2f))
                        else -> Modifier.backdropBlur(CircleShape).liquidGlass(colors, CircleShape, Elevation.none, body = 0.55f, tint = colors.accent.copy(alpha = 0.22f))
                    }
                } else {
                    Modifier.clip(CircleShape).background(Brush.linearGradient(listOf(colors.accentStrong, colors.accent))).liquidRim(colors, CircleShape)
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        IconButton(onClick = onClick, interactionSource = interaction, modifier = Modifier.size(diameter)) {
            Icon(FitnexIcons.Sparkle, contentDescription = contentDescription, tint = if (liquid) (if (colors.isDark) colors.accentStrong else colors.accent) else colors.onAccent, modifier = Modifier.size(22.dp))
        }
    }
}

/** True inside the header's glass capsule: buttons there are bare icons, the capsule is the glass. */
internal val LocalInCapsule = androidx.compose.runtime.staticCompositionLocalOf { false }

/**
 * Screen header, iOS-style: a top row with a round glass back button on the left and the actions
 * grouped in one glass capsule on the right, then the large title on its own line.
 */
@Composable
fun FitnexHeader(
    title: String,
    modifier: Modifier = Modifier,
    overline: String? = null,
    onBack: (() -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
) {
    val colors = FitnexTheme.colors
    val titleStyle = FitnexTheme.typography.headlineLarge.copy(fontSize = 36.sp, lineHeight = 42.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.6).sp)
    // Each action is its own round glass button, spaced apart.
    val actionRow: @Composable () -> Unit = {
        if (actions != null) Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) { actions() }
    }
    if (onBack == null) {
        // Tab screens: overline + big title on the left, round buttons on the right.
        Row(
            modifier.fillMaxWidth().padding(horizontal = Spacing.gutter).padding(top = Spacing.lg, bottom = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                if (overline != null) Text(overline, style = FitnexTheme.typography.labelMedium, color = colors.accent)
                Text(title, style = titleStyle, color = colors.textPrimary)
            }
            actionRow()
        }
    } else {
        // Pushed screens: round "<" on its own row, then the big title.
        Column(modifier.fillMaxWidth().padding(horizontal = Spacing.gutter).padding(top = Spacing.sm, bottom = Spacing.md)) {
            Row(Modifier.fillMaxWidth().height(54.dp), verticalAlignment = Alignment.CenterVertically) {
                GlassIconButton(
                    icon = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                    contentDescription = stringResource(R.string.ds_back),
                    onClick = onBack,
                    modifier = Modifier.size(50.dp).testTag("header_back"),
                    iconSize = 30.dp,
                )
                Spacer(Modifier.weight(1f))
                actionRow()
            }
            if (overline != null) {
                Text(overline, style = FitnexTheme.typography.labelMedium, color = colors.accent, modifier = Modifier.padding(top = Spacing.md))
            }
            Text(title, style = titleStyle, color = colors.textPrimary, modifier = Modifier.padding(top = if (overline == null) Spacing.md else Spacing.xxs))
        }
    }
}
