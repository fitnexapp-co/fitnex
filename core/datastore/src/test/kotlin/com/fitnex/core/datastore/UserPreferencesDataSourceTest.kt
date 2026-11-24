package com.fitnex.core.datastore

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class UserPreferencesDataSourceTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun TestScope.newSource(file: File = File(tmp.root, "prefs.preferences_pb")) =
        UserPreferencesDataSource(
            PreferenceDataStoreFactory.create(scope = backgroundScope) { file },
        )

    @Test
    fun defaultsAreDarkModeAndOnboardingPending() = runTest(UnconfinedTestDispatcher()) {
        val prefs = newSource().preferences.first()
        assertEquals(ThemeMode.DARK, prefs.themeMode)
        assertFalse(prefs.onboardingCompleted)
        assertEquals(90, prefs.defaultRestSeconds)
    }

    @Test
    fun valuesArePersisted() = runTest(UnconfinedTestDispatcher()) {
        val source = newSource()
        source.setOnboardingCompleted(true)
        source.setThemeMode(ThemeMode.LIGHT)
        source.setDefaultRestSeconds(120)
        source.setPreferMuscleMap3d(false)

        val prefs = source.preferences.first()
        assertTrue(prefs.onboardingCompleted)
        assertEquals(ThemeMode.LIGHT, prefs.themeMode)
        assertEquals(120, prefs.defaultRestSeconds)
        assertFalse(prefs.preferMuscleMap3d)
    }

    @Test(expected = IllegalArgumentException::class)
    fun restOutsideSafeRangeIsRejected() = runTest(UnconfinedTestDispatcher()) {
        newSource().setDefaultRestSeconds(5)
    }
}
