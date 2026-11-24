package com.fitnex.feature.nutrition

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.fitnex.core.designsystem.component.FitnexHeader
import com.fitnex.core.designsystem.component.FitnexPrimaryButton
import com.fitnex.core.designsystem.component.FitnexTextField
import com.fitnex.core.designsystem.component.picker.FitnexNumberPickerField
import com.fitnex.core.designsystem.theme.FitnexTheme
import com.fitnex.core.designsystem.theme.Spacing
import com.fitnex.core.model.Nutrients

/** A food that is not in the catalog: the user types what they know (e.g. from the label). */
@Composable
internal fun CustomFoodScreen(
    mealName: String,
    showKcal: Boolean,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onSave: (String, Float?, Nutrients) -> Unit,
) {
    val colors = FitnexTheme.colors
    var name by rememberSaveable { mutableStateOf("") }
    var grams by rememberSaveable { mutableStateOf<Float?>(null) }
    var kcal by rememberSaveable { mutableStateOf<Float?>(null) }
    var protein by rememberSaveable { mutableStateOf<Float?>(null) }
    var carbs by rememberSaveable { mutableStateOf<Float?>(null) }
    var fat by rememberSaveable { mutableStateOf<Float?>(null) }
    var fiber by rememberSaveable { mutableStateOf<Float?>(null) }
    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(contentPadding)) {
        FitnexHeader(stringResource(R.string.custom_title), overline = stringResource(R.string.custom_overline, mealName), onBack = onBack)
        Column(Modifier.padding(horizontal = Spacing.gutter), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            FitnexTextField(value = name, onValueChange = { name = it.take(40) }, label = stringResource(R.string.custom_name), modifier = Modifier.fillMaxWidth().testTag("custom_name"))
            FitnexNumberPickerField(grams, { grams = it }, stringResource(R.string.custom_grams), "g", 1..1000, 100f)
            if (showKcal) FitnexNumberPickerField(kcal, { kcal = it }, stringResource(R.string.custom_kcal), "kcal", 0..2000, 200f)
            FitnexNumberPickerField(protein, { protein = it }, stringResource(R.string.protein), "g", 0..200, 10f, withDecimal = true)
            FitnexNumberPickerField(carbs, { carbs = it }, stringResource(R.string.carbs), "g", 0..300, 20f, withDecimal = true)
            FitnexNumberPickerField(fat, { fat = it }, stringResource(R.string.fat), "g", 0..200, 5f, withDecimal = true)
            FitnexNumberPickerField(fiber, { fiber = it }, stringResource(R.string.fiber), "g", 0..100, 2f, withDecimal = true)
            Text(stringResource(R.string.custom_note), style = FitnexTheme.typography.bodySmall, color = colors.textSecondary)
            FitnexPrimaryButton(
                stringResource(R.string.custom_save),
                { onSave(name, grams, Nutrients(kcal = kcal ?: 0f, proteinG = protein ?: 0f, carbsG = carbs ?: 0f, fatG = fat ?: 0f, fiberG = fiber ?: 0f)) },
                Modifier.fillMaxWidth().testTag("custom_save"),
                enabled = name.isNotBlank(),
            )
        }
    }
}
