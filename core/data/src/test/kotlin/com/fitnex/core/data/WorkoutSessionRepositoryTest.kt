package com.fitnex.core.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fitnex.core.data.catalog.AssetCatalogSource
import com.fitnex.core.data.catalog.CatalogSeeder
import com.fitnex.core.data.training.RoomWorkoutSessionRepository
import com.fitnex.core.database.FitnexDatabase
import com.fitnex.core.datastore.UserPreferencesDataSource
import com.fitnex.core.model.RecordType
import com.fitnex.core.model.Routine
import com.fitnex.core.model.RoutineExercise
import java.io.File
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

/** Clock the test can move forward. */
class MutableClock(var now: Instant) : Clock() {
    override fun instant(): Instant = now
    override fun getZone(): ZoneId = ZoneOffset.UTC
    override fun withZone(zone: ZoneId?): Clock = this
    fun advance(seconds: Long) { now = now.plusSeconds(seconds) }
}

@RunWith(AndroidJUnit4::class)
class WorkoutSessionRepositoryTest {
    @get:Rule val tmp = TemporaryFolder()

    private lateinit var db: FitnexDatabase
    private val clock = MutableClock(Instant.parse("2026-11-19T10:00:00Z"))
    private var nextId = 0
    private val user = "u1"
    private val bench = RoutineExercise("re1", "bench_press", "Press de banca", 3, 10, 90, 25f)
    private val pushUp = RoutineExercise("re2", "push_up", "Flexiones", 2, 15, 60, null)
    private val routine = Routine("r1", "Empuje", emptySet(), false, listOf(bench, pushUp), clock.now)

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), FitnexDatabase::class.java)
            .allowMainThreadQueries().build()
    }

    @After fun tearDown() = db.close()

    /** Sessions reference their routine (FK), exactly like in the app where a routine is started from the list. */
    private fun repo(): RoomWorkoutSessionRepository {
        kotlinx.coroutines.runBlocking {
            if (db.workoutDao().observeWorkout(routine.id).first() == null) {
                com.fitnex.core.data.training.RoomRoutineRepository(db.workoutDao(), db.catalogDao(), clock).save(user, routine)
            }
        }
        return RoomWorkoutSessionRepository(db.sessionDao(), db.catalogDao(), db.calendarDao(), { "id-${nextId++}" }, clock)
    }

    private suspend fun seedCatalog(scope: kotlinx.coroutines.CoroutineScope) = CatalogSeeder(
        AssetCatalogSource(ApplicationProvider.getApplicationContext()), db.catalogDao(),
        UserPreferencesDataSource(PreferenceDataStoreFactory.create(scope = scope) { File(tmp.root, "p.preferences_pb") }),
        clock,
    ).ensureSeeded()

    @Test
    fun startIsIdempotentAndSessionSurvivesRestartWithPlanAndSets() = runTest {
        val id = repo().start(user, routine)
        assertEquals("a second start resumes the active one", id, repo().start(user, routine))
        repo().logSet(id, bench, 0, 10, 25f)
        repo().setCurrentExercise(id, 1)

        // "Process death": a brand-new repository reads everything back from Room.
        val restored = repo().observeActive(user).first()!!
        assertEquals("Empuje", restored.routineName)
        assertEquals(listOf("bench_press", "push_up"), restored.plan.map { it.exerciseId })
        assertEquals(25f, restored.plan.first().targetWeightKg)
        assertEquals(1, restored.currentExerciseIndex)
        assertEquals(1, restored.sets.single().setNumber)
    }

    @Test
    fun pausesAreExcludedFromElapsedAndDuration() = runTest {
        seedCatalog(backgroundScope)
        val r = repo()
        val id = r.start(user, routine)
        clock.advance(600)
        r.pause(id)
        clock.advance(300) // paused: does not count
        assertEquals(Duration.ofMinutes(10), r.observeActive(user).first()!!.elapsed(clock.now))
        r.resume(id)
        clock.advance(120)
        r.logSet(id, bench, 0, 10, 25f)
        val summary = r.finish(id)!!
        assertEquals(Duration.ofMinutes(12), summary.duration)
        assertNull("no longer active", r.observeActive(user).first())
    }

    @Test
    fun finishingWithoutSetsDiscards() = runTest {
        val r = repo()
        val id = r.start(user, routine)
        assertNull(r.finish(id))
        assertNull(r.observeActive(user).first())
    }

    @Test
    fun summaryCountsVolumeRecordsMusclesAndPreviousPerformance() = runTest {
        seedCatalog(backgroundScope)
        val r = repo()
        // Earlier session: bench 2×10 @ 25 kg.
        val first = r.start(user, routine)
        r.logSet(first, bench, 0, 10, 25f)
        r.logSet(first, bench, 0, 10, 25f)
        r.finish(first)

        clock.advance(86_400)
        val second = r.start(user, routine)
        val previous = r.previousPerformance(user, "bench_press", clock.now)!!
        assertEquals(listOf(10, 10), previous.sets.map { it.reps })

        r.logSet(second, bench, 0, 8, 30f)
        val updated = r.logSet(second, bench, 0, 6, 30f)
        r.updateSet(updated.id, 7, 30f)
        r.logSet(second, pushUp, 1, 15, 0f)
        clock.advance(1800)
        val summary = r.finish(second)!!

        assertEquals(2, summary.exercisesCompleted)
        assertEquals(3, summary.sets)
        assertEquals(8 + 7 + 15, summary.totalReps)
        assertEquals(8 * 30f + 7 * 30f, summary.volumeKg!!, 0.01f)
        assertEquals(listOf(RecordType.MAX_WEIGHT), summary.records.map { it.type }) // push-ups: first time, no record
        assertTrue(summary.musclesWorked.first().id == "pectoralis_major")
        assertTrue(summary.musclesWorked.any { it.id == "triceps_brachii" })
        assertTrue("stabilisers excluded", summary.musclesWorked.none { it.id == "rectus_abdominis" })
    }

    @Test
    fun deletedSetIsNotCountedAndNumbersContinue() = runTest {
        seedCatalog(backgroundScope)
        val r = repo()
        val id = r.start(user, routine)
        val s1 = r.logSet(id, bench, 0, 10, 25f)
        r.deleteSet(s1.id)
        val s2 = r.logSet(id, bench, 0, 10, 25f)
        assertEquals(1, s2.setNumber)
        assertEquals(1, r.observeActive(user).first()!!.sets.size)
    }

    @Test
    fun deletingAMiddleSetKeepsTheNumbersContiguous() = runTest {
        seedCatalog(backgroundScope)
        val r = repo()
        val id = r.start(user, routine)
        r.logSet(id, bench, 0, 10, 25f)
        val middle = r.logSet(id, bench, 0, 9, 25f)
        r.logSet(id, bench, 0, 8, 25f)
        r.deleteSet(middle.id)
        r.logSet(id, bench, 0, 7, 25f)
        val sets = r.observeActive(user).first()!!.sets
        assertEquals("no repeated or missing numbers", listOf(1, 2, 3), sets.map { it.setNumber }.sorted())
        assertEquals(listOf(10, 8, 7), sets.sortedBy { it.setNumber }.map { it.reps })
    }
}
