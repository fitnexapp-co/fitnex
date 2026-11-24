package com.fitnex.core.designsystem.component.picker

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.fitnex.core.designsystem.R
import com.fitnex.core.designsystem.component.FitnexBottomSheet
import com.fitnex.core.designsystem.component.FitnexPrimaryButton
import com.fitnex.core.designsystem.theme.FitnexTheme
import com.fitnex.core.designsystem.theme.Spacing

/** One wheel in a [FitnexMultiWheelSheet]: a caption above it and its options. */
@Immutable
data class WheelColumn(
    val caption: String,
    val options: List<String>,
    val initialIndex: Int,
    val weight: Float = 1f,
    val testTag: String? = null,
)

/**
 * Several wheels side by side with one confirm button, e.g. series × reps × rest.
 * [onConfirm] receives the chosen index of every column, in order.
 */
@Composable
fun FitnexMultiWheelSheet(
    title: String,
    columns: List<WheelColumn>,
    onConfirm: (List<Int>) -> Unit,
    onDismiss: () -> Unit,
    footer: @Composable () -> Unit = {},
) {
    val selected = remember(columns) { mutableStateListOf(*columns.map { it.initialIndex }.toTypedArray()) }
    FitnexBottomSheet(onDismiss = onDismiss, title = title, dragToDismiss = false) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            columns.forEachIndexed { i, column ->
                Column(Modifier.weight(column.weight), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        column.caption,
                        style = FitnexTheme.typography.labelSmall,
                        color = FitnexTheme.colors.textSecondary,
                        textAlign = TextAlign.Center,
                    )
                    FitnexWheelPicker(
                        items = column.options,
                        selectedIndex = selected[i],
                        onSelectedIndexChange = { selected[i] = it },
                        label = { it },
                        testTag = column.testTag,
                    )
                }
            }
        }
        footer()
        Spacer(Modifier.height(Spacing.lg))
        FitnexPrimaryButton(
            stringResource(R.string.ds_picker_done),
            onClick = { onConfirm(selected.toList()) },
            modifier = Modifier.fillMaxWidth().testTag("picker_done"),
        )
    }
}
