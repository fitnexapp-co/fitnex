package com.fitnex.feature.workout

import com.fitnex.core.model.ActiveSession
import com.fitnex.core.model.LoggedSet
import com.fitnex.core.model.PreviousPerformance
import com.fitnex.core.model.RoutineExercise
import com.fitnex.core.model.SetSource
import com.fitnex.feature.workout.live.DraftLogic
import com.fitnex.feature.workout.live.RecordLogic
import com.fitnex.feature.workout.live.RestTimer
import com.fitnex.feature.workout.live.SetDraft
import com.fitnex.feature.workout.live.formatClock
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveLogicTest {
    private val t0 = Instant.parse("2026-11-19T10:00:00Z")

    @Test
    fun restCountsDownByWallClockAndPausesExactly() {
        var r = RestTimer.start(90, t0)
        assertEquals(90, r.remaining(t0))
        assertEquals(60, r.remaining(t0.plusSeconds(30)))
        assertEquals(61, r.remaining(t0.plusMillis(29_500))) // 60.5 s left rounds UP: never shows 0 early

        r = r.pause(t0.plusSeconds(30))
        assertTrue(r.isPaused)
        assertEquals(60, r.remaining(t0.plusSeconds(500))) // frozen while paused
        r = r.resume(t0.plusSeconds(500))
        assertEquals(50, r.remaining(t0.plusSeconds(510)))
        assertFalse(r.isFinished(t0.plusSeconds(559)))
        assertTrue(r.isFinished(t0.plusSeconds(560)))
    }

    @Test
    fun addingTimeNeverGoesNegativeAndGrowsTheRing() {
        val r = RestTimer.start(30, t0)
        val plus = r.add(15, t0)
        assertEquals(45, plus.remaining(t0))
        assertEquals(45, plus.totalSeconds)
        assertEquals(0, r.add(-60, t0).remaining(t0))
    }

    private val bench = RoutineExercise("re1", "bench", "Press", 3, 10, 90, 20f)
    private fun session(sets: List<LoggedSet>) = ActiveSession("s", "r", "R", t0, 0, null, 0, listOf(bench), sets)
    private fun set(n: Int, reps: Int, kg: Float, session: String = "s") = LoggedSet("$session$n", session, "bench", "Press", 0, n, reps, kg, t0, SetSource.MANUAL)

    @Test
    fun draftPrefersThisSessionThenLastTimeThenRoutineTargets() {
        // Nothing done, no history → routine targets.
        assertEquals(SetDraft(10, 20f), DraftLogic.nextDraft(bench, session(emptyList()), null))
        // History: same set number from last time.
        val prev = PreviousPerformance(t0.minusSeconds(86_400), listOf(set(1, 10, 25f, "old"), set(2, 8, 27.5f, "old")))
        assertEquals(SetDraft(10, 25f), DraftLogic.nextDraft(bench, session(emptyList()), prev))
        // Did set 1 today at 30 kg → follow today's values, not history.
        assertEquals(SetDraft(9, 30f), DraftLogic.nextDraft(bench, session(listOf(set(1, 9, 30f))), prev))
    }

    @Test
    fun allSetsCompletedLastTimeProposesALittleMoreWeight() {
        // 3 × 10 planned; last time 3 × 10 at 40 kg → +2.5 kg on the first set.
        val done = PreviousPerformance(t0.minusSeconds(86_400), listOf(set(1, 10, 40f, "old"), set(2, 10, 40f, "old"), set(3, 11, 40f, "old")))
        assertEquals(SetDraft(10, 42.5f, increaseKg = 2.5f), DraftLogic.nextDraft(bench, session(emptyList()), done))
        // Light dumbbells: +1 kg.
        val light = PreviousPerformance(t0, listOf(set(1, 10, 6f, "old"), set(2, 10, 6f, "old"), set(3, 10, 6f, "old")))
        assertEquals(7f, DraftLogic.nextDraft(bench, session(emptyList()), light).weightKg)
        // One set short of the reps → same weight, no proposal.
        val missed = PreviousPerformance(t0, listOf(set(1, 10, 40f, "old"), set(2, 9, 40f, "old"), set(3, 10, 40f, "old")))
        assertEquals(SetDraft(10, 40f), DraftLogic.nextDraft(bench, session(emptyList()), missed))
        // Body weight: never adds kilos.
        val bw = PreviousPerformance(t0, listOf(set(1, 10, 0f, "old"), set(2, 10, 0f, "old"), set(3, 10, 0f, "old")))
        assertEquals(null, DraftLogic.nextDraft(bench, session(emptyList()), bw).increaseKg)
    }

    @Test
    fun recordIsDetectedAgainstHistoryAndTodaysSets() {
        val history = listOf(set(1, 10, 40f, "old"))
        val r = RecordLogic.recordFor(bench, SetDraft(8, 45f), emptyList(), history)!!
        assertEquals(com.fitnex.core.model.RecordType.MAX_WEIGHT, r.type)
        assertEquals(45f, r.value)
        assertEquals(40f, r.previous)
        // Already lifted 45 today: a second 45 is not a new record.
        assertEquals(null, RecordLogic.recordFor(bench, SetDraft(8, 45f), listOf(set(1, 8, 45f)), history))
        // First time ever: nothing to beat, no record.
        assertEquals(null, RecordLogic.recordFor(bench, SetDraft(8, 45f), emptyList(), emptyList()))
    }

    @Test
    fun timedAndCardioDraftsUseTheRoutineTargetAsTime() {
        val plank = RoutineExercise("re2", "plank", "Plancha", 3, 45, 60, null) // 45 s
        assertEquals(SetDraft(0, 0f, durationSeconds = 45), DraftLogic.nextDraft(plank, session(emptyList()), null, com.fitnex.core.model.Tracking.TIME))
        val run = RoutineExercise("re3", "run", "Carrera", 1, 20, 0, null) // 20 min
        assertEquals(
            SetDraft(0, 0f, durationSeconds = 1_200, distanceMeters = 0f),
            DraftLogic.nextDraft(run, session(emptyList()), null, com.fitnex.core.model.Tracking.TIME_DISTANCE),
        )
        // Last time: 25 min and 4,5 km → proposed again.
        val last = LoggedSet("old1", "old", "run", "Carrera", 0, 1, 0, 0f, t0, SetSource.MANUAL, durationSeconds = 1_500, distanceMeters = 4_500f)
        assertEquals(
            SetDraft(0, 0f, durationSeconds = 1_500, distanceMeters = 4_500f),
            DraftLogic.nextDraft(run, session(emptyList()), PreviousPerformance(t0, listOf(last)), com.fitnex.core.model.Tracking.TIME_DISTANCE),
        )
    }

    @Test
    fun clockFormat() {
        assertEquals("0:05", formatClock(5))
        assertEquals("12:34", formatClock(754))
        assertEquals("1:02:05", formatClock(3725))
    }
}
