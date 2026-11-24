package com.fitnex.app.ui

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fitnex.app.FakeRestAlerts
import com.fitnex.app.HiltTestActivity
import com.fitnex.core.auth.AuthResult
import com.fitnex.core.data.profile.UserProfileRepository
import com.fitnex.core.data.training.RoutineRepository
import com.fitnex.core.data.training.WorkoutSessionRepository
import com.fitnex.core.model.Routine
import com.fitnex.core.model.RoutineExercise
import com.fitnex.core.model.SessionStatus
import com.fitnex.core.testing.FakeAuthRepository
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import java.time.Instant
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

@HiltAndroidTest
@Config(application = HiltTestApplication::class)
@RunWith(AndroidJUnit4::class)
class LiveWorkoutFlowTest {
    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createAndroidComposeRule<HiltTestActivity>()

    @Inject lateinit var auth: FakeAuthRepository
    @Inject lateinit var profiles: UserProfileRepository
    @Inject lateinit var routines: RoutineRepository
    @Inject lateinit var sessions: WorkoutSessionRepository
    @Inject lateinit var alerts: FakeRestAlerts
    private lateinit var uid: String

    private val routine = Routine(
        "r1", "Empuje", emptySet(), false,
        listOf(
            RoutineExercise("re1", "bench_press", "Press de banca", 2, 10, 90, 25f),
            RoutineExercise("re2", "push_up", "Flexiones", 1, 15, 60, null),
        ),
        Instant.parse("2026-11-01T10:00:00Z"),
    )

    @Before
    fun setUp() {
        hilt.inject()
        runBlocking {
            val user = (auth.signUp("ana@example.com", "secreto123") as AuthResult.Success).value
            uid = user.uid
            profiles.createAtSignUp(uid, user.email, LocalDate.of(2000, 5, 1))
            profiles.saveAndCompleteOnboarding(profiles.get(uid)!!.copy(name = "Ana"))
            routines.save(uid, routine)
        }
        compose.setContent { TestApp() }
        compose.waitForTag("tab_workout")
        compose.onNodeWithTag("tab_workout").performClick()
        compose.waitForTag("start_Empuje")
    }

    private fun tapDraft() = compose.onNodeWithTag("live_draft").performScrollTo().performSemanticsAction(SemanticsActions.OnClick)

    @Test
    fun fullWorkoutWithWheelsRestRepeatAndSummary() {
        compose.onNodeWithTag("start_Empuje").performSemanticsAction(SemanticsActions.OnClick) // FAB overlaps it on the tiny test screen
        compose.waitForTag("live_complete")
        compose.onNodeWithTag("live_exercise_name", useUnmergedTree = true).assertTextContains("Press de banca")
        compose.onNodeWithText("Primera vez que registras este ejercicio.").assertExists()
        compose.onNodeWithTag("live_draft", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("live_draft").assertTextContains("10 reps × 25 kg", substring = true) // proposed from the routine target
        compose.onNodeWithTag("fitnex_ai_button").assertDoesNotExist() // full-screen mode
        compose.onNodeWithTag("tab_workout").assertDoesNotExist()

        // Set 1 with one tap → rest starts and a notification is scheduled.
        compose.onNodeWithTag("live_complete").performClick()
        compose.waitForTag("live_set_1")
        compose.waitForTag("live_rest")
        assertEquals("Press de banca", alerts.scheduled.last().second)
        compose.onNodeWithTag("live_rest_skip").performScrollTo().performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("live_rest").fetchSemanticsNodes().isEmpty() }

        // Set 2: raise the weight to 30 kg on the ruler (the same adjustable action TalkBack uses).
        compose.onNodeWithTag("ruler_kg").performScrollTo().performSemanticsAction(SemanticsActions.SetProgress) { it(30f) }
        compose.waitForText("10 reps × 30 kg")
        compose.onNodeWithTag("live_complete").performClick()
        compose.waitForTag("live_set_2")
        compose.onNodeWithTag("live_rest_skip").performScrollTo().performClick()
        compose.waitForText("¡Ejercicio completado! Pasa al siguiente o añade una serie.")

        // Next exercise: body weight, then "repetir anterior" logs an identical set.
        compose.onNodeWithTag("live_next").performScrollTo().performClick()
        compose.waitForText("15 reps") // flexiones: se registran solo con repeticiones
        compose.onNodeWithTag("live_complete").performClick()
        compose.waitForTag("live_set_1")
        compose.onNodeWithTag("live_rest_skip").performScrollTo().performClick()
        compose.onNodeWithTag("live_repeat").performScrollTo().performClick()
        compose.waitForTag("live_set_2")
        compose.onNodeWithTag("live_progress").assertTextContains("4/3 series", substring = true)

        // Finish → summary computed from the saved sets.
        compose.onNodeWithTag("live_finish").performScrollTo().performClick()
        compose.onNodeWithText("Finalizar").performClick()
        compose.waitForTag("summary_done")
        compose.waitForTag("summary_sets")
        compose.onNodeWithTag("summary_sets").assertTextContains("4", substring = true)
        compose.onNodeWithTag("summary_reps").assertTextContains("50", substring = true) // 10+10+15+15
        compose.onNodeWithTag("summary_volume").assertTextContains("550", substring = true) // 10×25 + 10×30
        compose.onNodeWithText("Pectoral mayor").assertExists()

        compose.onNodeWithTag("summary_done").performClick()
        compose.waitForTag("start_Empuje")
        compose.onNodeWithTag("resume_workout").assertDoesNotExist()
        assertNull(runBlocking { sessions.observeActive(uid).first() })
    }

    @Test
    fun leavingKeepsTheSessionAndHomeOffersToContinue() {
        compose.onNodeWithTag("start_Empuje").performSemanticsAction(SemanticsActions.OnClick) // FAB overlaps it on the tiny test screen
        compose.waitForTag("live_complete")
        compose.onNodeWithTag("live_complete").performClick()
        compose.waitForTag("live_set_1")

        compose.onNodeWithTag("live_close").performClick()
        compose.waitForTag("resume_workout")
        compose.onNodeWithText("series registradas: 1", substring = true).assertExists()

        compose.onNodeWithTag("resume_workout").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForTag("live_set_1") // the logged set is still there
        val active = runBlocking { sessions.observeActive(uid).first()!! }
        assertEquals(1, active.sets.size)

        // Finishing with sets keeps it; discarding (no sets) is covered in repository tests.
        compose.onNodeWithTag("live_finish").performScrollTo().performClick()
        compose.onNodeWithText("Finalizar").performClick()
        compose.waitForTag("summary_done")
        assertTrue(runBlocking { sessions.observeActive(uid).first() } == null)
    }
}
