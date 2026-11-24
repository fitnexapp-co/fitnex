package com.fitnex.core.anatomy.map2d

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.fitnex.core.anatomy.geometry.P2
import com.fitnex.core.anatomy.rememberMusclePalette

/** Where the 200 × 440 atlas lands inside a canvas: uniform scale, centred. */
data class MapFit(val scale: Float, val left: Float, val top: Float) {
    fun toMap(px: Offset) = P2((px.x - left) / scale, (px.y - top) / scale)
    fun toPx(p: P2) = Offset(left + p.x * scale, top + p.y * scale)

    companion object {
        fun of(size: Size): MapFit {
            val s = minOf(size.width / BodyAtlas2D.VIEW_WIDTH, size.height / BodyAtlas2D.VIEW_HEIGHT)
            return MapFit(s, (size.width - BodyAtlas2D.VIEW_WIDTH * s) / 2f, (size.height - BodyAtlas2D.VIEW_HEIGHT * s) / 2f)
        }
    }
}

/**
 * Front or back 2D muscle map. Every muscle is its own tappable shape; [levels] colours worked
 * muscles red by intensity, and the [selected] one pulses. Tapping the body outside a muscle
 * clears the selection.
 */
@Composable
fun MuscleMap2D(
    view: BodyView,
    levels: Map<String, Float>,
    selected: String?,
    onSelect: ((String?) -> Unit)?,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val palette = rememberMusclePalette()
    val silhouette = remember { BodyAtlas2D.silhouette.map { smoothPath(it) } }
    val shapes = remember(view) { BodyAtlas2D.shapes(view).map { it.muscleId to smoothPath(it.polygon) } }
    val details = remember(view) {
        BodyAtlas2D.detailLines(view).map { line ->
            Path().apply { moveTo(line[0].x, line[0].y); line.drop(1).forEach { lineTo(it.x, it.y) } }
        }
    }

    val reduceMotion = com.fitnex.core.designsystem.theme.FitnexTheme.appearance.reduceMotion
    // Selection: a quick fill-in when it changes, then a soft pulse on the outline (none with reduced motion).
    val selectIn = remember { Animatable(1f) }
    LaunchedEffect(selected) { if (reduceMotion) selectIn.snapTo(1f) else { selectIn.snapTo(0f); selectIn.animateTo(1f, tween(280)) } }
    // Only animate while something is selected: an idle map draws no frames. Read in the draw
    // phase only, so the pulse redraws the canvas without recomposing.
    val pulse: State<Float>? = if (selected != null && !reduceMotion) {
        rememberInfiniteTransition(label = "musclePulse").animateFloat(
            initialValue = 0.45f, targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "pulse",
        )
    } else null
    val select = rememberUpdatedState(onSelect)

    Canvas(
        modifier
            .aspectRatio(BodyAtlas2D.VIEW_WIDTH / BodyAtlas2D.VIEW_HEIGHT)
            .semantics { this.contentDescription = contentDescription }
            .then(
                if (onSelect == null) Modifier else Modifier.pointerInput(view) {
                    detectTapGestures { px ->
                        val p = MapFit.of(Size(size.width.toFloat(), size.height.toFloat())).toMap(px)
                        select.value?.invoke(BodyAtlas2D.hitTest(view, p.x, p.y))
                    }
                },
            ),
    ) {
        val fit = MapFit.of(size)
        translate(fit.left, fit.top) {
            scale(fit.scale, pivot = Offset.Zero) {
                // Porcelain figure: soft drop shadow, then the body lit from above.
                silhouette.forEach { translate(1.2f, 2.2f) { drawPath(it, androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.18f)) } }
                val shading = androidx.compose.ui.graphics.Brush.verticalGradient(listOf(palette.bodyHighlight, palette.body), 0f, BodyAtlas2D.VIEW_HEIGHT)
                silhouette.forEach { drawPath(it, shading) }
                silhouette.forEach { drawPath(it, palette.outline.copy(alpha = 0.55f), style = Stroke(width = 0.8f, join = StrokeJoin.Round)) }
                // Worked muscles glow softly in the accent colour, so they stand out at a glance.
                shapes.forEach { (id, path) ->
                    val level = levels[id] ?: 0f
                    if (level > 0f || id == selected) {
                        drawPath(path, palette.active.copy(alpha = 0.22f + 0.18f * level), style = Stroke(width = 5f, join = StrokeJoin.Round))
                    }
                }
                val outline = Stroke(width = 0.75f, join = StrokeJoin.Round)
                shapes.forEach { (id, path) ->
                    val base = palette.fill(levels[id] ?: 0f)
                    val fill = if (id == selected) lerp(base, palette.selected, selectIn.value) else base
                    drawPath(path, fill)
                    drawPath(path, palette.outline, style = outline)
                }
                // Anatomical detail lines (abdominal segments, muscle heads, folds) — decoration only.
                val detail = Stroke(width = 0.7f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                details.forEach { drawPath(it, palette.outline.copy(alpha = 0.55f), style = detail) }
                selected?.let { id ->
                    shapes.filter { it.first == id }.forEach { (_, path) ->
                        // Pointer ring: a halo around the selected muscle.
                        drawPath(path, palette.selected.copy(alpha = 0.25f * (pulse?.value ?: 1f)), style = Stroke(width = 8f, join = StrokeJoin.Round))
                        drawPath(path, palette.selected.copy(alpha = pulse?.value ?: 1f), style = Stroke(width = 2.4f, join = StrokeJoin.Round))
                    }
                }
            }
        }
    }
}

/**
 * Closed polygon → smooth outline through every point (Catmull-Rom as cubic Béziers), so the
 * drawn shape keeps the polygon's extent and neighbouring muscles meet instead of leaving gaps.
 */
internal fun smoothPath(points: List<P2>): Path = Path().apply {
    if (points.size < 3) return@apply
    val n = points.size
    moveTo(points[0].x, points[0].y)
    for (i in 0 until n) {
        val p0 = points[(i - 1 + n) % n]
        val p1 = points[i]
        val p2 = points[(i + 1) % n]
        val p3 = points[(i + 2) % n]
        cubicTo(
            p1.x + (p2.x - p0.x) / 6f, p1.y + (p2.y - p0.y) / 6f,
            p2.x - (p3.x - p1.x) / 6f, p2.y - (p3.y - p1.y) / 6f,
            p2.x, p2.y,
        )
    }
    close()
}
