package com.fitnex.core.anatomy.map3d

import com.fitnex.core.anatomy.geometry.P2
import com.fitnex.core.anatomy.geometry.V3
import com.fitnex.core.anatomy.map2d.BodyAtlas2D
import com.fitnex.core.anatomy.map2d.BodyView
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Gives every vertex of the 3D body the muscle drawn at the same place on the 2D atlas, so both
 * maps always agree. Each vertex is moved into the atlas pose with the body's own joints:
 * - arms: position along shoulder → elbow → wrist, plus sideways offset scaled to the atlas arm;
 * - legs: straightened about the hip, then scaled between the atlas leg's edges;
 * - trunk: height mapped by landmarks, width scaled to the atlas trunk at that height.
 * Front-facing vertices read the front atlas, back-facing ones the back atlas. Vertices that land
 * outside every muscle (head, joints, hands, feet, shins) stay "body" (null).
 */
object MuscleSegmentation {

    private enum class Region { ARM, TRUNK }

    /** Atlas x, y per vertex ([NO_COORD] where the body has no atlas position: head, hands). */
    fun atlasCoords(body: HumanBody): FloatArray = rawCoords(body)

    /**
     * One muscle per vertex (null = no muscle), read from the atlas at the vertex's position, then two
     * rounds of neighbour voting. Used for checks and where a triangle spans a mapping seam.
     */
    fun assign(body: HumanBody, coords: FloatArray = atlasCoords(body)): Array<String?> {
        val raw = Array(body.vertexCount) { i ->
            val ax = coords[i * 2]; val ay = coords[i * 2 + 1]
            if (ax == NO_COORD) null
            else BodyAtlas2D.hitTest(if (body.normal(i).z >= 0f) BodyView.FRONT else BodyView.BACK, ax, ay)
        }
        return smooth(body, raw, rounds = 2)
    }

    const val NO_COORD = -1000f

    /** Inside the lateral deltoid in both atlas views. */
    private val LATERAL_DELTOID = P2(43.5f, 106f)

    private fun smooth(body: HumanBody, labels: Array<String?>, rounds: Int): Array<String?> {
        val neighbours = Array(body.vertexCount) { HashSet<Int>(8) }
        val t = body.triangles
        for (i in 0 until body.triangleCount) {
            val a = t[i * 3]; val b = t[i * 3 + 1]; val c = t[i * 3 + 2]
            neighbours[a] += b; neighbours[a] += c; neighbours[b] += a; neighbours[b] += c; neighbours[c] += a; neighbours[c] += b
        }
        var current = labels
        repeat(rounds) {
            val prev = current
            current = Array(prev.size) { v ->
                val votes = HashMap<String?, Int>()
                votes[prev[v]] = 2 // a vertex keeps its label unless clearly outvoted
                neighbours[v].forEach { votes[prev[it]] = (votes[prev[it]] ?: 0) + 1 }
                votes.maxBy { it.value }.key
            }
        }
        return current
    }

