package com.fitnex.app.ui

import androidx.compose.ui.test.assert

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fitnex.app.HiltTestActivity
import com.fitnex.core.auth.AuthResult
import com.fitnex.core.data.profile.UserProfileRepository
import com.fitnex.core.testing.FakeAuthRepository
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@HiltAndroidTest
@Config(application = HiltTestApplication::class)
@RunWith(AndroidJUnit4::class)
class NutritionFlowTest {
    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createAndroidComposeRule<HiltTestActivity>()

    @Inject lateinit var auth: FakeAuthRepository
    @Inject lateinit var profiles: UserProfileRepository

    private fun start(birthDate: LocalDate, allergies: List<String> = emptyList()) {
        hilt.inject()
        runBlocking {
            val user = (auth.signUp("ana@example.com", "secreto123") as AuthResult.Success).value
            profiles.createAtSignUp(user.uid, user.email, birthDate)
            profiles.saveAndCompleteOnboarding(profiles.get(user.uid)!!.copy(name = "Ana", allergies = allergies))
        }
        compose.setContent { TestApp() }
        compose.waitForTag("tab_nutrition")
        compose.onNodeWithTag("tab_nutrition").performClick()
        compose.waitForTag("nutrition_summary")
    }

    private fun click(tag: String) = compose.onNodeWithTag(tag).performScrollTo().performSemanticsAction(SemanticsActions.OnClick)

    @Test
    fun adultLogsAFoodFromTheCatalogAndWater() {
        start(LocalDate.of(2000, 5, 1))

        // Water: add and undo.
        click("water_add_250")
        compose.waitUntil(5_000) { runCatching { compose.onNodeWithTag("water_total").assertTextContains("250", substring = true) }.isSuccess }
        click("water_undo")
        compose.waitUntil(5_000) { runCatching { compose.onNodeWithTag("water_total").assertTextContains("0 ml") }.isSuccess }

        // Breakfast: search without the accent, pick the default portion (1 medium banana, 118 g).
        click("meal_add_BREAKFAST")
        compose.waitForTag("food_search")
        compose.onNodeWithTag("food_search").performTextInput("platano")
        compose.waitForTag("food_banana")
        compose.onNodeWithTag("food_results").performScrollToNode(hasTestTag("food_banana"))
        compose.onNodeWithTag("food_banana").performClick()
        compose.waitForTag("portion_confirm")
        compose.onNodeWithTag("portion_confirm").performSemanticsAction(SemanticsActions.OnClick)

        // Back on the diary with USDA values: 22.84 g carbs / 100 g × 1.18 = 27 g.
        compose.waitForTag("nutrition_summary")
        compose.waitForText("1 × unidad mediana")
        compose.onNodeWithText("Plátano").assertExists()
        compose.onNodeWithTag("total_carbs").performScrollTo().assertTextContains("27", substring = true)
        compose.onNodeWithTag("total_kcal").performScrollTo().assert(androidx.compose.ui.test.hasContentDescription("estimación", substring = true))
    }

    @Test
    fun declaredAllergyIsFlaggedBeforeAdding() {
        start(LocalDate.of(2000, 5, 1), allergies = listOf("milk"))
        click("meal_add_LUNCH")
        compose.waitForTag("food_search")
        compose.onNodeWithTag("food_search").performTextInput("yogur griego natural")
        compose.waitForTag("food_yogurt_greek")
        compose.onNodeWithTag("food_yogurt_greek").performClick()
        compose.waitForTag("portion_allergen_warning")
        compose.onNodeWithText("Atención: contiene lácteos", substring = true).assertExists()
    }

    @Test
    fun recipeIsCreatedAndAddedByServingsAndTheWeekSummarizesIt() {
        start(LocalDate.of(2000, 5, 1))
        click("meal_add_BREAKFAST")
        compose.waitForTag("add_tabs")
        compose.onNodeWithText("Recetas").performClick()
        compose.waitForTag("recipe_new")
        compose.onNodeWithTag("recipe_new").performSemanticsAction(SemanticsActions.OnClick)

        compose.waitForTag("recipe_name")
        compose.onNodeWithTag("recipe_name").performTextInput("Gachas")
        click("recipe_add_item")
        compose.waitForTag("recipe_food_search")
        compose.onNodeWithTag("recipe_food_search").performTextInput("avena")
        compose.waitForTag("choose_oats")
        compose.onNodeWithTag("choose_oats").performClick()
        compose.waitForTag("portion_confirm")
        compose.onNodeWithTag("portion_confirm").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForText("Copos de avena")
        click("recipe_save")

        // Back on the Recipes tab: add one serving (the wheel starts at 1).
        compose.waitForTag("recipe_Gachas")
        compose.onNodeWithTag("recipe_Gachas").performClick()
        compose.waitForTag("servings_confirm")
        compose.onNodeWithTag("servings_confirm").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForTag("nutrition_summary")
        compose.waitForText("Gachas")
        compose.onNodeWithText("1 raciones").assertExists()

        click("tool_stats")
        compose.waitForTag("week_days")
        compose.onNodeWithTag("week_days").assertTextContains("1/7", substring = true)
        compose.onNodeWithText("Gachas").performScrollTo().assertExists()
    }

    @Test
    fun mealPlanIsFilledForTodayAndCopiedToTheDiary() {
        start(LocalDate.of(2000, 5, 1))
        click("tool_plan")
        compose.waitForTag("plan_days")
        // Wait for the screen transition to settle before tapping (the button is briefly merged into it).
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("plan_add_BREAKFAST").fetchSemanticsNodes().isNotEmpty() }
        click("plan_add_BREAKFAST")
        compose.waitForTag("food_search")
        compose.onNodeWithTag("custom_food").assertDoesNotExist() // plan: catalog foods and recipes only
        compose.onNodeWithTag("food_search").performTextInput("platano")
        compose.waitForTag("food_banana")
        compose.onNodeWithTag("food_results").performScrollToNode(hasTestTag("food_banana"))
        compose.onNodeWithTag("food_banana").performClick()
        compose.waitForTag("portion_confirm")
        compose.onNodeWithTag("portion_confirm").performSemanticsAction(SemanticsActions.OnClick)

        // Back on the plan: the banana is planned for today's breakfast, not yet in the diary.
        compose.waitForTag("plan_days")
        compose.waitForText("Plátano")
        click("plan_copy")
        compose.onNode(androidx.compose.ui.test.hasText("Añadir") and androidx.compose.ui.test.hasAnyAncestor(androidx.compose.ui.test.isDialog())).performClick()
        compose.waitForTag("plan_copied")
        compose.onNodeWithText("Añadido 1 alimento al diario de hoy.").assertExists()

        compose.onNodeWithTag("header_back").performSemanticsAction(SemanticsActions.OnClick) // scrolled off the tiny screen
        compose.waitForTag("nutrition_summary")
        compose.waitForText("1 × unidad mediana")
    }

    @Test
    fun minorsSeeNoCalorieFigures() {
        start(LocalDate.now().minusYears(15))
        compose.onNodeWithTag("minor_focus").performScrollTo().assertExists()
        compose.waitUntil(2_000) { compose.onAllNodesWithTag("total_kcal").fetchSemanticsNodes().isEmpty() }
    }
}
