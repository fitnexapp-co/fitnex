package com.fitnex.core.designsystem.component.chart

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.fitnex.core.designsystem.theme.FitnexColors
import com.fitnex.core.designsystem.theme.FitnexMotion
import com.fitnex.core.designsystem.theme.FitnexTheme

/** Holds the data a chart last showed, without being state (changing it must not recompose). */
private class LastShown(var data: Any?)

/**
 * Reveal progress (0..1) for a chart. Data that is already there when the chart appears (opening
 * Progress, coming back to a tab) is shown complete at once; the chart only grows when its data
 * changes while it is on screen (first load, another range), in 500 ms. Reduced motion: never.
 */
@Composable
internal fun rememberDataReveal(data: Any?): Animatable<Float, androidx.compose.animation.core.AnimationVector1D> {
    val reduce = FitnexTheme.appearance.reduceMotion
    val reveal = remember { Animatable(1f) }
    val last = remember { LastShown(data) }
    LaunchedEffect(data) {
        if (data == last.data) return@LaunchedEffect
        last.data = data
        if (reduce) return@LaunchedEffect reveal.snapTo(1f)
        reveal.snapTo(0f)
        reveal.animateTo(1f, tween(500, easing = FitnexMotion.Easing))
    }
    return reveal
}

private val AxisLabelHeight = 20.dp
private val TopPadding = 24.dp

/**
 * Line chart for evolution over time (strength, volume, frequency).
 * Tap a point to highlight its value. [accessibilitySummary] describes the trend for TalkBack,
 * e.g. "Press de banca: de 20 a 30 kg en 6 sesiones".
 */
@Composable
fun FitnexLineChart(
    points: List<ChartPoint>,
    accessibilitySummary: String,
    modifier: Modifier = Modifier,
    height: Dp = 180.dp,
    unit: String = "",
) {
    val colors = FitnexTheme.colors
    val measurer = rememberTextMeasurer()
    val labelStyle = FitnexTheme.typography.labelSmall.copy(color = colors.textSecondary)
    val valueStyle = FitnexTheme.typography.labelMedium.copy(color = colors.textPrimary)
    var selected by remember(points) { mutableStateOf(points.lastIndex) }
    val reveal = rememberDataReveal(points)

    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
            .semantics { contentDescription = accessibilitySummary }
            .pointerInput(points) {
                detectTapGestures { offset ->
                    selected = ChartMath.nearestIndex(offset.x, size.width.toFloat(), points.size)
                }
            },
    ) {
        val chartTop = TopPadding.toPx()
        val chartBottom = size.height - AxisLabelHeight.toPx()
        val chartHeight = chartBottom - chartTop
        val axisMax = ChartMath.gridCeiling(points.maxOfOrNull { it.value } ?: 0f)
        drawGrid(colors, chartTop, chartHeight, axisMax, measurer, labelStyle)
        if (points.isEmpty()) return@Canvas

        val step = if (points.size > 1) size.width / (points.size - 1) else 0f
        val offsets = points.mapIndexed { i, p ->
            Offset(
                x = if (points.size > 1) i * step else size.width / 2,
                y = chartBottom - chartHeight * ChartMath.fraction(p.value, axisMax) * reveal.value,
            )
        }

        val line = Path().apply {
            offsets.forEachIndexed { i, o -> if (i == 0) moveTo(o.x, o.y) else lineTo(o.x, o.y) }
        }
        val fill = Path().apply {
            addPath(line)
            lineTo(offsets.last().x, chartBottom)
            lineTo(offsets.first().x, chartBottom)
            close()
        }
        drawPath(fill, Brush.verticalGradient(listOf(colors.accent.copy(alpha = 0.28f), colors.accent.copy(alpha = 0f)), chartTop, chartBottom))
        drawPath(line, colors.accent, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))

        // Only the selected point is marked: a clean line, one clear focus.
        offsets.getOrNull(selected)?.let { o ->
            drawLine(colors.accent.copy(alpha = 0.35f), Offset(o.x, chartTop), Offset(o.x, chartBottom), strokeWidth = 1.dp.toPx())
            drawCircle(colors.surface, radius = 6.dp.toPx(), center = o)
            drawCircle(colors.accent, radius = 4.dp.toPx(), center = o)
        }

        ChartMath.visibleLabelIndices(points.size).forEach { i ->
            drawCenteredText(measurer, points[i].label, labelStyle, offsets[i].x, chartBottom + 4.dp.toPx())
        }

        points.getOrNull(selected)?.let { p ->
            val o = offsets[selected]
            drawCenteredText(measurer, ChartMath.formatValue(p.value) + unit, valueStyle, o.x, (o.y - 22.dp.toPx()).coerceAtLeast(0f))
        }
    }
}

