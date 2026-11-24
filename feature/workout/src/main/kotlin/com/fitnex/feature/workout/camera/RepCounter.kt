package com.fitnex.feature.workout.camera

import kotlin.math.abs
import kotlin.math.atan2

/**
 * Camera rep counter (spec §7), pure logic: body landmarks → one joint angle → counted repetitions.
 * The pose model runs on the device (ML Kit); this file never sees images, only 2D points, so it is
 * fully unit-tested. Counts are always editable by the user before they are logged.
 */
enum class Joint {
    LEFT_SHOULDER, RIGHT_SHOULDER, LEFT_ELBOW, RIGHT_ELBOW, LEFT_WRIST, RIGHT_WRIST,
    LEFT_HIP, RIGHT_HIP, LEFT_KNEE, RIGHT_KNEE, LEFT_ANKLE, RIGHT_ANKLE,
}

/** A landmark in image coordinates with the model's in-frame likelihood (0..1). */
data class Landmark(val x: Float, val y: Float, val likelihood: Float)

/** The angle at [vertex] between [a] and [c], measured on each side of the body. */
data class AngleSpec(val a: Joint, val vertex: Joint, val c: Joint)

enum class SideMerge {
    /** Average both sides when visible (symmetric moves: push-up, squat, curl, press). */
    AVERAGE,
    /** The more bent side (lunges: the front leg does the work). */
    MIN,
}

/**
 * [startsHigh] = the rep starts with the joint open (angle ≥ [high]) and closes to ≤ [low]
 * (squat, push-up, curl). Otherwise it starts closed and opens (press, lateral raise, bridge).
 */
data class CameraExercise(
    val exerciseId: String,
    val left: AngleSpec,
    val right: AngleSpec,
    val low: Float,
    val high: Float,
    val startsHigh: Boolean,
    val merge: SideMerge = SideMerge.AVERAGE,
)

private val ELBOW_L = AngleSpec(Joint.LEFT_SHOULDER, Joint.LEFT_ELBOW, Joint.LEFT_WRIST)
private val ELBOW_R = AngleSpec(Joint.RIGHT_SHOULDER, Joint.RIGHT_ELBOW, Joint.RIGHT_WRIST)
private val KNEE_L = AngleSpec(Joint.LEFT_HIP, Joint.LEFT_KNEE, Joint.LEFT_ANKLE)
private val KNEE_R = AngleSpec(Joint.RIGHT_HIP, Joint.RIGHT_KNEE, Joint.RIGHT_ANKLE)
private val SHOULDER_L = AngleSpec(Joint.LEFT_HIP, Joint.LEFT_SHOULDER, Joint.LEFT_ELBOW)
private val SHOULDER_R = AngleSpec(Joint.RIGHT_HIP, Joint.RIGHT_SHOULDER, Joint.RIGHT_ELBOW)
private val HIP_L = AngleSpec(Joint.LEFT_SHOULDER, Joint.LEFT_HIP, Joint.LEFT_KNEE)
private val HIP_R = AngleSpec(Joint.RIGHT_SHOULDER, Joint.RIGHT_HIP, Joint.RIGHT_KNEE)

/** Exercises with a detector. Must match the catalogue's `camera: true` flags (checked by a test). */
object CameraExercises {
    val all: List<CameraExercise> = listOf(
        CameraExercise("push_up", ELBOW_L, ELBOW_R, low = 100f, high = 150f, startsHigh = true),
        CameraExercise("bodyweight_squat", KNEE_L, KNEE_R, low = 110f, high = 160f, startsHigh = true),
        CameraExercise("goblet_squat", KNEE_L, KNEE_R, low = 110f, high = 160f, startsHigh = true),
        CameraExercise("lunge", KNEE_L, KNEE_R, low = 115f, high = 155f, startsHigh = true, merge = SideMerge.MIN),
        CameraExercise("barbell_curl", ELBOW_L, ELBOW_R, low = 75f, high = 140f, startsHigh = true),
        CameraExercise("hammer_curl", ELBOW_L, ELBOW_R, low = 75f, high = 140f, startsHigh = true),
        CameraExercise("dumbbell_shoulder_press", ELBOW_L, ELBOW_R, low = 105f, high = 155f, startsHigh = false),
        CameraExercise("lateral_raise", SHOULDER_L, SHOULDER_R, low = 35f, high = 70f, startsHigh = false),
        CameraExercise("glute_bridge", HIP_L, HIP_R, low = 145f, high = 165f, startsHigh = false),
    )
    private val byId = all.associateBy { it.exerciseId }
    fun forExercise(id: String): CameraExercise? = byId[id]
}

