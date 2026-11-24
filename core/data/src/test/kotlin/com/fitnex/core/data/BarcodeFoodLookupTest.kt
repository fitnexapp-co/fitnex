package com.fitnex.core.data

import com.fitnex.core.data.nutrition.BarcodeFoodLookup
import com.fitnex.core.data.nutrition.BarcodeResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BarcodeFoodLookupTest {
    private val lookup = BarcodeFoodLookup()

    @Test
    fun parsesLabelValuesPer100gAndAllergens() {
        val body = """
            {"status":1,"product":{"product_name":"Yogur natural","product_name_es":"Yogur natural","brands":"Marca, Otra",
             "serving_size":"125 g","serving_quantity":125,"allergens_tags":["en:milk","en:unknown"],
             "nutriments":{"energy-kcal_100g":61,"proteins_100g":3.5,"carbohydrates_100g":4.7,"fat_100g":3.3,"sodium_100g":0.05}}}
        """.trimIndent()
        val r = lookup.parse("8410000000001", body) as BarcodeResult.Found
        assertEquals("off:8410000000001", r.food.id)
        assertEquals("Yogur natural", r.food.name)
        assertEquals("Marca", r.brand)
        assertEquals(61f, r.food.per100g.kcal, 0.01f)
        assertEquals(50f, r.food.per100g.sodiumMg, 0.01f)
        assertEquals(setOf("milk"), r.food.allergens)
        assertEquals(125f, r.food.unitGrams)
    }

    @Test
    fun unknownProductsAreNotInvented() {
        assertTrue(lookup.parse("1", """{"status":0}""") is BarcodeResult.NotFound)
        assertTrue(lookup.parse("1", """{"status":1,"product":{"product_name":""}}""") is BarcodeResult.NotFound)
        assertTrue(lookup.parse("1", "not json") is BarcodeResult.NotFound)
    }
}
