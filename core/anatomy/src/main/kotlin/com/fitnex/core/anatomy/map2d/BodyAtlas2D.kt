package com.fitnex.core.anatomy.map2d

import com.fitnex.core.anatomy.geometry.P2
import com.fitnex.core.anatomy.geometry.pointInPolygon
import com.fitnex.core.anatomy.geometry.polygonArea
import kotlin.math.cos
import kotlin.math.sin

enum class BodyView { FRONT, BACK }

/** One selectable region of a muscle in one view. A muscle has a left and a right shape. */
data class MuscleShape(val muscleId: String, val view: BodyView, val polygon: List<P2>)

/**
 * Hand-drawn 2D anatomy chart (spec §5 "Mapa muscular 2D") in a 200 × 440 space, midline at x = 100,
 * about eight heads tall. Muscles cover the body and meet each other, as on an anatomy chart; what is
 * left uncovered (head, neck, joints, hands, feet, shins) is the "secondary zone". Each muscle is
 * outlined on the figure's left half and mirrored. Ids are the catalog's muscle ids
 * (Muscle.id = model2DIdentifier). Original artwork, stylised but proportioned like a real body.
 */
object BodyAtlas2D {
    const val VIEW_WIDTH = 200f
    const val VIEW_HEIGHT = 440f
    private const val MID = 100f

    // ------------------------------------------------------------------ silhouette

    internal val torsoHalf = pts(
        100, 72, 88, 73, 76, 78, 62, 83, 52, 87, 46, 95, 45, 108, 50, 120, 57, 128, 60, 144, 62, 162,
        66, 180, 68, 196, 66, 212, 65, 228, 70, 244, 100, 249,
    )
    private val armLeft = pts(
        56, 84, 46, 88, 40, 97, 37, 112, 38, 128, 38, 146, 38, 162, 39, 178, 35, 194, 31, 214, 30, 232,
        32, 243, 43, 243, 48, 226, 53, 206, 55, 190, 55, 178, 57, 160, 58, 140, 57, 124, 55, 108, 59, 94,
    )
    // Relaxed hand: palm, thumb towards the body, fingers slightly apart at the tips.
    private val handLeft = pts(
        32, 240, 43, 240, 46, 246, 49, 253, 50, 260, 47, 261, 44, 256, 44, 266, 42, 276, 40, 279,
        38, 277, 36, 281, 34, 279, 31, 280, 29, 276, 28, 268, 27, 258, 28, 248,
    )
    internal val legLeft = pts(
        64, 224, 100, 244, 99, 262, 97, 290, 95, 318, 94, 334, 95, 352, 94, 372, 91, 396, 90, 410,
        79, 410, 76, 396, 72, 372, 70, 352, 71, 336, 68, 318, 65, 292, 62, 264, 62, 242,
    )
    private val footLeft = pts(79, 406, 91, 406, 94, 420, 93, 432, 72, 433, 70, 426, 75, 417)
    internal val neck = pts(88, 50, 112, 50, 115, 66, 118, 78, 82, 78, 85, 66)
    // Human head: rounded cranium, small ears, jaw tapering to the chin (instead of a plain oval).
    private val headHalf = pts(
        100, 8, 92, 9, 86, 13, 83, 19, 82, 27, 82, 32, 79, 31, 78, 35, 79, 41, 82, 43, 84, 48, 87, 53, 92, 57, 100, 59,
    )
    private val head = headHalf + mirror(headHalf).reversed().drop(1).dropLast(1)

    /** Non-muscle body, drawn first in the "secondary zone" colour. */
    val silhouette: List<List<P2>> = listOf(
        head, neck, torsoHalf + mirror(torsoHalf).reversed(),
        armLeft, mirror(armLeft), handLeft, mirror(handLeft),
        legLeft, mirror(legLeft), footLeft, mirror(footLeft),
    )

    // ------------------------------------------------------------------ muscles (figure's left half)

    private val forearm = pts(39, 182, 49, 179, 54, 190, 53, 206, 48, 224, 43, 238, 35, 240, 31, 232, 32, 212, 35, 196)

