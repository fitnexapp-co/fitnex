package com.fitnex.core.anatomy

import com.fitnex.core.anatomy.map3d.AtlasTexture
import com.fitnex.core.anatomy.map3d.BodyMesh
import com.fitnex.core.anatomy.map3d.MuscleSegmentation
import com.fitnex.core.anatomy.map3d.Camera3D
import com.fitnex.core.anatomy.map3d.HumanBody
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.PI
import org.junit.Test

/** Dev-only: set FITNEX_GLB and FITNEX_RENDER_DIR to preview a .glb with the app's renderer. */
class GlbPreviewTest {
    /** FITNEX_OBJ: render an app-format body front view with a 10 cm grid (labels every 10 cm, x from the centre). */
    @Test
    fun gridPreview() {
        val path = System.getenv("FITNEX_OBJ") ?: return
        val dir = System.getenv("FITNEX_RENDER_DIR") ?: return
        val body = File(path).inputStream().use { HumanBody.parse(it) }
        val mesh = BodyMesh(body, FloatArray(body.vertexCount * 2) { MuscleSegmentation.NO_COORD }, AtlasTexture())
        val iw = 900; val ih = 1000
        val px = IntArray(iw * ih)
        val cam = Camera3D()
        mesh.rasterize(cam, iw, ih, intArrayOf(0xFFD8D8D8.toInt()), px)
        val img = BufferedImage(iw, ih, BufferedImage.TYPE_INT_ARGB)
        img.setRGB(0, 0, iw, ih, px.map { if (it == 0) 0xFF1E1E22.toInt() else it }.toIntArray(), 0, iw)
        val g = img.createGraphics()
        val s = cam.scale(ih.toFloat())
        g.font = java.awt.Font("SansSerif", java.awt.Font.PLAIN, 12)
        for (yc in 0..180 step 10) {
            val sy = (ih / 2f - (yc - BodyMesh.PIVOT_Y) * s).toInt()
            g.color = java.awt.Color(255, 60, 60, 120); g.drawLine(0, sy, iw, sy)
            g.color = java.awt.Color.YELLOW; g.drawString("$yc", 2, sy - 2)
        }
        for (xc in -50..50 step 10) {
            val sx = (iw / 2f + xc * s).toInt()
            g.color = java.awt.Color(60, 160, 255, 120); g.drawLine(sx, 0, sx, ih)
            g.color = java.awt.Color.CYAN; g.drawString("$xc", sx + 2, 14)
        }
        ImageIO.write(img, "png", File(dir, "grid_front.png"))
    }

    @Test
    fun preview() {
        val path = System.getenv("FITNEX_GLB") ?: return
        val dir = System.getenv("FITNEX_RENDER_DIR") ?: return
        val (raw, idx) = HumanBody.readGlb(File(path).readBytes())
        val n = raw.size / 3
        val axes = (0..2).map { a -> (0 until n).minOf { raw[it * 3 + a] } to (0 until n).maxOf { raw[it * 3 + a] } }
        File(dir, "glb_info.txt").writeText("vertices=$n triangles=${idx.size / 3}\n" + axes.joinToString("\n") { "${it.first} .. ${it.second}" })
        // Try y-up and x-up readings; normalise to 180 cm with feet at 0.
        listOf("asis" to { x: Float, y: Float, z: Float -> Triple(x, y, z) }, "xup" to { x: Float, y: Float, z: Float -> Triple(y, x, z) }).forEach { (name, f) ->
            val p = FloatArray(raw.size)
            for (i in 0 until n) { val (a, b, c) = f(raw[i * 3], raw[i * 3 + 1], raw[i * 3 + 2]); p[i * 3] = a; p[i * 3 + 1] = b; p[i * 3 + 2] = c }
            val minY = (0 until n).minOf { p[it * 3 + 1] }; val maxY = (0 until n).maxOf { p[it * 3 + 1] }
            val s = HumanBody.HEIGHT / (maxY - minY)
            for (i in 0 until n) { p[i * 3] *= s; p[i * 3 + 1] = (p[i * 3 + 1] - minY) * s; p[i * 3 + 2] *= s }
            val body = HumanBody(p, HumanBody.smoothNormals(p, idx), idx, emptyMap())
            val mesh = BodyMesh(body, FloatArray(n * 2) { MuscleSegmentation.NO_COORD }, AtlasTexture())
            val iw = 500; val ih = 900
            listOf("front" to 0f, "side" to (PI / 2).toFloat(), "back" to PI.toFloat()).forEach { (view, yaw) ->
                val px = IntArray(iw * ih)
                mesh.rasterize(Camera3D(yaw = yaw), iw, ih, intArrayOf(0xFFD8D8D8.toInt()), px)
                val img = BufferedImage(iw, ih, BufferedImage.TYPE_INT_ARGB)
                img.setRGB(0, 0, iw, ih, px.map { if (it == 0) 0xFF1E1E22.toInt() else it }.toIntArray(), 0, iw)
                ImageIO.write(img, "png", File(dir, "glb_${name}_$view.png"))
            }
        }
    }
}
