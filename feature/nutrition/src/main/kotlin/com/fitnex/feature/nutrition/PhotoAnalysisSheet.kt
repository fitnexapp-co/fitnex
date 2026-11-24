package com.fitnex.feature.nutrition

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.fitnex.core.designsystem.component.FitnexBottomSheet
import com.fitnex.core.designsystem.component.FitnexPrimaryButton
import com.fitnex.core.designsystem.component.picker.FitnexWheelPicker
import com.fitnex.core.designsystem.locale.rememberAppLocale
import com.fitnex.core.designsystem.theme.FitnexTheme
import com.fitnex.core.designsystem.theme.Spacing
import com.fitnex.core.model.AiFailure
import com.fitnex.core.model.DetectedFood
import com.fitnex.feature.nutrition.ui.fmt
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Review of a photo analysis (spec §6): every food can be kept or dropped and its grams changed
 * before saving; values are shown as estimates with the model's confidence. If the food could not
 * be identified, the AI's question is shown instead of any number.
 */
@Composable
internal fun PhotoAnalysisSheet(
    ui: PhotoAnalysisUi,
    audience: NutritionAudience,
    onSave: (List<Pair<DetectedFood, Float>>) -> Unit,
    onConsent: () -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = FitnexTheme.colors
    val locale = rememberAppLocale()
    FitnexBottomSheet(onDismiss = onDismiss, title = stringResource(R.string.analysis_title), dragToDismiss = false) {
        when {
            ui.loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(color = colors.accent)
                Text(stringResource(R.string.analysis_loading), style = FitnexTheme.typography.bodyMedium, color = colors.textSecondary, modifier = Modifier.padding(start = Spacing.md))
            }
            ui.failure != null -> {
                Text(stringResource(ui.failure.messageRes()), style = FitnexTheme.typography.bodyMedium, color = colors.warning, modifier = Modifier.testTag("analysis_error"))
                if (ui.failure == AiFailure.NO_CONSENT) {
                    Text(stringResource(R.string.analysis_consent), style = FitnexTheme.typography.bodySmall, color = colors.textSecondary, modifier = Modifier.padding(top = Spacing.xs))
                    FitnexPrimaryButton(stringResource(R.string.analysis_consent_accept), onConsent, Modifier.fillMaxWidth().padding(top = Spacing.sm))
                } else if (ui.failure != AiFailure.NOT_CONFIGURED) {
                    TextButton(onClick = onRetry) { Text(stringResource(R.string.analysis_retry), color = colors.accent) }
                }
            }
            ui.result != null && !ui.result.identifiable -> {
                Text(ui.result.question, style = FitnexTheme.typography.bodyLarge, color = colors.textPrimary, modifier = Modifier.testTag("analysis_question"))
                Text(stringResource(R.string.analysis_manual_hint), style = FitnexTheme.typography.bodySmall, color = colors.textSecondary, modifier = Modifier.padding(top = Spacing.xs))
            }
            ui.result != null -> {
                val foods = ui.result.foods
                val keep = remember(foods) { mutableStateListOf(*Array(foods.size) { true }) }
                val grams = remember(foods) { mutableStateListOf(*foods.map { GRAM_STEPS.indices.minBy { i -> abs(GRAM_STEPS[i] - it.grams) } }.toTypedArray()) }
                Text(stringResource(R.string.analysis_estimates), style = FitnexTheme.typography.bodySmall, color = colors.textSecondary)
                foods.forEachIndexed { i, f ->
                    if (i > 0) HorizontalDivider(color = colors.divider)
                    val g = GRAM_STEPS[grams[i]]
                    val n = f.nutrients.scaled(if (f.grams > 0f) g / f.grams else 1f)
                    Column(Modifier.padding(vertical = Spacing.sm).testTag("detected_$i")) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(keep[i], { keep[i] = it }, colors = CheckboxDefaults.colors(checkedColor = colors.accent))
                            Text(f.name, style = FitnexTheme.typography.titleMedium, color = colors.textPrimary, modifier = Modifier.weight(1f))
                            Text(stringResource(R.string.analysis_confidence, (f.confidence * 100).roundToInt()), style = FitnexTheme.typography.labelSmall, color = if (f.confidence < 0.5f) colors.warning else colors.textSecondary)
                        }
                        if (f.ingredients.isNotEmpty()) Text(f.ingredients.joinToString(), style = FitnexTheme.typography.bodySmall, color = colors.textSecondary)
                        FitnexWheelPicker(GRAM_STEPS, grams[i], { grams[i] = it }, { "${fmt(it, locale)} g" }, Modifier.fillMaxWidth(), visibleCount = 3)
                        NutrientLine(n, audience)
                    }
                }
                if (ui.result.notes.isNotBlank()) Text(ui.result.notes, style = FitnexTheme.typography.bodySmall, color = colors.textSecondary, modifier = Modifier.padding(top = Spacing.xs))
                FitnexPrimaryButton(
                    stringResource(R.string.analysis_save), { onSave(foods.indices.filter { keep[it] }.map { foods[it] to GRAM_STEPS[grams[it]] }) },
                    Modifier.fillMaxWidth().padding(top = Spacing.md).testTag("analysis_save"), enabled = keep.any { it },
                )
            }
        }
    }
}

internal fun AiFailure.messageRes(): Int = when (this) {
    AiFailure.NO_CONSENT -> R.string.analysis_error_consent
    AiFailure.NO_CONNECTION -> R.string.analysis_error_connection
    AiFailure.RATE_LIMITED -> R.string.analysis_error_rate
    AiFailure.NOT_CONFIGURED -> R.string.analysis_error_not_configured
    AiFailure.NOT_SIGNED_IN, AiFailure.UNAVAILABLE -> R.string.analysis_error_unavailable
}
