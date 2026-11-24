package com.fitnex.core.designsystem.component

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.fitnex.core.designsystem.theme.FitnexTheme

/**
 * Real Liquid Glass (Android 13+): the content behind the chrome is bent at the rounded rim like
 * light through a thick glass edge, with a slight chromatic split and a richer colour, then softly
 * frosted. It is recomputed every frame from the live content, so scrolling lists flow through it.
 *
 * Signed distance to the rounded rectangle gives the rim normal; displacement follows a circular
 * bevel profile (strong at the very edge, zero in the flat centre, which stays perfectly legible).
 */
private const val LENS_SHADER = """
uniform shader content;
uniform float2 lensOrigin;
uniform float2 lensSize;
uniform float cornerRadius;
uniform float refraction;
uniform float bevel;
uniform float dispersion;
uniform float saturation;
uniform float lift;

float sdRoundRect(float2 p, float2 halfSize, float r) {
    float2 q = abs(p) - halfSize + r;
    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r;
}

half4 main(float2 coord) {
    float2 halfSize = lensSize * 0.5;
    float2 p = coord - (lensOrigin + halfSize);
    float r = min(cornerRadius, min(halfSize.x, halfSize.y));
    float d = sdRoundRect(p, halfSize, r);
    if (d > 0.0) { return content.eval(coord); }

    float2 g = float2(
        sdRoundRect(p + float2(1.0, 0.0), halfSize, r) - sdRoundRect(p - float2(1.0, 0.0), halfSize, r),
        sdRoundRect(p + float2(0.0, 1.0), halfSize, r) - sdRoundRect(p - float2(0.0, 1.0), halfSize, r));
    float2 n = g / max(length(g), 0.0001);

    // 1 at the rim, 0 once `bevel` px inside; circular profile like a rounded glass edge.
    float t = clamp(1.0 + d / bevel, 0.0, 1.0);
    float bend = refraction * (1.0 - sqrt(max(1.0 - t * t, 0.0)));

    float2 base = coord - n * bend;
    float2 split = n * bend * dispersion;
    half4 c = content.eval(base);
    half a = c.a;
    // Colours are premultiplied: every channel must stay within 0..alpha, or transparent areas
    // (beyond the screen, gaps) turn into random bright colours.
    half3 rgb = half3(content.eval(base - split).r, c.g, content.eval(base + split).b);
    rgb = clamp(rgb, half3(0.0), half3(a));
    half l = dot(rgb, half3(0.2126, 0.7152, 0.0722));
    rgb = mix(half3(l), rgb, half(saturation));
    rgb = rgb + half3(half(lift * t)) * a;
    return half4(clamp(rgb, half3(0.0), half3(a)), a);
}
"""

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private class LensEffect {
    val shader = RuntimeShader(LENS_SHADER)

    fun update(layer: GraphicsLayer, margin: Float, w: Float, h: Float, corner: Float, refraction: Float, bevel: Float, blur: Float, dark: Boolean) {
        shader.setFloatUniform("lensOrigin", margin, margin)
        shader.setFloatUniform("lensSize", w, h)
        shader.setFloatUniform("cornerRadius", corner)
        shader.setFloatUniform("refraction", refraction)
        shader.setFloatUniform("bevel", bevel)
        shader.setFloatUniform("dispersion", 0.35f)
        shader.setFloatUniform("saturation", 1.35f)
        shader.setFloatUniform("lift", if (dark) 0.035f else 0.06f)
        val lens = android.graphics.RenderEffect.createRuntimeShaderEffect(shader, "content")
        // Inner (blur) runs first, then the lens bends the frosted image.
        val effect = if (blur > 0f) {
            android.graphics.RenderEffect.createChainEffect(
                lens,
                android.graphics.RenderEffect.createBlurEffect(blur, blur, android.graphics.Shader.TileMode.CLAMP),
            )
        } else lens
        layer.renderEffect = effect.asComposeRenderEffect()
    }
}

