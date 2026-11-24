package com.fitnex.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

enum class ThemeMode { DARK, LIGHT, SYSTEM }

/** GLASS = graphite cards, no blur (default, cheapest). LIQUID_GLASS = translucent, blurred, luminous edges. */
enum class SurfaceStyle { GLASS, LIQUID_GLASS }

/** Device-local preferences. Health and training data live in Room, never here. */
data class UserPreferences(
    val onboardingCompleted: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.DARK, // spec: dark mode by default
    val defaultRestSeconds: Int = 90,
    val restTimerSound: Boolean = true,
    val restTimerNotifications: Boolean = true,
    val preferMuscleMap3d: Boolean = true,
    /** ARGB of the accent colour; null = FITNEX violet #8B5CF6. */
    val accentArgb: Int? = null,
    /** Last colour chosen in the custom picker (kept so the custom swatch shows it). */
    val customAccentArgb: Int? = null,
    val surfaceStyle: SurfaceStyle = SurfaceStyle.GLASS,
    val reduceMotion: Boolean = false,
    /** Workouts per week the user aims for; null = derived from the profile's training days. */
    val weeklyGoal: Int? = null,
    /** Daily reminder on training days (hour of day, 0-23); null = reminders off. */
    val reminderHour: Int? = null,
    /** Solid surfaces instead of frosted/blurred ones (also cheaper on old phones). */
    val reduceBlur: Boolean = false,
)

