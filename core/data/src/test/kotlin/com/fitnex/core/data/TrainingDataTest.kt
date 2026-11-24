package com.fitnex.core.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fitnex.core.data.catalog.AssetCatalogSource
import com.fitnex.core.data.catalog.CatalogSeeder
import com.fitnex.core.data.training.RoomExerciseRepository
import com.fitnex.core.data.training.RoomRoutineRepository
import com.fitnex.core.database.FitnexDatabase
import com.fitnex.core.datastore.UserPreferencesDataSource
import com.fitnex.core.model.MuscleInvolvement
import com.fitnex.core.model.Routine
import com.fitnex.core.model.RoutineExercise
import java.io.File
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TrainingDataTest {
    @get:Rule val tmp = TemporaryFolder()

    private lateinit var db: FitnexDatabase
    private val clock = Clock.fixed(Instant.parse("2026-11-19T10:00:00Z"), ZoneOffset.UTC)
    private val user = "u1"

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), FitnexDatabase::class.java)
            .allowMainThreadQueries().build()
    }

    @After fun tearDown() = db.close()

    private fun TestScope.seeder() = CatalogSeeder(
        AssetCatalogSource(ApplicationProvider.getApplicationContext()),
        db.catalogDao(),
        UserPreferencesDataSource(PreferenceDataStoreFactory.create(scope = backgroundScope) { File(tmp.root, "p.preferences_pb") }),
        clock,
    )

    private val catalog by lazy { CatalogSeeder.parse(AssetCatalogSource(ApplicationProvider.getApplicationContext()).read()) }

    @Test
    fun expandedCatalogueHasUniqueNamesValidTrackingAndEnglishAliases() {
        val norm = { s: String -> java.text.Normalizer.normalize(s.lowercase(), java.text.Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").replace(Regex("[^a-z0-9]+"), " ").trim() }
        assertTrue("al menos 600 ejercicios", catalog.exercises.size >= 600)
        assertEquals("nombres sin duplicados", catalog.exercises.size, catalog.exercises.map { norm(it.name) }.toSet().size)
        val trackings = com.fitnex.core.model.Tracking.entries.map { it.name }.toSet()
        val equipment = com.fitnex.core.model.Equipment.entries.map { it.name }.toSet()
        val types = com.fitnex.core.model.ExerciseType.entries.map { it.name }.toSet()
        catalog.exercises.forEach { e ->
            assertTrue("${e.id}: tracking ${e.tracking}", e.tracking in trackings)
            assertTrue("${e.id}: tipo ${e.type}", e.type in types)
            assertTrue("${e.id}: material", e.equipment.isNotEmpty() && e.equipment.all { it in equipment })
            assertTrue("${e.id}: alias en inglés", e.aliases.isNotEmpty())
            assertTrue("${e.id}: errores comunes", e.mistakes.isNotEmpty())
        }
        // Cardio se registra por tiempo (y distancia cuando aplica).
        catalog.exercises.filter { it.type == "CARDIO" }.forEach { assertTrue(it.id, it.tracking == "TIME" || it.tracking == "TIME_DISTANCE") }
        catalog.exercises.filter { it.type == "STRETCHING" }.forEach { assertTrue(it.id, it.tracking == "TIME" || it.tracking == "REPS") }
        // Movilidad: tiempo o repeticiones; con carga (rotadores con mancuerna/polea) también kg × reps.
        catalog.exercises.filter { it.type == "MOBILITY" }.forEach { assertTrue(it.id, it.tracking != "TIME_DISTANCE") }
        // Los 63 originales siguen con sus ids (no se pierden rutinas ni historial).
        listOf("bench_press", "plank", "deadlift", "kettlebell_swing", "chest_dip").forEach { id -> assertTrue(id, catalog.exercises.any { it.id == id }) }
    }

    @Test
    fun catalogueIsConsistent() {
        val text = AssetCatalogSource(ApplicationProvider.getApplicationContext()).read()
        val catalog = CatalogSeeder.parse(text)
        val muscleIds = catalog.muscles.map { it.id }.toSet()
        assertEquals("muscle ids are unique", catalog.muscles.size, muscleIds.size)
        assertEquals("exercise ids are unique", catalog.exercises.size, catalog.exercises.map { it.id }.toSet().size)
        catalog.exercises.forEach { e ->
            assertTrue("${e.id} needs a primary muscle", e.primary.isNotEmpty())
            assertTrue("${e.id} needs instructions", e.instructions.size >= 2)
            (e.primary + e.secondary + e.stabilizer).forEach { assertTrue("${e.id} → unknown $it", it in muscleIds) }
        }
        // Every muscle is trained by at least one exercise, so no map region is a dead end.
        val used = catalog.exercises.flatMap { it.primary + it.secondary }.toSet()
        assertEquals(muscleIds, muscleIds.intersect(used))
        // Muscle.id is the model identifier on the maps: every catalog muscle must be drawable and
        // selectable in 2D, and the map must not draw muscles the catalog does not know. (The 3D body
        // takes its muscles from the 2D atlas; HumanBodyTest checks they match.)
        val in2D = com.fitnex.core.anatomy.map2d.BodyView.entries
            .flatMap { com.fitnex.core.anatomy.map2d.BodyAtlas2D.muscleIds(it) }.toSet()
        assertEquals(muscleIds, in2D)
        // Camera rep counting only where a joint-angle detector exists (spec §7; detectors in feature/workout/camera).
        assertEquals(
            setOf("push_up", "bodyweight_squat", "goblet_squat", "lunge", "barbell_curl", "hammer_curl", "dumbbell_shoulder_press", "lateral_raise", "glute_bridge"),
            catalog.exercises.filter { it.camera }.map { it.id }.toSet(),
        )
    }

    @Test
    fun seedingFillsRoomOnceAndLibraryShowsPrimaryMuscles() = runTest {
        val seeder = seeder()
        val repo = RoomExerciseRepository(db.catalogDao(), seeder, clock, { "x" })
        val library = repo.observeLibrary(user).first()
        assertEquals(catalog.exercises.size, library.size)
        val bench = library.first { it.exercise.id == "bench_press" }
        assertEquals(listOf("Pectoral mayor"), bench.primaryMuscles.map { it.name })
        assertTrue(bench.exercise.muscles.any { it.muscleId == "triceps_brachii" && it.involvement == MuscleInvolvement.SECONDARY })

        seeder.ensureSeeded() // second call: no duplicates
        assertEquals(catalog.exercises.size, db.catalogDao().builtInExerciseCount())
        assertEquals(20, repo.observeMuscles().first().size)
    }

    @Test
    fun customExercisesAreOwnedAndCanBeHidden() = runTest {
        val repo = RoomExerciseRepository(db.catalogDao(), seeder(), clock, { "x" })
        repo.observeLibrary(user).first()
        val id = repo.createCustom(user, com.fitnex.core.data.training.CustomExerciseDraft("Remo con toalla", "", emptySet(), com.fitnex.core.model.ExerciseType.STRENGTH, listOf("rhomboids")))
        val mine = repo.observeLibrary(user).first().first { it.exercise.id == id }
        assertTrue(mine.exercise.isCustom)
        assertEquals(listOf("rhomboids"), mine.exercise.primaryMuscleIds)
        assertFalse("other users never see it", repo.observeLibrary("other").first().any { it.exercise.id == id })
        repo.deleteCustom(id)
        assertFalse(repo.observeLibrary(user).first().any { it.exercise.id == id })
    }

    @Test
    fun favoritesToggle() = runTest {
        val repo = RoomExerciseRepository(db.catalogDao(), seeder(), clock, { "id-" + java.util.UUID.randomUUID() })
        repo.observeLibrary(user).first()
        repo.setFavorite(user, "push_up", true)
        assertTrue(repo.observeIsFavorite(user, "push_up").first())
        assertTrue(repo.observeLibrary(user).first().first { it.exercise.id == "push_up" }.isFavorite)
        repo.setFavorite(user, "push_up", false)
        assertFalse(repo.observeIsFavorite(user, "push_up").first())
        assertFalse("favourites are per user", repo.observeIsFavorite("other", "push_up").first())
    }

    @Test
    fun routineSaveReorderAndSoftDelete() = runTest {
        RoomExerciseRepository(db.catalogDao(), seeder(), clock, { "id-" + java.util.UUID.randomUUID() }).observeLibrary(user).first()
        val routines = RoomRoutineRepository(db.workoutDao(), db.catalogDao(), clock)
        val a = RoutineExercise("re1", "bench_press", "", 3, 10, 90, 40f)
        val b = RoutineExercise("re2", "push_up", "", 2, 15, 60, null)
        val routine = Routine("r1", "Empuje", setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY), false, listOf(a, b), clock.instant())
        routines.save(user, routine)

        val loaded = routines.getRoutine("r1")!!
        assertEquals(listOf("Press de banca", "Flexiones"), loaded.exercises.map { it.exerciseName })
        assertEquals(40f, loaded.exercises.first().targetWeightKg)

        routines.save(user, routine.copy(name = "Empuje A", exercises = listOf(b, a))) // reorder
        assertEquals(listOf("push_up", "bench_press"), routines.getRoutine("r1")!!.exercises.map { it.exerciseId })
        assertEquals(listOf("Empuje A"), routines.observeRoutines(user).first().map { it.name })

        routines.delete("r1")
        assertTrue(routines.observeRoutines(user).first().isEmpty())
        assertNull(routines.getRoutine("r1"))
    }
}
