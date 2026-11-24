package com.fitnex.app.ui

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fitnex.app.HiltTestActivity
import com.fitnex.app.navigation.TopLevelDestination
import com.fitnex.core.auth.AuthResult
import com.fitnex.core.data.profile.UserProfileRepository
import com.fitnex.core.testing.FakeAuthRepository
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Main navigation, with a signed-in user who already completed onboarding. */
@HiltAndroidTest
@Config(application = HiltTestApplication::class)
@RunWith(AndroidJUnit4::class)
class FitnexNavigationTest {
    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<HiltTestActivity>()

    @Inject lateinit var auth: FakeAuthRepository
    @Inject lateinit var profiles: UserProfileRepository

    @Before
    fun setUp() {
        hilt.inject()
        runBlocking {
            val user = (auth.signUp("ana@example.com", "secreto123") as AuthResult.Success).value
            profiles.createAtSignUp(user.uid, user.email, LocalDate.of(2000, 5, 1))
            profiles.saveAndCompleteOnboarding(profiles.get(user.uid)!!.copy(name = "Ana"))
        }
        compose.setContent { TestApp() }
        compose.waitForText("Tu progreso empieza hoy")
    }

    @Test
    fun newAccountSeesZeroStatsWithNoticeThatLeadsToWorkout() {
        compose.onNodeWithTag("tile_workouts").assertTextContains("0", substring = true)
        compose.onNodeWithTag("tile_sets").assertTextContains("0", substring = true)
        compose.onNodeWithTag("tile_volume").performScrollTo().assertTextContains("0", substring = true)
        compose.onNodeWithTag("chart_frequency").performScrollTo().assertExists()
        compose.onNodeWithTag("section_history").performScrollTo().assertExists()
        compose.onNodeWithTag("progress_go_train").performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForText("Todavía no tienes rutinas")
        compose.onNodeWithTag("tab_workout").assertIsSelected()
    }

    @Test
    fun appHasExactlyThreeMainSections() {
        assertEquals(3, TopLevelDestination.entries.size)
        listOf("tab_progress", "tab_nutrition", "tab_workout").forEach {
            compose.onAllNodesWithTag(it).assertCountEquals(1)
        }
    }

    @Test
    fun switchesTabs() {
        compose.onNodeWithTag("tab_nutrition").performClick()
        compose.waitForTag("nutrition_summary")
        compose.onNodeWithTag("tab_workout").performClick()
        compose.waitForText("Todavía no tienes rutinas")
    }

    @Test
    fun aiButtonOpensChatWithScreenContextAndBackReturns() {
        compose.onNodeWithTag("tab_nutrition").performClick()
        compose.onNodeWithTag("fitnex_ai_button").performClick()
        compose.onNodeWithText("Abierto desde: Nutrición").assertExists()
        compose.onNodeWithTag("tab_progress").assertDoesNotExist()
        compose.onNodeWithTag("fitnex_ai_button").assertDoesNotExist()
        compose.onNodeWithTag("header_back").performClick()
        compose.waitForTag("nutrition_summary")
    }

    @Test
    fun calendarOpensFromProgressAndKeepsAiWithCalendarContext() {
        compose.onNodeWithTag("open_calendar").performClick()
        compose.onNodeWithTag("calendar_month_title").assertExists()
        compose.onNodeWithText("No hay actividad registrada este día.").assertExists()
        compose.onNodeWithTag("tab_progress").assertDoesNotExist()
        compose.onNodeWithTag("fitnex_ai_button").performClick()
        compose.onNodeWithText("Abierto desde: Calendario").assertExists()
    }

    @Test
    fun calendarAlsoOpensFromWorkout() {
        compose.onNodeWithTag("tab_workout").performClick()
        compose.onNodeWithTag("open_calendar").performClick()
        compose.onNodeWithTag("calendar_month_title").assertExists()
        compose.onNodeWithTag("header_back").performClick()
        compose.waitForText("Todavía no tienes rutinas")
    }

    @Test
    fun settingsPersistThemeChoiceAndHideAiButton() {
        compose.onNodeWithTag("open_settings").performClick()
        compose.waitForTag("menu_settings")
        compose.onNodeWithTag("menu_settings").performClick()
        compose.waitForTag("settings_row_appearance")
        compose.onNodeWithTag("settings_row_appearance").performScrollTo().performClick()
        compose.waitForText("Claro")
        compose.onNodeWithTag("fitnex_ai_button").assertDoesNotExist()
        compose.onNodeWithText("Claro").performScrollTo().performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasText("Claro") and isSelected()).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Claro").assertIsSelected()
        compose.onNodeWithText("Oscuro").assertIsNotSelected()
    }

    @Test
    fun settingsShowsAccountAndEditProfileReturns() {
        compose.onNodeWithTag("open_settings").performClick()
        compose.waitForTag("menu_settings")
        compose.onNodeWithTag("menu_settings").performClick()
        compose.waitForTag("settings_row_account")
        compose.onNodeWithTag("settings_row_account").performClick()
        compose.waitForTag("account_section")
        compose.onNodeWithText("ana@example.com").assertExists()
        compose.onNodeWithTag("account_edit_profile").performScrollTo().performClick()
        compose.waitForText("Editar perfil")
        compose.waitForTag("onb_name")
        compose.onNodeWithTag("header_back").performClick()
        compose.waitForTag("account_section")
    }
}
