package com.fitnex.feature.workout.camera

import kotlin.math.cos
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RepCounterTest {
    private val squat = CameraExercises.forExercise("bodyweight_squat")!!
    private val press = CameraExercises.forExercise("dumbbell_shoulder_press")!!

    /** Feeds a smooth trip between two angles, 30 fps. */
    private fun RepCounter.sweep(from: Float, to: Float, startMs: Long, frames: Int = 15): Long {
        var t = startMs
        for (i in 0..frames) {
            onFrame(from + (to - from) * i / frames, t)
            t += 33
        }
        return t
    }

    @Test
    fun countsFullSquatsOnly() {
        val c = RepCounter(squat)
        var t = c.sweep(170f, 172f, 0)               // standing: armed
        repeat(3) { t = c.sweep(172f, 90f, t); t = c.sweep(90f, 172f, t) } // 3 full squats
        t = c.sweep(172f, 140f, t); t = c.sweep(140f, 172f, t)             // half squat: not counted
        assertEquals(3, c.state.count)
        assertEquals(RepPhase.START, c.state.phase)
    }

    @Test
    fun nothingCountsBeforeReachingTheStartPosition() {
        val c = RepCounter(squat)
        var t = c.sweep(120f, 90f, 0)    // starts already down
        t = c.sweep(90f, 130f, t)        // never stands fully
        assertEquals(0, c.state.count)
        assertEquals(RepPhase.GET_READY, c.state.phase)
    }

    @Test
    fun jitterAroundAThresholdIsIgnored() {
        val c = RepCounter(squat)
        var t = c.sweep(170f, 172f, 0)
        repeat(20) { i -> c.onFrame(if (i % 2 == 0) 158f else 162f, t); t += 33 } // hovering near "high"
        assertEquals(0, c.state.count)
    }

    @Test
    fun pressStartsLowAndCountsOnReturn() {
        val c = RepCounter(press)
        var t = c.sweep(90f, 92f, 0)     // dumbbells at shoulders
        t = c.sweep(92f, 170f, t)        // up
        assertEquals(0, c.state.count)   // not finished yet
        t = c.sweep(170f, 90f, t)        // back down
        assertEquals(1, c.state.count)
    }

    @Test
    fun losingTheBodyStopsTrackingWithoutLosingTheCount() {
        val c = RepCounter(squat)
        var t = c.sweep(172f, 172f, 0)
        t = c.sweep(172f, 90f, t); t = c.sweep(90f, 172f, t)
        c.onFrame(null, t + 2_000)
        assertFalse(c.state.tracking)
        assertEquals(1, c.state.count)
        c.reset()
        assertEquals(0, c.state.count)
    }

    @Test
    fun angleGeometryAndVisibility() {
        val origin = Landmark(0f, 0f, 1f)
        assertEquals(90f, jointAngle(Landmark(1f, 0f, 1f), origin, Landmark(0f, 1f, 1f)), 0.01f)
        assertEquals(180f, jointAngle(Landmark(-1f, 0f, 1f), origin, Landmark(1f, 0f, 1f)), 0.01f)

        // A knee bent to 100° on the left; the right side is hidden.
        val a = Math.toRadians(100.0)
        val pts = mapOf(
            Joint.LEFT_HIP to Landmark(0f, -1f, 0.9f),
            Joint.LEFT_KNEE to Landmark(0f, 0f, 0.9f),
            Joint.LEFT_ANKLE to Landmark((sin(a)).toFloat(), (-cos(a)).toFloat(), 0.9f),
            Joint.RIGHT_HIP to Landmark(0f, 0f, 0.1f),
        )
        assertEquals(100f, squat.measure(pts)!!, 0.5f)
        assertNull(squat.measure(pts.mapValues { it.value.copy(likelihood = 0.2f) }))
    }

    @Test
    fun everyDetectorHasSaneThresholds() {
        CameraExercises.all.forEach { assertTrue(it.exerciseId, it.low < it.high) }
    }
}
