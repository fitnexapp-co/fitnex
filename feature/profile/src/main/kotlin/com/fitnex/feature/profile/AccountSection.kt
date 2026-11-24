package com.fitnex.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fitnex.core.auth.AuthError
import com.fitnex.core.designsystem.component.FitnexConfirmDialog
import com.fitnex.core.designsystem.component.FitnexPasswordField
import com.fitnex.core.designsystem.component.FitnexSecondaryButton
import com.fitnex.core.designsystem.component.FitnexSwitchRow
import com.fitnex.core.designsystem.component.GlassCard
import com.fitnex.core.designsystem.component.SectionHeader
import com.fitnex.core.designsystem.theme.FitnexTheme
import com.fitnex.core.designsystem.theme.Radii
import com.fitnex.core.designsystem.theme.Spacing

/** Profile hero fed by the signed-in account (name, e-mail, photo, edit). */
@Composable
internal fun ProfileSection(onEditProfile: () -> Unit, viewModel: AccountViewModel = hiltViewModel()) {
    val account by viewModel.account.collectAsStateWithLifecycle()
    val info = account ?: return
    ProfileHero(info.name, info.email, onEditProfile)
}

/** "Cuenta" card: verification, AI consent, sign-out and deletion (spec §11). */
@Composable
internal fun AccountSection(viewModel: AccountViewModel = hiltViewModel()) {
    val account by viewModel.account.collectAsStateWithLifecycle()
    val actions by viewModel.actions.collectAsStateWithLifecycle()
    val companionActive by viewModel.companionActive.collectAsStateWithLifecycle()
    val colors = FitnexTheme.colors
    val info = account ?: return
    var confirmSignOut by rememberSaveable { mutableStateOf(false) }
    var showDelete by rememberSaveable { mutableStateOf(false) }
    var confirmReset by rememberSaveable { mutableStateOf(false) }

    GlassCard(Modifier.testTag("account_section")) {
        SectionHeader(stringResource(R.string.settings_account))

        if (!info.emailVerified) {
            Text(
                stringResource(R.string.account_unverified),
                style = FitnexTheme.typography.bodySmall, color = colors.warning,
                modifier = Modifier.padding(top = Spacing.sm),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                TextButton(onClick = viewModel::resendVerification) {
                    Text(stringResource(R.string.account_resend), color = colors.accent)
                }
                TextButton(onClick = viewModel::checkVerification) {
                    Text(stringResource(R.string.account_check_verified), color = colors.accent)
                }
            }
        }
        val notice = when (actions.verificationNotice) {
            VerificationNotice.SENT -> stringResource(R.string.account_verification_sent)
            VerificationNotice.STILL_UNVERIFIED -> stringResource(R.string.account_still_unverified)
            VerificationNotice.VERIFIED -> stringResource(R.string.account_verified)
            null -> null
        }
        notice?.let { Text(it, style = FitnexTheme.typography.bodySmall, color = colors.textSecondary) }
        actions.verificationError?.let { Text(stringResource(it.messageRes()), style = FitnexTheme.typography.bodySmall, color = colors.error) }

        if (companionActive) {
            // A friend is using their profile on this phone: the AI permission, signing out, wiping
            // every profile's history and deleting the account stay with the account holder, who
            // switches back to their own profile first.
            Text(
                stringResource(R.string.account_companion_locked, info.name),
                style = FitnexTheme.typography.bodySmall, color = colors.textSecondary,
                modifier = Modifier.padding(top = Spacing.sm).testTag("account_companion_locked"),
            )
            FitnexSecondaryButton(
                stringResource(R.string.account_switch_to_owner, info.name), viewModel::switchToOwner,
                Modifier.fillMaxWidth().padding(top = Spacing.md).testTag("account_switch_owner"),
            )
            return@GlassCard
        }

        FitnexSwitchRow(
            title = stringResource(R.string.account_ai_consent),
            description = stringResource(R.string.account_ai_consent_desc),
            checked = info.aiConsent,
            onCheckedChange = viewModel::setAiConsent,
            modifier = Modifier.testTag("account_ai_consent"),
        )
        HorizontalDivider(color = colors.divider, modifier = Modifier.padding(vertical = Spacing.md))
        FitnexSecondaryButton(
            stringResource(R.string.account_sign_out), { confirmSignOut = true },
            Modifier.fillMaxWidth().padding(top = Spacing.md).testTag("account_sign_out"),
        )
        TextButton(
            onClick = { confirmReset = true },
            enabled = !actions.resetting,
            modifier = Modifier.fillMaxWidth().testTag("account_reset_stats"),
        ) { Text(stringResource(R.string.account_reset_stats), color = colors.error, style = FitnexTheme.typography.labelLarge) }
        if (actions.resetDone) {
            Text(stringResource(R.string.account_reset_done), style = FitnexTheme.typography.bodySmall, color = colors.textSecondary)
        }
        TextButton(
            onClick = { showDelete = true },
            modifier = Modifier.fillMaxWidth().testTag("account_delete"),
        ) { Text(stringResource(R.string.account_delete), color = colors.error, style = FitnexTheme.typography.labelLarge) }
    }

    if (confirmSignOut) {
        FitnexConfirmDialog(
            title = stringResource(R.string.account_sign_out_title),
            message = stringResource(R.string.account_sign_out_body),
            confirmLabel = stringResource(R.string.account_sign_out),
            dismissLabel = stringResource(R.string.common_cancel),
            onConfirm = { confirmSignOut = false; viewModel.signOut() },
            onDismiss = { confirmSignOut = false },
        )
    }
    if (confirmReset) {
        FitnexConfirmDialog(
            title = stringResource(R.string.account_reset_title),
            message = stringResource(R.string.account_reset_body),
            confirmLabel = stringResource(R.string.account_reset_confirm),
            dismissLabel = stringResource(R.string.common_cancel),
            onConfirm = { confirmReset = false; viewModel.resetTrainingStats() },
            onDismiss = { confirmReset = false },
            destructive = true,
        )
    }
    if (showDelete) {
        DeleteAccountDialog(
            deleting = actions.deleting,
            error = actions.deleteError,
            onConfirm = viewModel::deleteAccount,
            onDismiss = { showDelete = false; viewModel.clearDeleteError() },
        )
    }
}

