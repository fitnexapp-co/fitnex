package com.fitnex.feature.workout

import com.fitnex.core.model.RoutineExercise
import com.fitnex.core.model.Tracking
import com.fitnex.feature.workout.library.MuscleGroup
import org.junit.Assert.assertEquals
import org.junit.Test

class RoutineEstimateTest {
    @Test
    fun minutesCountWorkRestAndChangeOverRoundedUpToFive() {
        // 3 × 40 s + 2 × 90 s + 60 s = 360 s → 6 min → 10.
        val bench = RoutineExercise("1", "bench_press", "", 3, 10, 90, null)
        assertEquals(10, RoutineEstimate.minutes(listOf(bench), mapOf("bench_press" to Tracking.WEIGHT_REPS)))
        // Running 1 × 20 min: 1200 + 60 = 1260 s → 21 min → 25.
        val run = RoutineExercise("2", "running", "", 1, 20, 60, null)
        assertEquals(25, RoutineEstimate.minutes(listOf(run), mapOf("running" to Tracking.TIME_DISTANCE)))
        assertEquals(0, RoutineEstimate.minutes(emptyList(), emptyMap()))
    }

    @Test
    fun mainGroupsAreTheMostTrainedFirst() {
        val groups = RoutineEstimate.mainGroups(
            listOf(listOf("pectoralis_major"), listOf("pectoralis_major"), listOf("deltoid_anterior"), listOf("triceps_brachii"), listOf("quadriceps")),
        )
        assertEquals(listOf(MuscleGroup.CHEST, MuscleGroup.SHOULDERS, MuscleGroup.TRICEPS), groups)
    }
}
