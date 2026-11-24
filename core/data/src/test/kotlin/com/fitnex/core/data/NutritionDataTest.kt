package com.fitnex.core.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fitnex.core.data.nutrition.AssetFoodCatalogRepository
import com.fitnex.core.data.nutrition.AssetFoodCatalogSource
import com.fitnex.core.data.nutrition.RoomNutritionRepository
import com.fitnex.core.database.FitnexDatabase
import com.fitnex.core.model.FoodCategory
import com.fitnex.core.model.MealType
import com.fitnex.core.model.Nutrients
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NutritionDataTest {
    private lateinit var db: FitnexDatabase
    private val clock = Clock.fixed(Instant.parse("2026-11-20T10:00:00Z"), ZoneOffset.UTC)
    private val today = LocalDate.of(2026, 11, 20)
    private val user = "u1"
    private val catalog = AssetFoodCatalogRepository(AssetFoodCatalogSource(ApplicationProvider.getApplicationContext()))

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), FitnexDatabase::class.java)
            .allowMainThreadQueries().build()
    }

    @After fun tearDown() = db.close()

    private fun repo() = RoomNutritionRepository(db.nutritionDao(), { UUID.randomUUID().toString() }, clock)

    @Test
    fun catalogIsCompleteSpanishAndMatchesUsda() = runTest {
        val all = catalog.all()
        assertTrue("about 200 common foods", all.size >= 200)
        assertEquals("ids are unique", all.size, all.map { it.id }.toSet().size)
        FoodCategory.entries.forEach { c -> assertTrue("$c has foods", all.any { it.category == c }) }
        val profileKeys = setOf("nuts", "peanut", "milk", "egg", "gluten", "soy", "fish", "shellfish", "sesame")
        all.forEach { f ->
            assertTrue("${f.id} allergens use profile keys", profileKeys.containsAll(f.allergens))
            assertTrue("${f.id} macros are per 100 g", f.per100g.proteinG + f.per100g.carbsG + f.per100g.fatG <= 101f)
            assertTrue("${f.id} unit grams are sensible", (f.unitGrams ?: 1f) in 0.5f..1000f)
        }
        // Spot checks against USDA SR Legacy.
        val banana = catalog.get("banana")!!
        assertEquals("Plátano", banana.name)
        assertEquals(89f, banana.per100g.kcal, 0.5f)
        assertEquals(31.02f, catalog.get("chicken_breast")!!.per100g.proteinG, 0.01f)
        assertEquals(setOf("milk"), catalog.get("yogurt_greek")!!.allergens)
    }

    @Test
    fun loggingFillsOneMealPerTypeAndDayWithScaledNutrients() = runTest {
        val r = repo()
        val banana = catalog.get("banana")!!
        r.addFood(user, today, MealType.BREAKFAST, banana, 118f, "1 × unidad mediana")
        r.addFood(user, today, MealType.BREAKFAST, catalog.get("oats")!!, 40f, "40 g")
        r.addFood(user, today, MealType.DINNER, catalog.get("salmon")!!, 150f, "1 × filete")

        val day = r.observeDay(user, today).first()
        assertEquals(2, day.meals.size)
        val breakfast = day.meal(MealType.BREAKFAST)!!
        assertEquals(2, breakfast.items.size)
        val b = breakfast.items.first { it.foodId == "banana" }
        assertEquals(banana.per100g.carbsG * 1.18f, b.nutrients.carbsG, 0.01f)
        assertEquals("micronutrients survive storage", banana.per100g.vitaminCMg * 1.18f, b.nutrients.vitaminCMg, 0.01f)
        assertEquals(3, day.distinctFoods)
        assertTrue(r.observeDay(user, today.minusDays(1)).first().meals.isEmpty())
    }

    @Test
    fun editDeleteAndCustomFoods() = runTest {
        val r = repo()
        val egg = catalog.get("egg_boiled")!!
        val id = r.addFood(user, today, MealType.LUNCH, egg, 50f, "1 × unidad")
        r.updatePortion(id, egg, 100f, "2 × unidad")
        val edited = r.observeDay(user, today).first().meal(MealType.LUNCH)!!.items.single()
        assertEquals(egg.per100g.proteinG, edited.nutrients.proteinG, 0.01f)
        assertEquals("2 × unidad", edited.portion)

        r.deleteEntry(id)
        assertTrue(r.observeDay(user, today).first().meal(MealType.LUNCH)!!.items.isEmpty())

        r.addCustom(user, today, MealType.SNACK, "Tarta casera", null, Nutrients(proteinG = 4f, carbsG = 30f))
        val custom = r.observeDay(user, today).first().meal(MealType.SNACK)!!.items.single()
        assertNull(custom.foodId)
        assertEquals("1 ración", custom.portion)
        assertTrue(custom.userEdited)
    }

    @Test
    fun waterAddsUpPerDayAndUndoRemovesTheLastOne() = runTest {
        val r = repo()
        r.addWater(user, 250)
        val second = r.addWater(user, 500)
        assertEquals(750, r.observeDay(user, today).first().waterMl)
        r.deleteWater(second)
        assertEquals(250, r.observeDay(user, today).first().waterMl)
        assertEquals(listOf(250), r.observeWater(user, today).first().map { it.ml })
    }

    @Test
    fun recipesAreSavedEditedLoggedAndDeleted() = runTest {
        val r = repo()
        val recipe = com.fitnex.core.model.Recipe("r1", "Gachas de avena", 2, listOf(
            com.fitnex.core.model.RecipeItem("oats", 80f), com.fitnex.core.model.RecipeItem("milk_semi", 250f),
        ))
        r.saveRecipe(user, recipe)
        assertEquals(listOf(recipe), r.observeRecipes(user).first())
        val edited = recipe.copy(servings = 4, items = recipe.items.take(1))
        r.saveRecipe(user, edited)
        assertEquals(edited, r.getRecipe("r1"))

        val foods = catalog.all().associateBy { it.id }
        val per = com.fitnex.core.model.RecipeMath.perServing(edited, foods)
        r.addRecipe(user, today, MealType.BREAKFAST, edited, per, 2f, "2 raciones")
        val entry = r.observeDay(user, today).first().meal(MealType.BREAKFAST)!!.items.single()
        assertEquals("Gachas de avena", entry.name)
        assertEquals(per.proteinG * 2f, entry.nutrients.proteinG, 0.01f)

        r.deleteRecipe("r1")
        assertTrue(r.observeRecipes(user).first().isEmpty())
        assertEquals("logged entries stay in the diary", 1, r.observeDay(user, today).first().meal(MealType.BREAKFAST)!!.items.size)
    }

    @Test
    fun waterIsGroupedByDayForTheWeek() = runTest {
        val r = repo()
        r.addWater(user, 250); r.addWater(user, 500)
        assertEquals(mapOf(today to 750), r.observeWaterByDay(user, today.minusDays(6), today).first())
    }

    @Test
    fun photoIsKeptWithTheMealAndRecentFoodsAreListed() = runTest {
        val r = repo()
        r.attachPhoto(user, today, MealType.DINNER, "/data/photo.jpg")
        r.addFood(user, today, MealType.DINNER, catalog.get("rice_white")!!, 158f, "1 × taza")
        val dinner = r.observeDay(user, today).first().meal(MealType.DINNER)!!
        assertEquals("/data/photo.jpg", dinner.imageReference)
        assertEquals("photo and food share the same meal", 1, dinner.items.size)
        assertEquals(listOf("rice_white"), r.observeRecentFoodIds(user).first())
    }
}
