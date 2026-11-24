package com.fitnex.core.data.ai

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.fitnex.core.common.result.AppError
import com.fitnex.core.common.result.AppResult
import com.fitnex.core.common.time.IdGenerator
import com.fitnex.core.data.nutrition.NutritionRepository
import com.fitnex.core.data.profile.UserProfileRepository
import com.fitnex.core.data.stats.GlobalStatisticsRepository
import com.fitnex.core.database.dao.AiDao
import com.fitnex.core.database.entity.AiConversationEntity
import com.fitnex.core.database.entity.AiMessageEntity
import com.fitnex.core.database.entity.SyncMeta
import com.fitnex.core.model.AiFailure
import com.fitnex.core.model.AiRole
import com.fitnex.core.model.AiScreenContext
import com.fitnex.core.model.ChatMessage
import com.fitnex.core.model.DetectedFood
import com.fitnex.core.model.FoodPhotoAnalysis
import com.fitnex.core.model.Nutrients
import com.fitnex.core.model.StatsPeriod
import com.fitnex.core.model.UserProfile
import com.fitnex.core.network.BuildConfig
import com.fitnex.core.network.ChatRequest
import com.fitnex.core.network.ChatTurn
import com.fitnex.core.network.FitnexBackendApi
import com.fitnex.core.network.FoodPhotoRequest
import com.fitnex.core.network.safeApiCall
import java.io.ByteArrayOutputStream
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

sealed interface AiResult<out T> {
    data class Ok<T>(val value: T) : AiResult<T>
    data class Failed(val reason: AiFailure) : AiResult<Nothing>
}

/**
 * FITNEX AI on the device side (spec §8, §19): checks consent, sends only the current screen's
 * data, keeps the chat in Room, and turns backend errors into explainable failures.
 */
interface AiRepository {
    fun observeMessages(userId: String): Flow<List<ChatMessage>>
    /** [alreadyStored]: retry of the last question, which is already in the conversation. */
    suspend fun send(userId: String, text: String, screen: AiScreenContext?, alreadyStored: Boolean = false): AiResult<Unit>
    suspend fun clear(userId: String)
    suspend fun analyzeFoodPhoto(userId: String, imagePath: String, note: String = ""): AiResult<FoodPhotoAnalysis>
}

