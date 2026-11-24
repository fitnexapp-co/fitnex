package com.fitnex.core.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fitnex.core.data.ai.AiResult
import com.fitnex.core.data.ai.BackendAiRepository
import com.fitnex.core.data.nutrition.AssetFoodCatalogRepository
import com.fitnex.core.data.nutrition.AssetFoodCatalogSource
import com.fitnex.core.data.nutrition.RoomNutritionRepository
import com.fitnex.core.data.stats.GlobalStatisticsRepository
import com.fitnex.core.database.FitnexDatabase
import com.fitnex.core.model.AiFailure
import com.fitnex.core.model.AiScreenContext
import com.fitnex.core.model.MealType
import com.fitnex.core.network.ChatRequest
import com.fitnex.core.network.ChatResponse
import com.fitnex.core.network.FitnexBackendApi
import com.fitnex.core.network.FoodPhotoRequest
import com.fitnex.core.network.FoodPhotoResponse
import com.fitnex.core.network.HealthResponse
import com.fitnex.core.testing.FakeUserProfileRepository
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AiDataTest {
    private lateinit var db: FitnexDatabase
    private val clock = Clock.fixed(Instant.parse("2026-11-20T10:00:00Z"), ZoneOffset.UTC)
    private val user = "u1"
    private val profiles = FakeUserProfileRepository()
    private val api = FakeApi()

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), FitnexDatabase::class.java).allowMainThreadQueries().build()
        runBlocking { profiles.createAtSignUp(user, "a@b.c", LocalDate.of(2011, 1, 1)) } // a minor
    }

    @After fun tearDown() = db.close()

    private val ids = { UUID.randomUUID().toString() }
    private fun nutrition() = RoomNutritionRepository(db.nutritionDao(), ids, clock)
    private fun repo() = BackendAiRepository(api, db.aiDao(), profiles, NoStats, nutrition(), ids, clock)

    @Test
    fun nothingIsSentWithoutConsent() = runTest {
        assertEquals(AiResult.Failed(AiFailure.NO_CONSENT), repo().send(user, "hola", AiScreenContext.PROGRESS))
        assertTrue(api.chats.isEmpty())
    }

    @Test
    fun chatKeepsTheConversationAndSendsOnlyThisScreensData() = runTest {
        profiles.setAiConsent(user, true)
        val n = nutrition()
        val catalog = AssetFoodCatalogRepository(AssetFoodCatalogSource(ApplicationProvider.getApplicationContext()))
        n.addFood(user, LocalDate.of(2026, 11, 20), MealType.BREAKFAST, catalog.get("banana")!!, 118f, "1 × unidad mediana")
        val r = repo()

        assertEquals(AiResult.Ok(Unit), r.send(user, "¿Qué me falta hoy?", AiScreenContext.NUTRITION))
        val sent = api.chats.single()
        assertEquals("nutrition", sent.screen)
        assertEquals("MINOR", sent.ageProfile)
        assertTrue(sent.context, sent.context.contains("Plátano"))
        assertTrue(sent.history.isEmpty())

        r.send(user, "Gracias", AiScreenContext.NUTRITION)
        assertEquals(listOf("user", "model"), api.chats.last().history.map { it.role })
        val messages = r.observeMessages(user).first()
        assertEquals(listOf(true, false, true, false), messages.map { it.fromUser })

        r.clear(user)
        assertTrue(r.observeMessages(user).first().isEmpty())
    }

    @Test
    fun networkFailureKeepsTheQuestionAndRetryDoesNotDuplicateIt() = runTest {
        profiles.setAiConsent(user, true)
        api.fail = true
        val r = repo()
        assertEquals(AiResult.Failed(AiFailure.NO_CONNECTION), r.send(user, "hola", AiScreenContext.WORKOUT))
        assertEquals(1, r.observeMessages(user).first().size)
        api.fail = false
        r.send(user, "hola", AiScreenContext.WORKOUT, alreadyStored = true)
        assertEquals(listOf(true, false), r.observeMessages(user).first().map { it.fromUser })
        assertTrue("workout screen sends no personal data", api.chats.last().context.isEmpty())
    }

    private class FakeApi : FitnexBackendApi {
        val chats = mutableListOf<ChatRequest>()
        var fail = false
        override suspend fun health() = HealthResponse("ok", "1")
        override suspend fun chat(request: ChatRequest): ChatResponse {
            if (fail) throw IOException("offline")
            chats += request
            return ChatResponse("Respuesta ${chats.size}")
        }
        override suspend fun analyzeFood(request: FoodPhotoRequest) = FoodPhotoResponse(identifiable = false, question = "¿Qué es?")
        override suspend fun syncPush(request: com.fitnex.core.network.SyncPushRequest) = error("not used")
        override suspend fun syncPull(request: com.fitnex.core.network.SyncPullRequest) = error("not used")
        override suspend fun syncErase() = error("not used")
        override suspend fun routine(request: com.fitnex.core.network.RoutineRequestDto) = error("not used")
        override suspend fun plan(request: com.fitnex.core.network.PlanRequestDto) = error("not used")
    }

    private object NoStats : GlobalStatisticsRepository {
        override fun observeStats(userId: String, period: com.fitnex.core.model.StatsPeriod) = emptyFlow<com.fitnex.core.model.GlobalStats>()
        override fun observeExerciseStats(userId: String, exerciseId: String) = emptyFlow<com.fitnex.core.model.ExerciseStats>()
        override fun observeCompletedSessions(userId: String) = emptyFlow<List<com.fitnex.core.model.CompletedSession>>()
    }
}
