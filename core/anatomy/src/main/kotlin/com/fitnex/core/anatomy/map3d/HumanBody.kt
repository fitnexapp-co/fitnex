package com.fitnex.core.anatomy.map3d

import com.fitnex.core.anatomy.geometry.V3
import java.io.InputStream

/**
 * The realistic 3D body: the MakeHuman base mesh (CC0 1.0) with the "young male, max muscle,
 * min weight" target applied, helpers removed (see the header of assets/anatomy/body.obj).
 *
 * Normalised on load to the app's 3D frame: centimetres of a [HEIGHT] cm figure, feet at y = 0,
 * x towards the figure's left, z towards the viewer in the front view.
 */
class HumanBody(
    /** x, y, z per vertex. */
    val positions: FloatArray,
    /** Smooth per-vertex normals, x, y, z. */
    val normals: FloatArray,
    /** Triangles as vertex indices. */
    val triangles: IntArray,
    /** Joint centres for the figure's left side (and the midline), by MakeHuman name. */
    val joints: Map<String, V3>,
) {
    val vertexCount: Int get() = positions.size / 3
    val triangleCount: Int get() = triangles.size / 3

    fun vertex(i: Int) = V3(positions[i * 3], positions[i * 3 + 1], positions[i * 3 + 2])
    fun normal(i: Int) = V3(normals[i * 3], normals[i * 3 + 1], normals[i * 3 + 2])
    fun joint(name: String): V3 = joints[name] ?: error("joint $name missing from body asset")

    companion object {
        const val HEIGHT = 180f
        const val ASSET = "anatomy/body.obj"

        /** Parses the asset format: `j name x y z`, `v x y z`, `f a b c d` (quads, 0-based). */
        fun parse(input: InputStream): HumanBody {
            val v = ArrayList<Float>(13_380 * 3)
            val tris = ArrayList<Int>(13_378 * 6)
            val rawJoints = LinkedHashMap<String, V3>()
            input.bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    when {
                        line.startsWith("v ") -> line.split(' ').let { p -> v += p[1].toFloat(); v += p[2].toFloat(); v += p[3].toFloat() }
                        line.startsWith("f ") -> {
                            val p = line.split(' ').drop(1).map { it.toInt() }
                            for (k in 1 until p.size - 1) { tris += p[0]; tris += p[k]; tris += p[k + 1] } // fan → triangles
                        }
                        line.startsWith("j ") -> line.split(' ').let { p -> rawJoints[p[1]] = V3(p[2].toFloat(), p[3].toFloat(), p[4].toFloat()) }
                    }
                }
            }
            // Normalise: feet on the floor, HEIGHT cm tall, pelvis on the z = 0 plane.
            val minY = (0 until v.size / 3).minOf { v[it * 3 + 1] }
            val maxY = (0 until v.size / 3).maxOf { v[it * 3 + 1] }
            val s = HEIGHT / (maxY - minY)
            val zc = rawJoints["pelvis"]?.z ?: 0f
            val pos = FloatArray(v.size)
            for (i in 0 until v.size / 3) {
                pos[i * 3] = v[i * 3] * s
                pos[i * 3 + 1] = (v[i * 3 + 1] - minY) * s
                pos[i * 3 + 2] = (v[i * 3 + 2] - zc) * s
            }
            val joints = rawJoints.mapValues { (_, j) -> V3(j.x * s, (j.y - minY) * s, (j.z - zc) * s) }
            val triArray = tris.toIntArray()
            return HumanBody(pos, smoothNormals(pos, triArray), triArray, joints)
        }

        /**
         * Raw triangle soup from a binary glTF (.glb): first mesh primitive, POSITION + indices only.
         * Returns positions (x, y, z) and triangle indices, untouched.
         */
        fun readGlb(bytes: ByteArray): Pair<FloatArray, IntArray> {
            val bb = java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            require(bb.getInt(0) == 0x46546C67) { "not a GLB file" }
            val jsonLen = bb.getInt(12)
            val json = String(bytes, 20, jsonLen, Charsets.UTF_8)
            val binStart = 20 + jsonLen + 8
            fun num(src: String, key: String) = Regex("\"$key\"\\s*:\\s*(\\d+)").find(src)!!.groupValues[1].toInt()
            fun objects(arrayKey: String): List<String> {
                val start = json.indexOf("\"$arrayKey\"").let { json.indexOf('[', it) }
                val out = ArrayList<String>(); var depth = 0; var objStart = -1
                for (i in start + 1 until json.length) {
                    when (json[i]) {
                        '{' -> { if (depth == 0) objStart = i; depth++ }
                        '}' -> { depth--; if (depth == 0) out += json.substring(objStart, i + 1) }
                        ']' -> if (depth == 0) return out
                    }
                }
                return out
            }
            val accessors = objects("accessors")
            val views = objects("bufferViews")
            // Files can hold several meshes (eyes, props): take the primitive with the most vertices.
            val prims = Regex("\"POSITION\"\\s*:\\s*(\\d+)").findAll(json).map { m ->
                val tail = json.substring(m.range.first)
                m.groupValues[1].toInt() to num(tail, "indices")
            }.toList()
            val (posIdx, idxIdx) = prims.maxBy { num(accessors[it.first], "count") }
            val posAcc = accessors[posIdx]
            val idxAcc = accessors[idxIdx]
            fun viewOffset(acc: String): Int {
                val v = views[num(acc, "bufferView")]
                val accOffset = Regex("\"byteOffset\"\\s*:\\s*(\\d+)").find(acc)?.groupValues?.get(1)?.toInt() ?: 0
                val viewOff = Regex("\"byteOffset\"\\s*:\\s*(\\d+)").find(v)?.groupValues?.get(1)?.toInt() ?: 0
                return binStart + viewOff + accOffset
            }
            val pCount = num(posAcc, "count")
            val pos = FloatArray(pCount * 3)
            var o = viewOffset(posAcc)
            for (i in pos.indices) { pos[i] = bb.getFloat(o); o += 4 }
            val iCount = num(idxAcc, "count")
            val comp = num(idxAcc, "componentType")
            val idx = IntArray(iCount)
            o = viewOffset(idxAcc)
            for (i in 0 until iCount) {
                idx[i] = when (comp) { 5125 -> bb.getInt(o).also { o += 4 }; 5123 -> (bb.getShort(o).toInt() and 0xFFFF).also { o += 2 }; else -> error("index type $comp") }
            }
            return pos to idx
        }

        /** Area-weighted vertex normals, so shading is smooth across the quads. */
        internal fun smoothNormals(pos: FloatArray, tris: IntArray): FloatArray {
            val n = FloatArray(pos.size)
            for (t in 0 until tris.size / 3) {
                val a = tris[t * 3]; val b = tris[t * 3 + 1]; val c = tris[t * 3 + 2]
                val ux = pos[b * 3] - pos[a * 3]; val uy = pos[b * 3 + 1] - pos[a * 3 + 1]; val uz = pos[b * 3 + 2] - pos[a * 3 + 2]
                val wx = pos[c * 3] - pos[a * 3]; val wy = pos[c * 3 + 1] - pos[a * 3 + 1]; val wz = pos[c * 3 + 2] - pos[a * 3 + 2]
                val nx = uy * wz - uz * wy; val ny = uz * wx - ux * wz; val nz = ux * wy - uy * wx
                for (k in intArrayOf(a, b, c)) { n[k * 3] += nx; n[k * 3 + 1] += ny; n[k * 3 + 2] += nz }
            }
            for (i in 0 until n.size / 3) {
                val l = kotlin.math.sqrt(n[i * 3] * n[i * 3] + n[i * 3 + 1] * n[i * 3 + 1] + n[i * 3 + 2] * n[i * 3 + 2])
                if (l > 0f) { n[i * 3] /= l; n[i * 3 + 1] /= l; n[i * 3 + 2] /= l }
            }
            // Winding decides which way the normals point: make sure they face outwards
            // (the top of the head must point up).
            val top = (0 until pos.size / 3).maxBy { pos[it * 3 + 1] }
            if (n[top * 3 + 1] < 0f) for (i in n.indices) n[i] = -n[i]
            return n
        }
    }
}
