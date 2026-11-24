package com.fitnex.core.designsystem.component.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.fitnex.core.designsystem.theme.FitnexTheme
import com.fitnex.core.designsystem.theme.Spacing

/** Shades of the accent for chart series: from bright to deep, then greys. */
@Composable
fun chartPalette(count: Int): List<Color> {
    val c = FitnexTheme.colors
    val light = lerp(c.accent, Color.White, 0.45f)
    val deep = lerp(c.accent, Color.Black, 0.35f)
    return List(count) { i ->
        when (i) {
            0 -> c.accent
            1 -> light
            2 -> deep
            3 -> lerp(c.accent, Color(0xFF38BDF8), 0.6f)
            4 -> lerp(c.accent, Color(0xFFEC4899), 0.6f)
            else -> c.textDisabled.copy(alpha = 0.6f)
        }
    }
}

/**
 * Donut chart: each slice a share of the total, a big number in the hole and a legend beside it.
 * Gaps between slices, rounded ends, slices sweep in when the data changes on screen.
 */
@Composable
fun FitnexDonutChart(
    slices: List<ChartPoint>,
    centerValue: String,
    centerLabel: String,
    accessibilitySummary: String,
    modifier: Modifier = Modifier,
    diameter: Dp = 150.dp,
    valueFormatter: (Float) -> String = { ChartMath.formatValue(it) },
) {
    val colors = FitnexTheme.colors
    val palette = chartPalette(slices.size)
    val sweep = rememberDataReveal(slices)
    val total = slices.sumOf { it.value.toDouble() }.toFloat()

    Row(modifier.semantics { contentDescription = accessibilitySummary }, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(diameter), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = size.minDimension * 0.13f
                val inset = stroke / 2 + 2.dp.toPx()
                val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
                val topLeft = Offset(inset, inset)
                drawArc(colors.textDisabled.copy(alpha = 0.18f), 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
                if (total <= 0f) return@Canvas
                val gap = if (slices.count { it.value > 0f } > 1) 6f else 0f
                var start = -90f
                slices.forEachIndexed { i, s ->
                    val angle = 360f * s.value / total * sweep.value
                    if (angle > gap) {
                        // Soft glow under the slice, then the slice itself.
                        drawArc(palette[i].copy(alpha = 0.25f), start + gap / 2, angle - gap, false, topLeft, arcSize, style = Stroke(stroke * 1.7f, cap = StrokeCap.Round))
                        drawArc(palette[i], start + gap / 2, angle - gap, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                    }
                    start += 360f * s.value / total * sweep.value
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(centerValue, style = FitnexTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold), color = colors.textPrimary, maxLines = 1)
                Text(centerLabel, style = FitnexTheme.typography.labelSmall, color = colors.textSecondary, maxLines = 1)
            }
        }
        Column(Modifier.padding(start = Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            slices.forEachIndexed { i, s ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).background(palette[i], CircleShape))
                    Text(
                        s.label, style = FitnexTheme.typography.bodyMedium, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = Spacing.sm).weight(1f, fill = false),
                    )
                    val pct = if (total > 0f) (s.value / total * 100).toInt() else 0
                    Text("  $pct %", style = FitnexTheme.typography.labelMedium, color = colors.textSecondary, maxLines = 1)
                }
            }
        }
    }
}

/** One ring of [FitnexRings]: 0..1 of its own goal. */
data class RingValue(val label: String, val fraction: Float, val valueText: String)

/**
 * Concentric progress rings (like activity rings): each ring fills towards its own goal and the
 * legend states the real figures. Values above the goal close the ring and stop there.
 */
@Composable
fun FitnexRings(
    rings: List<RingValue>,
    accessibilitySummary: String,
    modifier: Modifier = Modifier,
    diameter: Dp = 150.dp,
) {
    val colors = FitnexTheme.colors
    val palette = chartPalette(rings.size)
    val grow = rememberDataReveal(rings)

    Row(modifier.semantics { contentDescription = accessibilitySummary }, verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(diameter)) {
            val stroke = size.minDimension * 0.095f
            val step = stroke * 1.35f
            rings.forEachIndexed { i, r ->
                val inset = stroke / 2 + step * i + 2.dp.toPx()
                val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
                if (arcSize.width <= 0f) return@forEachIndexed
                val topLeft = Offset(inset, inset)
                drawArc(palette[i].copy(alpha = 0.16f), 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
                val sweepAngle = 360f * r.fraction.coerceIn(0f, 1f) * grow.value
                if (sweepAngle > 0.5f) {
                    drawArc(palette[i].copy(alpha = 0.3f), -90f, sweepAngle, false, topLeft, arcSize, style = Stroke(stroke * 1.6f, cap = StrokeCap.Round))
                    drawArc(palette[i], -90f, sweepAngle, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                }
            }
        }
        Column(Modifier.padding(start = Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            rings.forEachIndexed { i, r ->
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).background(palette[i], CircleShape))
                        Text(r.label, style = FitnexTheme.typography.labelMedium, color = colors.textSecondary, modifier = Modifier.padding(start = Spacing.sm), maxLines = 1)
                    }
                    Text(r.valueText, style = FitnexTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold), color = colors.textPrimary, modifier = Modifier.padding(start = 18.dp), maxLines = 1)
                }
            }
        }
    }
}