    private val front: List<Pair<String, List<P2>>> = listOf(
        "trapezius" to pts(89, 58, 88, 70, 80, 77, 68, 82, 57, 85, 66, 79, 78, 72),
        "pectoralis_major" to pts(99, 88, 99, 135, 92, 137, 80, 137, 69, 131, 61, 121, 58, 110, 60, 100, 66, 92, 80, 88),
        "obliques" to pts(86, 140, 85, 162, 85, 190, 87, 212, 90, 230, 82, 226, 72, 212, 68, 196, 66, 178, 63, 160, 61, 145, 66, 137, 76, 139),
        "rectus_abdominis" to pts(99, 136, 99, 233, 94, 235, 89, 222, 87, 200, 86, 172, 87, 148, 92, 138),
        "deltoid_anterior" to pts(66, 85, 55, 84, 50, 95, 47, 109, 46, 123, 51, 129, 56, 121, 60, 108, 64, 96),
        "deltoid_lateral" to pts(55, 84, 47, 87, 42, 96, 39, 109, 40, 121, 46, 124, 47, 109, 50, 95),
        "biceps_brachii" to pts(46, 127, 52, 131, 55, 141, 55, 157, 53, 172, 47, 179, 42, 172, 40, 154, 41, 138),
        "forearm" to forearm,
        // Lateral head of the triceps, visible on the outer arm from the front.
        "triceps_brachii" to pts(41, 128, 44, 131, 42, 150, 41, 168, 38, 160, 37, 142),
        "quadriceps" to pts(65, 234, 74, 240, 82, 252, 90, 262, 94, 280, 94, 300, 92, 318, 88, 328, 80, 330, 73, 324, 69, 310, 66, 290, 63, 264),
        "adductors" to pts(92, 254, 99, 249, 99, 262, 97, 282, 95, 300, 94, 282, 92, 266),
        // Drawn after the quadriceps: the hip flexors sit over the top of the thigh.
        "hip_flexors" to pts(80, 226, 90, 234, 95, 246, 91, 258, 84, 254, 77, 240),
        "calves" to pts(72, 340, 76, 347, 77, 366, 75, 384, 72, 371, 70, 354),
    )

    /** The inner (medial) calf also shows from the front. */
    private val frontExtra: List<Pair<String, List<P2>>> = listOf(
        "calves" to pts(94, 342, 96, 356, 95, 372, 92, 385, 91, 366, 92, 350),
    )

    private val back: List<Pair<String, List<P2>>> = listOf(
        "trapezius" to pts(99, 56, 99, 170, 94, 156, 88, 134, 82, 114, 72, 98, 58, 88, 68, 82, 82, 74, 90, 64),
        "latissimus_dorsi" to pts(60, 124, 72, 122, 84, 130, 92, 150, 97, 176, 96, 200, 88, 206, 76, 198, 68, 180, 64, 160, 60, 140),
        "obliques" to pts(62, 168, 68, 182, 70, 198, 67, 214, 64, 198, 63, 182),
        "erector_spinae" to pts(99, 150, 99, 232, 94, 234, 90, 214, 90, 186, 93, 160),
        "deltoid_posterior" to pts(60, 88, 50, 89, 44, 98, 42, 112, 44, 124, 50, 127, 54, 114, 60, 101, 67, 94),
        "deltoid_lateral" to pts(52, 85, 45, 89, 40, 99, 38, 111, 40, 121, 42, 109, 46, 97),
        "triceps_brachii" to pts(44, 125, 52, 129, 56, 141, 56, 158, 53, 174, 47, 180, 41, 174, 39, 154, 40, 136),
        "forearm" to forearm,
        "gluteus_medius" to pts(67, 206, 80, 203, 90, 211, 91, 222, 78, 227, 65, 224),
        "gluteus_maximus" to pts(65, 226, 78, 227, 91, 222, 99, 228, 99, 270, 92, 280, 80, 283, 70, 277, 64, 258),
        "hamstrings" to pts(66, 285, 80, 285, 96, 281, 97, 300, 95, 320, 92, 332, 84, 334, 74, 330, 69, 314, 66, 298),
        "calves" to pts(71, 340, 80, 334, 90, 334, 96, 342, 96, 362, 92, 382, 86, 394, 80, 392, 74, 380, 70, 360),
        // Under the trapezius, between the shoulder blades: drawn last so it stays tappable.
        "rhomboids" to pts(97, 94, 97, 142, 88, 135, 80, 115, 85, 100),
    )

