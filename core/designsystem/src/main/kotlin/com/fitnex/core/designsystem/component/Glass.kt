package com.fitnex.core.designsystem.component

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.fitnex.core.designsystem.theme.Elevation
import com.fitnex.core.designsystem.theme.FitnexColors
import com.fitnex.core.designsystem.theme.FitnexTheme
import com.fitnex.core.designsystem.theme.Radii
import com.fitnex.core.designsystem.theme.Spacing
import com.fitnex.core.designsystem.theme.pressFeedback

/**
 * Standard FITNEX surface ("Glass" — default): solid graphite card, hairline border, faint top
 * highlight, discreet shadow. No blur and no translucency over content: cheapest and most legible.
 */
fun Modifier.glass(
    colors: FitnexColors,
    shape: Shape = RoundedCornerShape(Radii.lg),
    elevation: Dp = Elevation.card,
    tint: Color = Color.Transparent,
): Modifier = this
    .then(if (elevation > 0.dp) Modifier.shadow(elevation, shape, clip = false, ambientColor = Color.Black.copy(alpha = 0.35f), spotColor = Color.Black.copy(alpha = 0.45f)) else Modifier)
    .clip(shape)
    // Dark: smoked glass — the backdrop's light shows through faintly, like the out-of-focus lights
    // behind a frosted pane. Light theme stays solid white.
    .background(if (colors.isDark) colors.surface.copy(alpha = 0.50f) else colors.surface)
    .background(Brush.verticalGradient(listOf(colors.glassHighlight, colors.glassFill)))
    // Light caught by the glass from the top-left: neutral in the FITNEX style, the accent otherwise.
    .background(
        Brush.linearGradient(
            listOf((if (colors.signature) Color.White else colors.accent).copy(alpha = if (!colors.isDark) 0.04f else if (colors.signature) 0.05f else 0.10f), Color.Transparent),
            start = Offset.Zero, end = Offset(700f, 520f),
        ),
    )
    .background(tint)
    // Liquid glass: diagonal specular rim on every surface.
    .liquidRim(colors, shape)

/**
 * "Liquid Glass" (optional): translucent layer, optional real backdrop blur, specular sheen and a
 * luminous rim that fades downwards. Used only on chrome (bars, floating buttons, selectors, sheets)
 * and featured cards, never on every surface.
 */
fun Modifier.liquidGlass(
    colors: FitnexColors,
    shape: Shape = RoundedCornerShape(Radii.lg),
    elevation: Dp = Elevation.card,
    /** How opaque the base is: chrome over content needs more body than a card over the background. */
    body: Float = 0.55f,
    tint: Color = Color.Transparent,
): Modifier = this
    // A shadow under a translucent fill shows through it; on light surfaces it reads as a dirty frame,
    // so there depth comes from the rim alone.
    .then(if (elevation > 0.dp && colors.isDark) Modifier.shadow(elevation, shape, clip = false, ambientColor = Color.Black.copy(alpha = 0.3f), spotColor = Color.Black.copy(alpha = 0.4f)) else Modifier)
    .clip(shape)
    .background(colors.surface.copy(alpha = if (colors.isDark) body else (body + 0.2f).coerceAtMost(0.92f)))
    // Light theme: a hint of ink so glass controls stay visible on white cards.
    .background(if (colors.isDark) Color.Transparent else Color.Black.copy(alpha = 0.035f))
    .background(tint)
    .drawBehind {
        // Specular sheen: light falling from the top-left.
        drawRect(
            Brush.linearGradient(
                listOf(Color.White.copy(alpha = if (colors.isDark) 0.10f else 0.45f), Color.White.copy(alpha = 0f)),
                start = Offset.Zero,
                end = Offset(size.width * 0.6f, size.height),
            ),
        )
    }
    .liquidRim(colors, shape)

/** Picks the surface for chrome elements according to the user's appearance setting. */
@Composable
fun Modifier.chromeSurface(
    shape: Shape,
    elevation: Dp = Elevation.none,
    body: Float = 0.62f,
    fallback: Modifier.(FitnexColors) -> Modifier = { glass(it, shape, elevation) },
): Modifier {
    val colors = FitnexTheme.colors
    val appearance = FitnexTheme.appearance
    return when {
        !appearance.liquidGlass -> this.fallback(colors)
        // "Reducir desenfoque": same glass look on a nearly solid body, nothing sampled behind.
        appearance.reduceBlur -> this.liquidGlass(colors, shape, elevation, (body + 0.3f).coerceAtMost(0.95f))
        // Real refraction: the clear centre lets the bent content show, so the body is thinner.
        liquidLensAvailable() -> this.liquidLens(shape, blur = 9.dp).liquidGlass(colors, shape, elevation, body * 0.55f)
        else -> this.backdropBlur(shape).liquidGlass(colors, shape, elevation, body)
    }
}

