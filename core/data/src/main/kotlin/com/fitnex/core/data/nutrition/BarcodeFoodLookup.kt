package com.fitnex.core.data.nutrition

import com.fitnex.core.model.Food
import com.fitnex.core.model.FoodCategory
import com.fitnex.core.model.Nutrients
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request

sealed interface BarcodeResult {
    data class Found(val food: Food, val brand: String?) : BarcodeResult
    data object NotFound : BarcodeResult
    data object NoConnection : BarcodeResult
}

/**
 * Packaged foods by barcode from Open Food Facts (free, open data, ODbL). Only the barcode is sent:
 * no account, no FITNEX token, no personal data. Values are per 100 g as declared on the label.
 */
@Singleton
class BarcodeFoodLookup @Inject constructor() {
    // A separate client on purpose: the app's main client attaches the Firebase token to every call.
    private val client = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).build()
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun lookup(barcode: String): BarcodeResult = withContext(Dispatchers.IO) {
        val code = barcode.filter(Char::isDigit)
        if (code.length !in 8..14) return@withContext BarcodeResult.NotFound
        val request = Request.Builder()
            .url("https://world.openfoodfacts.org/api/v2/product/$code.json?fields=product_name,product_name_es,brands,nutriments,allergens_tags,serving_size,serving_quantity")
            .header("User-Agent", "FITNEX-Android/1.0")
            .build()
        try {
            client.newCall(request).execute().use { res ->
                if (res.code == 404) return@withContext BarcodeResult.NotFound
                if (!res.isSuccessful) return@withContext BarcodeResult.NoConnection
                parse(code, res.body?.string().orEmpty())
            }
        } catch (_: IOException) {
            BarcodeResult.NoConnection
        }
    }

    internal fun parse(code: String, body: String): BarcodeResult {
        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return BarcodeResult.NotFound
        if (root["status"]?.jsonPrimitive?.int != 1) return BarcodeResult.NotFound
        val p = root["product"]?.jsonObject ?: return BarcodeResult.NotFound
        val name = (p.str("product_name_es") ?: p.str("product_name"))?.trim()?.takeIf { it.isNotEmpty() } ?: return BarcodeResult.NotFound
        val n = p["nutriments"]?.jsonObject ?: JsonObject(emptyMap())
        fun v(key: String) = n[key]?.jsonPrimitive?.floatOrNull ?: 0f
        val per100 = Nutrients(
            kcal = v("energy-kcal_100g"), proteinG = v("proteins_100g"), carbsG = v("carbohydrates_100g"),
            fatG = v("fat_100g"), fiberG = v("fiber_100g"), sugarsG = v("sugars_100g"), satFatG = v("saturated-fat_100g"),
            sodiumMg = v("sodium_100g") * 1000f,
        )
        val allergens = p["allergens_tags"]?.jsonArray?.mapNotNull { ALLERGENS[it.jsonPrimitive.contentOrNull?.removePrefix("en:")] }?.toSet().orEmpty()
        val servingGrams = p["serving_quantity"]?.jsonPrimitive?.floatOrNull?.takeIf { it in 1f..2000f }
        val food = Food(
            id = "off:$code", name = name.take(60), category = FoodCategory.SNACK, allergens = allergens,
            unitName = p.str("serving_size")?.takeIf { servingGrams != null }?.take(30), unitGrams = servingGrams,
            per100g = per100, fdcId = 0,
        )
        return BarcodeResult.Found(food, p.str("brands")?.substringBefore(',')?.trim())
    }

    private fun JsonObject.str(key: String) = this[key]?.jsonPrimitive?.contentOrNull

    private companion object {
        /** Open Food Facts allergen tags → the app's allergen ids (same as the food catalogue). */
        val ALLERGENS = mapOf(
            "milk" to "milk", "eggs" to "egg", "fish" to "fish", "crustaceans" to "shellfish", "molluscs" to "shellfish",
            "gluten" to "gluten", "nuts" to "nuts", "peanuts" to "peanut", "sesame-seeds" to "sesame", "soybeans" to "soy",
        )
    }
}
