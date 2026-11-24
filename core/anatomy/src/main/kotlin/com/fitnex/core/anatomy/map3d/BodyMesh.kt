package com.fitnex.core.anatomy.map3d

import com.fitnex.core.anatomy.geometry.Mat3
import com.fitnex.core.anatomy.geometry.V3
import com.fitnex.core.anatomy.map2d.BodyView
import kotlin.math.abs

/** Orbit camera around the body. yaw 0 = front, π = back. Orthographic projection. */
data class Camera3D(
    val yaw: Float = 0f,
    val pitch: Float = 0f,
    val zoom: Float = 1f,
    val panX: Float = 0f,
    val panY: Float = 0f,
) {
    val rotation: Mat3 get() = Mat3.pitch(pitch) * Mat3.yaw(yaw)

    fun scale(height: Float) = height * FIT / HumanBody.HEIGHT * zoom

    companion object {
        const val FIT = 0.94f
        const val MIN_ZOOM = 0.8f
        const val MAX_ZOOM = 3.5f
    }
}

/**
 * The 3D body ready to draw. Every pixel of the surface carries its position on the 2D atlas
 * (interpolated from the vertices), so the muscles are painted with exactly the 2D shapes and
 * separated by the same dividing lines ([AtlasTexture]); facing decides front or back atlas.
 * Triangles that straddle a mapping seam (armpit, groin, wrists, neck) fall back to the muscle of
 * their nearest corner. Pure Kotlin (unit-tested directly); buffers are reused between frames.
 */
class BodyMesh(val body: HumanBody, private val coords: FloatArray, private val texture: AtlasTexture) {

    constructor(body: HumanBody, texture: AtlasTexture = AtlasTexture()) : this(body, MuscleSegmentation.atlasCoords(body), texture)

    /** Part 0 = no muscle; part k = texture.muscleIds[k - 1]. Colours are given per part. */
    val parts: List<String?> = listOf<String?>(null) + texture.muscleIds

    private val vertexMuscle: Array<String?> = MuscleSegmentation.assign(body, coords)
    private val vertexPart = IntArray(body.vertexCount) { i -> vertexMuscle[i]?.let { texture.muscleIds.indexOf(it) + 1 } ?: 0 }

    /** Muscles that actually appear on the body (all 20 for a well-mapped model). */
    val muscleIds: Set<String> = vertexMuscle.filterNotNull().toSet()

    val triangleCount get() = body.triangleCount
    private val vertexCount = body.vertexCount
    private val pos = body.positions
    private val nrm = body.normals
    private val tris = body.triangles

    /** Seam triangles: corners map to distant (or no) atlas places, so interpolating would smear. */
    private val seam = BooleanArray(triangleCount) { t ->
        val a = tris[t * 3]; val b = tris[t * 3 + 1]; val c = tris[t * 3 + 2]
        fun none(v: Int) = coords[v * 2] == MuscleSegmentation.NO_COORD
        fun far(u: Int, v: Int): Boolean {
            val dx = coords[u * 2] - coords[v * 2]; val dy = coords[u * 2 + 1] - coords[v * 2 + 1]
            return dx * dx + dy * dy > SEAM_UNITS * SEAM_UNITS
        }
        none(a) || none(b) || none(c) || far(a, b) || far(b, c) || far(a, c)
    }

    private val sx = FloatArray(vertexCount)
    private val sy = FloatArray(vertexCount)
    private val vz = FloatArray(vertexCount)
    private val nz = FloatArray(vertexCount)
    private val shade = FloatArray(vertexCount)
    private var depthBuffer = FloatArray(0)

