package com.fitnex.core.designsystem.component.picker

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import com.fitnex.core.designsystem.R
import com.fitnex.core.designsystem.component.FitnexBottomSheet
import com.fitnex.core.designsystem.component.FitnexPrimaryButton
import com.fitnex.core.designsystem.component.FitnexSecondaryButton
import com.fitnex.core.designsystem.component.FitnexTextField
import com.fitnex.core.designsystem.locale.rememberAppLocale
import com.fitnex.core.designsystem.theme.FitnexTheme
import com.fitnex.core.designsystem.theme.Spacing
import java.time.LocalDate
import java.time.Month
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

/**
 * A field that looks like a text field but opens a picker instead of the keyboard.
 * The whole field is one tap target with role Button (TalkBack: "Fecha de nacimiento, botón").
 */
@Composable
fun FitnexPickerField(
    label: String,
    valueText: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    errorText: String? = null,
) {
    Box(modifier) {
        FitnexTextField(
            value = valueText.orEmpty(),
            onValueChange = {},
            label = label,
            readOnly = true,
            supportingText = supportingText,
            errorText = errorText,
        )
        // Transparent overlay over the WHOLE component (field + supporting text): any tap opens
        // the picker. Leaving the supporting text uncovered let taps there focus the read-only
        // field instead (red outline, no sheet) — seen on the emulator.
        Box(
            Modifier
                .matchParentSize()
                .clickable(role = Role.Button, onClickLabel = label, onClick = onClick),
        )
    }
}

/** Birth date and other dates: tap → bottom sheet with day / month / year wheels. */
@Composable
fun FitnexDatePickerField(
    value: LocalDate?,
    onValueChange: (LocalDate) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    errorText: String? = null,
    /** Shown on the wheels when there is no value yet (e.g. a typical adult birth year). */
    defaultDate: LocalDate = LocalDate.now().minusYears(25).withDayOfYear(1),
    minYear: Int = LocalDate.now().year - 120,
    maxYear: Int = LocalDate.now().year,
) {
    val locale = rememberAppLocale()
    var open by rememberSaveable { mutableStateOf(false) }
    val format = remember(locale) { DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(locale) }
    FitnexPickerField(
        label = label,
        valueText = value?.format(format),
        onClick = { open = true },
        modifier = modifier,
        supportingText = supportingText,
        errorText = errorText,
    )
    if (open) {
        DatePickerSheet(
            title = label,
            initial = value ?: defaultDate,
            minYear = minYear,
            maxYear = maxYear,
            locale = locale,
            onConfirm = { onValueChange(it); open = false },
            onDismiss = { open = false },
        )
    }
}

@Composable
private fun DatePickerSheet(
    title: String,
    initial: LocalDate,
    minYear: Int,
    maxYear: Int,
    locale: Locale,
    onConfirm: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val years = remember(minYear, maxYear) { (minYear..maxYear).toList() }
    var year by rememberSaveable { mutableIntStateOf(initial.year.coerceIn(minYear, maxYear)) }
    var month by rememberSaveable { mutableIntStateOf(initial.monthValue) }
    var day by rememberSaveable { mutableIntStateOf(initial.dayOfMonth) }
    val daysInMonth = YearMonth.of(year, month).lengthOfMonth()
    // 31 March → February: clamp instead of rolling over into March.
    val safeDay = day.coerceAtMost(daysInMonth)
    val monthNames = remember(locale) {
        Month.entries.map { m -> m.getDisplayName(TextStyle.FULL_STANDALONE, locale).replaceFirstChar { it.titlecase(locale) } }
    }

    FitnexBottomSheet(onDismiss = onDismiss, title = title, dragToDismiss = false) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            FitnexWheelPicker(
                items = (1..daysInMonth).toList(), selectedIndex = safeDay - 1,
                onSelectedIndexChange = { day = it + 1 }, label = { it.toString() },
                modifier = Modifier.weight(0.8f), testTag = "wheel_day",
            )
            FitnexWheelPicker(
                items = monthNames, selectedIndex = month - 1,
                onSelectedIndexChange = { month = it + 1 }, label = { it },
                modifier = Modifier.weight(1.6f), testTag = "wheel_month",
            )
            FitnexWheelPicker(
                items = years, selectedIndex = years.indexOf(year).coerceAtLeast(0),
                onSelectedIndexChange = { year = years[it] }, label = { it.toString() },
                modifier = Modifier.weight(1f), testTag = "wheel_year",
            )
        }
        Spacer(Modifier.height(Spacing.lg))
        FitnexPrimaryButton(
            stringResource(R.string.ds_picker_done),
            onClick = { onConfirm(LocalDate.of(year, month, safeDay)) },
            modifier = Modifier.fillMaxWidth().testTag("picker_done"),
        )
    }
}

