package com.fitnex.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** An English device gets the English texts and English date formatting (values-en). */
@RunWith(AndroidJUnit4::class)
@Config(application = android.app.Application::class) // resources only: no Hilt/Firebase app needed
class EnglishTranslationTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    @Config(qualifiers = "en")
    fun englishDeviceGetsEnglishTextsAndLocale() {
        assertEquals("Progress", context.getString(R.string.tab_progress))
        assertEquals("Workout", context.getString(R.string.tab_workout))
        assertEquals("en", context.getString(com.fitnex.core.designsystem.R.string.ds_locale_tag))
        assertEquals("Streak: 2 weeks", context.resources.getQuantityString(R.plurals.widget_streak, 2, 2))
    }

    @Test
    @Config(qualifiers = "es")
    fun spanishDeviceKeepsSpanish() {
        assertEquals("Progreso", context.getString(R.string.tab_progress))
        assertEquals("es-ES", context.getString(com.fitnex.core.designsystem.R.string.ds_locale_tag))
    }
}
