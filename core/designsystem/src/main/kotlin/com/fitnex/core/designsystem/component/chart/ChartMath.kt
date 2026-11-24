package com.fitnex.core.designsystem.component.chart

import androidx.compose.runtime.Immutable
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

/** One value on a chart. [label] is shown on the X axis; [value] must be finite and ≥ 0. */
@Immutable
data class ChartPoint(val label: String, val value: Float)

/** Pure chart maths, kept separate from drawing so it can be unit-tested. */
object ChartMath {
    /**
     * Rounds the axis maximum up to a "nice" number (1, 2, 2.5, 5 × 10ⁿ) so gridlines land on
     * readable values: 37 → 50, 0.8 → 1, 125 → 200, 22 → 25, 0 → 1.
     */
    fun niceCeiling(max: Float): Float {
        if (max <= 0f || !max.isFinite()) return 1f
        val exponent = floor(log10(max.toDouble()))
        val base = 10.0.pow(exponent)
        val fraction = max / base
        val nice = when {
            fraction <= 1.0 -> 1.0
            fraction <= 2.0 -> 2.0
            fraction <= 2.5 -> 2.5
            fraction <= 5.0 -> 5.0
            else -> 10.0
        }
        return (nice * base).toFloat()
    }

    /**
     * Axis maximum for counts (workouts, sessions): a multiple of [gridLines] so every gridline is a
     * whole number. 0 or 1 → 3 (lines 1, 2, 3), 4 → 6, 7 → 9. Never "0.7 workouts".
     */
    fun wholeCeiling(max: Float, gridLines: Int = GRID_LINES): Float {
        if (max <= 0f || !max.isFinite()) return gridLines.toFloat()
        val step = max / gridLines
        // Big totals (ml of water, kcal): round each gridline to 1, 2 or 5 × 10ⁿ, so 1250 ml gives
        // 500 / 1000 / 1500 instead of 417 / 834 / 1251.
        if (step > 10f) {
            val base = 10.0.pow(floor(log10(step.toDouble())))
            val f = step / base
            val nice = when { f <= 1.0 -> 1.0; f <= 2.0 -> 2.0; f <= 5.0 -> 5.0; else -> 10.0 }
            return (nice * base * gridLines).toFloat()
        }
        return (kotlin.math.ceil(step) * gridLines).coerceAtLeast(gridLines.toFloat())
    }

    /**
     * Axis maximum for measured values (kg, reps, volume) so each of the [gridLines] labels is a
     * round number: 37 kg → 45 (15 / 30 / 45), 70 → 75 (25 / 50 / 75), 1300 → 1500. With
     * [niceCeiling] and 3 lines, 37 gave 50 and labels like 16,7 / 33,3.
     */
    fun gridCeiling(max: Float, gridLines: Int = GRID_LINES): Float {
        if (max <= 0f || !max.isFinite()) return gridLines.toFloat()
        val step = max / gridLines
        val base = 10.0.pow(floor(log10(step.toDouble())))
        val f = step / base
        val nice = listOf(1.0, 1.5, 2.0, 2.5, 3.0, 4.0, 5.0, 6.0, 8.0, 10.0).first { f <= it + 1e-9 }
        return (nice * base * gridLines).toFloat()
    }

    const val GRID_LINES = 3

    /** Value as a 0..1 fraction of [axisMax] (clamped; non-finite or negative → 0). */
    fun fraction(value: Float, axisMax: Float): Float =
        if (axisMax <= 0f || !value.isFinite()) 0f else (value / axisMax).coerceIn(0f, 1f)

    /** Index of the point closest to a horizontal position, for tap selection. */
    fun nearestIndex(x: Float, width: Float, count: Int): Int {
        if (count <= 1 || width <= 0f) return 0
        val step = width / (count - 1)
        return (x / step).let { kotlin.math.round(it).toInt() }.coerceIn(0, count - 1)
    }

    /** Which X labels to draw without overlap: all when few, else first / middle / last. */
    fun visibleLabelIndices(count: Int, maxLabels: Int = 7): Set<Int> = when {
        count <= 0 -> emptySet()
        count <= maxLabels -> (0 until count).toSet()
        else -> setOf(0, count / 2, count - 1)
    }

    fun formatValue(value: Float): String =
        if (value == floor(value)) value.toInt().toString() else "%.1f".format(value)
}