@Composable
private fun DeleteAccountDialog(
    deleting: Boolean,
    error: AuthError?,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = FitnexTheme.colors
    var password by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { if (!deleting) onDismiss() },
        containerColor = colors.surfaceRaised,
        shape = RoundedCornerShape(Radii.lg),
        title = { Text(stringResource(R.string.account_delete_title), color = colors.textPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(stringResource(R.string.account_delete_body), color = colors.textSecondary, style = FitnexTheme.typography.bodyMedium)
                FitnexPasswordField(
                    value = password, onValueChange = { password = it },
                    label = stringResource(R.string.account_delete_password),
                    errorText = error?.let { stringResource(it.messageRes()) },
                    modifier = Modifier.testTag("delete_password"),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(password) },
                enabled = password.isNotEmpty() && !deleting,
                modifier = Modifier.testTag("delete_confirm"),
            ) { Text(stringResource(R.string.account_delete_confirm), color = colors.error) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !deleting) {
                Text(stringResource(R.string.common_cancel), color = colors.textSecondary)
            }
        },
    )
}

internal fun AuthError.messageRes(): Int = when (this) {
    AuthError.InvalidCredentials -> R.string.account_err_password
    AuthError.Network -> R.string.account_err_network
    AuthError.TooManyRequests -> R.string.account_err_too_many
    AuthError.RequiresRecentLogin -> R.string.account_err_recent
    else -> R.string.account_err_unknown
}
