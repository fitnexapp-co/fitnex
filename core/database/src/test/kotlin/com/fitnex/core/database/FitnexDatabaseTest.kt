package com.fitnex.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fitnex.core.database.Fixtures.T0
import com.fitnex.core.database.Fixtures.USER
import com.fitnex.core.model.AnatomicalRegion
import com.fitnex.core.model.MuscleInvolvement
import com.fitnex.core.model.SessionStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FitnexDatabaseTest {
    private lateinit var db: FitnexDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            FitnexDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun deletingRoutineKeepsTrainingHistory() = runTest {
        db.workoutDao().upsertWorkout(Fixtures.workout("w1", "Push day"))
        db.sessionDao().upsertSession(Fixtures.session("s1", workoutId = "w1"))
        db.sessionDao().upsertSet(Fixtures.set("set1", "s1", "bench_press", 1, 10, 20f))

        db.workoutDao().hardDelete("w1")

        val restored = db.sessionDao().observeSessionWithSets("s1").first()
        assertNotNull(restored)
        assertNull(restored!!.session.workoutId) // FK SET_NULL
        assertEquals("Push day", restored.session.workoutNameSnapshot)
        assertEquals(1, restored.sets.size)
        assertEquals(20f, restored.sets.single().weightKg)
    }

    @Test
    fun softDeletedRoutineIsHiddenButPendingSync() = runTest {
        db.workoutDao().upsertWorkout(Fixtures.workout("w1", "Legs"))
        db.workoutDao().softDelete("w1", T0.plusSeconds(60))
        assertEquals(0, db.workoutDao().observeWorkouts(USER).first().size)
    }

    @Test
    fun unfinishedSessionIsRestoredAfterRestart() = runTest {
        db.sessionDao().upsertSession(Fixtures.session("old", null, SessionStatus.FINISHED))
        db.sessionDao().upsertSession(
            Fixtures.session("live", null, SessionStatus.IN_PROGRESS, startedAt = T0.plusSeconds(7200)),
        )
        assertEquals("live", db.sessionDao().observeActiveSession(USER).first()?.id)
    }

    @Test
    fun muscleWorkloadUsesExerciseMuscleAssociation() = runTest {
        val catalog = db.catalogDao()
        catalog.upsertMuscles(
            listOf(
                Fixtures.muscle("pectoralis_major", AnatomicalRegion.CHEST),
                Fixtures.muscle("triceps_brachii", AnatomicalRegion.ARMS),
            ),
        )
        catalog.upsertExercises(listOf(Fixtures.exercise("bench_press")))
        catalog.upsertExerciseMuscles(
            listOf(
                Fixtures.link("bench_press", "pectoralis_major", MuscleInvolvement.PRIMARY),
                Fixtures.link("bench_press", "triceps_brachii", MuscleInvolvement.SECONDARY),
            ),
        )
        db.sessionDao().upsertSession(Fixtures.session("s1", null))
        repeat(3) { i -> db.sessionDao().upsertSet(Fixtures.set("set$i", "s1", "bench_press", i + 1, 10, 25f)) }

        val counts = db.sessionDao()
            .observeMuscleSetCounts(USER, T0.minusSeconds(1), T0.plusSeconds(1))
            .first()
            .associateBy { it.muscleId }

        assertEquals(3, counts.getValue("pectoralis_major").setCount)
        assertEquals(MuscleInvolvement.PRIMARY, counts.getValue("pectoralis_major").involvement)
        assertEquals(MuscleInvolvement.SECONDARY, counts.getValue("triceps_brachii").involvement)
        assertEquals(1, counts.getValue("triceps_brachii").sessionCount)
    }

    @Test
    fun exerciseHistoryIsOrderedAndExcludesDeletedSets() = runTest {
        db.sessionDao().upsertSession(Fixtures.session("s1", null))
        db.sessionDao().upsertSet(Fixtures.set("a", "s1", "squat", 1, 8, 60f, T0))
        db.sessionDao().upsertSet(Fixtures.set("b", "s1", "squat", 2, 8, 65f, T0.plusSeconds(120)))
        db.sessionDao().upsertSet(Fixtures.set("c", "s1", "squat", 3, 6, 70f, T0.plusSeconds(240)))
        db.sessionDao().softDeleteSet("c", T0.plusSeconds(300))

        val history = db.sessionDao().observeExerciseHistory(USER, "squat").first()
        assertEquals(listOf("a", "b"), history.map { it.id })
    }
}
