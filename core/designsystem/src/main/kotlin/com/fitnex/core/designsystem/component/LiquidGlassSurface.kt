package com.fitnex.core.designsystem.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.fitnex.core.designsystem.theme.Elevation
import com.fitnex.core.designsystem.theme.FitnexColors
import com.fitnex.core.designsystem.theme.FitnexMotion
import com.fitnex.core.designsystem.theme.FitnexTheme
import com.fitnex.core.designsystem.theme.Radii
import com.fitnex.core.designsystem.theme.enterAnimation

/** Scale while a liquid-glass surface is held down. */
const val LIQUID_PRESS_SCALE = 0.965f

/**
 * Diagonal specular rim (Rim Light) of every FITNEX glass surface: white 32 % top-left → clear in
 * the middle → white 10 % bottom-right.
 */
fun Modifier.liquidRim(colors: FitnexColors, shape: Shape, width: Dp = 1.dp): Modifier = border(
    width,
    Brush.linearGradient(
        0f to Color.White.copy(alpha = if (colors.isDark) 0.32f else 0.9f),
        0.5f to Color.Transparent,
        1f to Color.White.copy(alpha = if (colors.isDark) 0.10f else 0.4f),
        start = Offset.Zero, end = Offset.Infinite,
    ),
    shape,
)

/**
 * The living part of liquid glass, only for things you can actually press (buttons, clickable
 * cards): a spring press scale (0.965) and a brighter top edge while pressed. Static surfaces don't
 * react to touch at all (sliding a ruler inside a card must not make the card sink), and there is
 * no light sweep. Reduced motion: a light dim instead of the scale.
 * [pressable] and [shimmerKey] are kept for call-site compatibility and have no effect.
 */
@Composable
fun Modifier.liquidInteraction(
    shape: Shape,
    interactionSource: InteractionSource? = null,
    @Suppress("UNUSED_PARAMETER") pressable: Boolean = true,
    @Suppress("UNUSED_PARAMETER") shimmerKey: Any? = null,
    enabled: Boolean = true,
): Modifier {
    if (interactionSource == null) return this
    val reduce = FitnexTheme.appearance.reduceMotion
    val pressed = enabled && interactionSource.collectIsPressedAsState().value
    val scale by animateFloatAsState(if (pressed && !reduce) LIQUID_PRESS_SCALE else 1f, FitnexMotion.pressSpring(), label = "liquidPress")
    val rimBoost by animateFloatAsState(if (pressed) 1f else 0f, FitnexMotion.spec(FitnexMotion.SHORT), label = "liquidRim")
    val dim = if (pressed && reduce) 0.75f else 1f
    return this
        .graphicsLayer { scaleX = scale; scaleY = scale; alpha = dim }
        .drawWithContent {
            drawContent()
            if (rimBoost <= 0f) return@drawWithContent
            val outline = shape.createOutline(size, layoutDirection, this)
            val path = Path().apply {
                when (outline) {
                    is Outline.Rectangle -> addRect(outline.rect)
                    is Outline.Rounded -> addRoundRect(outline.roundRect)
                    is Outline.Generic -> addPath(outline.path)
                }
            }
            // Pressed: the top edge catches a little more light.
            clipPath(path) {
                drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.18f * rimBoost), Color.Transparent), endY = size.height * 0.2f))
            }
        }
}
/**
 * iOS-style "Liquid Glass" surface, in optical layers:
 * 1. Real backdrop blur ([blur] = true, Android 12+, for chrome floating over the content such as
 *    the dock; content cannot blur itself, so cards inside the scroll use [blur] = false).
 * 2. A translucent body ([fill], or the surface colour at [body] opacity).
 * 3. The diagonal specular rim ([liquidRim]).
 * 4. [liquidInteraction]: spring press, brighter top edge and a light sweep.
 * With "Reducir desenfoque" there is no blur.
 */
@Composable
fun LiquidGlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(Radii.lg),
    blur: Boolean = false,
    body: Float = 0.62f,
    fill: Brush? = null,
    elevation: Dp = Elevation.card,
    pressable: Boolean = true,
    interactionSource: InteractionSource? = null,
    shimmerKey: Any? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = FitnexTheme.colors
    val dark = colors.isDark
    val blurOn = blur && !FitnexTheme.appearance.reduceBlur
    val bodyColor = colors.surface.copy(alpha = if (dark) body else (body + 0.25f).coerceAtMost(0.95f))
    Box(
        modifier
            .liquidInteraction(shape, interactionSource, pressable, shimmerKey)
            .then(if (elevation > 0.dp && dark) Modifier.shadow(elevation, shape, clip = false, ambientColor = Color.Black.copy(alpha = 0.35f), spotColor = Color.Black.copy(alpha = 0.45f)) else Modifier)
            .then(if (blurOn) Modifier.backdropBlur(shape) else Modifier)
            .clip(shape)
            .then(if (fill != null) Modifier.background(fill) else Modifier.background(bodyColor))
            .liquidRim(colors, shape),
        content = content,
    )
}

/**
 * Staggered entrance for key figures and texts: the content rises 14 dp into place and fades in,
 * inside a clipped box (overflow hidden), [index] × 70 ms after the screen appears. Only on the
 * first screen of the app session (see [enterAnimation]).
 */
@Composable
fun StaggeredReveal(index: Int, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(modifier.clipToBounds()) {
        Box(Modifier.enterAnimation(delayMs = index * 70, risePx = 14), content = content)
    }
}
