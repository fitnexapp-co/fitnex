package com.fitnex.app.ui

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fitnex.app.HiltTestActivity
import com.fitnex.core.data.profile.UserProfileRepository
import com.fitnex.core.testing.FakeAuthRepository
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** End-to-end account journeys through the real UI, with identity simulated in memory. */
@HiltAndroidTest
@Config(application = HiltTestApplication::class)
@RunWith(AndroidJUnit4::class)
class AuthFlowTest {
    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<HiltTestActivity>()

    @Inject lateinit var auth: FakeAuthRepository
    @Inject lateinit var profiles: UserProfileRepository

    @Before
    fun setUp() {
        hilt.inject()
        compose.setContent { TestApp() }
        compose.waitForTag("welcome_create")
    }

    /** Opens the date sheet and turns the three wheels, exactly like a user swiping them. */
    private fun pickDate(fieldTag: String, date: LocalDate) {
        compose.onNodeWithTag(fieldTag).performClick()
        compose.waitForTag("wheel_year")
        val minYear = LocalDate.now().year - 120
        compose.onNodeWithTag("wheel_year").performScrollToIndex(date.year - minYear)
        compose.onNodeWithTag("wheel_month").performScrollToIndex(date.monthValue - 1)
        compose.onNodeWithTag("wheel_day").performScrollToIndex(date.dayOfMonth - 1)
        compose.waitForIdle()
        compose.onNodeWithTag("picker_done").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("wheel_year").fetchSemanticsNodes().isEmpty() }
    }

    private fun fillSignUp(birth: LocalDate, email: String = "ana@example.com", password: String = "secreto123") {
        compose.onNodeWithTag("welcome_create").performScrollTo().performClick()
        compose.waitForTag("signup_birth")
        pickDate("signup_birth", birth)
        compose.onNodeWithTag("signup_email").performTextInput(email)
        compose.onNodeWithTag("signup_password").performTextInput(password)
        compose.onNodeWithTag("signup_confirm").performTextInput(password)
        compose.onNodeWithTag("signup_submit").performScrollTo().performClick()
    }

    @Test
    fun signUpThenOnboardingLandsOnDashboardWithSavedProfile() {
        fillSignUp(LocalDate.of(2000, 5, 1))

        // Session switches to onboarding automatically; birth date carried over from sign-up.
        compose.waitForTag("onb_name")
        compose.onNodeWithTag("onb_name").performTextInput("Ana")
        // Weight on wheels: 72 , 5 kg (whole wheel starts at 25 kg).
        compose.onNodeWithTag("onb_weight").performScrollTo().performClick()
        compose.waitForTag("wheel_whole")
        compose.onNodeWithTag("wheel_whole").performScrollToIndex(72 - 25)
        compose.onNodeWithTag("wheel_decimal").performScrollToIndex(5)
        compose.waitForIdle()
        compose.onNodeWithTag("picker_done").performClick()
        compose.waitForText("72,5 kg")
        compose.onNodeWithTag("onb_next").performClick()           // about → training
        compose.waitForTag("onb_skip")
        compose.onNodeWithTag("onb_skip").performClick()           // training → equipment
        compose.onNodeWithText("Mancuernas").performClick()
        compose.onNodeWithTag("onb_next").performClick()           // equipment → food
        compose.onNodeWithTag("onb_skip").performClick()           // food → privacy
        compose.waitForTag("onb_ai_consent")
        compose.onNodeWithTag("onb_next").performClick()           // finish

        compose.waitForText("Tu progreso empieza hoy")
        val profile = runBlocking { profiles.get(auth.currentUser.first()!!.uid)!! }
        assertEquals("Ana", profile.name)
        assertEquals(java.time.LocalDate.of(2000, 5, 1), profile.birthDate)
        assertTrue(profile.equipment.contains(com.fitnex.core.model.Equipment.DUMBBELL))
        assertEquals(72.5f, profile.weightKg!!, 0.001f)
        assertNull("AI consent is opt-in", profile.aiConsentAt)
        assertEquals(1, auth.verificationEmailsSent)
    }

    @Test
    fun underFourteenIsBlockedBeforeAnyAccountIsCreated() {
        val thirteen = LocalDate.now().minusYears(13)
        fillSignUp(thirteen)
        compose.waitForTag("signup_blocked")
        assertNull(runBlocking { auth.currentUser.first() })
        assertEquals(0, auth.verificationEmailsSent)
    }

    @Test
    fun minorAboveMinimumGetsProtectedNoteInOnboarding() {
        val fifteen = LocalDate.now().minusYears(15)
        fillSignUp(fifteen)
        compose.waitForTag("onb_minor_note")
    }

    @Test
    fun wrongPasswordShowsGenericErrorAndForgotPasswordWorks() {
        runBlocking { auth.signUp("ana@example.com", "secreto123"); auth.signOut() }
        compose.onNodeWithTag("welcome_signin").performScrollTo().performClick()
        compose.waitForTag("signin_email")
        compose.onNodeWithTag("signin_email").performTextInput("ana@example.com")
        compose.onNodeWithTag("signin_password").performTextInput("incorrecta1")
        compose.onNodeWithTag("signin_submit").performClick()
        compose.waitForText("Correo o contraseña incorrectos.")

        compose.onNodeWithTag("signin_forgot").performClick()
        compose.waitForTag("reset_email")
        compose.onNodeWithTag("reset_email").performTextInput("ana@example.com")
        compose.onNodeWithTag("reset_submit").performClick()
        compose.waitForTag("reset_sent")
        assertEquals(listOf("ana@example.com"), auth.passwordResetsSent)
    }

    @Test
    fun signOutAndDeleteAccountReturnToWelcome() {
        fillSignUp(LocalDate.of(2000, 5, 1))
        compose.waitForTag("onb_name")
        compose.onNodeWithTag("onb_name").performTextInput("Ana")
        repeat(5) {
            compose.onNodeWithTag("onb_next").performClick()
            compose.waitForIdle()
        }
        compose.waitForTag("open_settings")

        // Delete account: wrong password keeps everything, right password wipes and signs out.
        compose.onNodeWithTag("open_settings").performClick()
        compose.waitForTag("menu_settings")
        compose.onNodeWithTag("menu_settings").performClick()
        compose.waitForTag("settings_row_account")
        compose.onNodeWithTag("settings_row_account").performClick()
        compose.waitForTag("account_delete")
        compose.onNodeWithTag("account_delete").performScrollTo().performClick()
        compose.onNodeWithTag("delete_password").performTextInput("nope")
        compose.onNodeWithTag("delete_confirm").performClick()
        compose.waitForText("Contraseña incorrecta.")
        val uid = runBlocking { auth.currentUser.first()!!.uid }

        compose.onNodeWithTag("delete_password").performTextClearance()
        compose.onNodeWithTag("delete_password").performTextInput("secreto123")
        compose.onNodeWithTag("delete_confirm").performClick()

        compose.waitForTag("welcome_create")
        assertNull(runBlocking { profiles.get(uid) })
    }
}