    /** Every selectable shape, in drawing order (later = on top). */
    val shapes: List<MuscleShape> =
        (front + frontExtra).flatMap { (id, p) -> listOf(MuscleShape(id, BodyView.FRONT, p), MuscleShape(id, BodyView.FRONT, mirror(p))) } +
            back.flatMap { (id, p) -> listOf(MuscleShape(id, BodyView.BACK, p), MuscleShape(id, BodyView.BACK, mirror(p))) }

    // ------------------------------------------------------------------ anatomical detail lines

    private val frontLinesHalf: List<List<P2>> = listOf(
        pts(87, 160, 99, 158), pts(87, 182, 99, 181), pts(88, 204, 99, 204), // rectus intersections
        pts(66, 112, 76, 120, 88, 124), // lower pec fibres
        pts(79, 258, 82, 290, 85, 324), pts(88, 266, 91, 300), // vastus separations
        pts(62, 148, 70, 150), pts(63, 158, 71, 160), pts(65, 168, 72, 170), // serratus digitations
        pts(76, 132, 82, 128), // pec–ribcage edge
    )
    private val backLinesHalf: List<List<P2>> = listOf(
        pts(72, 104, 80, 116, 86, 130), // scapula edge
        pts(85, 340, 85, 380), // calf heads
        pts(72, 256, 84, 262, 97, 262), // gluteal fold
        pts(80, 290, 82, 330), // biceps femoris / semitendinosus
        pts(47, 146, 52, 160), // triceps heads
    )

    /** Decorative lines (no hit testing): abdominal segments, muscle heads, folds. */
    fun detailLines(view: BodyView): List<List<P2>> {
        val half = if (view == BodyView.FRONT) frontLinesHalf else backLinesHalf
        return half + half.map { mirror(it) }
    }

    fun shapes(view: BodyView): List<MuscleShape> = shapes.filter { it.view == view }

    /** Muscle ids drawn in a view. */
    fun muscleIds(view: BodyView): Set<String> = shapes(view).mapTo(linkedSetOf()) { it.muscleId }

    /** The view where a muscle is best seen (its larger area), for "show me this muscle". */
    fun preferredView(muscleId: String): BodyView =
        BodyView.entries.maxBy { v -> shapes(v).filter { it.muscleId == muscleId }.sumOf { polygonArea(it.polygon).toDouble() } }

    /**
     * Muscle under a point in map coordinates, or null on the silhouette / background.
     * Between overlapping shapes the smaller one wins (a muscle drawn over another), then the one on top.
     */
    fun hitTest(view: BodyView, x: Float, y: Float): String? =
        shapes(view).withIndex()
            .filter { pointInPolygon(x, y, it.value.polygon) }
            .minWithOrNull(compareBy<IndexedValue<MuscleShape>> { polygonArea(it.value.polygon) }.thenByDescending { it.index })
            ?.value?.muscleId

    // ------------------------------------------------------------------ helpers

    private fun pts(vararg v: Int): List<P2> = v.toList().chunked(2) { (x, y) -> P2(x.toFloat(), y.toFloat()) }
    private fun mirror(p: List<P2>) = p.map { P2(2 * MID - it.x, it.y) }
    private fun ellipse(cx: Float, cy: Float, rx: Float, ry: Float, n: Int = 32) =
        (0 until n).map { i -> val a = 2 * Math.PI * i / n; P2(cx + rx * cos(a).toFloat(), cy + ry * sin(a).toFloat()) }
}
