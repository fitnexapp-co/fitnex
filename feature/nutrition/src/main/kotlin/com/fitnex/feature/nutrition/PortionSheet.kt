package com.fitnex.feature.nutrition

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.fitnex.core.designsystem.component.FitnexBottomSheet
import com.fitnex.core.designsystem.component.FitnexPrimaryButton
import com.fitnex.core.designsystem.component.FitnexSegmentedControl
import com.fitnex.core.designsystem.component.GlassCard
import com.fitnex.core.designsystem.component.StatTile
import com.fitnex.core.designsystem.component.picker.FitnexWheelPicker
import com.fitnex.core.designsystem.locale.rememberAppLocale
import com.fitnex.core.designsystem.theme.FitnexTheme
import com.fitnex.core.designsystem.theme.Spacing
import com.fitnex.core.model.Food
import com.fitnex.core.model.NutritionLimits
import com.fitnex.core.model.NutritionMath
import com.fitnex.feature.nutrition.ui.allergenLabel
import com.fitnex.feature.nutrition.ui.fmt
import kotlin.math.abs

private enum class PortionMode { UNITS, GRAMS }

/** Grams wheel: 5 g steps up to 1 kg. Units wheel: halves from ½ to 10. */
internal val GRAM_STEPS = (1..200).map { it * 5f }
internal val UNIT_STEPS = (1..20).map { it * 0.5f }

/** The label stored with an entry, e.g. "1,5 × taza" or "150 g". */
internal fun portionLabel(food: Food, units: Float?, grams: Float, locale: java.util.Locale): String =
    if (units != null && food.unitName != null) "${fmt(units, locale)} × ${food.unitName}" else "${fmt(grams, locale)} g"

/**
 * Wheel-based portion picker with a live nutrient preview and an allergen warning
 * (spec §6: editable quantities; §4: calories only for adults, always as estimates).
 */
@Composable
internal fun PortionSheet(
    food: Food,
    initialGrams: Float?,
    audience: NutritionAudience,
    confirmLabel: String,
    onConfirm: (grams: Float, portion: String) -> Unit,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    val colors = FitnexTheme.colors
    val locale = rememberAppLocale()
    val hasUnits = food.unitGrams != null && food.unitName != null
    // Editing an entry logged in units (e.g. "1 × unidad" = 201 g) reopens in units, not rounded grams.
    val matchesUnit = initialGrams != null && food.unitGrams != null && UNIT_STEPS.any { abs(it * food.unitGrams!! - initialGrams) < 0.6f }
    var mode by rememberSaveable { mutableStateOf(if (hasUnits && (initialGrams == null || matchesUnit)) PortionMode.UNITS else PortionMode.GRAMS) }
    val startGrams = initialGrams ?: food.unitGrams ?: 100f
    var gramIndex by rememberSaveable { mutableIntStateOf(GRAM_STEPS.indices.minBy { abs(GRAM_STEPS[it] - startGrams) }) }
    var unitIndex by rememberSaveable {
        mutableIntStateOf(food.unitGrams?.let { u -> UNIT_STEPS.indices.minBy { abs(UNIT_STEPS[it] * u - startGrams) } } ?: 1)
    }
    val units = if (mode == PortionMode.UNITS && hasUnits) UNIT_STEPS[unitIndex] else null
    val grams = (units?.let { it * food.unitGrams!! } ?: GRAM_STEPS[gramIndex]).coerceAtMost(NutritionLimits.MAX_GRAMS)
    val n = remember(food, grams) { NutritionMath.forGrams(food, grams) }
    val conflicts = NutritionMath.conflicts(food, audience.allergies)

    FitnexBottomSheet(onDismiss = onDismiss, title = food.name, dragToDismiss = false) {
        if (hasUnits) {
            val labels = mapOf(PortionMode.UNITS to stringResource(R.string.portion_units), PortionMode.GRAMS to stringResource(R.string.portion_grams))
            FitnexSegmentedControl(PortionMode.entries, mode, { mode = it }, { labels.getValue(it) }, Modifier.testTag("portion_mode"))
            Spacer(Modifier.height(Spacing.sm))
        }
        if (units != null) {
            FitnexWheelPicker(UNIT_STEPS, unitIndex, { unitIndex = it }, { "${fmt(it, locale)} × ${food.unitName}" }, Modifier.fillMaxWidth(), testTag = "portion_units_wheel")
            Text(stringResource(R.string.portion_total, fmt(grams, locale)), style = FitnexTheme.typography.bodySmall, color = colors.textSecondary)
        } else {
            FitnexWheelPicker(GRAM_STEPS, gramIndex, { gramIndex = it }, { "${fmt(it, locale)} g" }, Modifier.fillMaxWidth(), testTag = "portion_grams_wheel")
        }
        Spacer(Modifier.height(Spacing.md))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            StatTile(stringResource(R.string.protein), fmt(n.proteinG, locale), Modifier.weight(1f), unit = "g")
            StatTile(stringResource(R.string.carbs), fmt(n.carbsG, locale), Modifier.weight(1f), unit = "g")
            StatTile(stringResource(R.string.fat), fmt(n.fatG, locale), Modifier.weight(1f), unit = "g")
        }
        Text(
            buildString {
                append(stringResource(R.string.fiber)).append(": ").append(fmt(n.fiberG, locale)).append(" g")
                if (audience.showKcal) append("   ").append(stringResource(R.string.kcal_estimate, fmt(n.kcal, locale)))
            },
            style = FitnexTheme.typography.bodySmall, color = colors.textSecondary, modifier = Modifier.padding(top = Spacing.xs),
        )
        if (conflicts.isNotEmpty()) {
            GlassCard(Modifier.fillMaxWidth().padding(top = Spacing.sm).testTag("portion_allergen_warning")) {
                Text(
                    stringResource(R.string.portion_allergen_warning, conflicts.map { allergenLabel(it) }.joinToString()),
                    style = FitnexTheme.typography.bodyMedium, color = colors.warning,
                )
            }
        }
        FitnexPrimaryButton(
            confirmLabel, { onConfirm(grams, portionLabel(food, units, grams, locale)) },
            Modifier.fillMaxWidth().padding(top = Spacing.md).testTag("portion_confirm"),
        )
        if (onDelete != null) {
            androidx.compose.material3.TextButton(onClick = onDelete, modifier = Modifier.fillMaxWidth().testTag("portion_delete")) {
                Text(stringResource(R.string.entry_delete), color = colors.error)
            }
        }
    }
}
