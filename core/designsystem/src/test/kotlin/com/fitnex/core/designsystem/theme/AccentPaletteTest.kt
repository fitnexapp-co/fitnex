package com.fitnex.core.designsystem.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AccentPaletteTest {
    @Test
    fun defaultIsFitnexViolet() {
        assertEquals(Color(0xFF8B5CF6), FitnexAccents.Default)
        assertEquals(8, FitnexAccents.presets.size)
        assertEquals(FitnexAccents.Default, fitnexColors(dark = true, accent = FitnexAccents.Default).accent)
    }

    @Test
    fun defaultIsTheMulticolourSignatureStyleAndAPickedAccentLightsEverything() {
        val default = fitnexColors(dark = true, accent = FitnexAccents.Default)
        assertTrue(default.signature)
        assertEquals(listOf(FitnexSignature.Blue, FitnexSignature.Mint, FitnexSignature.Violet), listOf(default.glowTop, default.glowSide, default.glowBottom))
        val coral = fitnexColors(dark = true, accent = FitnexAccents.Coral)
        assertTrue(!coral.signature)
        assertEquals(coral.accent, coral.glowTop)
        assertEquals(coral.accent, coral.glowBottom)
    }

    @Test
    fun onlyAccentRolesChangeWithTheAccent() {
        val violet = fitnexColors(dark = true, accent = FitnexAccents.Violet)
        val green = fitnexColors(dark = true, accent = FitnexAccents.Green)
        assertEquals(violet.background, green.background)
        assertEquals(violet.surface, green.surface)
        assertEquals(violet.textPrimary, green.textPrimary)
        assertEquals(Color(0xFF000000), violet.background)
        assertTrue(violet.accent != green.accent)
        assertEquals("muscle map follows the accent", green.accent, green.muscleActive)
    }

    @Test
    fun lightThemeDarkensPaleAccentsUntilReadable() {
        val pale = Color(0xFFFFF59D) // a custom pastel yellow
        val light = fitnexColors(dark = false, accent = pale)
        assertTrue(contrastRatio(light.accent, Color.White) >= 3.0)
        // Dark theme keeps the exact colour the user picked.
        assertEquals(pale, fitnexColors(dark = true, accent = pale).accent)
    }

    @Test
    fun textOnAccentStaysReadable() {
        (FitnexAccents.presets + Color(0xFFFFF59D) + Color(0xFF101010)).forEach { accent ->
            listOf(true, false).forEach { dark ->
                val c = fitnexColors(dark, accent)
                assertTrue("onAccent on $accent (dark=$dark)", contrastRatio(c.onAccent, c.accent) >= 2.5)
            }
        }
    }
}