/** Angle in degrees (0..180) at [b]. */
fun jointAngle(a: Landmark, b: Landmark, c: Landmark): Float {
    val ang1 = atan2(a.y - b.y, a.x - b.x)
    val ang2 = atan2(c.y - b.y, c.x - b.x)
    var deg = abs(Math.toDegrees((ang1 - ang2).toDouble())).toFloat()
    if (deg > 180f) deg = 360f - deg
    return deg
}

/** The exercise's angle for this frame, or null when the needed joints are not clearly visible. */
fun CameraExercise.measure(points: Map<Joint, Landmark>, minLikelihood: Float = 0.5f): Float? {
    fun side(s: AngleSpec): Float? {
        val a = points[s.a] ?: return null
        val b = points[s.vertex] ?: return null
        val c = points[s.c] ?: return null
        if (minOf(a.likelihood, b.likelihood, c.likelihood) < minLikelihood) return null
        return jointAngle(a, b, c)
    }
    val l = side(left)
    val r = side(right)
    return when {
        l != null && r != null -> if (merge == SideMerge.MIN) minOf(l, r) else (l + r) / 2f
        else -> l ?: r
    }
}

enum class RepPhase {
    /** Not yet in the start position: nothing is counted until the user gets there. */
    GET_READY,
    /** In the start position, about to move. */
    START,
    /** Reached the turning point; returning to start completes a rep. */
    TURN,
}

data class RepState(
    val count: Int = 0,
    val phase: RepPhase = RepPhase.GET_READY,
    /** False while the body (the needed joints) is not visible. */
    val tracking: Boolean = false,
    /** 0 = start position, 1 = turning point; drives the on-screen gauge. */
    val progress: Float = 0f,
)

/**
 * Hysteresis state machine on a smoothed angle: a rep needs a full trip start → turn → start, so
 * jitter around one threshold never counts, and half reps never count.
 */
class RepCounter(private val exercise: CameraExercise, private val minRepMillis: Long = 400L, private val lostAfterMillis: Long = 1_500L) {
    var state: RepState = RepState()
        private set
    private var smoothed: Float? = null
    private var lastSeen: Long = Long.MIN_VALUE
    private var leftStartAt: Long = 0L

    fun reset() {
        state = RepState(tracking = state.tracking)
        smoothed = null
        leftStartAt = 0L
    }

    /** Feeds one frame's angle (null = joints not visible) taken at [timeMillis]. */
    fun onFrame(angle: Float?, timeMillis: Long): RepState {
        if (angle == null) {
            if (lastSeen != Long.MIN_VALUE && timeMillis - lastSeen > lostAfterMillis) state = state.copy(tracking = false)
            return state
        }
        lastSeen = timeMillis
        val s = smoothed?.let { it + ALPHA * (angle - it) } ?: angle
        smoothed = s
        val atStart = if (exercise.startsHigh) s >= exercise.high else s <= exercise.low
        val atTurn = if (exercise.startsHigh) s <= exercise.low else s >= exercise.high
        val span = exercise.high - exercise.low
        val progress = (if (exercise.startsHigh) (exercise.high - s) / span else (s - exercise.low) / span).coerceIn(0f, 1f)

        var count = state.count
        val phase = when (state.phase) {
            RepPhase.GET_READY -> if (atStart) RepPhase.START else RepPhase.GET_READY
            RepPhase.START -> if (atTurn) { leftStartAt = timeMillis; RepPhase.TURN } else RepPhase.START
            RepPhase.TURN -> if (atStart) {
                if (timeMillis - leftStartAt >= minRepMillis / 2) count++
                RepPhase.START
            } else {
                RepPhase.TURN
            }
        }
        state = RepState(count, phase, tracking = true, progress = progress)
        return state
    }

    private companion object {
        /** Exponential smoothing: steady enough to ignore landmark jitter, fast enough for quick reps. */
        const val ALPHA = 0.45f
    }
}