    private fun rawCoords(body: HumanBody): FloatArray {
        val n = body.vertexCount
        val ground = 0f
        val shoulder = body.joint("l-shoulder")
        val elbow = body.joint("l-elbow")
        val wrist = body.joint("l-hand")
        val hip = body.joint("l-upper-leg")
        val knee = body.joint("l-knee")
        val ankle = body.joint("l-ankle")
        val pelvis = body.joint("pelvis")
        val neck = body.joint("neck")

        // Height landmarks: body y (cm) → atlas y.
        val yMap = listOf(
            ground to 433f, ankle.y to 405f, knee.y to 334f, hip.y to 236f, pelvis.y to 226f,
            shoulder.y to 100f, shoulder.y + 8f to 90f, neck.y to 72f, HumanBody.HEIGHT to 8f,
        ).sortedBy { it.first }
        fun atlasY(y: Float): Float {
            if (y <= yMap.first().first) return yMap.first().second
            for (i in 1 until yMap.size) {
                val (y0, a0) = yMap[i - 1]; val (y1, a1) = yMap[i]
                if (y <= y1) return a0 + (a1 - a0) * (y - y0) / (y1 - y0)
            }
            return yMap.last().second
        }

        // Leg straightening: rotate about the hip so hip → ankle is vertical.
        val legAngle = atan2(ankle.x - hip.x, hip.y - ankle.y)
        val cosL = cos(-legAngle); val sinL = sin(-legAngle)
        fun straightenLeg(x: Float, y: Float): P2 {
            val dx = x - hip.x; val dy = y - hip.y
            return P2(hip.x + dx * cosL + dy * sinL, hip.y - dx * sinL + dy * cosL)
        }

        // Hip band where the leg mapping hands over to the trunk mapping (no seam, no jagged line).
        val blendBottom = hip.y - 4f
        val blendTop = pelvis.y + 6f

        // ---- pass 1: region, and the per-height / per-t extents used for scaling
        val region = arrayOfNulls<Region>(n)
        val armT = FloatArray(n)
        val armLat = FloatArray(n)
        val legP = arrayOfNulls<P2>(n)
        val trunkMax = FloatArray(BINS)
        val legMin = FloatArray(BINS) { Float.MAX_VALUE }
        val legMax = FloatArray(BINS) { -Float.MAX_VALUE }
        val armMax = FloatArray(ARM_BINS)

        for (i in 0 until n) {
            val p = body.vertex(i)
            val q = V3(abs(p.x), p.y, p.z) // every side as the figure's left
            val (t, closest) = closestOnChain(q, shoulder, elbow, wrist)
            val d = (q - closest).length()
            // Distance seen from the front (depth ignored): a thick arm's back (triceps) is far from the
            // bone in 3D but still within the arm's outline in the frontal plane.
            val dFront = (V3(q.x, q.y, 0f) - V3(closest.x, closest.y, 0f)).length()
            val armLimit = if (t > 1f) 12f else 9.5f
            val lateralOfShoulder = q.x > shoulder.x - 2.5f || t > 0.08f
            val nearArm = d < armLimit || (t in 0f..1f && dFront < FRONT_ARM_RADIUS)
            if (q.x > shoulder.x * 0.6f && nearArm && t > -0.2f && lateralOfShoulder && q.y < shoulder.y + 9f) {
                region[i] = Region.ARM
                armT[i] = t
                // Sideways offset in the frontal plane, positive = away from the body.
                val axis = if (t < 0.5f) elbow - shoulder else wrist - elbow
                val out = V3(axis.y, -axis.x, 0f).normalized().let { if (it.x < 0f) it * -1f else it }
                armLat[i] = (q - closest) dot out
                val b = armBin(t)
                armMax[b] = maxOf(armMax[b], abs(armLat[i]))
            } else {
                // Trunk and legs overlap in a band around the hips, where both mappings are blended.
                region[i] = Region.TRUNK
                if (q.y < blendTop) {
                    val s = straightenLeg(q.x, q.y)
                    legP[i] = s
                    val b = bin(s.y)
                    legMin[b] = minOf(legMin[b], s.x); legMax[b] = maxOf(legMax[b], s.x)
                }
                if (q.y > blendBottom) {
                    val b = bin(q.y)
                    trunkMax[b] = maxOf(trunkMax[b], q.x)
                }
            }
        }
        smoothBins(trunkMax); smoothBins(armMax)

        // ---- pass 2: atlas position → muscle
        // The body's shoulder joint is the centre of the humeral head, a little below the atlas shoulder line.
        val atlasChain = listOf(P2(51f, 102f), P2(46f, 181f), P2(37f, 240f))
        val coords = FloatArray(n * 2)
        for (i in 0 until n) {
            val p = body.vertex(i)
            val nrm = body.normal(i)
            val a: P2? = if (region[i] == Region.ARM && armT[i] in -0.2f..0.3f && abs(nrm.z) < 0.45f && (abs(nrm.x) > 0.55f || nrm.y > 0.6f)) {
                // The side of the shoulder cap faces sideways/up: in both atlas views it is only a thin
                // edge, so place it inside the lateral deltoid.
                LATERAL_DELTOID
            } else when (region[i]) {
                Region.ARM -> {
                    val t = armT[i]
                    if (t > 1.02f) null // hand
                    else {
                        val (c, out) = chainAt(atlasChain, t)
                        val w = armMax[armBin(t)].takeIf { it > 0f } ?: 1f
                        val k = armLat[i] / w * atlasArmHalfWidth(t)
                        P2(c.x + out.x * k, c.y + out.y * k)
                    }
                }
                Region.TRUNK -> {
                    val leg = legP[i]?.let { s ->
                        val ay = atlasY(s.y)
                        val b = bin(s.y)
                        val e = edges(BodyAtlas2D.legLeft, ay)
                        val lo = legMin[b]; val hi = legMax[b]
                        if (e == null || hi <= lo) null
                        else {
                            val (outer, inner) = e
                            val f = (s.x - lo) / (hi - lo) // 0 = inner (midline side), 1 = outer
                            P2(inner + (outer - inner) * f, ay)
                        }
                    }
                    val trunk = if (p.y > blendBottom) {
                        val ay = atlasY(p.y)
                        val half = trunkHalfWidth(ay)
                        val w = trunkMax[bin(p.y)]
                        // Left and right atlas shapes leave a hairline gap on the midline: keep points just off it.
                        if (half == null || w <= 0f) null else P2(minOf(100f - abs(p.x) / w * half, MIDLINE_LIMIT), ay)
                    } else null
                    when {
                        leg != null && trunk != null -> {
                            val s = ((p.y - blendBottom) / (blendTop - blendBottom)).coerceIn(0f, 1f).let { it * it * (3 - 2 * it) }
                            P2(leg.x + (trunk.x - leg.x) * s, leg.y + (trunk.y - leg.y) * s)
                        }
                        else -> leg ?: trunk
                    }
                }
                null -> null
            }
            coords[i * 2] = a?.x ?: NO_COORD
            coords[i * 2 + 1] = a?.y ?: NO_COORD
        }
        return coords
    }

