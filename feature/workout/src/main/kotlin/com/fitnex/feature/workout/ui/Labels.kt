package com.fitnex.feature.workout.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.fitnex.core.designsystem.locale.rememberAppLocale
import com.fitnex.core.model.AnatomicalRegion
import com.fitnex.core.model.Difficulty
import com.fitnex.core.model.Equipment
import com.fitnex.core.model.ExerciseType
import com.fitnex.feature.workout.R
import java.time.DayOfWeek
import java.time.format.TextStyle

@Composable
internal fun Equipment.label(): String = stringResource(
    when (this) {
        Equipment.BODYWEIGHT -> R.string.eq_bodyweight
        Equipment.DUMBBELL -> R.string.eq_dumbbell
        Equipment.BARBELL -> R.string.eq_barbell
        Equipment.KETTLEBELL -> R.string.eq_kettlebell
        Equipment.MACHINE -> R.string.eq_machine
        Equipment.CABLE -> R.string.eq_cable
        Equipment.RESISTANCE_BAND -> R.string.eq_band
        Equipment.BENCH -> R.string.eq_bench
        Equipment.PULL_UP_BAR -> R.string.eq_pullup_bar
        Equipment.OTHER -> R.string.eq_other
        Equipment.EZ_BAR -> R.string.eq_ez_bar
        Equipment.DIP_BARS -> R.string.eq_dip_bars
        Equipment.RACK -> R.string.eq_rack
        Equipment.MEDICINE_BALL -> R.string.eq_medicine_ball
        Equipment.ROPE -> R.string.eq_rope
        Equipment.SLED -> R.string.eq_sled
        Equipment.SANDBAG -> R.string.eq_sandbag
        Equipment.CARDIO_MACHINE -> R.string.eq_cardio_machine
    },
)

@Composable
internal fun Difficulty.label(): String = stringResource(
    when (this) {
        Difficulty.BEGINNER -> R.string.diff_beginner
        Difficulty.INTERMEDIATE -> R.string.diff_intermediate
        Difficulty.ADVANCED -> R.string.diff_advanced
    },
)

@Composable
internal fun ExerciseType.label(): String = stringResource(
    when (this) {
        ExerciseType.STRENGTH -> R.string.type_strength
        ExerciseType.HYPERTROPHY -> R.string.type_hypertrophy
        ExerciseType.CARDIO -> R.string.type_cardio
        ExerciseType.MOBILITY -> R.string.type_mobility
        ExerciseType.CALISTHENICS -> R.string.type_calisthenics
        ExerciseType.FUNCTIONAL -> R.string.type_functional
        ExerciseType.STRETCHING -> R.string.type_stretching
    },
)

@Composable
internal fun com.fitnex.core.model.Tracking.label(): String = stringResource(
    when (this) {
        com.fitnex.core.model.Tracking.WEIGHT_REPS -> R.string.tracking_weight_reps
        com.fitnex.core.model.Tracking.REPS -> R.string.tracking_reps
        com.fitnex.core.model.Tracking.TIME -> R.string.tracking_time
        com.fitnex.core.model.Tracking.TIME_DISTANCE -> R.string.tracking_time_distance
    },
)

@Composable
internal fun AnatomicalRegion.label(): String = stringResource(
    when (this) {
        AnatomicalRegion.CHEST -> R.string.region_chest
        AnatomicalRegion.BACK -> R.string.region_back
        AnatomicalRegion.SHOULDERS -> R.string.region_shoulders
        AnatomicalRegion.ARMS -> R.string.region_arms
        AnatomicalRegion.CORE -> R.string.region_core
        AnatomicalRegion.LEGS -> R.string.region_legs
        AnatomicalRegion.GLUTES -> R.string.region_glutes
        AnatomicalRegion.NECK -> R.string.region_neck
    },
)

