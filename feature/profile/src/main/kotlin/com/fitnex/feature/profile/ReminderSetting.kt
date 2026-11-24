package com.fitnex.feature.profile

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.fitnex.core.designsystem.component.FitnexSegmentedControl
import com.fitnex.core.designsystem.component.FitnexSwitchRow
import com.fitnex.core.designsystem.theme.FitnexTheme
import com.fitnex.core.designsystem.theme.Spacing

private val HOURS = listOf(7, 9, 13, 18, 20)

/** Daily reminder on training days: on/off and a few sensible hours (no fiddly time picker). */
@Composable
internal fun ReminderSetting(hour: Int?, onHour: (Int?) -> Unit) {
    // Android 13+: notifications need permission; the switch asks for it the first time.
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    Column {
        FitnexSwitchRow(
            title = stringResource(R.string.reminder_setting),
            description = stringResource(R.string.reminder_setting_desc),
            checked = hour != null,
            onCheckedChange = { on ->
                if (on && Build.VERSION.SDK_INT >= 33) ask.launch(Manifest.permission.POST_NOTIFICATIONS)
                onHour(if (on) 18 else null)
            },
            modifier = Modifier.testTag("reminder_switch"),
        )
        if (hour != null) {
            Text(stringResource(R.string.reminder_hour), style = FitnexTheme.typography.labelMedium, color = FitnexTheme.colors.textSecondary, modifier = Modifier.padding(bottom = Spacing.sm))
            FitnexSegmentedControl(HOURS, HOURS.minBy { kotlin.math.abs(it - hour) }, { onHour(it) }, { "$it:00" }, Modifier.testTag("reminder_hour"), compact = true)
        }
    }
}
