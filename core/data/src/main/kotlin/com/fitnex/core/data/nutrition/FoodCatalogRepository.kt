package com.fitnex.core.data.nutrition

import android.content.Context
import com.fitnex.core.model.Food
import com.fitnex.core.model.FoodCategory
import com.fitnex.core.model.Nutrients
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Source of the foods JSON; an interface so tests can feed a small catalogue. */
fun interface FoodCatalogSource {
    fun read(): String
}

class AssetFoodCatalogSource @Inject constructor(@ApplicationContext private val context: Context) : FoodCatalogSource {
    override fun read(): String = context.assets.open(ASSET_PATH).bufferedReader().use { it.readText() }

    companion object {
        const val ASSET_PATH = "nutrition/foods.json"
    }
}

/**
 * The built-in food database (spec §6 "Base de datos nutricional"): 200+ common foods with Spanish
 * names, from USDA FoodData Central SR Legacy (public domain, CC0 1.0). Read-only and shared,
 * so it stays in memory instead of Room; loaded once, off the main thread.
 */
interface FoodCatalogRepository {
    suspend fun all(): List<Food>
    suspend fun get(id: String): Food?
    /** Attribution line to show in the app. */
    val sourceNote: String
}

@Singleton
class AssetFoodCatalogRepository @Inject constructor(private val source: FoodCatalogSource) : FoodCatalogRepository {
    private val mutex = Mutex()
    private var cache: Map<String, Food>? = null

    override val sourceNote = "USDA FoodData Central (SR Legacy)"

    override suspend fun all(): List<Food> = load().values.sortedBy { it.name }
    override suspend fun get(id: String): Food? = load()[id]

    private suspend fun load(): Map<String, Food> = mutex.withLock {
        cache ?: withContext(Dispatchers.Default) { parse(source.read()) }.also { cache = it }
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun parse(text: String): Map<String, Food> = json.decodeFromString<FoodsDto>(text).foods.associate { f ->
            f.id to Food(
                id = f.id,
                name = f.name,
                category = FoodCategory.valueOf(f.category),
                allergens = f.allergens.toSet(),
                unitName = f.unit,
                unitGrams = f.unitG,
                per100g = Nutrients.fromMap(f.per100g),
                fdcId = f.fdcId,
            )
        }
    }
}

@Serializable
private data class FoodsDto(val version: Int, val foods: List<FoodDto>)

@Serializable
private data class FoodDto(
    val id: String,
    val name: String,
    val category: String,
    val allergens: List<String> = emptyList(),
    @SerialName("fdc_id") val fdcId: Int,
    val unit: String? = null,
    @SerialName("unit_g") val unitG: Float? = null,
    val per100g: Map<String, Float>,
)
