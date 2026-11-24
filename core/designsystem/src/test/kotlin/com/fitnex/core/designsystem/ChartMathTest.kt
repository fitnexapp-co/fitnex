package com.fitnex.core.designsystem

import com.fitnex.core.designsystem.component.chart.ChartMath
import kotlin.math.pow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChartMathTest {
    @Test
    fun gridCeilingGivesRoundLabelsOnEveryGridline() {
        assertEquals(45f, ChartMath.gridCeiling(37f), 1e-4f) // 15 / 30 / 45, not 16,7 / 33,3 / 50
        assertEquals(75f, ChartMath.gridCeiling(70f), 1e-4f) // 25 / 50 / 75
        assertEquals(1500f, ChartMath.gridCeiling(1300f), 1e-2f) // 500 / 1000 / 1500
        assertEquals(3f, ChartMath.gridCeiling(0f), 1e-4f)
        for (max in listOf(0.7f, 3f, 12f, 37f, 99f, 140f, 5172f)) {
            val step = ChartMath.gridCeiling(max) / ChartMath.GRID_LINES
            assertTrue("$max fits", ChartMath.gridCeiling(max) >= max)
            val scaled = step / 10.0.pow(kotlin.math.floor(kotlin.math.log10(step.toDouble()))).toFloat()
            assertTrue("$max → step $step is round", listOf(1f, 1.5f, 2f, 2.5f, 3f, 4f, 5f, 6f, 8f, 10f).any { kotlin.math.abs(it - scaled) < 1e-3f })
        }
    }

    @Test
    fun niceCeilingRoundsToReadableAxisValues() {
        assertEquals(1f, ChartMath.niceCeiling(0f))
        assertEquals(1f, ChartMath.niceCeiling(-5f))
        assertEquals(1f, ChartMath.niceCeiling(Float.NaN))
        assertEquals(1f, ChartMath.niceCeiling(0.8f), 1e-4f)
        assertEquals(10f, ChartMath.niceCeiling(10f), 1e-4f)
        assertEquals(25f, ChartMath.niceCeiling(22f), 1e-4f)
        assertEquals(50f, ChartMath.niceCeiling(37f), 1e-4f)
        assertEquals(200f, ChartMath.niceCeiling(125f), 1e-3f)
        assertEquals(1000f, ChartMath.niceCeiling(720f), 1e-2f)
    }

    @Test
    fun wholeCeilingKeepsEveryGridlineAWholeNumber() {
        assertEquals(3f, ChartMath.wholeCeiling(0f))
        assertEquals(3f, ChartMath.wholeCeiling(1f))
        assertEquals(3f, ChartMath.wholeCeiling(3f))
        assertEquals(6f, ChartMath.wholeCeiling(4f))
        assertEquals(9f, ChartMath.wholeCeiling(7f))
        assertEquals(3f, ChartMath.wholeCeiling(Float.NaN))
        // Big totals get round gridlines (500 / 1000 / 1500 ml), not 417 / 834 / 1251.
        assertEquals(1500f, ChartMath.wholeCeiling(1250f))
        assertEquals(60f, ChartMath.wholeCeiling(52.1f))
        assertEquals(30f, ChartMath.wholeCeiling(30f))
    }

    @Test
    fun fractionIsClampedAndSafe() {
        assertEquals(0.5f, ChartMath.fraction(25f, 50f))
        assertEquals(1f, ChartMath.fraction(80f, 50f))
        assertEquals(0f, ChartMath.fraction(-3f, 50f))
        assertEquals(0f, ChartMath.fraction(10f, 0f))
        assertEquals(0f, ChartMath.fraction(Float.POSITIVE_INFINITY, 50f))
    }

    @Test
    fun nearestIndexPicksClosestPoint() {
        // 5 points across 400 px → one every 100 px.
        assertEquals(0, ChartMath.nearestIndex(10f, 400f, 5))
        assertEquals(2, ChartMath.nearestIndex(190f, 400f, 5))
        assertEquals(4, ChartMath.nearestIndex(999f, 400f, 5))
        assertEquals(0, ChartMath.nearestIndex(-20f, 400f, 5))
        assertEquals(0, ChartMath.nearestIndex(50f, 400f, 1))
    }

    @Test
    fun labelsDoNotCrowdLongSeries() {
        assertEquals(emptySet<Int>(), ChartMath.visibleLabelIndices(0))
        assertEquals((0..6).toSet(), ChartMath.visibleLabelIndices(7))
        assertEquals(setOf(0, 15, 29), ChartMath.visibleLabelIndices(30))
    }

    @Test
    fun wholeNumbersHaveNoDecimals() {
        assertEquals("30", ChartMath.formatValue(30f))
    }
}