@Singleton
class BackendAiRepository @Inject constructor(
    private val api: FitnexBackendApi,
    private val dao: AiDao,
    private val profiles: UserProfileRepository,
    private val stats: GlobalStatisticsRepository,
    private val nutrition: NutritionRepository,
    private val ids: IdGenerator,
    private val clock: Clock,
) : AiRepository {

    private fun conversationId(userId: String) = "chat_$userId"

    override fun observeMessages(userId: String): Flow<List<ChatMessage>> =
        dao.observeMessages(conversationId(userId)).map { rows -> rows.map { ChatMessage(it.id, it.role == AiRole.USER, it.content, it.timestamp) } }

    override suspend fun send(userId: String, text: String, screen: AiScreenContext?, alreadyStored: Boolean): AiResult<Unit> {
        val profile = profiles.get(userId) ?: return AiResult.Failed(AiFailure.NOT_SIGNED_IN)
        if (!profiles.hasAiConsent(userId)) return AiResult.Failed(AiFailure.NO_CONSENT)
        if (!configured()) return AiResult.Failed(AiFailure.NOT_CONFIGURED)
        val convId = ensureConversation(userId)
        val past = dao.lastMessages(convId, 13).reversed().let { if (alreadyStored) it.dropLast(1) else it }.takeLast(12)
        val history = past.map { ChatTurn(if (it.role == AiRole.USER) "user" else "model", it.content) }
        if (!alreadyStored) save(convId, AiRole.USER, text.trim(), screen)
        val request = ChatRequest(
            message = text.trim(), screen = screen?.key ?: "progress", ageProfile = ageProfile(profile),
            history = history, context = contextFor(userId, screen),
        )
        return when (val r = safeApiCall { api.chat(request) }) {
            is AppResult.Success -> { save(convId, AiRole.ASSISTANT, r.data.reply, screen); AiResult.Ok(Unit) }
            is AppResult.Failure -> AiResult.Failed(r.error.toFailure())
        }
    }

    override suspend fun clear(userId: String) = dao.deleteConversation(conversationId(userId))

    override suspend fun analyzeFoodPhoto(userId: String, imagePath: String, note: String): AiResult<FoodPhotoAnalysis> {
        val profile = profiles.get(userId) ?: return AiResult.Failed(AiFailure.NOT_SIGNED_IN)
        if (!profiles.hasAiConsent(userId)) return AiResult.Failed(AiFailure.NO_CONSENT)
        if (!configured()) return AiResult.Failed(AiFailure.NOT_CONFIGURED)
        val jpeg = compress(imagePath) ?: return AiResult.Failed(AiFailure.UNAVAILABLE)
        val request = FoodPhotoRequest(Base64.encodeToString(jpeg, Base64.NO_WRAP), "image/jpeg", ageProfile(profile), note.take(500))
        return when (val r = safeApiCall { api.analyzeFood(request) }) {
            is AppResult.Success -> AiResult.Ok(
                FoodPhotoAnalysis(
                    identifiable = r.data.identifiable,
                    question = r.data.question,
                    notes = r.data.notes,
                    foods = r.data.foods.map { f ->
                        DetectedFood(
                            name = f.name, ingredients = f.ingredients, grams = f.portionGrams,
                            portion = f.portionDescription.ifBlank { "${f.portionGrams.toInt()} g" },
                            nutrients = Nutrients(kcal = f.kcal, proteinG = f.proteinG, carbsG = f.carbsG, fatG = f.fatG, fiberG = f.fiberG),
                            vitamins = f.vitamins, minerals = f.minerals, confidence = f.confidence.coerceIn(0f, 1f),
                        )
                    },
                ),
            )
            is AppResult.Failure -> AiResult.Failed(r.error.toFailure())
        }
    }

    // ------------------------------------------------------------------ helpers

    /** The placeholder URL means the backend has not been deployed/configured for this build yet. */
    private fun configured() = !BuildConfig.BACKEND_URL.contains(".invalid")

    private fun ageProfile(p: UserProfile) = p.ageProfile(LocalDate.now(clock)).name

    /** Only what the current screen shows (spec §19): training stats in Progress, today's diary in Nutrition. */
    private suspend fun contextFor(userId: String, screen: AiScreenContext?): String = when (screen) {
        AiScreenContext.PROGRESS, AiScreenContext.STATISTICS, AiScreenContext.CALENDAR -> {
            val s = stats.observeStats(userId, StatsPeriod.MONTH).first()
            buildString {
                appendLine("Este mes: ${s.workouts} entrenamientos, ${s.sets} series, ${s.reps} repeticiones, ${s.activeDays} días activos.")
                s.volumeKg?.let { appendLine("Volumen: ${it.toInt()} kg.") }
                if (s.regions.isNotEmpty()) appendLine("Grupos más trabajados (series ponderadas): " + s.regions.take(4).joinToString { "${it.region.name.lowercase()} ${"%.1f".format(it.weightedSets)}" })
                if (s.exercises.isNotEmpty()) appendLine("Ejercicios: " + s.exercises.take(6).joinToString { e -> e.name + (e.bestWeightKg?.let { " (mejor ${it} kg)" } ?: "") })
                if (!s.hasAnyHistory) appendLine("Todavía no ha terminado ningún entrenamiento.")
            }
        }
        AiScreenContext.NUTRITION -> {
            val day = nutrition.observeDay(userId, LocalDate.now(clock)).first()
            val t = day.totals
            buildString {
                appendLine("Hoy: proteínas ${t.proteinG.toInt()} g, carbohidratos ${t.carbsG.toInt()} g, grasas ${t.fatG.toInt()} g, fibra ${t.fiberG.toInt()} g, agua ${day.waterMl} ml.")
                appendLine("Alimentos registrados: " + day.meals.flatMap { it.items }.joinToString { it.name }.ifEmpty { "ninguno" })
            }
        }
        else -> ""
    }

    private suspend fun ensureConversation(userId: String): String {
        val id = conversationId(userId)
        if (dao.getConversation(id) == null) dao.upsertConversation(AiConversationEntity(id, userId, null, clock.instant(), SyncMeta(clock.instant())))
        return id
    }

    private suspend fun save(convId: String, role: AiRole, text: String, screen: AiScreenContext?) =
        dao.upsertMessage(AiMessageEntity(ids.newId(), convId, role, text, screen?.key, clock.instant()))

    /** Max 1280 px, JPEG 80 %: enough to recognise food, far below the server's 4 MB limit. */
    private suspend fun compress(path: String): ByteArray? = withContext(Dispatchers.IO) {
        runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= 1280 || bounds.outHeight / (sample * 2) >= 1280) sample *= 2
            val bmp = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return@runCatching null
            ByteArrayOutputStream().use { out -> bmp.compress(Bitmap.CompressFormat.JPEG, 80, out); out.toByteArray() }
        }.getOrNull()
    }

    private fun AppError.toFailure() = when (this) {
        AppError.NoConnection -> AiFailure.NO_CONNECTION
        AppError.Unauthorized -> AiFailure.NOT_SIGNED_IN
        is AppError.RateLimited -> AiFailure.RATE_LIMITED
        else -> AiFailure.UNAVAILABLE
    }
}
