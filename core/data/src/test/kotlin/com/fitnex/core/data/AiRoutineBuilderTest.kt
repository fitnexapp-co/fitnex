package com.fitnex.core.data

import com.fitnex.core.data.ai.AiResult
import com.fitnex.core.data.ai.AiRoutineBuilder
import com.fitnex.core.data.training.ExerciseRepository
import com.fitnex.core.data.training.RoutineRepository
import com.fitnex.core.model.AiFailure
import com.fitnex.core.model.Difficulty
import com.fitnex.core.model.Equipment
import com.fitnex.core.model.Exercise
import com.fitnex.core.model.ExerciseListItem
import com.fitnex.core.model.ExerciseType
import com.fitnex.core.model.Routine
import com.fitnex.core.model.RoutineGoal
import com.fitnex.core.model.RoutineRequest
import com.fitnex.core.network.ChatRequest
import com.fitnex.core.network.FitnexBackendApi
import com.fitnex.core.network.FoodPhotoRequest
import com.fitnex.core.network.RoutineExerciseDto
import com.fitnex.core.network.RoutineRequestDto
import com.fitnex.core.network.RoutineResponseDto
import com.fitnex.core.network.SyncPullRequest
import com.fitnex.core.network.SyncPushRequest
import com.fitnex.core.testing.FakeUserProfileRepository
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiRoutineBuilderTest {
    private val clock = Clock.fixed(Instant.parse("2026-11-20T10:00:00Z"), ZoneOffset.UTC)
    private val profiles = FakeUserProfileRepository()
    private val saved = mutableListOf<Routine>()
    private var nextId = 0

    private fun exercise(id: String, vararg equipment: Equipment) = ExerciseListItem(
        Exercise(id, id, "", emptyList(), emptyList(), emptyList(), equipment.toSet(), ExerciseType.STRENGTH, Difficulty.BEGINNER, null, false, false, emptyList()),
        emptyList(), false,
    )

    private val library = listOf(
        exercise("push_up", Equipment.BODYWEIGHT),
        exercise("bodyweight_squat", Equipment.BODYWEIGHT),
        exercise("plank", Equipment.BODYWEIGHT),
        exercise("bench_press", Equipment.BARBELL, Equipment.BENCH),
    )

    private val api = object : FitnexBackendApi {
        var lastRequest: RoutineRequestDto? = null
        override suspend fun routine(request: RoutineRequestDto): RoutineResponseDto {
            lastRequest = request
            return RoutineResponseDto(
                name = "Cuerpo completo", rationale = "Tres básicos con tu peso corporal.",
                exercises = listOf(
                    RoutineExerciseDto("push_up", 3, 12, 60),
                    RoutineExerciseDto("invented_move", 3, 10, 60),
                    RoutineExerciseDto("bodyweight_squat", 3, 15, 70),
                ),
            )
        }
        override suspend fun health() = error("unused")
        override suspend fun chat(request: ChatRequest) = error("unused")
        override suspend fun analyzeFood(request: FoodPhotoRequest) = error("unused")
        override suspend fun syncPush(request: SyncPushRequest) = error("unused")
        override suspend fun syncPull(request: SyncPullRequest) = error("unused")
        override suspend fun syncErase() = error("unused")
        override suspend fun plan(request: com.fitnex.core.network.PlanRequestDto) = error("unused")
    }

    private val exercises = object : ExerciseRepository {
        override fun observeLibrary(userId: String) = flowOf(library)
        override fun observeExercise(id: String) = emptyFlow<Exercise?>()
        override fun observeMuscles() = emptyFlow<List<com.fitnex.core.model.Muscle>>()
        override fun observeIsFavorite(userId: String, exerciseId: String) = flowOf(false)
        override suspend fun setFavorite(userId: String, exerciseId: String, favorite: Boolean) = Unit
        override suspend fun getExercises(ids: List<String>) = emptyList<Exercise>()
    }

    private val routines = object : RoutineRepository {
        override fun observeRoutines(userId: String) = flowOf(saved.toList())
        override suspend fun getRoutine(id: String) = saved.firstOrNull { it.id == id }
        override suspend fun save(userId: String, routine: Routine) { saved += routine }
        override suspend fun delete(id: String) = Unit
    }

    private val stats = object : com.fitnex.core.data.stats.GlobalStatisticsRepository {
        override fun observeStats(userId: String, period: com.fitnex.core.model.StatsPeriod) = emptyFlow<com.fitnex.core.model.GlobalStats>()
        override fun observeExerciseStats(userId: String, exerciseId: String) = emptyFlow<com.fitnex.core.model.ExerciseStats>()
        override fun observeCompletedSessions(userId: String) = flowOf(emptyList<com.fitnex.core.model.CompletedSession>())
    }

    private val builder = AiRoutineBuilder(api, profiles, exercises, routines, { "id-${nextId++}" }, clock, stats)

    private suspend fun user(consent: Boolean) {
        profiles.createAtSignUp("u1", null, LocalDate.of(2000, 1, 1))
        profiles.saveAndCompleteOnboarding(profiles.get("u1")!!.copy(availableDays = setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY)))
        if (consent) profiles.setAiConsent("u1", true)
    }

    @Test
    fun withoutConsentNothingIsSent() = runTest {
        user(consent = false)
        assertEquals(AiResult.Failed(AiFailure.NO_CONSENT), builder.propose("u1", RoutineRequest(RoutineGoal.GENERAL, 45)))
        assertEquals(null, api.lastRequest)
    }

    @Test
    fun sendsOnlyUsableExercisesAndDropsInventedOnes() = runTest {
        user(consent = true)
        val r = builder.propose("u1", RoutineRequest(RoutineGoal.STRENGTH, 45, "rodilla delicada"))
        // No equipment in the profile → body weight only: the bench press is never offered.
        assertEquals(listOf("push_up", "bodyweight_squat", "plank"), api.lastRequest!!.catalog.map { it.id })
        assertEquals(2, api.lastRequest!!.daysPerWeek)
        val proposal = (r as AiResult.Ok).value
        assertEquals(listOf("push_up", "bodyweight_squat"), proposal.exercises.map { it.exerciseId })
    }

    @Test
    fun acceptedProposalBecomesAnEditableRoutineWithItsExplanation() = runTest {
        user(consent = true)
        val proposal = (builder.propose("u1", RoutineRequest(RoutineGoal.GENERAL, 30)) as AiResult.Ok).value
        builder.save("u1", proposal)
        val routine = saved.single()
        assertEquals("Cuerpo completo", routine.name)
        assertEquals("Tres básicos con tu peso corporal.", routine.aiRationale)
        assertEquals(setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY), routine.days)
        assertTrue("rest snapped to the editor's 15 s steps", routine.exercises.all { it.restSeconds % 15 == 0 })
    }
}
