package com.fitnex.app.update

import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.fitnex.app.R
import com.fitnex.core.designsystem.component.FitnexConfirmDialog

/**
 * Offers a newer FITNEX once it is published: "Descargar" opens the APK download (it installs over
 * this one and keeps the data), "Ahora no" stops offering that version.
 */
@Composable
fun UpdatePrompt() {
    val context = LocalContext.current
    var update by remember { mutableStateOf<AvailableUpdate?>(null) }
    LaunchedEffect(Unit) {
        if (UpdateChecker.enabled(context)) update = UpdateChecker.check(context)
    }
    val available = update ?: return
    FitnexConfirmDialog(
        title = stringResource(R.string.update_title),
        message = stringResource(R.string.update_message, available.version),
        confirmLabel = stringResource(R.string.update_download),
        dismissLabel = stringResource(R.string.update_later),
        onConfirm = {
            runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(available.downloadUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            update = null
        },
        onDismiss = {
            UpdateChecker.dismiss(context, available.version)
            update = null
        },
    )
}