/**
 * Numeric value chosen on wheels (height, weight, later reps and loads).
 * With [withDecimal] a second wheel picks one decimal: 72 , 5 kg.
 * When [optional], the sheet offers "Quitar" to leave the value unset (null).
 */
@Composable
fun FitnexNumberPickerField(
    value: Float?,
    onValueChange: (Float?) -> Unit,
    label: String,
    unit: String,
    range: IntRange,
    defaultValue: Float,
    modifier: Modifier = Modifier,
    withDecimal: Boolean = false,
    optional: Boolean = true,
    supportingText: String? = null,
    errorText: String? = null,
) {
    val locale = rememberAppLocale()
    var open by rememberSaveable { mutableStateOf(false) }
    FitnexPickerField(
        label = label,
        valueText = value?.let { formatNumber(it, withDecimal, locale) + " " + unit },
        onClick = { open = true },
        modifier = modifier,
        supportingText = supportingText,
        errorText = errorText,
    )
    if (open) {
        val start = value ?: defaultValue
        var whole by rememberSaveable { mutableIntStateOf(start.toInt().coerceIn(range)) }
        var tenth by rememberSaveable { mutableIntStateOf(Math.round((start - start.toInt()) * 10).coerceIn(0, 9)) }
        val wholes = remember(range) { range.toList() }
        val decimalSeparator = remember(locale) { java.text.DecimalFormatSymbols.getInstance(locale).decimalSeparator }

        FitnexBottomSheet(onDismiss = { open = false }, title = label, dragToDismiss = false) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                FitnexWheelPicker(
                    items = wholes, selectedIndex = wholes.indexOf(whole).coerceAtLeast(0),
                    onSelectedIndexChange = { whole = wholes[it] }, label = { it.toString() },
                    modifier = Modifier.weight(1f), testTag = "wheel_whole",
                )
                if (withDecimal) {
                    Text(decimalSeparator.toString(), style = FitnexTheme.typography.titleLarge, color = FitnexTheme.colors.textPrimary)
                    FitnexWheelPicker(
                        items = (0..9).toList(), selectedIndex = tenth,
                        onSelectedIndexChange = { tenth = it }, label = { it.toString() },
                        modifier = Modifier.weight(0.6f), testTag = "wheel_decimal",
                    )
                }
                Text(
                    unit, style = FitnexTheme.typography.titleMedium, color = FitnexTheme.colors.textSecondary,
                    modifier = Modifier.padding(start = Spacing.md, end = Spacing.lg),
                )
            }
            Spacer(Modifier.height(Spacing.lg))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                if (optional) {
                    FitnexSecondaryButton(
                        stringResource(R.string.ds_picker_clear),
                        onClick = { onValueChange(null); open = false },
                        modifier = Modifier.weight(1f).testTag("picker_clear"),
                    )
                }
                FitnexPrimaryButton(
                    stringResource(R.string.ds_picker_done),
                    onClick = {
                        onValueChange(if (withDecimal) whole + tenth / 10f else whole.toFloat())
                        open = false
                    },
                    modifier = Modifier.weight(1.4f).testTag("picker_done"),
                )
            }
        }
    }
}

internal fun formatNumber(value: Float, withDecimal: Boolean, locale: Locale): String =
    if (!withDecimal || value % 1f == 0f) value.toInt().toString() else String.format(locale, "%.1f", value)