enum class CardEmphasis {
    /** Every regular card: Glass in both styles. */
    Normal,
    /** Hero / highlighted cards: become Liquid Glass when that style is on. */
    Featured,
    /** Always iOS-style liquid glass ([LiquidGlassSurface]): rim light, light sweep, press physics. */
    Liquid,
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    contentPadding: PaddingValues = PaddingValues(Spacing.lg + Spacing.xxs),
    emphasis: CardEmphasis = CardEmphasis.Normal,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = FitnexTheme.colors
    val shape = RoundedCornerShape(Radii.lg)
    val interaction = remember { MutableInteractionSource() }
    if (emphasis == CardEmphasis.Liquid) {
        LiquidGlassSurface(
            modifier = modifier,
            shape = shape,
            interactionSource = if (onClick != null) interaction else null,
        ) {
            Column(
                Modifier
                    .then(
                        if (onClick != null) {
                            Modifier.clickable(interaction, indication = androidx.compose.material3.ripple(color = colors.accent), onClickLabel = onClickLabel, role = Role.Button, onClick = onClick)
                        } else {
                            Modifier
                        },
                    )
                    .padding(contentPadding),
                content = content,
            )
        }
        return
    }
    val surface = if (emphasis == CardEmphasis.Featured && FitnexTheme.appearance.liquidGlass) {
        Modifier.liquidGlass(colors, shape, Elevation.card, body = 0.5f)
    } else {
        Modifier.glass(colors, shape)
    }
    Column(
        // No entrance animation: cards are everywhere (and recycled in lists), so they just appear.
        modifier = modifier
            // Liquid glass on every card: spring press, brighter top edge and a sweep of light.
            .liquidInteraction(shape, if (onClick != null) interaction else null)
            .then(surface)
            .then(
                if (onClick != null) {
                    Modifier.clickable(interaction, indication = androidx.compose.material3.ripple(color = colors.accent), onClickLabel = onClickLabel, role = Role.Button, onClick = onClick)
                } else {
                    Modifier
                },
            )
            .padding(contentPadding),
        content = content,
    )
}

/**
 * App-wide backdrop: deep black (or soft grey) with a very faint accent glow at the top. Liquid
 * Glass shows a little more light so its translucent surfaces have something to refract.
 */
@Composable
fun FitnexBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(modifier = modifier.fillMaxSize().fitnexBackdrop(), content = content)
}

// --- Real backdrop blur for Liquid Glass ---------------------------------------------------------

/**
 * What lies behind floating chrome (the screen content). The content records itself into [layer];
 * chrome redraws that layer blurred and clipped to its own shape. Only on Android 12+ (RenderEffect)
 * and not on low-RAM devices; elsewhere the translucent fill alone is the fallback.
 */
@Stable
class LiquidBackdrop internal constructor(val layer: GraphicsLayer, val supported: Boolean) {
    internal var sourceOffset by mutableStateOf(Offset.Zero)
}

val LocalLiquidBackdrop = staticCompositionLocalOf<LiquidBackdrop?> { null }

@Composable
fun rememberLiquidBackdrop(): LiquidBackdrop {
    val context = LocalContext.current
    val layer = rememberGraphicsLayer()
    return remember(layer) {
        val lowRam = (context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)?.isLowRamDevice == true
        LiquidBackdrop(layer, supported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !lowRam)
    }
}

/** Provides [backdrop] to the chrome drawn inside [content]. */
@Composable
fun ProvideLiquidBackdrop(backdrop: LiquidBackdrop?, content: @Composable () -> Unit) =
    CompositionLocalProvider(LocalLiquidBackdrop provides backdrop, content = content)

/**
 * Marks the content that floating chrome may blur (the dock always frosts what scrolls behind it).
 * Off with "Reducir desenfoque" or on devices without RenderEffect.
 */
@Composable
fun Modifier.liquidBackdropSource(backdrop: LiquidBackdrop?): Modifier {
    if (backdrop == null || !backdrop.supported || FitnexTheme.appearance.reduceBlur) return this
    return this
        .onGloballyPositioned { backdrop.sourceOffset = it.positionInRoot() }
        .drawWithContent {
            backdrop.layer.record { this@drawWithContent.drawContent() }
            drawLayer(backdrop.layer)
        }
}

/** Draws the blurred backdrop inside [shape]; no-op without a supported source. */
@Composable
fun Modifier.backdropBlur(shape: Shape, radius: Dp = 22.dp): Modifier {
    val backdrop = LocalLiquidBackdrop.current
    if (backdrop == null || !backdrop.supported) return this
    val blurLayer = rememberGraphicsLayer()
    var myOffset by remember { mutableStateOf(Offset.Zero) }
    return this
            .onGloballyPositioned { myOffset = it.positionInRoot() }
            .drawBehind {
                val outline = shape.createOutline(size, layoutDirection, this)
                val path = Path().apply {
                    when (outline) {
                        is Outline.Rectangle -> addRect(outline.rect)
                        is Outline.Rounded -> addRoundRect(outline.roundRect)
                        is Outline.Generic -> addPath(outline.path)
                    }
                }
                val r = radius.toPx()
                blurLayer.renderEffect = BlurEffect(r, r, TileMode.Clamp)
                blurLayer.record(IntSize(size.width.toInt().coerceAtLeast(1), size.height.toInt().coerceAtLeast(1))) {
                    val d = backdrop.sourceOffset - myOffset
                    translate(d.x, d.y) { drawLayer(backdrop.layer) }
                }
                clipPath(path) { drawLayer(blurLayer) }
            }
}
