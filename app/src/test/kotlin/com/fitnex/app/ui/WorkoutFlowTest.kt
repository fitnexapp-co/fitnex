package com.fitnex.app.ui

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fitnex.app.HiltTestActivity
import com.fitnex.core.auth.AuthResult
import com.fitnex.core.data.profile.UserProfileRepository
import com.fitnex.core.data.training.ExerciseRepository
import com.fitnex.core.data.training.RoutineRepository
import com.fitnex.core.testing.FakeAuthRepository
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@HiltAndroidTest
@Config(application = HiltTestApplication::class)
@RunWith(AndroidJUnit4::class)
class WorkoutFlowTest {
    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<HiltTestActivity>()

    @Inject lateinit var auth: FakeAuthRepository
    @Inject lateinit var profiles: UserProfileRepository
    @Inject lateinit var exercises: ExerciseRepository
    @Inject lateinit var routines: RoutineRepository
    private lateinit var uid: String

    @Before
    fun setUp() {
        hilt.inject()
        runBlocking {
            val user = (auth.signUp("ana@example.com", "secreto123") as AuthResult.Success).value
            uid = user.uid
            profiles.createAtSignUp(uid, user.email, LocalDate.of(2000, 5, 1))
            profiles.saveAndCompleteOnboarding(profiles.get(uid)!!.copy(name = "Ana"))
        }
        compose.setContent { TestApp() }
        compose.waitForTag("tab_workout")
        compose.onNodeWithTag("tab_workout").performClick()
        compose.waitForText("Todavía no tienes rutinas")
    }

    @Test
    fun libraryLoadsCatalogueFiltersAndOpensDetail() {
        compose.onNodeWithTag("open_library").performClick()
        // The catalogue has loaded (count shown), then the first exercise alphabetically is reachable
        // by scrolling: the list is lazy and the muscle-group tiles sit above it.
        compose.waitForTag("library_list")
        compose.waitUntil(5_000) { runCatching { compose.onNodeWithTag("library_list").performScrollToNode(hasTestTag("exercise_sit_up")) }.isSuccess }
        compose.onNodeWithTag("library_list").performScrollToNode(hasTestTag("exercise_sit_up"))
        compose.onNodeWithTag("library_list").performScrollToNode(hasTestTag("library_count"))
        compose.onNodeWithTag("library_count").assertTextContains("611 ejercicios")

        // Search ignores accents and matches muscle names and equipment words.
        compose.onNodeWithTag("library_search").performTextInput("gluteo medio banda pasos")
        compose.waitUntil(5_000) { runCatching { compose.onNodeWithTag("library_list").performScrollToNode(hasTestTag("exercise_band_lateral_walk")) }.isSuccess }
        compose.onNodeWithTag("exercise_bench_press").assertDoesNotExist()

        compose.onNodeWithTag("exercise_band_lateral_walk").performClick()
        compose.waitForText("Cómo se hace")
        compose.onNodeWithTag("exercise_media").assertExists()
        compose.onNodeWithText("● Glúteo medio").assertExists()

        // AI is available here with the exercise-detail context.
        compose.onNodeWithTag("detail_favorite").performClick()
        compose.waitUntil(5_000) { runBlocking { exercises.observeIsFavorite(uid, "band_lateral_walk").first() } }
        compose.onNodeWithTag("fitnex_ai_button").performClick()
        compose.waitForText("Abierto desde: Detalle de ejercicio")
    }

    @Test
    fun createConfigureReorderAndDeleteRoutine() {
        // On Robolectric's small screen the scrolled-to button sits under the floating tab bar, so a
        // coordinate tap would hit the bar; invoke the button's click action directly instead.
        compose.onNodeWithText("Crear rutina").performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForTag("editor_name")
        compose.onNodeWithTag("fitnex_ai_button").assertDoesNotExist() // keeps the save button clear

        // Saving empty shows both errors and saves nothing.
        compose.onNodeWithTag("editor_save").performClick()
        compose.waitForText("Ponle un nombre a la rutina.")
        compose.onNodeWithText("Añade al menos un ejercicio.").assertExists()

        compose.onNodeWithTag("editor_name").performTextInput("Pierna")
        compose.onNodeWithTag("editor_add_exercises").performScrollTo().performClick()
        // The catalogue has loaded (count shown), then the first exercise alphabetically is reachable
        // by scrolling: the list is lazy and the muscle-group tiles sit above it.
        compose.waitForTag("library_list")
        compose.waitUntil(5_000) { runCatching { compose.onNodeWithTag("library_list").performScrollToNode(hasTestTag("exercise_sit_up")) }.isSuccess }
        compose.onNodeWithTag("library_list").performScrollToNode(hasTestTag("exercise_sit_up"))
        compose.onNodeWithTag("library_list").performScrollToNode(hasTestTag("exercise_goblet_squat"))
        compose.onNodeWithTag("exercise_goblet_squat").performClick()
        compose.onNodeWithTag("library_list").performScrollToNode(hasTestTag("exercise_lunge"))
        compose.onNodeWithTag("exercise_lunge").performClick()
        compose.onNodeWithText("Añadir (2)").assertExists()
        compose.onNodeWithTag("library_confirm").performClick()

        // Back in the editor with both exercises; configure the first with the wheels.
        compose.waitForTag("item_goblet_squat")
        compose.onNodeWithTag("item_lunge").assertExists()
        // Robolectric's screen is 320 px wide: a centre tap lands on the card's ▲ button, so use
        // the card's own click action (what TalkBack does) instead of a coordinate tap.
        compose.onNodeWithTag("item_goblet_squat").performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForTag("wheel_sets")
        compose.onNodeWithTag("wheel_sets").performScrollToIndex(3)  // 4 series
        compose.onNodeWithTag("wheel_reps").performScrollToIndex(7)  // 8 reps
        compose.onNodeWithTag("wheel_weight").performScrollToIndex(40) // 20 kg
        compose.waitForIdle()
        compose.onNodeWithTag("picker_done").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("wheel_sets").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag("summary_goblet_squat", useUnmergedTree = true).assertTextContains("4 × 8 reps", substring = true)
        compose.onNodeWithTag("summary_goblet_squat", useUnmergedTree = true).assertTextContains("20 kg", substring = true)

        // Reorder: move the goblet squat below the lunge, then save.
        // Same action TalkBack offers on the card (on screen it is the ≡ drag handle).
        val moveDown = compose.onNodeWithTag("item_goblet_squat").fetchSemanticsNode()
            .config[androidx.compose.ui.semantics.SemanticsActions.CustomActions]
            .first { it.label == "Bajar Sentadilla goblet" }.action
        compose.runOnIdle { moveDown() }
        compose.onNodeWithTag("editor_save").performClick()
        compose.waitForTag("routine_Pierna")

        val saved = runBlocking { routines.observeRoutines(uid).first().single() }
        assertEquals(listOf("lunge", "goblet_squat"), saved.exercises.map { it.exerciseId })
        assertEquals(4, saved.exercises[1].sets)
        assertEquals(8, saved.exercises[1].reps)
        assertEquals(20f, saved.exercises[1].targetWeightKg)

        // Edit → delete with confirmation → empty state again.
        // The row's centre is its start button on the 320 px test screen: use the row's own click action.
        compose.onNodeWithTag("routine_Pierna").performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForTag("editor_delete")
        compose.onNodeWithTag("editor_delete").performClick()
        compose.onNodeWithText("Eliminar").performClick()
        compose.waitForText("Todavía no tienes rutinas")
        assertTrue(runBlocking { routines.observeRoutines(uid).first().isEmpty() })
    }
}