    /**
     * Draws the body into [pixels] (ARGB, row-major, [width] × [height]; transparent background).
     * [partColors] has one colour per entry of [parts]. The buffer may be smaller than the canvas
     * (scaled up when drawn): [bufferPerCanvasPx] converts the camera's pan, which is in canvas pixels.
     */
    fun rasterize(camera: Camera3D, width: Int, height: Int, partColors: IntArray, pixels: IntArray, bufferPerCanvasPx: Float = 1f) {
        val m = camera.rotation.m
        val s = camera.scale(height.toFloat())
        val cx = width / 2f + camera.panX * bufferPerCanvasPx
        val cy = height / 2f + camera.panY * bufferPerCanvasPx
        val py = PIVOT_Y
        for (k in 0 until vertexCount) {
            val x = pos[k * 3]; val y = pos[k * 3 + 1] - py; val z = pos[k * 3 + 2]
            val nx0 = nrm[k * 3]; val ny0 = nrm[k * 3 + 1]; val nz0 = nrm[k * 3 + 2]
            sx[k] = cx + (m[0] * x + m[1] * y + m[2] * z) * s
            sy[k] = cy - (m[3] * x + m[4] * y + m[5] * z) * s
            vz[k] = m[6] * x + m[7] * y + m[8] * z
            val nx = m[0] * nx0 + m[1] * ny0 + m[2] * nz0
            val ny = m[3] * nx0 + m[4] * ny0 + m[5] * nz0
            val nzv = m[6] * nx0 + m[7] * ny0 + m[8] * nz0
            nz[k] = nzv
            val lambert = (nx * LIGHT.x + ny * LIGHT.y + nzv * LIGHT.z).coerceAtLeast(0f)
            val rim = (1f - nzv.coerceIn(0f, 1f)).let { it * it * it } * RIM
            shade[k] = AMBIENT + DIFFUSE * lambert + rim
        }
        val size = width * height
        if (depthBuffer.size < size) depthBuffer = FloatArray(size)
        java.util.Arrays.fill(depthBuffer, 0, size, Float.NEGATIVE_INFINITY)
        java.util.Arrays.fill(pixels, 0, size, 0)

        for (t in 0 until triangleCount) {
            val a = tris[t * 3]; val b = tris[t * 3 + 1]; val c = tris[t * 3 + 2]
            if (nz[a] + nz[b] + nz[c] < -0.6f) continue // clearly facing away
            val ax = sx[a]; val ay = sy[a]; val bx = sx[b]; val by = sy[b]; val qx = sx[c]; val qy = sy[c]
            val area = (bx - ax) * (qy - ay) - (by - ay) * (qx - ax)
            if (area == 0f) continue
            val minX = maxOf(0, minOf(ax, bx, qx).toInt())
            val maxX = minOf(width - 1, maxOf(ax, bx, qx).toInt() + 1)
            val minY = maxOf(0, minOf(ay, by, qy).toInt())
            val maxY = minOf(height - 1, maxOf(ay, by, qy).toInt() + 1)
            if (minX > maxX || minY > maxY) continue
            val inv = 1f / area
            val za = vz[a]; val zb = vz[b]; val zc = vz[c]
            val sa = shade[a]; val sb = shade[b]; val sc = shade[c]
            val isSeam = seam[t]
            for (yy in minY..maxY) {
                val fy = yy + 0.5f
                var idx = yy * width + minX
                for (xx in minX..maxX) {
                    val fx = xx + 0.5f
                    val w0 = ((bx - fx) * (qy - fy) - (by - fy) * (qx - fx)) * inv
                    val w1 = ((qx - fx) * (ay - fy) - (qy - fy) * (ax - fx)) * inv
                    val w2 = 1f - w0 - w1
                    if (w0 >= 0f && w1 >= 0f && w2 >= 0f) {
                        val depth = w0 * za + w1 * zb + w2 * zc
                        if (depth > depthBuffer[idx]) {
                            depthBuffer[idx] = depth
                            val texel = surfaceTexel(t, isSeam, w0, w1, w2)
                            var k = w0 * sa + w1 * sb + w2 * sc
                            if (texel and AtlasTexture.EDGE != 0) k *= LINE
                            val col = partColors[texel and AtlasTexture.LABEL_MASK]
                            val r = ((col shr 16 and 0xFF) * k).toInt().coerceIn(0, 255)
                            val g = ((col shr 8 and 0xFF) * k).toInt().coerceIn(0, 255)
                            val bl = ((col and 0xFF) * k).toInt().coerceIn(0, 255)
                            pixels[idx] = (0xFF shl 24) or (r shl 16) or (g shl 8) or bl
                        }
                    }
                    idx++
                }
            }
        }
    }

