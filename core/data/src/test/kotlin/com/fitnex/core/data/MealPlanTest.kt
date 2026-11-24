package com.fitnex.core.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fitnex.core.data.nutrition.AssetFoodCatalogRepository
import com.fitnex.core.data.nutrition.AssetFoodCatalogSource
import com.fitnex.core.data.nutrition.MealPlanRepository
import com.fitnex.core.data.nutrition.RoomNutritionRepository
import com.fitnex.core.database.FitnexDatabase
import com.fitnex.core.model.MealType
import com.fitnex.core.model.Recipe
import com.fitnex.core.model.RecipeItem
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MealPlanTest {
    private lateinit var db: FitnexDatabase
    private val clock = Clock.fixed(Instant.parse("2026-11-20T10:00:00Z"), ZoneOffset.UTC)
    private val today = LocalDate.of(2026, 11, 20) // viernes
    private val user = "u1"
    private val catalog = AssetFoodCatalogRepository(AssetFoodCatalogSource(ApplicationProvider.getApplicationContext()))
    private val ids = { UUID.randomUUID().toString() }

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), FitnexDatabase::class.java)
            .allowMainThreadQueries().build()
    }

    @After fun tearDown() = db.close()

    @Test
    fun planIsPerWeekdayAndCopiesToTheDiaryWithCurrentNutrients() = runTest {
        val nutrition = RoomNutritionRepository(db.nutritionDao(), ids, clock)
        val plan = MealPlanRepository(db.nutritionDao(), nutrition, catalog, ids, clock)
        val banana = catalog.get("banana")!!
        val oats = catalog.get("oats")!!
        nutrition.saveRecipe(user, Recipe("r1", "Gachas", 2, listOf(RecipeItem("oats", 80f))))

        plan.addFood(user, DayOfWeek.FRIDAY, MealType.BREAKFAST, banana, 118f, "1 × unidad mediana")
        plan.addRecipe(user, DayOfWeek.FRIDAY, MealType.BREAKFAST, nutrition.getRecipe("r1")!!, 1f, "1 raciones")
        val mondayId = plan.addFood(user, DayOfWeek.MONDAY, MealType.LUNCH, oats, 40f, "40 g")

        val items = plan.observe(user).first()
        assertEquals(3, items.size)
        assertEquals("orden de alta dentro de la comida", listOf("Plátano", "Gachas"), items.filter { it.day == DayOfWeek.FRIDAY }.map { it.name })
        assertTrue("otros usuarios no lo ven", plan.observe("other").first().isEmpty())

        // Viernes → diario de hoy: 2 entradas en el desayuno, con los nutrientes calculados ahora.
        assertEquals(2, plan.copyDayToDiary(user, DayOfWeek.FRIDAY, today))
        val day = nutrition.observeDay(user, today).first()
        val breakfast = day.meal(MealType.BREAKFAST)!!
        assertEquals(setOf("Plátano", "Gachas"), breakfast.items.map { it.name }.toSet())
        assertEquals(22.84f * 1.18f + oats.per100g.carbsG * 0.4f, day.totals.carbsG, 0.2f) // banana 118 g + ½ receta (40 g de avena)

        // Quitar es un borrado lógico (se sincroniza); el plan sigue en los demás días.
        plan.remove(mondayId)
        assertEquals(2, plan.observe(user).first().size)
        assertEquals(0, plan.copyDayToDiary(user, DayOfWeek.MONDAY, today))
    }

    @Test
    fun deletedRecipesAreSkippedWhenCopying() = runTest {
        val nutrition = RoomNutritionRepository(db.nutritionDao(), ids, clock)
        val plan = MealPlanRepository(db.nutritionDao(), nutrition, catalog, ids, clock)
        nutrition.saveRecipe(user, Recipe("r1", "Gachas", 2, listOf(RecipeItem("oats", 80f))))
        plan.addRecipe(user, DayOfWeek.FRIDAY, MealType.DINNER, nutrition.getRecipe("r1")!!, 1f, "1 raciones")
        nutrition.deleteRecipe("r1")
        assertEquals(0, plan.copyDayToDiary(user, DayOfWeek.FRIDAY, today))
    }
}
