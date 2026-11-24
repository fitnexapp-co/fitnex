package com.fitnex.feature.workout

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.SavedStateHandle
import com.fitnex.core.auth.AuthUser
import com.fitnex.core.data.session.SessionRepository
import com.fitnex.core.data.training.ExerciseRepository
import com.fitnex.core.data.training.RoutineRepository
import com.fitnex.core.datastore.UserPreferencesDataSource
import com.fitnex.core.model.Difficulty
import com.fitnex.core.model.Exercise
import com.fitnex.core.model.ExerciseListItem
import com.fitnex.core.model.ExerciseType
import com.fitnex.core.model.Muscle
import com.fitnex.core.model.Routine
import com.fitnex.core.model.RoutineLimits
import com.fitnex.core.testing.FakeAuthRepository
import com.fitnex.core.testing.FakeUserProfileRepository
import com.fitnex.feature.workout.editor.RoutineEditorViewModel
import java.io.File
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class RoutineEditorViewModelTest {
    @get:Rule val tmp = TemporaryFolder()

    private val clock = Clock.fixed(Instant.parse("2026-11-19T10:00:00Z"), ZoneOffset.UTC)
    private val routines = InMemoryRoutines()
    private val auth = FakeAuthRepository().apply { signInDirectly(AuthUser("u1", "a@b.c", true)) }
    private var nextId = 0

    // One scheduler for Main, DataStore and the test body, so advanceUntilIdle() drains everything.
    private val dispatcher = UnconfinedTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.vm(routineId: String? = null, savedState: SavedStateHandle = SavedStateHandle(mapOf("routineId" to routineId))) =
        RoutineEditorViewModel(
            savedState,
            SessionRepository(auth, FakeUserProfileRepository(), com.fitnex.core.testing.FakeCloudSync()),
            routines,
            CatalogStub,
            UserPreferencesDataSource(PreferenceDataStoreFactory.create(scope = backgroundScope) { File(tmp.root, "p${nextId++}.preferences_pb") }),
            { "id-${nextId++}" },
            clock,
        )

    @Test
    fun newRoutineNeedsNameAndExercises() = runTest(dispatcher) {
        val vm = vm()
        vm.save()
        assertTrue(vm.state.value.nameError)
        assertTrue(vm.state.value.exercisesError)
        assertTrue(routines.saved.isEmpty())
    }

    @Test
    fun pickedExercisesAreAddedOnceWithDefaultRest() = runTest(dispatcher) {
        val vm = vm()
        vm.onExercisesPicked(listOf("bench_press", "push_up", "bench_press"))
        advanceUntilIdle()
        val items = vm.state.value.items
        assertEquals(listOf("bench_press", "push_up"), items.map { it.exerciseId }) // duplicates ignored
        assertEquals(90, items.first().restSeconds) // default from preferences
        assertEquals(RoutineLimits.DEFAULT_SETS, items.first().sets)
        vm.onExercisesPicked(listOf("push_up")) // already present
        advanceUntilIdle()
        assertEquals(2, vm.state.value.items.size)
    }
    @Test
    fun reorderConfigureAndSave() = runTest(dispatcher) {
        val vm = vm()
        advanceUntilIdle()
        vm.add(listOf("bench_press", "push_up"))
        vm.setName("  Empuje  ")
        vm.toggleDay(DayOfWeek.MONDAY)
        vm.move(1, -1)
        vm.move(0, -1) // out of range: ignored
        vm.configure(0, sets = 4, reps = 12, restSeconds = 100, weightKg = null)
        vm.configure(1, sets = 99, reps = 0, restSeconds = 90, weightKg = 42.5f)
        vm.save()

        val saved = routines.saved.single()
        assertEquals("Empuje", saved.name)
        assertEquals(setOf(DayOfWeek.MONDAY), saved.days)
        assertEquals(listOf("push_up", "bench_press"), saved.exercises.map { it.exerciseId })
        assertEquals(105, saved.exercises[0].restSeconds) // snapped to 15 s steps
        assertEquals(RoutineLimits.SETS.last, saved.exercises[1].sets) // clamped
        assertEquals(RoutineLimits.REPS.first, saved.exercises[1].reps)
        assertEquals(42.5f, saved.exercises[1].targetWeightKg)
        assertTrue(vm.state.value.finished)
    }

    @Test
    fun editingKeepsIdsAndDeleteRemoves() = runTest(dispatcher) {
        val first = vm()
        advanceUntilIdle()
        first.add(listOf("bench_press"))
        first.setName("Pecho")
        first.save()
        val stored = routines.saved.single()

        val edit = vm(routineId = stored.id)
        advanceUntilIdle()
        assertFalse(edit.state.value.isNew)
        assertEquals("Pecho", edit.state.value.name)
        edit.remove(0)
        edit.add(listOf("push_up"))
        edit.save()
        assertEquals(stored.id, routines.saved.last().id)
        assertEquals(stored.createdAt, routines.saved.last().createdAt)

        edit.delete()
        assertEquals(listOf(stored.id), routines.deleted)
    }

    private class InMemoryRoutines : RoutineRepository {
        val saved = mutableListOf<Routine>()
        val deleted = mutableListOf<String>()
        override fun observeRoutines(userId: String): Flow<List<Routine>> = flowOf(saved)
        override suspend fun getRoutine(id: String) = saved.lastOrNull { it.id == id }
        override suspend fun save(userId: String, routine: Routine) { saved += routine }
        override suspend fun delete(id: String) { deleted += id }
    }

    private object CatalogStub : ExerciseRepository {
        private fun ex(id: String, name: String) = Exercise(
            id, name, "", emptyList(), emptyList(), emptyList(), emptySet(), ExerciseType.STRENGTH,
            Difficulty.BEGINNER, null, false, false, emptyList(),
        )
        private val all = listOf(ex("bench_press", "Press de banca"), ex("push_up", "Flexiones"))
        override fun observeLibrary(userId: String) = MutableStateFlow(all.map { ExerciseListItem(it, emptyList(), false) })
        override fun observeExercise(id: String) = MutableStateFlow(all.firstOrNull { it.id == id })
        override fun observeMuscles(): Flow<List<Muscle>> = flowOf(emptyList())
        override fun observeIsFavorite(userId: String, exerciseId: String) = flowOf(false).map { it }
        override suspend fun setFavorite(userId: String, exerciseId: String, favorite: Boolean) = Unit
        override suspend fun getExercises(ids: List<String>) = ids.mapNotNull { id -> all.firstOrNull { it.id == id } }
    }
}
