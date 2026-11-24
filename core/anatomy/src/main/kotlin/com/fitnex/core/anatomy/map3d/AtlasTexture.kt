package com.fitnex.core.anatomy.map3d

import com.fitnex.core.anatomy.geometry.P2
import com.fitnex.core.anatomy.geometry.pointInPolygon
import com.fitnex.core.anatomy.map2d.BodyAtlas2D
import com.fitnex.core.anatomy.map2d.BodyView

/**
 * The 2D atlas rasterised into label images (one per view) so the 3D body can be painted with the
 * exact same muscle shapes and separation lines as the 2D map, pixel by pixel.
 *
 * Each texel holds 0 (no muscle) or 1 + index into [muscleIds]; [EDGE] is set on texels where two
 * regions meet, which the renderer draws as the dividing line.
 */
class AtlasTexture(val texelsPerUnit: Float = 3f) {
    val muscleIds: List<String> = BodyAtlas2D.shapes.map { it.muscleId }.distinct().sorted()
    val width = (BodyAtlas2D.VIEW_WIDTH * texelsPerUnit).toInt()
    val height = (BodyAtlas2D.VIEW_HEIGHT * texelsPerUnit).toInt()
    private val front = build(BodyView.FRONT)
    private val back = build(BodyView.BACK)

    /** Raw texel (label | EDGE) at atlas coordinates, 0 outside. */
    fun texel(view: BodyView, ax: Float, ay: Float): Int {
        val x = (ax * texelsPerUnit).toInt(); val y = (ay * texelsPerUnit).toInt()
        if (ax < 0f || ay < 0f || x >= width || y >= height) return 0
        return (if (view == BodyView.FRONT) front else back)[y * width + x].toInt() and 0xFF
    }

    /** Muscle id at atlas coordinates, or null. */
    fun muscleAt(view: BodyView, ax: Float, ay: Float): String? =
        (texel(view, ax, ay) and LABEL_MASK).takeIf { it > 0 }?.let { muscleIds[it - 1] }

    private fun build(view: BodyView): ByteArray {
        val img = ByteArray(width * height)
        // Same order as the 2D map draws them: later shapes cover earlier ones.
        BodyAtlas2D.shapes(view).forEach { shape ->
            val label = (muscleIds.indexOf(shape.muscleId) + 1).toByte()
            val outline = smooth(shape.polygon)
            val minX = (outline.minOf { it.x } * texelsPerUnit).toInt().coerceAtLeast(0)
            val maxX = (outline.maxOf { it.x } * texelsPerUnit).toInt().coerceAtMost(width - 1)
            val minY = (outline.minOf { it.y } * texelsPerUnit).toInt().coerceAtLeast(0)
            val maxY = (outline.maxOf { it.y } * texelsPerUnit).toInt().coerceAtMost(height - 1)
            for (y in minY..maxY) for (x in minX..maxX) {
                if (pointInPolygon((x + 0.5f) / texelsPerUnit, (y + 0.5f) / texelsPerUnit, outline)) img[y * width + x] = label
            }
        }
        // Dividing lines: a texel touching a different region within 1 texel.
        val edges = BooleanArray(img.size)
        for (y in 0 until height) for (x in 0 until width) {
            val v = img[y * width + x]
            loop@ for (dy in -1..1) for (dx in -1..1) {
                val nx = x + dx; val ny = y + dy
                if (nx !in 0 until width || ny !in 0 until height) continue
                if (img[ny * width + nx] != v) { edges[y * width + x] = true; break@loop }
            }
        }
        for (i in img.indices) if (edges[i]) img[i] = (img[i].toInt() or EDGE).toByte()
        return img
    }

    /** Same Catmull-Rom curve as the 2D map, sampled into a dense polygon. */
    private fun smooth(points: List<P2>): List<P2> {
        val n = points.size
        val out = ArrayList<P2>(n * SAMPLES)
        for (i in 0 until n) {
            val p0 = points[(i - 1 + n) % n]; val p1 = points[i]; val p2 = points[(i + 1) % n]; val p3 = points[(i + 2) % n]
            val c1 = P2(p1.x + (p2.x - p0.x) / 6f, p1.y + (p2.y - p0.y) / 6f)
            val c2 = P2(p2.x - (p3.x - p1.x) / 6f, p2.y - (p3.y - p1.y) / 6f)
            for (k in 0 until SAMPLES) {
                val t = k.toFloat() / SAMPLES; val u = 1 - t
                out += P2(
                    u * u * u * p1.x + 3 * u * u * t * c1.x + 3 * u * t * t * c2.x + t * t * t * p2.x,
                    u * u * u * p1.y + 3 * u * u * t * c1.y + 3 * u * t * t * c2.y + t * t * t * p2.y,
                )
            }
        }
        return out
    }

    companion object {
        const val EDGE = 0x80
        const val LABEL_MASK = 0x7F
        private const val SAMPLES = 8
    }
}
