package com.fitnex.app.ui

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performTouchInput
import com.fitnex.core.anatomy.geometry.P2
import com.fitnex.core.anatomy.map2d.BodyAtlas2D
import com.fitnex.core.anatomy.map2d.BodyView
import com.fitnex.core.anatomy.map2d.MapFit
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fitnex.app.HiltTestActivity
import com.fitnex.core.auth.AuthResult
import com.fitnex.core.data.profile.UserProfileRepository
import com.fitnex.core.data.training.RoutineRepository
import com.fitnex.core.data.training.WorkoutSessionRepository
import com.fitnex.core.model.Routine
import com.fitnex.core.model.RoutineExercise
import com.fitnex.core.testing.FakeAuthRepository
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@HiltAndroidTest
@Config(application = HiltTestApplication::class)
@RunWith(AndroidJUnit4::class)
class ProgressCalendarFlowTest {
    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createAndroidComposeRule<HiltTestActivity>()

    @Inject lateinit var auth: FakeAuthRepository
    @Inject lateinit var profiles: UserProfileRepository
    @Inject lateinit var routines: RoutineRepository
    @Inject lateinit var sessions: WorkoutSessionRepository

    private val routine = Routine(
        "r1", "Empuje", emptySet(), false,
        listOf(RoutineExercise("re1", "bench_press", "Press de banca", 2, 10, 90, 25f)),
        Instant.parse("2026-11-01T10:00:00Z"),
    )

    @Before
    fun setUp() {
        hilt.inject()
        runBlocking {
            val user = (auth.signUp("ana@example.com", "secreto123") as AuthResult.Success).value
            profiles.createAtSignUp(user.uid, user.email, LocalDate.of(2000, 5, 1))
            profiles.saveAndCompleteOnboarding(profiles.get(user.uid)!!.copy(name = "Ana"))
            routines.save(user.uid, routine)
            // A real finished workout today: 10×25 and 8×30.
            val id = sessions.start(user.uid, routine)
            sessions.logSet(id, routine.exercises.single(), 0, 10, 25f)
            sessions.logSet(id, routine.exercises.single(), 0, 8, 30f)
            sessions.finish(id)
        }
        compose.setContent { TestApp() }
    }

    @Test
    fun dashboardShowsRealNumbersAndExercisePage() {
        compose.waitForTag("tile_workouts")
        compose.onNodeWithTag("tile_workouts").assertTextContains("1", substring = true)
        compose.onNodeWithTag("tile_sets").assertTextContains("2", substring = true)
        compose.onNodeWithTag("tile_sets").assertTextContains("18 repeticiones", substring = true)
        compose.onNodeWithTag("tile_volume").assertTextContains("490", substring = true) // 250 + 240
        compose.onNodeWithTag("chart_frequency").assertExists()
        compose.onNodeWithTag("progress_first_workout_notice").assertDoesNotExist() // gone after the first workout

        compose.onNodeWithTag("exercise_stats_bench_press").performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForTag("exstats_sessions")
        compose.onNodeWithTag("exstats_best_weight").assertTextContains("30", substring = true)
        compose.onNodeWithText("10×25  ·  8×30").performScrollTo().assertExists()
        compose.onNodeWithTag("fitnex_ai_button").performClick()
        compose.waitForText("Abierto desde: Estadísticas")
    }

    @Test
    fun muscleMapLeadsToMuscleDetailBuiltFromRealSets() {
        // Tap the pectoral where it is actually drawn on the 2D map.
        compose.waitForTag("muscle_map_2d")
        val map = compose.onNodeWithTag("muscle_map_2d").performScrollTo()
        val size = map.fetchSemanticsNode().size
        val fit = MapFit.of(Size(size.width.toFloat(), size.height.toFloat()))
        val pec = BodyAtlas2D.shapes(BodyView.FRONT).first { it.muscleId == "pectoralis_major" }.polygon
        val target = fit.toPx(P2(pec.map { it.x }.average().toFloat(), pec.map { it.y }.average().toFloat()))
        map.performTouchInput { click(target) }

        compose.waitForTag("muscle_info")
        compose.onNodeWithTag("muscle_info", useUnmergedTree = true).assertExists()
        compose.waitForText("2 series ponderadas · 1 sesión en el periodo")
        compose.onNodeWithTag("muscle_open_detail").performScrollTo().performSemanticsAction(SemanticsActions.OnClick)

        // Muscle page: two bench sets in one session, primary muscle → 2 weighted sets.
        compose.waitForTag("muscle_sessions")
        compose.onNodeWithTag("muscle_sessions").assertTextContains("1", substring = true)
        compose.onNodeWithTag("muscle_sets").assertTextContains("2", substring = true)
        compose.onNodeWithTag("muscle_exercises_primary").performScrollTo()
        compose.onNodeWithTag("muscle_history").performScrollTo()
        compose.onNodeWithText("2 series · Press de banca").assertExists()
        compose.onNodeWithTag("fitnex_ai_button").performClick()
        compose.waitForText("Abierto desde: Detalle muscular")
        compose.onNodeWithTag("header_back").performClick()

        // Muscle page → exercise page → secondary muscle page (cross-feature navigation).
        compose.waitForTag("muscle_exercise_bench_press")
        compose.onNodeWithTag("muscle_exercise_bench_press").performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForTag("detail_muscle_triceps_brachii")
        compose.onNodeWithTag("detail_muscle_triceps_brachii").performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForTag("muscle_sets")
        compose.onNodeWithTag("muscle_sets").assertTextContains("1", substring = true) // 2 sets × 0.5
    }

    @Test
    fun calendarShowsTodaysWorkoutAndCanScheduleAndCancel() {
        compose.waitForTag("open_calendar")
        compose.onNodeWithTag("open_calendar").performClick()
        compose.waitForTag("calendar_session")
        compose.onNodeWithText("Press de banca: 10×25 · 8×30").assertExists()

        compose.onNodeWithTag("calendar_schedule").performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForTag("pick_Empuje")
        compose.onNodeWithTag("pick_Empuje").performClick()
        compose.waitForText("Programado: Empuje")
        compose.onNodeWithTag("cancel_Empuje").performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("cancel_Empuje").fetchSemanticsNodes().isEmpty() }
    }
}