@Singleton
class UserPreferencesDataSource @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    val preferences: Flow<UserPreferences> = dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { p ->
            UserPreferences(
                onboardingCompleted = p[Keys.ONBOARDING_DONE] ?: false,
                themeMode = p[Keys.THEME]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.DARK,
                defaultRestSeconds = p[Keys.REST_SECONDS] ?: 90,
                restTimerSound = p[Keys.REST_SOUND] ?: true,
                restTimerNotifications = p[Keys.REST_NOTIFY] ?: true,
                preferMuscleMap3d = p[Keys.MAP_3D] ?: true,
                accentArgb = p[Keys.ACCENT],
                customAccentArgb = p[Keys.CUSTOM_ACCENT],
                surfaceStyle = p[Keys.SURFACE]?.let { runCatching { SurfaceStyle.valueOf(it) }.getOrNull() } ?: SurfaceStyle.GLASS,
                reduceMotion = p[Keys.REDUCE_MOTION] ?: false,
                weeklyGoal = p[Keys.WEEKLY_GOAL],
                reminderHour = p[Keys.REMINDER_HOUR],
                reduceBlur = p[Keys.REDUCE_BLUR] ?: false,
            )
        }

    suspend fun setOnboardingCompleted(done: Boolean) = dataStore.edit { it[Keys.ONBOARDING_DONE] = done }
    suspend fun setThemeMode(mode: ThemeMode) = dataStore.edit { it[Keys.THEME] = mode.name }
    suspend fun setDefaultRestSeconds(seconds: Int) {
        require(seconds in 10..600) { "Rest must be between 10 s and 10 min" }
        dataStore.edit { it[Keys.REST_SECONDS] = seconds }
    }
    suspend fun setRestTimerSound(enabled: Boolean) = dataStore.edit { it[Keys.REST_SOUND] = enabled }
    suspend fun setRestTimerNotifications(enabled: Boolean) = dataStore.edit { it[Keys.REST_NOTIFY] = enabled }
    suspend fun setPreferMuscleMap3d(enabled: Boolean) = dataStore.edit { it[Keys.MAP_3D] = enabled }

    /** Null restores the FITNEX default. A custom colour is also remembered for the custom swatch. */
    suspend fun setAccent(argb: Int?, custom: Boolean = false) = dataStore.edit {
        if (argb == null) it.remove(Keys.ACCENT) else it[Keys.ACCENT] = argb
        if (custom && argb != null) it[Keys.CUSTOM_ACCENT] = argb
    }
    suspend fun setSurfaceStyle(style: SurfaceStyle) = dataStore.edit { it[Keys.SURFACE] = style.name }
    suspend fun setReduceMotion(enabled: Boolean) = dataStore.edit { it[Keys.REDUCE_MOTION] = enabled }
    suspend fun setReduceBlur(enabled: Boolean) = dataStore.edit { it[Keys.REDUCE_BLUR] = enabled }

    /** "Entrenar juntos": which profile of [accountId] is on screen (null = the account owner). */
    fun activeProfile(accountId: String): Flow<String?> = dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { it[activeKey(accountId)] }

    suspend fun setActiveProfile(accountId: String, profileId: String?) = dataStore.edit {
        if (profileId == null || profileId == accountId) it.remove(activeKey(accountId)) else it[activeKey(accountId)] = profileId
    }

    private fun activeKey(accountId: String) = stringPreferencesKey("active_profile_$accountId")
    suspend fun setWeeklyGoal(sessions: Int) = dataStore.edit { it[Keys.WEEKLY_GOAL] = sessions.coerceIn(1, 7) }
    suspend fun setReminderHour(hour: Int?) = dataStore.edit { if (hour == null) it.remove(Keys.REMINDER_HOUR) else it[Keys.REMINDER_HOUR] = hour.coerceIn(0, 23) }

    /**
     * Colour being dragged in the custom picker: applied app-wide immediately, persisted only when
     * the user confirms (avoids a disk write per drag frame). Null = no preview.
     */
    val previewAccent = kotlinx.coroutines.flow.MutableStateFlow<Int?>(null)

    /** Path of the user's profile photo inside the app's private storage (null = no photo). */
    fun profilePhotoPath(userId: String): Flow<String?> = dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { it[photoKey(userId)] }

    suspend fun setProfilePhotoPath(userId: String, path: String?) = dataStore.edit {
        if (path == null) it.remove(photoKey(userId)) else it[photoKey(userId)] = path
    }

    private fun photoKey(userId: String) = stringPreferencesKey("profile_photo_$userId")

    /** Daily calorie target the adult chose (kcal); null = no target set. Per user/profile. */
    fun kcalGoal(userId: String): Flow<Int?> = dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { it[kcalKey(userId)] }

    suspend fun setKcalGoal(userId: String, kcal: Int?) = dataStore.edit {
        if (kcal == null) it.remove(kcalKey(userId)) else it[kcalKey(userId)] = kcal
    }

    private fun kcalKey(userId: String) = intPreferencesKey("kcal_goal_$userId")

    /** Version of the built-in exercise catalogue last written to Room (0 = never). */
    val catalogVersion: Flow<Int> = dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { it[Keys.CATALOG_VERSION] ?: 0 }

    suspend fun setCatalogVersion(version: Int) = dataStore.edit { it[Keys.CATALOG_VERSION] = version }

    private object Keys {
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_completed")
        val THEME = stringPreferencesKey("theme_mode")
        val REST_SECONDS = intPreferencesKey("default_rest_seconds")
        val REST_SOUND = booleanPreferencesKey("rest_timer_sound")
        val REST_NOTIFY = booleanPreferencesKey("rest_timer_notifications")
        val MAP_3D = booleanPreferencesKey("prefer_muscle_map_3d")
        val CATALOG_VERSION = intPreferencesKey("catalog_version")
        val ACCENT = intPreferencesKey("accent_argb")
        val CUSTOM_ACCENT = intPreferencesKey("custom_accent_argb")
        val SURFACE = stringPreferencesKey("surface_style")
        val REDUCE_MOTION = booleanPreferencesKey("reduce_motion")
        val WEEKLY_GOAL = intPreferencesKey("weekly_goal")
        val REMINDER_HOUR = intPreferencesKey("reminder_hour")
        val REDUCE_BLUR = booleanPreferencesKey("reduce_blur")
    }
}

@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {
    @Provides
    @Singleton
    fun providesPreferencesDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("fitnex_prefs") }
}
