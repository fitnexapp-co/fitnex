package com.fitnex.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import com.fitnex.core.data.session.SessionState
import com.fitnex.feature.auth.AuthFlow
import com.fitnex.feature.auth.onboarding.EditProfileRoute
import com.fitnex.feature.auth.onboarding.OnboardingFlow
import com.fitnex.feature.auth.onboarding.editProfileScreen
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.fitnex.app.R
import com.fitnex.app.navigation.TopLevelDestination
import com.fitnex.core.designsystem.component.FitnexAiButton
import com.fitnex.core.designsystem.component.FitnexBackground
import com.fitnex.core.designsystem.component.FitnexBottomBar
import com.fitnex.core.designsystem.component.FitnexBottomBarItem
import com.fitnex.core.designsystem.theme.FitnexTheme
import com.fitnex.core.designsystem.theme.FitnexAppearance
import com.fitnex.core.designsystem.theme.FitnexMotion
import com.fitnex.core.designsystem.component.LocalProfilePhoto
import com.fitnex.core.designsystem.component.ProvideLiquidBackdrop
import com.fitnex.core.designsystem.component.liquidBackdropSource
import com.fitnex.core.designsystem.component.rememberLiquidBackdrop
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.ImageBitmap
import com.fitnex.core.designsystem.theme.Spacing
import com.fitnex.core.model.AiScreenContext
import com.fitnex.feature.ai.aiChatScreen
import com.fitnex.feature.ai.navigateToAiChat
import com.fitnex.feature.calendar.CalendarRoute
import com.fitnex.feature.calendar.calendarScreen
import com.fitnex.feature.nutrition.AddFoodRoute
import com.fitnex.feature.nutrition.nutritionGraph
import com.fitnex.feature.profile.SettingsRoute
import com.fitnex.feature.profile.settingsScreen
import com.fitnex.feature.progress.ProgressRoute
import com.fitnex.feature.progress.ExerciseStatsRoute
import com.fitnex.feature.progress.MuscleDetailRoute
import com.fitnex.feature.progress.navigateToMuscle
import com.fitnex.feature.progress.progressGraph
import androidx.navigation.NavBackStackEntry
import androidx.navigation.toRoute
import com.fitnex.feature.workout.ExerciseDetailRoute
import com.fitnex.feature.workout.ExerciseLibraryRoute
import com.fitnex.feature.workout.workoutGraph

/** Space reserved at the bottom of tab screens so content scrolls clear of the floating bar. */
private val TabBottomPadding = PaddingValues(bottom = 112.dp)

/** Secondary screens only need room for the floating AI button. */
private val SecondaryBottomPadding = PaddingValues(bottom = 96.dp)

/**
 * Root of the UI. Which experience is shown is decided by the session alone, so signing in,
 * finishing onboarding, signing out or deleting the account all switch screens automatically
 * and no back-stack entry from one account can leak into another.
 */
@Composable
fun FitnexRoot(
    darkTheme: Boolean,
    session: SessionState,
    appearance: FitnexAppearance = FitnexAppearance(),
    profilePhoto: ImageBitmap? = null,
    companionName: String? = null,
) {
    FitnexTheme(darkTheme = darkTheme, appearance = appearance) {
        CompositionLocalProvider(LocalProfilePhoto provides profilePhoto, com.fitnex.core.designsystem.component.LocalCompanionName provides companionName) {
        FitnexBackground {
            val key = when (session) {
                SessionState.SignedOut -> "signed-out"
                is SessionState.Restoring -> "restoring:${session.user.uid}"
                is SessionState.NeedsOnboarding -> "onboarding:${session.user.uid}"
                is SessionState.Ready -> "ready:${session.user.uid}"
            }
            Crossfade(targetState = key, animationSpec = FitnexMotion.spec(350), label = "session") { shown ->
                when {
                    shown == "signed-out" -> AuthFlow()
                    shown.startsWith("restoring:") -> RestoringScreen()
                    shown.startsWith("onboarding:") -> OnboardingFlow()
                    else -> MainNavigation()
                }
            }
        }
        }
    }
}

