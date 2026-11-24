package com.fitnex.core.anatomy

import com.fitnex.core.anatomy.map2d.BodyAtlas2D
import com.fitnex.core.anatomy.map2d.BodyView
import com.fitnex.core.anatomy.map3d.BodyMesh
import com.fitnex.core.anatomy.map3d.Camera3D
import com.fitnex.core.anatomy.map3d.HumanBody
import com.fitnex.core.anatomy.map3d.MuscleSegmentation
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.PI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HumanBodyTest {
    private val body: HumanBody = File("src/main/assets/${HumanBody.ASSET}").inputStream().use { HumanBody.parse(it) }
    private val muscles = MuscleSegmentation.assign(body)
    private val mesh = BodyMesh(body)

    @Test
    fun assetLoadsAsOneStandingBody() {
        assertTrue(body.vertexCount > 5_000)
        val ys = (0 until body.vertexCount).map { body.positions[it * 3 + 1] }
        assertEquals(0f, ys.min(), 0.01f)
        assertEquals(HumanBody.HEIGHT, ys.max(), 0.01f)
        listOf("pelvis", "neck", "l-shoulder", "l-elbow", "l-hand", "l-upper-leg", "l-knee", "l-ankle").forEach { body.joint(it) }
        assertTrue(body.joint("l-shoulder").y > body.joint("l-elbow").y)
        assertTrue(body.joint("l-knee").y > body.joint("l-ankle").y)
    }

    @Test
    fun everyCatalogMuscleIsOnThe3DBodyAndMatchesThe2DMaps() {
        val in2D = BodyView.entries.flatMap { BodyAtlas2D.muscleIds(it) }.toSet()
        assertEquals(in2D, mesh.muscleIds)
        val counts = muscles.filterNotNull().groupingBy { it }.eachCount()
        // ≥ 10 vertices on a ~7k-vertex body ≈ a patch big enough to tap (the rhomboids are the smallest).
        counts.forEach { (id, c) -> assertTrue("$id has only $c vertices", c >= 10) }
        assertTrue("head, hands, feet, joints stay body", muscles.count { it == null } > body.vertexCount / 8)
        val left = (0 until body.vertexCount).count { muscles[it] == "biceps_brachii" && body.positions[it * 3] > 0 }
        val right = (0 until body.vertexCount).count { muscles[it] == "biceps_brachii" && body.positions[it * 3] < 0 }
        assertTrue("symmetric biceps $left / $right", left > 0 && right > 0 && left / right.toFloat() in 0.6f..1.6f)
    }

    private val w = 300f
    private val h = 600f
    private fun screen(x: Float, y: Float, cam: Camera3D = Camera3D()): Pair<Float, Float> {
        val s = cam.scale(h)
        return w / 2 + cam.panX + x * s to h / 2 + cam.panY - (y - BodyMesh.PIVOT_Y) * s
    }

    @Test
    fun pickingHitsTheMuscleUnderTheFinger() {
        val chest = body.joint("l-shoulder").let { screen(it.x * 0.45f, it.y - 8f) }
        assertEquals("pectoralis_major", mesh.pick(Camera3D(), w, h, chest.first, chest.second))
        // From behind, between the shoulders: the upper back (trapezius / rhomboids / lats). The exact height of the
        // shoulder blade (left blank on the 2D atlas) depends on the model, so probe a short vertical span.
        val sh = body.joint("l-shoulder")
        val probes = (-12..8 step 2).map { dy -> screen(sh.x * 0.45f, sh.y + dy) }
        val hits = probes.mapNotNull { (x, y) -> mesh.pick(Camera3D(yaw = PI.toFloat()), w, h, x, y) }
        val back = hits.firstOrNull { it in setOf("trapezius", "rhomboids", "latissimus_dorsi") } ?: hits.firstOrNull()
        assertTrue("from behind the same spot is the upper back, was $back", back in setOf("trapezius", "rhomboids", "latissimus_dorsi"))
        val head = screen(0f, 170f)
        assertNull(mesh.pick(Camera3D(), w, h, head.first, head.second))
        assertNull(mesh.pick(Camera3D(), w, h, 2f, 2f))
    }

    @Test
    fun rasterizerDrawsTheBody() {
        val pixels = IntArray(w.toInt() * h.toInt())
        val colors = IntArray(mesh.parts.size) { if (it == 0) 0xFF555555.toInt() else 0xFFFFFFFF.toInt() }
        mesh.rasterize(Camera3D(), w.toInt(), h.toInt(), colors, pixels)
        assertEquals(0, pixels[0])
        assertTrue(pixels.count { it != 0 } > pixels.size / 12)
        renderDebugImages()
    }

    /** Set FITNEX_RENDER_DIR to get PNGs: app colours (white + grey + lines) and one colour per muscle. */
    private fun renderDebugImages() {
        val dir = System.getenv("FITNEX_RENDER_DIR") ?: return
        val palette = intArrayOf(
            0xFFE6194B.toInt(), 0xFF3CB44B.toInt(), 0xFFFFE119.toInt(), 0xFF4363D8.toInt(), 0xFFF58231.toInt(),
            0xFF911EB4.toInt(), 0xFF46F0F0.toInt(), 0xFFF032E6.toInt(), 0xFFBCF60C.toInt(), 0xFFFABEBE.toInt(),
            0xFF008080.toInt(), 0xFFE6BEFF.toInt(), 0xFF9A6324.toInt(), 0xFFFFFAC8.toInt(), 0xFF800000.toInt(),
            0xFFAAFFC3.toInt(), 0xFF808000.toInt(), 0xFFFFD8B1.toInt(), 0xFF000075.toInt(), 0xFFFFFFFF.toInt(),
        )
        val app = IntArray(mesh.parts.size) { if (it == 0) 0xFF9A9CA3.toInt() else 0xFFF2F2F2.toInt() }
        val ids = IntArray(mesh.parts.size) { if (it == 0) 0xFF606060.toInt() else palette[(it - 1) % palette.size] }
        val iw = 500; val ih = 900
        listOf("app" to app, "ids" to ids).forEach { (style, colors) ->
            listOf("front" to 0f, "back" to PI.toFloat(), "side" to (PI / 2).toFloat()).forEach { (name, yaw) ->
                val px = IntArray(iw * ih)
                mesh.rasterize(Camera3D(yaw = yaw), iw, ih, colors, px)
                val img = BufferedImage(iw, ih, BufferedImage.TYPE_INT_ARGB)
                img.setRGB(0, 0, iw, ih, px.map { if (it == 0) 0xFF1E1E22.toInt() else it }.toIntArray(), 0, iw)
                ImageIO.write(img, "png", File(dir, "body_${style}_$name.png"))
            }
        }
    }
}
