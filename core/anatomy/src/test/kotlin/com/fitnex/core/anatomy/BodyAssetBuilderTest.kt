package com.fitnex.core.anatomy

import com.fitnex.core.anatomy.map3d.HumanBody
import java.io.File
import java.util.Locale
import org.junit.Test

/**
 * Dev tool, not a regular test: builds assets/anatomy/body.obj from the owner's .glb.
 * Set FITNEX_GLB (input) and FITNEX_ASSET_OUT (output .obj). Steps:
 * 1. keep the figure that faces the camera (hy3d-model.glb holds front, side and back figures side by side);
 * 2. normalise to 180 cm, feet at y = 0, centred on x and z;
 * 3. write the joints the muscle mapping needs. They were read from a front render with a 10 cm grid
 *    (GlbPreviewTest.gridPreview, FITNEX_OBJ); their depth is measured from the mesh.
 */
class BodyAssetBuilderTest {
    @Test
    fun build() {
        val glb = System.getenv("FITNEX_GLB") ?: return
        val out = System.getenv("FITNEX_ASSET_OUT") ?: return
        val (raw, idx) = HumanBody.readGlb(File(glb).readBytes())
        val n = raw.size / 3

        // 1. Figures are separated along x by empty gaps; the lowest-x one faces the camera.
        val xs = (0 until n).map { raw[it * 3] }.sorted()
        // FITNEX_SINGLE=1: the file holds one figure only (arms may be apart from the torso: no cut).
        val single = System.getenv("FITNEX_SINGLE") == "1"
        val cut = if (single) Float.MAX_VALUE
        else (1 until xs.size).firstOrNull { xs[it] - xs[it - 1] > 0.02f }?.let { (xs[it - 1] + xs[it]) / 2 } ?: Float.MAX_VALUE
        val keep = BooleanArray(n) { raw[it * 3] < cut }
        val remap = IntArray(n) { -1 }
        var count = 0
        for (i in 0 until n) if (keep[i]) remap[i] = count++
        val tris = ArrayList<Int>()
        for (t in 0 until idx.size / 3) {
            val a = idx[t * 3]; val b = idx[t * 3 + 1]; val c = idx[t * 3 + 2]
            if (keep[a] && keep[b] && keep[c]) { tris += remap[a]; tris += remap[b]; tris += remap[c] }
        }

        // 2. Normalise.
        val p = FloatArray(count * 3)
        for (i in 0 until n) if (keep[i]) { val k = remap[i]; p[k * 3] = raw[i * 3]; p[k * 3 + 1] = raw[i * 3 + 1]; p[k * 3 + 2] = raw[i * 3 + 2] }
        val minY = (0 until count).minOf { p[it * 3 + 1] }; val maxY = (0 until count).maxOf { p[it * 3 + 1] }
        val s = HumanBody.HEIGHT / (maxY - minY)
        val mx = (0 until count).map { p[it * 3] }.average().toFloat()
        val mz = (0 until count).map { p[it * 3 + 2] }.average().toFloat()
        for (i in 0 until count) { p[i * 3] = (p[i * 3] - mx) * s; p[i * 3 + 1] = (p[i * 3 + 1] - minY) * s; p[i * 3 + 2] = (p[i * 3 + 2] - mz) * s }

        // 2b. FITNEX_ARM_DOWN="deg;px,py": lower T-pose arms into an A-pose by rotating them about the
        // shoulder pivot (px, py). The weight ramps in across the shoulder and armpit so the skin
        // stretches smoothly instead of tearing; mirrored for the right arm.
        System.getenv("FITNEX_ARM_DOWN")?.let { spec ->
            val (deg, piv) = spec.split(';')
            val (px, py) = piv.split(',').map { it.toFloat() }
            val a = Math.toRadians(deg.toDouble())
            fun smooth(e0: Float, e1: Float, v: Float): Float { val t = ((v - e0) / (e1 - e0)).coerceIn(0f, 1f); return t * t * (3 - 2 * t) }
            for (i in 0 until count) {
                val x = p[i * 3]; val y = p[i * 3 + 1]
                val ax = kotlin.math.abs(x)
                // Skin on top of the shoulder stays more anchored, so the cap rounds over instead of squaring off.
                val w = smooth(px - 4f, px + 8f, ax) * smooth(py - 26f, py - 14f, y) * (1f - 0.55f * smooth(py - 4f, py + 6f, y) * (1f - smooth(px + 6f, px + 16f, ax)))
                if (w <= 0f) continue
                val th = a * w
                val dx = ax - px; val dy = y - py
                val nx = (dx * kotlin.math.cos(th) + dy * kotlin.math.sin(th)).toFloat()
                val ny = (-dx * kotlin.math.sin(th) + dy * kotlin.math.cos(th)).toFloat()
                p[i * 3] = kotlin.math.sign(x) * (px + nx)
                p[i * 3 + 1] = py + ny
            }
        }

        // 3. Joints (figure's left = +x), depth = mean z of vertices within 4 cm in the frontal plane.
        fun read(x: Float, y: Float): Triple<Float, Float, Float> {
            val z = (0 until count).filter { val dx = p[it * 3] - x; val dy = p[it * 3 + 1] - y; dx * dx + dy * dy < 16f }
                .map { p[it * 3 + 2] }.ifEmpty { listOf(0f) }.average().toFloat()
            return Triple(x, y, z)
        }
        // FITNEX_JOINTS="pelvis=0,95;neck=0,149;…" overrides the joints read for the original model.
        val custom = System.getenv("FITNEX_JOINTS")?.split(';')?.filter { it.isNotBlank() }?.associate { e ->
            val (k, v) = e.split('='); val (x, y) = v.split(',').map { it.trim().toFloat() }; k.trim() to read(x, y)
        }
        val joints = if (custom != null) LinkedHashMap(custom) else linkedMapOf(
            "pelvis" to read(0f, 95f), "neck" to read(0f, 149f),
            "l-shoulder" to read(22f, 135f), "l-elbow" to read(32f, 103f), "l-hand" to read(38f, 86f),
            "l-upper-leg" to read(10f, 88f), "l-knee" to read(11f, 47f), "l-ankle" to read(13f, 10f),
        )

        val sb = StringBuilder()
        val source = System.getenv("FITNEX_SOURCE")
        if (source != null) source.split('|').forEach { sb.appendLine("# $it") } else {
        sb.appendLine("# FITNEX anatomy body. Source: 3D model supplied by the app owner (hy3d-model.glb, 20/11/2026;")
        sb.appendLine("# by its name, generated with Hunyuan3D). Front-facing figure extracted; normalised to 180 cm;")
        }
        sb.appendLine("# joints read from a gridded front render. Format: j name x y z | v x y z | f a b c (0-based).")
        joints.forEach { (name, t) -> sb.appendLine(String.format(Locale.ROOT, "j %s %.3f %.3f %.3f", name, t.first, t.second, t.third)) }
        for (i in 0 until count) sb.appendLine(String.format(Locale.ROOT, "v %.3f %.3f %.3f", p[i * 3], p[i * 3 + 1], p[i * 3 + 2]))
        for (t in 0 until tris.size / 3) sb.appendLine("f ${tris[t * 3]} ${tris[t * 3 + 1]} ${tris[t * 3 + 2]}")
        File(out).writeText(sb.toString())
    }
}