    // ------------------------------------------------------------------ helpers

    private const val MIDLINE_LIMIT = 98.6f
    private const val FRONT_ARM_RADIUS = 7.5f
    private const val BIN_CM = 2f
    private const val BINS = (HumanBody.HEIGHT / BIN_CM).toInt() + 2
    private const val ARM_BINS = 24

    private fun bin(y: Float) = (y / BIN_CM).toInt().coerceIn(0, BINS - 1)
    private fun armBin(t: Float) = ((t + 0.2f) / 1.4f * ARM_BINS).toInt().coerceIn(0, ARM_BINS - 1)

    /** Fills empty bins from neighbours and smooths, so one odd vertex cannot skew a slice. */
    private fun smoothBins(a: FloatArray) {
        for (i in a.indices) if (a[i] == 0f) a[i] = (1..4).firstNotNullOfOrNull { d ->
            listOfNotNull(a.getOrNull(i - d), a.getOrNull(i + d)).filter { it > 0f }.maxOrNull()
        } ?: 0f
        // Max over neighbours (a slice's widest point may fall in the next bin), then a 5-bin average
        // so the width changes gradually and muscle borders stay straight instead of wavy.
        val c = a.copyOf()
        for (i in a.indices) a[i] = listOfNotNull(c.getOrNull(i - 1), c[i], c.getOrNull(i + 1)).max()
        val d = a.copyOf()
        for (i in a.indices) a[i] = (-2..2).mapNotNull { d.getOrNull(i + it)?.takeIf { v -> v > 0f } }.ifEmpty { listOf(0f) }.average().toFloat()
    }

