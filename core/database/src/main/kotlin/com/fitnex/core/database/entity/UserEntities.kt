package com.fitnex.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.fitnex.core.model.ActivityLevel
import com.fitnex.core.model.Equipment
import com.fitnex.core.model.ExperienceLevel
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate

/** USER (spec §9). Every optional field is nullable because onboarding lets the user skip it. */
@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val id: String,
    val name: String,
    val email: String?,
    @ColumnInfo(name = "birth_date") val birthDate: LocalDate?,
    @ColumnInfo(name = "height_cm") val heightCm: Float?,
    @ColumnInfo(name = "optional_weight_kg") val optionalWeightKg: Float?,
    val experience: ExperienceLevel?,
    @ColumnInfo(name = "activity_level") val activityLevel: ActivityLevel?,
    @ColumnInfo(name = "available_days") val availableDays: Set<DayOfWeek>,
    @ColumnInfo(name = "session_minutes") val sessionMinutes: Int?,
    val equipment: Set<Equipment>,
    @ColumnInfo(name = "dietary_preferences") val dietaryPreferences: List<String>,
    val allergies: List<String>,
    /** Explicit consent to process data with FITNEX AI; null = not granted (spec §11). */
    @ColumnInfo(name = "ai_consent_at") val aiConsentAt: Instant?,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
    /** Added in schema v2. Null while onboarding is unfinished (the row is created at sign-up). */
    @ColumnInfo(name = "onboarding_completed_at") val onboardingCompletedAt: Instant? = null,
    @Embedded val sync: SyncMeta,
)
