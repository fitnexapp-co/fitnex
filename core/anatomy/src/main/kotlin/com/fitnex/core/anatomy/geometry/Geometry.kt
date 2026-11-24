package com.fitnex.core.anatomy.geometry

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** 2D point in a map's own coordinate space (see [com.fitnex.core.anatomy.map2d.BodyAtlas2D.VIEW_WIDTH]). */
data class P2(val x: Float, val y: Float)

/** Even-odd ray casting; points exactly on an edge may fall either way, which is fine for taps. */
fun pointInPolygon(x: Float, y: Float, polygon: List<P2>): Boolean {
    var inside = false
    var j = polygon.lastIndex
    for (i in polygon.indices) {
        val a = polygon[i]
        val b = polygon[j]
        if ((a.y > y) != (b.y > y) && x < (b.x - a.x) * (y - a.y) / (b.y - a.y) + a.x) inside = !inside
        j = i
    }
    return inside
}

/** Area by the shoelace formula; used to prefer the smallest shape when several contain a tap. */
fun polygonArea(polygon: List<P2>): Float {
    var sum = 0f
    var j = polygon.lastIndex
    for (i in polygon.indices) {
        sum += (polygon[j].x + polygon[i].x) * (polygon[j].y - polygon[i].y)
        j = i
    }
    return kotlin.math.abs(sum / 2f)
}

data class V3(val x: Float, val y: Float, val z: Float) {
    operator fun plus(o: V3) = V3(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: V3) = V3(x - o.x, y - o.y, z - o.z)
    operator fun times(s: Float) = V3(x * s, y * s, z * s)
    infix fun dot(o: V3) = x * o.x + y * o.y + z * o.z
    infix fun cross(o: V3) = V3(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x)
    fun length() = sqrt(this dot this)
    fun normalized(): V3 = length().let { if (it == 0f) this else V3(x / it, y / it, z / it) }
    /** Component-wise product / division (scaling by ellipsoid radii). */
    fun scale(o: V3) = V3(x * o.x, y * o.y, z * o.z)
    fun unscale(o: V3) = V3(x / o.x, y / o.y, z / o.z)
}

/** Row-major 3×3 rotation. */
class Mat3(internal val m: FloatArray) {
    operator fun times(v: V3) = V3(
        m[0] * v.x + m[1] * v.y + m[2] * v.z,
        m[3] * v.x + m[4] * v.y + m[5] * v.z,
        m[6] * v.x + m[7] * v.y + m[8] * v.z,
    )

    operator fun times(o: Mat3): Mat3 {
        val r = FloatArray(9)
        for (row in 0..2) for (col in 0..2) {
            r[row * 3 + col] = (0..2).sumOf { (m[row * 3 + it] * o.m[it * 3 + col]).toDouble() }.toFloat()
        }
        return Mat3(r)
    }

    /** A rotation's inverse is its transpose. */
    fun transposed() = Mat3(floatArrayOf(m[0], m[3], m[6], m[1], m[4], m[7], m[2], m[5], m[8]))

    companion object {
        val IDENTITY = Mat3(floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f))

        /** Rotation about the vertical axis; positive turns the body's front towards +x. */
        fun yaw(rad: Float): Mat3 { val c = cos(rad); val s = sin(rad); return Mat3(floatArrayOf(c, 0f, s, 0f, 1f, 0f, -s, 0f, c)) }
        fun pitch(rad: Float): Mat3 { val c = cos(rad); val s = sin(rad); return Mat3(floatArrayOf(1f, 0f, 0f, 0f, c, -s, 0f, s, c)) }
        fun roll(rad: Float): Mat3 { val c = cos(rad); val s = sin(rad); return Mat3(floatArrayOf(c, -s, 0f, s, c, 0f, 0f, 0f, 1f)) }
    }
}