/** Signed in on a device that has none of this user's data yet: the cloud copy is downloading. */
@Composable
private fun RestoringScreen() {
    androidx.compose.foundation.layout.Column(
        Modifier.fillMaxSize().padding(Spacing.xxl).testTag("restoring"),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        androidx.compose.material3.CircularProgressIndicator(color = FitnexTheme.colors.accent)
        androidx.compose.material3.Text(
            stringResource(R.string.restoring_data),
            style = FitnexTheme.typography.titleMedium,
            color = FitnexTheme.colors.textPrimary,
            modifier = Modifier.padding(top = Spacing.lg),
        )
    }
}

/** The signed-in app: three tabs, secondary screens and the global FITNEX AI button. */
@Composable
fun MainNavigation(navController: NavHostController = rememberNavController()) {
        val backStackEntry by navController.currentBackStackEntryAsState()
        val destination = backStackEntry?.destination
        val currentTab = TopLevelDestination.entries.firstOrNull { destination.isInHierarchy(it.routeClass) }
        val aiContext = aiContextFor(destination, currentTab, backStackEntry)
        // The avatar in every header opens the account menu (profiles, Ajustes).
        var accountMenu by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
        val openSettings = { accountMenu = true }
        val openCalendar = { navController.navigate(CalendarRoute) { launchSingleTop = true } }
        // Reduced motion: a short cross-fade only (still shows that the screen changed).
        val reduce = FitnexTheme.appearance.reduceMotion
        val backdrop = rememberLiquidBackdrop()

        Box(Modifier.fillMaxSize()) {
            // Screen content is the blur SOURCE: nothing inside it may sample the backdrop
            // (it would draw itself into itself), so it gets no backdrop; only the floating chrome does.
            NavHost(
                navController = navController,
                startDestination = ProgressRoute,
                modifier = Modifier.fillMaxSize().liquidBackdropSource(backdrop),
                // iOS-like push/pop: the new screen slides in from the right edge while the previous
                // one drifts a little to the left and dims; back reverses it (and follows the
                // predictive back gesture on Android 14+). Switching tabs is a quick fade-through.
                enterTransition = {
                    when {
                        reduce -> fadeIn(tween(90))
                        isTabSwitch(initialState, targetState) -> TabFadeIn
                        else -> slideInHorizontally(tween(PUSH_MS, easing = PushEasing)) { it } + fadeIn(tween(PUSH_FADE_MS))
                    }
                },
                exitTransition = {
                    when {
                        reduce -> fadeOut(tween(60))
                        isTabSwitch(initialState, targetState) -> TabFadeOut
                        else -> slideOutHorizontally(tween(PUSH_MS, easing = PushEasing)) { -it / 4 } + fadeOut(tween(PUSH_FADE_MS))
                    }
                },
                popEnterTransition = {
                    when {
                        reduce -> fadeIn(tween(90))
                        isTabSwitch(initialState, targetState) -> TabFadeIn
                        else -> slideInHorizontally(tween(PUSH_MS, easing = PushEasing)) { -it / 4 } + fadeIn(tween(PUSH_FADE_MS))
                    }
                },
                popExitTransition = {
                    when {
                        reduce -> fadeOut(tween(60))
                        isTabSwitch(initialState, targetState) -> TabFadeOut
                        else -> slideOutHorizontally(tween(PUSH_MS, easing = PushEasing)) { it } + fadeOut(tween(PUSH_FADE_MS))
                    }
                },
                // The back gesture follows the finger with the same motion (not the default shrink):
                // the page slides off to the right and the previous one comes back from the left.
                predictivePopEnterTransition = { _ ->
                    when {
                        reduce -> fadeIn(tween(90))
                        isTabSwitch(initialState, targetState) -> TabFadeIn
                        else -> slideInHorizontally(tween(PUSH_MS, easing = androidx.compose.animation.core.LinearEasing)) { -it / 4 }
                    }
                },
                predictivePopExitTransition = { _ ->
                    when {
                        reduce -> fadeOut(tween(60))
                        isTabSwitch(initialState, targetState) -> TabFadeOut
                        else -> slideOutHorizontally(tween(PUSH_MS, easing = androidx.compose.animation.core.LinearEasing)) { it }
                    }
                },
            ) {
                progressGraph(
                    nav = navController,
                    tabPadding = TabBottomPadding,
                    secondaryPadding = SecondaryBottomPadding,
                    onOpenCalendar = openCalendar,
                    onOpenSettings = openSettings,
                    onGoTrain = { navController.navigateToTab(TopLevelDestination.WORKOUT) },
                    onOpenExerciseDetail = { navController.navigate(ExerciseDetailRoute(it)) },
                    onAnalyzeProgress = { navController.navigateToAiChat(AiScreenContext.PROGRESS, ask = true) },
                )
                nutritionGraph(navController, TabBottomPadding, SecondaryBottomPadding, onOpenSettings = openSettings)
                workoutGraph(
                    nav = navController,
                    tabPadding = TabBottomPadding,
                    secondaryPadding = SecondaryBottomPadding,
                    onOpenCalendar = openCalendar,
                    onOpenSettings = openSettings,
                    onOpenMuscle = navController::navigateToMuscle,
                )
                calendarScreen(SecondaryBottomPadding, onBack = navController::popBackStack)
                settingsScreen(
                    PaddingValues(bottom = Spacing.xxl),
                    onBack = navController::popBackStack,
                    onEditProfile = { navController.navigate(EditProfileRoute) { launchSingleTop = true } },
                )
                editProfileScreen(onDone = navController::popBackStack)
                aiChatScreen(onBack = navController::popBackStack)
            }

          ProvideLiquidBackdrop(backdrop) {
            // The bottom of the tab screens dissolves (blurred and darker) under the floating bar.
            AnimatedVisibility(
                visible = currentTab != null,
                enter = fadeIn(tween(220)),
                exit = fadeOut(tween(160)),
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                com.fitnex.core.designsystem.component.BottomScreenFade(height = 170.dp)
            }

            // A workout in progress floats above the tab bar, like a music app's mini player.
            val activeVm: ActiveWorkoutViewModel = androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel()
            val activeWorkout by activeVm.active.collectAsStateWithLifecycle()
            AnimatedVisibility(
                visible = currentTab != null && activeWorkout != null,
                enter = if (reduce) fadeIn(tween(90)) else slideInVertically(androidx.compose.animation.core.spring(dampingRatio = 0.8f, stiffness = 380f)) { it / 2 } + fadeIn(),
                exit = if (reduce) fadeOut(tween(60)) else slideOutVertically(tween(200)) { it / 2 } + fadeOut(tween(160)),
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(start = Spacing.gutter, end = Spacing.gutter, bottom = 92.dp),
            ) {
                activeWorkout?.let { MiniWorkoutBar(it, onOpen = { navController.navigate(com.fitnex.feature.workout.LiveWorkoutRoute()) { launchSingleTop = true } }) }
            }

            // Bottom bar only on the three main sections.
            AnimatedVisibility(
                visible = currentTab != null,
                enter = if (reduce) fadeIn(tween(90)) else slideInVertically { it } + fadeIn(),
                exit = if (reduce) fadeOut(tween(60)) else slideOutVertically { it } + fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                FitnexBottomBar(
                    selectedIndex = TopLevelDestination.entries.indexOf(currentTab),
                    itemCount = TopLevelDestination.entries.size,
                    onSelect = { navController.navigateToTab(TopLevelDestination.entries[it]) },
                    trailing = {
                        FitnexAiButton(
                            onClick = { aiContext?.let { navController.navigateToAiChat(it) } },
                            contentDescription = stringResource(R.string.open_fitnex_ai),
                            modifier = Modifier.testTag("fitnex_ai_button"),
                            diameter = 64.dp,
                        )
                    },
                ) {
                    TopLevelDestination.entries.forEachIndexed { i, tab ->
                        FitnexBottomBarItem(
                            index = i,
                            selected = tab == currentTab,
                            onClick = { navController.navigateToTab(tab) },
                            icon = tab.icon,
                            label = stringResource(tab.labelRes),
                            modifier = Modifier.testTag(tab.testTag),
                        )
                    }
                }
            }

            // FITNEX AI everywhere it has a context (spec §19); hidden in the chat and settings.
            // On the three tabs it lives beside the bottom bar; elsewhere it floats bottom-right.
            AnimatedVisibility(
                visible = aiContext != null && currentTab == null,
                enter = if (reduce) fadeIn(tween(90)) else scaleIn(initialScale = 0.85f) + fadeIn(),
                exit = if (reduce) fadeOut(tween(60)) else scaleOut(targetScale = 0.85f) + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(end = Spacing.gutter, bottom = Spacing.xl),
            ) {
                FitnexAiButton(
                    onClick = { aiContext?.let { navController.navigateToAiChat(it) } },
                    contentDescription = stringResource(R.string.open_fitnex_ai),
                    modifier = Modifier.testTag("fitnex_ai_button"),
                )
            }

            com.fitnex.feature.profile.AccountMenu(
                visible = accountMenu,
                onDismiss = { accountMenu = false },
                onOpenSettings = { navController.navigate(SettingsRoute()) { launchSingleTop = true } },
                onManageProfiles = { navController.navigate(SettingsRoute(com.fitnex.feature.profile.SettingsPage.TRAIN_TOGETHER.name)) { launchSingleTop = true } },
            )
          }
        }
}