internal fun DrawScope.outlinePath(shape: Shape): Pair<Path, Float> {
    val outline = shape.createOutline(size, layoutDirection, this)
    val path = Path()
    val corner = when (outline) {
        is Outline.Rectangle -> { path.addRect(outline.rect); 0f }
        is Outline.Rounded -> { path.addRoundRect(outline.roundRect); outline.roundRect.topLeftCornerRadius.x }
        is Outline.Generic -> { path.addPath(outline.path); minOf(size.width, size.height) / 2f }
    }
    return path to corner
}

/** True when the real refracting glass can run here (and the user wants blur at all). */
@Composable
fun liquidLensAvailable(): Boolean {
    val backdrop = LocalLiquidBackdrop.current
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && backdrop != null && backdrop.supported &&
        FitnexTheme.appearance.liquidGlass && !FitnexTheme.appearance.reduceBlur
}

/**
 * Refracting glass behind floating chrome. Falls back to the plain frosted blur on Android 12, and
 * to nothing (solid translucent fill) where no backdrop is recorded.
 *
 * @param refraction how far (dp) content is pulled at the rim; a dragged selection uses more.
 */
@Composable
fun Modifier.liquidLens(shape: Shape, refraction: Dp = 14.dp, bevel: Dp = 18.dp, blur: Dp = 6.dp): Modifier {
    val backdrop = LocalLiquidBackdrop.current ?: return this
    if (!backdrop.supported) return this
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return this.backdropBlur(shape)
    val dark = FitnexTheme.colors.isDark
    val layer = rememberGraphicsLayer()
    val effect = remember { LensEffect() }
    var myOffset by remember { mutableStateOf(Offset.Zero) }
    return this
        .onGloballyPositioned { myOffset = it.positionInRoot() }
        .drawBehind {
            val (path, corner) = outlinePath(shape)
            val m = (refraction + blur * 2).toPx()
            effect.update(layer, m, size.width, size.height, corner, refraction.toPx(), bevel.toPx(), blur.toPx(), dark)
            layer.record(IntSize((size.width + 2 * m).toInt().coerceAtLeast(1), (size.height + 2 * m).toInt().coerceAtLeast(1))) {
                val d = backdrop.sourceOffset - myOffset
                translate(d.x + m, d.y + m) { drawLayer(backdrop.layer) }
            }
            clipPath(path) { translate(-m, -m) { drawLayer(layer) } }
        }
}

/**
 * Bottom of the screen dissolving under the floating bar: the content gets progressively more
 * blurred and darker towards the edge. With Liquid Glass off (or "Reducir desenfoque") only the
 * cheap darkening gradient remains.
 */
@Composable
fun BottomScreenFade(modifier: Modifier = Modifier, height: Dp = 150.dp) {
    val colors = FitnexTheme.colors
    val backdrop = LocalLiquidBackdrop.current
    val appearance = FitnexTheme.appearance
    val blurOn = backdrop != null && backdrop.supported && appearance.liquidGlass && !appearance.reduceBlur
    val layer = rememberGraphicsLayer()
    var myOffset by remember { mutableStateOf(Offset.Zero) }
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .onGloballyPositioned { myOffset = it.positionInRoot() }
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawBehind {
                if (blurOn && backdrop != null) {
                    val r = 16.dp.toPx()
                    layer.renderEffect = BlurEffect(r, r, TileMode.Clamp)
                    layer.record(IntSize(size.width.toInt().coerceAtLeast(1), size.height.toInt().coerceAtLeast(1))) {
                        val d = backdrop.sourceOffset - myOffset
                        translate(d.x, d.y) { drawLayer(backdrop.layer) }
                    }
                    drawLayer(layer)
                    // Progressive: the blurred copy fades in from nothing at the top edge.
                    drawRect(
                        Brush.verticalGradient(0f to Color.Transparent, 0.6f to Color.Black, 1f to Color.Black),
                        blendMode = BlendMode.DstIn,
                    )
                }
                drawRect(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.45f to colors.background.copy(alpha = 0.45f),
                        1f to colors.background.copy(alpha = 0.92f),
                    ),
                )
            },
    )
}
