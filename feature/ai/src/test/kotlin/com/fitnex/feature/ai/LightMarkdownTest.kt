package com.fitnex.feature.ai

import androidx.compose.ui.text.font.FontWeight
import org.junit.Assert.assertEquals
import org.junit.Test

class LightMarkdownTest {
    @Test
    fun boldMarkersBecomeBoldSpans() {
        val result = lightMarkdown("1. **Empieza poco a poco:** con calma")
        assertEquals("1. Empieza poco a poco: con calma", result.text)
        val bold = result.spanStyles.single()
        assertEquals(FontWeight.Bold, bold.item.fontWeight)
        assertEquals("Empieza poco a poco:", result.text.substring(bold.start, bold.end))
    }

    @Test
    fun bulletsAndHeadingsAreCleaned() {
        assertEquals("Plan\n• Agua\n• Dormir", lightMarkdown("## Plan\n* Agua\n- Dormir").text)
    }

    @Test
    fun unmatchedMarkerStaysPlain() {
        assertEquals("a **b", lightMarkdown("a **b").text)
    }
}
