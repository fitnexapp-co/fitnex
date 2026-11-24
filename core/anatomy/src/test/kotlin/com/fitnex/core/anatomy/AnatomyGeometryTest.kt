package com.fitnex.core.anatomy

import com.fitnex.core.anatomy.geometry.P2
import com.fitnex.core.anatomy.geometry.pointInPolygon
import com.fitnex.core.anatomy.geometry.polygonArea
import com.fitnex.core.anatomy.map2d.BodyAtlas2D
import com.fitnex.core.anatomy.map2d.BodyView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnatomyGeometryTest {

    @Test
    fun polygonBasics() {
        val square = listOf(P2(0f, 0f), P2(10f, 0f), P2(10f, 10f), P2(0f, 10f))
        assertTrue(pointInPolygon(5f, 5f, square))
        assertTrue(!pointInPolygon(15f, 5f, square))
        assertEquals(100f, polygonArea(square), 0.01f)
    }

    // ------------------------------------------------------------------ 2D

    private fun centroid(muscle: String, view: BodyView): P2 {
        val p = BodyAtlas2D.shapes(view).first { it.muscleId == muscle }.polygon
        return P2(p.map { it.x }.average().toFloat(), p.map { it.y }.average().toFloat())
    }

    @Test
    fun everyMuscleHasLeftAndRightShapesAndIsTappableAtItsCentre() {
        BodyView.entries.forEach { view ->
            BodyAtlas2D.muscleIds(view).forEach { id ->
                assertEquals("$id in $view must have a left and a right shape", 0, BodyAtlas2D.shapes(view).count { it.muscleId == id } % 2)
                val c = centroid(id, view)
                assertEquals("tap at the centre of $id ($view)", id, BodyAtlas2D.hitTest(view, c.x, c.y))
            }
        }
    }

    @Test
    fun hitTestOutsideMusclesIsNull() {
        assertNull(BodyAtlas2D.hitTest(BodyView.FRONT, 100f, 36f)) // head: secondary zone
        assertNull(BodyAtlas2D.hitTest(BodyView.FRONT, 5f, 5f)) // background
    }

    @Test
    fun deepMuscleOnTopWinsOverTheOneAroundIt() {
        // The rhomboids sit inside the trapezius outline on the back view.
        val c = centroid("rhomboids", BodyView.BACK)
        assertEquals("rhomboids", BodyAtlas2D.hitTest(BodyView.BACK, c.x, c.y))
    }

    @Test
    fun preferredViewShowsTheMuscle() {
        assertEquals(BodyView.FRONT, BodyAtlas2D.preferredView("quadriceps"))
        assertEquals(BodyView.BACK, BodyAtlas2D.preferredView("hamstrings"))
        assertEquals(BodyView.BACK, BodyAtlas2D.preferredView("latissimus_dorsi"))
    }

}
