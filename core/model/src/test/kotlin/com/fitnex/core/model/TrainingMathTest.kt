package com.fitnex.core.model

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrainingMathTest {
    private val t = Instant.parse("2026-11-19T10:00:00Z")
    private fun set(ex: String, n: Int, reps: Int, kg: Float, session: String = "s") =
        LoggedSet("$session-$ex-$n", session, ex, ex, 0, n, reps, kg, t, SetSource.MANUAL)

    @Test
    fun epleyOnlyForLoadedLowRepSets() {
        assertEquals(100f, TrainingMath.estimatedOneRepMax(100f, 1)!!, 0.01f)
        assertEquals(133.33f, TrainingMath.estimatedOneRepMax(100f, 10)!!, 0.01f)
        assertNull(TrainingMath.estimatedOneRepMax(0f, 10)) // body weight
        assertNull(TrainingMath.estimatedOneRepMax(60f, 20)) // too many reps to be meaningful
        assertNull(TrainingMath.estimatedOneRepMax(60f, 0))
    }

    @Test
    fun volumeIsRepsTimesKg() {
        assertEquals(10 * 20f + 8 * 25f, TrainingMath.volume(listOf(set("bench", 1, 10, 20f), set("bench", 2, 8, 25f))), 0.001f)
    }

    @Test
    fun firstTimeIsNeverARecord() {
        assertTrue(TrainingMath.newRecords(listOf(set("bench", 1, 10, 100f)), emptyList()).isEmpty())
    }

    @Test
    fun heavierWeightIsAWeightRecord() {
        val history = listOf(set("bench", 1, 10, 25f, "old"))
        val records = TrainingMath.newRecords(listOf(set("bench", 1, 8, 30f)), history)
        assertEquals(listOf(RecordType.MAX_WEIGHT), records.map { it.type })
        assertEquals(30f, records.single().value)
        assertEquals(25f, records.single().previous)
    }

    @Test
    fun sameWeightMoreRepsIsAnEstimated1RmRecord() {
        val history = listOf(set("bench", 1, 8, 30f, "old"))
        val records = TrainingMath.newRecords(listOf(set("bench", 1, 10, 30f)), history)
        assertEquals(listOf(RecordType.ESTIMATED_1RM), records.map { it.type })
    }

    @Test
    fun bodyWeightExercisesUseRepRecords() {
        val history = listOf(set("push_up", 1, 15, 0f, "old"))
        assertEquals(listOf(RecordType.MAX_REPS), TrainingMath.newRecords(listOf(set("push_up", 1, 18, 0f)), history).map { it.type })
        assertTrue(TrainingMath.newRecords(listOf(set("push_up", 1, 12, 0f)), history).isEmpty())
    }
}