    /** Closest point on shoulder → elbow → wrist; t in 0..1 over the chain (extrapolated past the ends). */
    private fun closestOnChain(p: V3, s: V3, e: V3, w: V3): Pair<Float, V3> {
        val l1 = (e - s).length(); val l2 = (w - e).length(); val total = l1 + l2
        fun onSeg(a: V3, b: V3, clampLow: Boolean, clampHigh: Boolean): Pair<Float, V3> {
            val ab = b - a
            var u = ((p - a) dot ab) / (ab dot ab)
            if (clampLow) u = maxOf(u, 0f)
            if (clampHigh) u = minOf(u, 1f)
            return u to (a + ab * u)
        }
        val (u1, c1) = onSeg(s, e, clampLow = false, clampHigh = true)
        val (u2, c2) = onSeg(e, w, clampLow = true, clampHigh = false)
        return if ((p - c1).length() <= (p - c2).length()) (u1 * l1 / total) to c1 else ((l1 + u2 * l2) / total) to c2
    }

    /** Point on the atlas arm chain at t, and the outward (away from the body) unit normal. */
    private fun chainAt(chain: List<P2>, t: Float): Pair<P2, P2> {
        val l1 = dist(chain[0], chain[1]); val l2 = dist(chain[1], chain[2])
        val s = t * (l1 + l2)
        val (a, b, u) = if (s <= l1) Triple(chain[0], chain[1], s / l1) else Triple(chain[1], chain[2], (s - l1) / l2)
        val dx = b.x - a.x; val dy = b.y - a.y; val len = sqrt(dx * dx + dy * dy)
        // Atlas left side: outward is towards smaller x.
        var ox = dy / len; var oy = -dx / len
        if (ox > 0f) { ox = -ox; oy = -oy }
        return P2(a.x + dx * u, a.y + dy * u) to P2(ox, oy)
    }

    private fun atlasArmHalfWidth(t: Float): Float {
        // Slightly inside the atlas arm outline, so the arm's edges land on muscle, not on the grey gap.
        val stops = listOf(-0.2f to 9f, 0f to 9.5f, 0.3f to 8f, 0.55f to 7.5f, 0.8f to 6.5f, 1f to 5.5f)
        if (t <= stops.first().first) return stops.first().second
        for (i in 1 until stops.size) {
            val (t0, w0) = stops[i - 1]; val (t1, w1) = stops[i]
            if (t <= t1) return w0 + (w1 - w0) * (t - t0) / (t1 - t0)
        }
        return stops.last().second
    }

    /** Half width of the atlas trunk (or neck above it) at atlas height ay. */
    private fun trunkHalfWidth(ay: Float): Float? {
        val poly = if (ay < BodyAtlas2D.torsoHalf.first().y) BodyAtlas2D.neck else BodyAtlas2D.torsoHalf
        val xs = crossings(poly, ay)
        return xs.minOrNull()?.let { 100f - it }?.takeIf { it > 0f }
    }

    /** Outer (smaller x) and inner (larger x) edge of a closed atlas outline at height ay. */
    private fun edges(poly: List<P2>, ay: Float): Pair<Float, Float>? {
        val xs = crossings(poly, ay)
        if (xs.size < 2) return null
        return xs.min() to xs.max()
    }

    private fun crossings(poly: List<P2>, ay: Float): List<Float> {
        val out = ArrayList<Float>()
        for (i in poly.indices) {
            val a = poly[i]; val b = poly[(i + 1) % poly.size]
            if ((a.y <= ay && b.y > ay) || (b.y <= ay && a.y > ay)) out += a.x + (b.x - a.x) * (ay - a.y) / (b.y - a.y)
        }
        return out
    }

    private fun dist(a: P2, b: P2) = sqrt((a.x - b.x) * (a.x - b.x) + (a.y - b.y) * (a.y - b.y))
}