    /** Texel (part | EDGE) of the surface point with barycentric weights w0..w2 in triangle t. */
    private fun surfaceTexel(t: Int, isSeam: Boolean, w0: Float, w1: Float, w2: Float): Int {
        val a = tris[t * 3]; val b = tris[t * 3 + 1]; val c = tris[t * 3 + 2]
        if (isSeam) return vertexPart[if (w0 >= w1 && w0 >= w2) a else if (w1 >= w2) b else c]
        val ax = w0 * coords[a * 2] + w1 * coords[b * 2] + w2 * coords[c * 2]
        val ay = w0 * coords[a * 2 + 1] + w1 * coords[b * 2 + 1] + w2 * coords[c * 2 + 1]
        val facing = w0 * nrm[a * 3 + 2] + w1 * nrm[b * 3 + 2] + w2 * nrm[c * 3 + 2]
        val primary = if (facing >= 0f) BodyView.FRONT else BodyView.BACK
        val texel = texture.texel(primary, ax, ay)
        // On the sides (surface facing neither way) a muscle may only be drawn in the other view:
        // use it rather than leave a grey speck.
        if (texel and AtlasTexture.LABEL_MASK == 0 && abs(facing) < SIDE_FACING) {
            val other = texture.texel(if (primary == BodyView.FRONT) BodyView.BACK else BodyView.FRONT, ax, ay)
            if (other and AtlasTexture.LABEL_MASK != 0) return other
        }
        return texel
    }

    /** Muscle under a screen point, or null for the rest of the body / empty space. */
    fun pick(camera: Camera3D, width: Float, height: Float, x: Float, y: Float): String? {
        val s = camera.scale(height)
        val vx = (x - width / 2f - camera.panX) / s
        val vy = (height / 2f + camera.panY - y) / s
        val inv = camera.rotation.transposed()
        val origin = inv * V3(vx, vy, 1000f) + V3(0f, PIVOT_Y, 0f)
        val dir = inv * V3(0f, 0f, -1f)
        var bestT = Float.MAX_VALUE
        var best = -1
        var bestU = 0f; var bestV = 0f
        for (t in 0 until triangleCount) {
            val hit = rayTriangle(origin, dir, body.vertex(tris[t * 3]), body.vertex(tris[t * 3 + 1]), body.vertex(tris[t * 3 + 2])) ?: continue
            if (hit[0] < bestT) { bestT = hit[0]; best = t; bestU = hit[1]; bestV = hit[2] }
        }
        if (best < 0) return null
        val part = surfaceTexel(best, seam[best], 1f - bestU - bestV, bestU, bestV) and AtlasTexture.LABEL_MASK
        return parts[part]
    }

    /** Möller–Trumbore: [distance, u, v] or null. */
    private fun rayTriangle(o: V3, d: V3, a: V3, b: V3, c: V3): FloatArray? {
        val e1 = b - a; val e2 = c - a
        val p = d cross e2
        val det = e1 dot p
        if (abs(det) < 1e-7f) return null
        val inv = 1f / det
        val tv = o - a
        val u = (tv dot p) * inv
        if (u < 0f || u > 1f) return null
        val q = tv cross e1
        val v = (d dot q) * inv
        if (v < 0f || u + v > 1f) return null
        val t = (e2 dot q) * inv
        return if (t > 0f) floatArrayOf(t, u, v) else null
    }

    companion object {
        /** Rotation pivot height: about the body's centre of mass. */
        const val PIVOT_Y = 92f
        private val LIGHT = V3(-0.35f, 0.45f, 0.82f).normalized()
        // Peak brightness stays ≤ 1 so white muscles keep their shading instead of clipping flat.
        private const val AMBIENT = 0.30f
        private const val DIFFUSE = 0.60f
        private const val RIM = 0.10f
        /** Dividing lines between muscles, as on the 2D chart. */
        private const val LINE = 0.55f
        /** Corners further apart than this on the atlas are on different sides of a seam. */
        private const val SEAM_UNITS = 25f
        private const val SIDE_FACING = 0.4f
    }
}