/**
 * Switching tabs is a fade-through: the old tab is gone in 90 ms, then the new one fades in, so
 * two full screens are never visible on top of each other.
 */
private val TabFadeOut = fadeOut(tween(90, easing = androidx.compose.animation.core.LinearEasing))
private val TabFadeIn = fadeIn(tween(210, delayMillis = 90, easing = FitnexMotion.Easing))

/**
 * Opening and going back use one duration (they were 420 / 380 ms with four different fades). The
 * screens are see-through over a shared background, so both also fade (200 ms) to never mix texts.
 */
private const val PUSH_MS = 350
private const val PUSH_FADE_MS = 200

/** Decelerating curve of iOS navigation pushes: fast start, long soft landing. */
private val PushEasing = androidx.compose.animation.core.CubicBezierEasing(0.2f, 0.9f, 0.1f, 1f)

private fun isTabSwitch(from: NavBackStackEntry, to: NavBackStackEntry): Boolean {
    fun NavDestination.isTab() = TopLevelDestination.entries.any { isInHierarchy(it.routeClass) }
    return from.destination.isTab() && to.destination.isTab()
}

/** Which AI context the current screen provides, or null where the AI button must not show. */
internal fun aiContextFor(
    destination: NavDestination?,
    tab: TopLevelDestination?,
    entry: NavBackStackEntry?,
): AiScreenContext? = when {
    tab != null -> tab.aiContext
    destination.isInHierarchy(CalendarRoute::class) -> AiScreenContext.CALENDAR
    destination.isInHierarchy(ExerciseDetailRoute::class) -> AiScreenContext.EXERCISE_DETAIL
    destination.isInHierarchy(ExerciseStatsRoute::class) -> AiScreenContext.STATISTICS
    destination.isInHierarchy(MuscleDetailRoute::class) -> AiScreenContext.MUSCLE_DETAIL
    // Food search keeps the nutrition context ("¿Qué nutrientes tiene esta comida?", spec §19).
    destination.isInHierarchy(AddFoodRoute::class) -> AiScreenContext.NUTRITION
    // Picking exercises for a routine has its own bottom action bar: keep it uncluttered.
    destination.isInHierarchy(ExerciseLibraryRoute::class) ->
        if (entry?.toRoute<ExerciseLibraryRoute>()?.pickMode == true) null else AiScreenContext.EXERCISE_LIBRARY
    else -> null // routine editor, settings, AI chat
}

private fun NavDestination?.isInHierarchy(route: kotlin.reflect.KClass<*>): Boolean =
    this?.hierarchy?.any { it.hasRoute(route) } == true

/** Standard tab behaviour: single top, restore each tab's own back stack and scroll state. */
internal fun NavHostController.navigateToTab(tab: TopLevelDestination) {
    navigate(tab.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
