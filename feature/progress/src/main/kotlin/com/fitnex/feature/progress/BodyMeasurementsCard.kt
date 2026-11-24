package com.fitnex.feature.progress

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.fitnex.core.data.body.BODY_CM_RANGE
import com.fitnex.core.data.body.BodyMeasurement
import com.fitnex.core.designsystem.component.FitnexBottomSheet
import com.fitnex.core.designsystem.component.FitnexPrimaryButton
import com.fitnex.core.designsystem.component.FitnexSecondaryButton
import com.fitnex.core.designsystem.component.FitnexTextField
import com.fitnex.core.designsystem.component.GlassCard
import com.fitnex.core.designsystem.icon.FitnexIcons
import com.fitnex.core.designsystem.locale.rememberAppLocale
import com.fitnex.core.designsystem.theme.FitnexTheme
import com.fitnex.core.designsystem.theme.Spacing
import java.time.LocalDate

private enum class Measure(val label: Int, val get: (BodyMeasurement) -> Float?) {
    WAIST(R.string.body_waist, { it.waistCm }),
    CHEST(R.string.body_chest, { it.chestCm }),
    HIPS(R.string.body_hips, { it.hipsCm }),
    ARM(R.string.body_arm, { it.armCm }),
    THIGH(R.string.body_thigh, { it.thighCm }),
}

/**
 * Optional measurements (adults): the latest value of each and how it changed since the first
 * record. Neutral wording and colours — a change is information, not a verdict.
 */
@Composable
internal fun BodyMeasurementsCard(list: List<BodyMeasurement>, onAdd: (BodyMeasurement) -> Unit, onDelete: (String) -> Unit) {
    val colors = FitnexTheme.colors
    val locale = rememberAppLocale()
    var adding by rememberSaveable { mutableStateOf(false) }
    var deleting by rememberSaveable { mutableStateOf<String?>(null) }
    GlassCard(Modifier.fillMaxWidth().testTag("body_card")) {
        CardTitle(FitnexIcons.Body, stringResource(R.string.body_title))
        if (list.isEmpty()) {
            Text(stringResource(R.string.body_empty), style = FitnexTheme.typography.bodyMedium, color = colors.textSecondary, modifier = Modifier.padding(bottom = Spacing.sm))
        } else {
            Measure.entries.forEach { m ->
                val values = list.mapNotNull { e -> m.get(e)?.let { e.date to it } } // newest first
                val latest = values.firstOrNull() ?: return@forEach
                val first = values.last()
                Row(Modifier.fillMaxWidth().padding(vertical = Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(m.label), style = FitnexTheme.typography.bodyLarge, color = colors.textPrimary, modifier = Modifier.weight(1f))
                    Text("${formatKg(latest.second)} cm", style = FitnexTheme.typography.titleMedium, color = colors.textPrimary)
                    if (values.size > 1) {
                        val d = latest.second - first.second
                        Text(
                            "  " + (if (d >= 0f) "+" else "−") + formatKg(kotlin.math.abs(d)),
                            style = FitnexTheme.typography.bodySmall, color = colors.textSecondary,
                        )
                    }
                }
            }
            // With a single record there is no change to show yet: say so instead of "change since…".
            Text(
                if (list.size > 1) stringResource(R.string.body_since, shortDate(list.last().date, locale))
                else stringResource(R.string.body_first_only, shortDate(list.last().date, locale)),
                style = FitnexTheme.typography.bodySmall, color = colors.textDisabled, modifier = Modifier.padding(top = Spacing.xs),
            )
            HorizontalDivider(color = colors.divider, modifier = Modifier.padding(vertical = Spacing.sm))
            list.take(5).forEach { e ->
                Row(Modifier.fillMaxWidth().testTag("body_entry_${e.id}"), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        shortDate(e.date, locale) + " · " + Measure.entries.mapNotNull { m -> m.get(e)?.let { "${formatKg(it)}" } }.joinToString(" / ") + " cm",
                        style = FitnexTheme.typography.bodySmall, color = colors.textSecondary, modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { deleting = e.id }) {
                        Icon(Icons.Rounded.Close, stringResource(R.string.body_delete_cd, shortDate(e.date, locale)), tint = colors.textDisabled)
                    }
                }
            }
        }
        FitnexSecondaryButton(stringResource(R.string.body_add), { adding = true }, Modifier.fillMaxWidth().padding(top = Spacing.sm).testTag("body_add"))
    }
    if (adding) {
        AddMeasurementSheet(onSave = { onAdd(it); adding = false }, onDismiss = { adding = false })
    }
    deleting?.let { id ->
        com.fitnex.core.designsystem.component.FitnexConfirmDialog(
            title = stringResource(R.string.body_delete_title),
            message = stringResource(R.string.body_delete_body),
            confirmLabel = stringResource(R.string.body_delete_confirm),
            dismissLabel = stringResource(R.string.body_delete_cancel),
            onConfirm = { onDelete(id); deleting = null },
            onDismiss = { deleting = null },
            destructive = true,
        )
    }
}

@Composable
private fun AddMeasurementSheet(onSave: (BodyMeasurement) -> Unit, onDismiss: () -> Unit) {
    val colors = FitnexTheme.colors
    val values = Measure.entries.associateWith { rememberSaveable(it.name) { mutableStateOf("") } }
    fun parse(s: String) = s.replace(',', '.').toFloatOrNull()
    val parsed = Measure.entries.associateWith { parse(values.getValue(it).value) }
    val invalid = parsed.any { (m, v) -> values.getValue(m).value.isNotBlank() && (v == null || v !in BODY_CM_RANGE) }
    val anyValue = parsed.values.any { it != null && it in BODY_CM_RANGE }
    FitnexBottomSheet(onDismiss = onDismiss, title = stringResource(R.string.body_add)) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(stringResource(R.string.body_sheet_hint), style = FitnexTheme.typography.bodySmall, color = colors.textSecondary)
            Measure.entries.forEach { m ->
                val state = values.getValue(m)
                FitnexTextField(
                    state.value, { state.value = it.filter { c -> c.isDigit() || c == ',' || c == '.' }.take(5) },
                    stringResource(m.label) + " (cm)",
                    Modifier.fillMaxWidth().testTag("body_field_${m.name}"),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            }
            if (invalid) Text(stringResource(R.string.body_invalid), style = FitnexTheme.typography.bodySmall, color = colors.warning)
            FitnexPrimaryButton(
                stringResource(R.string.body_save),
                {
                    onSave(
                        BodyMeasurement(
                            "", LocalDate.now(),
                            waistCm = parsed[Measure.WAIST], chestCm = parsed[Measure.CHEST], hipsCm = parsed[Measure.HIPS],
                            armCm = parsed[Measure.ARM], thighCm = parsed[Measure.THIGH],
                        ),
                    )
                },
                Modifier.fillMaxWidth().padding(vertical = Spacing.md).testTag("body_save"),
                enabled = anyValue && !invalid,
            )
        }
    }
}