@Composable
internal fun com.fitnex.feature.workout.library.MuscleGroup.label(): String = stringResource(
    when (this) {
        com.fitnex.feature.workout.library.MuscleGroup.CHEST -> R.string.group_chest
        com.fitnex.feature.workout.library.MuscleGroup.BACK -> R.string.group_back
        com.fitnex.feature.workout.library.MuscleGroup.SHOULDERS -> R.string.group_shoulders
        com.fitnex.feature.workout.library.MuscleGroup.BICEPS -> R.string.group_biceps
        com.fitnex.feature.workout.library.MuscleGroup.TRICEPS -> R.string.group_triceps
        com.fitnex.feature.workout.library.MuscleGroup.FOREARMS -> R.string.group_forearms
        com.fitnex.feature.workout.library.MuscleGroup.QUADS -> R.string.group_quads
        com.fitnex.feature.workout.library.MuscleGroup.HAMSTRINGS -> R.string.group_hamstrings
        com.fitnex.feature.workout.library.MuscleGroup.GLUTES -> R.string.group_glutes
        com.fitnex.feature.workout.library.MuscleGroup.ADDUCTORS -> R.string.group_adductors
        com.fitnex.feature.workout.library.MuscleGroup.ABDUCTORS -> R.string.group_abductors
        com.fitnex.feature.workout.library.MuscleGroup.CALVES -> R.string.group_calves
        com.fitnex.feature.workout.library.MuscleGroup.ABS -> R.string.group_abs
        com.fitnex.feature.workout.library.MuscleGroup.TRAPS -> R.string.group_traps
        com.fitnex.feature.workout.library.MuscleGroup.LOWER_BACK -> R.string.group_lower_back
    },
)

/**
 * "Lun, Mié, Vie" in the app language, ordered Monday-first. Short names, not single letters: in a
 * line of text a lone "X" (miércoles) read like a close icon, and "M" could be martes or miércoles.
 */
@Composable
internal fun daysLabel(days: Set<DayOfWeek>): String {
    val locale = rememberAppLocale()
    return days.sorted().joinToString(", ") {
        it.getDisplayName(TextStyle.SHORT_STANDALONE, locale).removeSuffix(".").replaceFirstChar { c -> c.titlecase(locale) }
    }
}

/** Full short day names for chips: "lun", "mar"… capitalised. */
@Composable
internal fun DayOfWeek.shortLabel(): String {
    val locale = rememberAppLocale()
    return getDisplayName(TextStyle.SHORT_STANDALONE, locale).replaceFirstChar { it.titlecase(locale) }
}

/** 90 → "1:30", 45 → "45 s". */
internal fun formatRest(seconds: Int): String =
    if (seconds < 60) "$seconds s" else "%d:%02d".format(seconds / 60, seconds % 60)

/** 42.5 → "42,5", 40.0 → "40". */
internal fun formatKg(kg: Float): String =
    if (kg % 1f == 0f) kg.toInt().toString() else "%d,%d".format(kg.toInt(), Math.round((kg - kg.toInt()) * 10))

/** 45 → "45 s", 90 → "1:30 min", 600 → "10 min". */
internal fun formatSeconds(seconds: Int): String = when {
    seconds < 60 -> "$seconds s"
    seconds % 60 == 0 -> "${seconds / 60} min"
    else -> "%d:%02d min".format(seconds / 60, seconds % 60)
}

/** Value on the routine target wheel: reps, seconds (timed) or minutes (cardio). */
internal fun formatTargetValue(tracking: com.fitnex.core.model.Tracking, value: Int): String = when (tracking) {
    com.fitnex.core.model.Tracking.TIME -> formatSeconds(value)
    com.fitnex.core.model.Tracking.TIME_DISTANCE -> "$value min"
    else -> value.toString()
}

/** Target in a routine summary: "12 reps", "45 s", "20 min". */
internal fun formatTarget(tracking: com.fitnex.core.model.Tracking, value: Int): String = when (tracking) {
    com.fitnex.core.model.Tracking.TIME -> formatSeconds(value)
    com.fitnex.core.model.Tracking.TIME_DISTANCE -> "$value min"
    else -> "$value reps"
}

/** Metres → "3,2 km" / "800 m". */
internal fun formatDistance(meters: Float): String =
    if (meters >= 1000f) "%.1f km".format(java.util.Locale("es"), meters / 1000f).replace(".0 km", " km") else "${meters.toInt()} m"
