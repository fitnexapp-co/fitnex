package com.fitnex.core.anatomy

import com.fitnex.core.anatomy.map2d.BodyView
import org.junit.Assert.assertEquals
import org.junit.Test

class BestViewTest {
    @Test
    fun backExercisesOpenOnTheBack() {
        // Face pull: rear delts and rhomboids are primary, the trapezius (on both sides) secondary.
        val levels = mapOf("deltoid_posterior" to 1f, "rhomboids" to 1f, "trapezius" to 0.5f)
        assertEquals(BodyView.BACK, bestView(levels))
    }

    @Test
    fun frontExercisesAndNoDataOpenOnTheFront() {
        assertEquals(BodyView.FRONT, bestView(mapOf("pectoralis_major" to 1f, "deltoid_anterior" to 0.5f)))
        assertEquals(BodyView.FRONT, bestView(emptyMap()))
    }
}
