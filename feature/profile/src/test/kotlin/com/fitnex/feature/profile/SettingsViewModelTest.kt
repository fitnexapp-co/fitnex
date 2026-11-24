package com.fitnex.feature.profile

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import app.cash.turbine.test
import com.fitnex.core.datastore.ThemeMode
import com.fitnex.core.datastore.UserPreferencesDataSource
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val dispatcher = UnconfinedTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.newViewModel() = SettingsViewModel(
        UserPreferencesDataSource(
            PreferenceDataStoreFactory.create(scope = backgroundScope) { File(tmp.root, "p.preferences_pb") },
        ),
    )

    private suspend fun app.cash.turbine.ReceiveTurbine<SettingsUiState>.awaitReady(): SettingsUiState.Ready {
        while (true) {
            val item = awaitItem()
            if (item is SettingsUiState.Ready) return item
        }
    }

    @Test
    fun themeChangeIsPersistedAndReflected() = runTest(dispatcher) {
        val vm = newViewModel()
        vm.state.test {
            assertEquals(ThemeMode.DARK, awaitReady().preferences.themeMode)
            vm.setThemeMode(ThemeMode.LIGHT)
            assertEquals(ThemeMode.LIGHT, awaitReady().preferences.themeMode)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun restIsSnappedToStepsAndClampedToSafeRange() = runTest(dispatcher) {
        val vm = newViewModel()
        vm.state.test {
            awaitReady()
            vm.setDefaultRestSeconds(112) // 112 / 15 = 7.47 → 7 steps → 105 s
            assertEquals(105, awaitReady().preferences.defaultRestSeconds)
            vm.setDefaultRestSeconds(400) // → 300
            assertEquals(300, awaitReady().preferences.defaultRestSeconds)
            vm.setDefaultRestSeconds(5) // → 30
            assertEquals(30, awaitReady().preferences.defaultRestSeconds)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun togglesArePersisted() = runTest(dispatcher) {
        val vm = newViewModel()
        vm.state.test {
            awaitReady()
            vm.setRestTimerSound(false)
            assertFalse(awaitReady().preferences.restTimerSound)
            vm.setPreferMuscleMap3d(false)
            assertFalse(awaitReady().preferences.preferMuscleMap3d)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun restLabelFormatting() {
        assertEquals("45 s", formatRest(45))
        assertEquals("1:30 min", formatRest(90))
        assertEquals("5:00 min", formatRest(300))
    }
}
