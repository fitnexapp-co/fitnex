package com.fitnex.core.network

import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

/**
 * FITNEX backend (Firebase Cloud Functions, `backend/`). Flow for every AI feature (spec §8):
 *   App -> [this API, Firebase ID token] -> backend validates auth and rate limits
 *       -> Gemini (key in Secret Manager, model name in server config) -> validated JSON -> App.
 */
interface FitnexBackendApi {
    @GET("v1/health")
    suspend fun health(): HealthResponse

    @POST("v1/ai/chat")
    suspend fun chat(@Body request: ChatRequest): ChatResponse

    @POST("v1/ai/analyze-food")
    suspend fun analyzeFood(@Body request: FoodPhotoRequest): FoodPhotoResponse

    /** FITNEX AI routine builder: picks only from [RoutineRequestDto.catalog]. */
    @POST("v1/ai/routine")
    suspend fun routine(@Body request: RoutineRequestDto): RoutineResponseDto

    /** Weekly plan: several different sessions, adjusted to the recent training [PlanRequestDto.history]. */
    @POST("v1/ai/plan")
    suspend fun plan(@Body request: PlanRequestDto): PlanResponseDto

    /** Cloud copy of the user's data (Phase 9): upload pending changes, last-writer-wins on the server. */
    @POST("v1/sync/push")
    suspend fun syncPush(@Body request: SyncPushRequest): SyncPushResponse

    /** Changes stored after [SyncPullRequest.since] (server sequence), oldest first. */
    @POST("v1/sync/pull")
    suspend fun syncPull(@Body request: SyncPullRequest): SyncPullResponse

    /** Erases the whole cloud copy of the signed-in user (account deletion). */
    @POST("v1/sync/erase")
    suspend fun syncErase(): SyncEraseResponse
}

@Serializable
data class CatalogExerciseDto(val id: String, val name: String, val muscles: List<String> = emptyList(), val difficulty: String = "")

@Serializable
data class RoutineRequestDto(
    val goal: String,
    val sessionMinutes: Int,
    val daysPerWeek: Int,
    val experience: String,
    val ageProfile: String,
    val focus: String,
    val catalog: List<CatalogExerciseDto>,
)

@Serializable
data class RoutineExerciseDto(val exerciseId: String, val sets: Int, val reps: Int, val restSeconds: Int, val note: String = "")

@Serializable
data class PlanRequestDto(
    val goal: String,
    val sessionMinutes: Int,
    val daysPerWeek: Int,
    val experience: String,
    val ageProfile: String,
    val focus: String,
    val catalog: List<CatalogExerciseDto>,
    val sessions: Int,
    /** Plain-language summary of the last weeks (counts and bests only). */
    val history: String,
)

@Serializable
data class PlanSessionDto(val name: String, val exercises: List<RoutineExerciseDto>)

@Serializable
data class PlanResponseDto(val name: String, val rationale: String = "", val sessions: List<PlanSessionDto>)

@Serializable
data class RoutineResponseDto(val name: String, val rationale: String = "", val exercises: List<RoutineExerciseDto>)

/** t = table, u = updated_at (epoch ms), d = deleted, data = the row (null for a bare deletion). */
@Serializable
data class SyncRecordDto(
    val t: String,
    val id: String,
    val u: Long,
    val d: Boolean,
    val data: kotlinx.serialization.json.JsonObject? = null,
    val seq: Long = 0,
)

@Serializable
data class SyncPushRequest(val records: List<SyncRecordDto>)

@Serializable
data class SyncPushResponse(val stored: Int)

@Serializable
data class SyncPullRequest(val since: Long, val limit: Int = 500)

@Serializable
data class SyncPullResponse(val records: List<SyncRecordDto>, val next: Long, val hasMore: Boolean)

@Serializable
data class SyncEraseResponse(val erased: Boolean)

@Serializable
data class HealthResponse(val status: String, val apiVersion: String)

@Serializable
data class ChatTurn(val role: String, val text: String)

/** [ageProfile] "ADULT" or "MINOR": the server applies the minor rules (spec §4) to anything not "ADULT". */
@Serializable
data class ChatRequest(
    val message: String,
    val screen: String,
    val ageProfile: String,
    val history: List<ChatTurn>,
    /** Only the data of the current screen, summarised by the app (spec §19). */
    val context: String,
)

@Serializable
data class ChatResponse(val reply: String)

@Serializable
data class FoodPhotoRequest(val imageBase64: String, val mimeType: String, val ageProfile: String, val note: String = "")

@Serializable
data class DetectedFoodDto(
    val name: String,
    val ingredients: List<String> = emptyList(),
    val portionGrams: Float,
    val portionDescription: String = "",
    val proteinG: Float,
    val carbsG: Float,
    val fatG: Float,
    val fiberG: Float,
    val kcal: Float = 0f,
    val vitamins: List<String> = emptyList(),
    val minerals: List<String> = emptyList(),
    val confidence: Float,
)

@Serializable
data class FoodPhotoResponse(
    val identifiable: Boolean,
    val question: String = "",
    val foods: List<DetectedFoodDto> = emptyList(),
    val notes: String = "",
)
