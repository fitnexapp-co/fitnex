package com.fitnex.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class CalorieMathTest {
    @Test
    fun mifflinStJeorWithActivityAndAim() {
        // 30 años, 80 kg, 180 cm, hombre: BMR = 800 + 1125 - 150 + 5 = 1780.
        assertEquals(1780f, CalorieMath.bmr(CalculatorSex.MALE, 30, 80f, 180f), 0.01f)
        // × 1.55 (moderada) = 2759 → 2760.
        assertEquals(2760, CalorieMath.dailyTarget(CalculatorSex.MALE, 30, 80f, 180f, ActivityLevel.MODERATE, CalorieAim.MAINTAIN))
        // Mujer, 25 años, 60 kg, 165 cm, sedentaria: (600 + 1031.25 - 125 - 161) × 1.2 = 1614.3 → 1610.
        assertEquals(1610, CalorieMath.dailyTarget(CalculatorSex.FEMALE, 25, 60f, 165f, ActivityLevel.SEDENTARY, CalorieAim.MAINTAIN))
    }

    @Test
    fun targetStaysInsideASafeRange() {
        assertEquals(1200, CalorieMath.dailyTarget(CalculatorSex.FEMALE, 80, 40f, 145f, ActivityLevel.SEDENTARY, CalorieAim.LOSE))
    }

    @Test
    fun macrosSplitTheTarget() {
        val m = CalorieMath.macros(2000)
        assertEquals(MacroTargets(125, 225, 67), m)
    }
}