/**
 * Bar chart for distributions (sessions per week, sets per muscle group).
 * The highest bar uses the strong accent; the rest a muted red so the peak stands out.
 */
@Composable
fun FitnexBarChart(
    bars: List<ChartPoint>,
    accessibilitySummary: String,
    modifier: Modifier = Modifier,
    height: Dp = 180.dp,
    /** True for counts (sessions per week): gridlines only on whole numbers. */
    wholeNumbers: Boolean = false,
    /** Most X labels that fit; with more bars only the first, middle and last are labelled. */
    maxLabels: Int = 12,
) {
    val colors = FitnexTheme.colors
    val measurer = rememberTextMeasurer()
    val labelStyle = FitnexTheme.typography.labelSmall.copy(color = colors.textSecondary)
    val valueStyle = FitnexTheme.typography.labelSmall.copy(color = colors.textPrimary)
    val grow = rememberDataReveal(bars)

    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
            .semantics { contentDescription = accessibilitySummary },
    ) {
        val chartTop = TopPadding.toPx()
        val chartBottom = size.height - AxisLabelHeight.toPx()
        val chartHeight = chartBottom - chartTop
        val maxValue = bars.maxOfOrNull { it.value } ?: 0f
        val axisMax = if (wholeNumbers) ChartMath.wholeCeiling(maxValue) else ChartMath.gridCeiling(maxValue)
        drawGrid(colors, chartTop, chartHeight, axisMax, measurer, labelStyle)
        if (bars.isEmpty()) return@Canvas

        val slot = size.width / bars.size
        val barWidth = (slot * 0.56f).coerceAtMost(36.dp.toPx())
        val peak = bars.maxOf { it.value }
        val showLabels = ChartMath.visibleLabelIndices(bars.size, maxLabels = maxLabels)
        bars.forEachIndexed { i, bar ->
            val h = chartHeight * ChartMath.fraction(bar.value, axisMax) * grow.value
            val left = slot * i + (slot - barWidth) / 2
            val isPeak = bar.value == peak && peak > 0f
            // The peak glows in a full violet gradient; the others are a softer tint of it.
            val corner = CornerRadius((barWidth / 2).coerceAtMost(7.dp.toPx()))
            if (isPeak && h > 0f) {
                drawRoundRect(
                    brush = Brush.verticalGradient(listOf(colors.accent.copy(alpha = 0.35f), Color.Transparent), startY = chartBottom - h, endY = chartBottom),
                    topLeft = Offset(left - barWidth * 0.35f, chartBottom - h - 4.dp.toPx()),
                    size = Size(barWidth * 1.7f, h + 4.dp.toPx()),
                    cornerRadius = CornerRadius(corner.x * 1.6f),
                )
            }
            drawRoundRect(
                brush = if (isPeak) {
                    Brush.verticalGradient(listOf(androidx.compose.ui.graphics.lerp(colors.accent, Color.White, 0.3f), colors.accent), startY = chartBottom - h, endY = chartBottom)
                } else {
                    Brush.verticalGradient(listOf(colors.accent.copy(alpha = 0.5f), colors.accent.copy(alpha = 0.22f)), startY = chartBottom - h, endY = chartBottom)
                },
                topLeft = Offset(left, chartBottom - h),
                size = Size(barWidth, h),
                cornerRadius = corner,
            )
            if (bar.value > 0f && grow.value == 1f) {
                drawCenteredText(measurer, ChartMath.formatValue(bar.value), valueStyle, left + barWidth / 2, chartBottom - h - 16.dp.toPx())
            }
            if (i in showLabels) {
                drawCenteredText(measurer, bar.label, labelStyle, left + barWidth / 2, chartBottom + 4.dp.toPx())
            }
        }
    }
}

private fun DrawScope.drawGrid(
    colors: FitnexColors,
    top: Float,
    height: Float,
    axisMax: Float,
    measurer: TextMeasurer,
    labelStyle: TextStyle,
) {
    val lines = ChartMath.GRID_LINES
    for (i in 0..lines) {
        val y = top + height * i / lines
        // Hairline grid, dashed except the baseline.
        drawLine(
            colors.divider, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx(),
            pathEffect = if (i < lines) androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 4.dp.toPx())) else null,
        )
        if (i < lines) {
            val value = axisMax * (lines - i) / lines
            drawText(measurer, ChartMath.formatValue(value), Offset(0f, y - 16.dp.toPx()), labelStyle)
        }
    }
}

private fun DrawScope.drawCenteredText(measurer: TextMeasurer, text: String, style: TextStyle, centerX: Float, top: Float) {
    val layout = measurer.measure(text, style)
    val x = (centerX - layout.size.width / 2f).coerceIn(0f, (size.width - layout.size.width).coerceAtLeast(0f))
    drawText(layout, topLeft = Offset(x, top))
}
