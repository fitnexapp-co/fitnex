package com.fitnex.feature.auth

import com.fitnex.core.designsystem.component.fitnexScreen

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.fitnex.core.designsystem.component.FitnexHeader
import com.fitnex.core.designsystem.component.picker.FitnexDatePickerField
import com.fitnex.feature.auth.validation.FormRules
import com.fitnex.core.designsystem.component.FitnexPasswordField
import com.fitnex.core.designsystem.component.FitnexPrimaryButton
import com.fitnex.core.designsystem.component.FitnexSecondaryButton
import com.fitnex.core.designsystem.component.FitnexTextField
import com.fitnex.core.designsystem.component.GlassCard
import com.fitnex.core.designsystem.icon.FitnexIcons
import com.fitnex.core.designsystem.theme.FitnexTheme
import com.fitnex.core.designsystem.theme.Spacing
import com.fitnex.feature.auth.ui.MIN_AGE
import com.fitnex.feature.auth.ui.messageRes
import com.fitnex.core.designsystem.R as DsR
import kotlinx.serialization.Serializable

// Internal, not private: kotlinx.serialization reads route objects reflectively and a private
// top-level class is package-private in bytecode (IllegalAccessException at navigation time).
@Serializable internal data object WelcomeRoute
@Serializable internal data object SignUpRoute
@Serializable internal data object SignInRoute
@Serializable internal data object ResetPasswordRoute

/**
 * Everything a signed-out user can see. Leaving this graph happens automatically: as soon as
 * sign-in/up succeeds, the session state changes and the app shows onboarding or the dashboard.
 */
@Composable
fun AuthFlow() {
    val nav = rememberNavController()
    NavHost(nav, startDestination = WelcomeRoute) {
        fitnexScreen<WelcomeRoute> {
            WelcomeScreen(
                onCreateAccount = { nav.navigate(SignUpRoute) },
                onSignIn = { nav.navigate(SignInRoute) },
            )
        }
        fitnexScreen<SignUpRoute> { SignUpScreen(onBack = nav::popBackStack) }
        fitnexScreen<SignInRoute> {
            SignInScreen(onBack = nav::popBackStack, onForgotPassword = { nav.navigate(ResetPasswordRoute) })
        }
        fitnexScreen<ResetPasswordRoute> { ResetPasswordScreen(onBack = nav::popBackStack) }
    }
}

@Composable
private fun AuthScaffold(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState()),
    ) { content() }
}

@Composable
internal fun WelcomeScreen(onCreateAccount: () -> Unit, onSignIn: () -> Unit) {
    val colors = FitnexTheme.colors
    AuthScaffold {
        Column(Modifier.padding(horizontal = Spacing.gutter)) {
            Spacer(Modifier.height(Spacing.xxxl))
            Image(painterResource(DsR.drawable.ic_fitnex_mark), contentDescription = null, modifier = Modifier.size(96.dp))
            Text("FITNEX", style = FitnexTheme.typography.displayLarge, color = colors.textPrimary)
            Text(stringResource(R.string.welcome_slogan), style = FitnexTheme.typography.labelLarge, color = colors.accent)
            Spacer(Modifier.height(Spacing.xxl))
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                ValueRow(FitnexIcons.Workout, R.string.welcome_train_title, R.string.welcome_train_body)
                ValueRow(FitnexIcons.Nutrition, R.string.welcome_eat_title, R.string.welcome_eat_body)
                ValueRow(FitnexIcons.Sparkle, R.string.welcome_ai_title, R.string.welcome_ai_body)
            }
            Spacer(Modifier.height(Spacing.xxl))
            FitnexPrimaryButton(stringResource(R.string.welcome_create_account), onCreateAccount, Modifier.fillMaxWidth().testTag("welcome_create"))
            Spacer(Modifier.height(Spacing.md))
            FitnexSecondaryButton(stringResource(R.string.welcome_have_account), onSignIn, Modifier.fillMaxWidth().testTag("welcome_signin"))
            Spacer(Modifier.height(Spacing.xl))
        }
    }
}

@Composable
private fun ValueRow(icon: ImageVector, title: Int, body: Int) {
    val colors = FitnexTheme.colors
    GlassCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).background(colors.accent.copy(alpha = 0.14f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = colors.accent)
            }
            Column(Modifier.padding(start = Spacing.md)) {
                Text(stringResource(title), style = FitnexTheme.typography.titleMedium, color = colors.textPrimary)
                Text(stringResource(body), style = FitnexTheme.typography.bodySmall, color = colors.textSecondary)
            }
        }
    }
}

@Composable
internal fun SignUpScreen(onBack: () -> Unit, viewModel: SignUpViewModel = hiltViewModel()) {
    val s by viewModel.state.collectAsStateWithLifecycle()
    val colors = FitnexTheme.colors
    AuthScaffold {
        FitnexHeader(stringResource(R.string.signup_title), overline = stringResource(R.string.signup_overline), onBack = onBack)
        Column(Modifier.padding(horizontal = Spacing.gutter), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            if (s.blockedByAge) {
                GlassCard(Modifier.testTag("signup_blocked")) {
                    Text(stringResource(R.string.signup_too_young_title), style = FitnexTheme.typography.titleLarge, color = colors.textPrimary)
                    Text(stringResource(R.string.signup_too_young_body, MIN_AGE), style = FitnexTheme.typography.bodyMedium, color = colors.textSecondary)
                }
            }
            FitnexDatePickerField(
                value = FormRules.parseDate(s.birthDigits),
                onValueChange = { viewModel.onBirthDigits(FormRules.formatDigits(it)) },
                label = stringResource(R.string.signup_birthdate),
                supportingText = stringResource(R.string.signup_birthdate_why, MIN_AGE),
                errorText = s.birthError(viewModel.today)?.let { stringResource(it.messageRes()) },
                modifier = Modifier.testTag("signup_birth"),
            )
            FitnexTextField(
                value = s.email, onValueChange = viewModel::onEmail, label = stringResource(R.string.field_email),
                errorText = s.emailError?.let { stringResource(it.messageRes()) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next, autoCorrectEnabled = false),
                modifier = Modifier.testTag("signup_email"),
            )
            FitnexPasswordField(
                value = s.password, onValueChange = viewModel::onPassword, label = stringResource(R.string.field_password),
                supportingText = stringResource(R.string.signup_password_hint),
                errorText = s.passwordError?.let { stringResource(it.messageRes()) },
                imeAction = ImeAction.Next, modifier = Modifier.testTag("signup_password"),
            )
            FitnexPasswordField(
                value = s.confirmation, onValueChange = viewModel::onConfirmation, label = stringResource(R.string.field_password_confirm),
                errorText = s.confirmationError?.let { stringResource(it.messageRes()) },
                modifier = Modifier.testTag("signup_confirm"),
            )
            s.authError?.let { AuthErrorText(stringResource(it.messageRes())) }
            Spacer(Modifier.height(Spacing.sm))
            FitnexPrimaryButton(
                stringResource(R.string.signup_submit), viewModel::submit, Modifier.fillMaxWidth().testTag("signup_submit"),
                loading = s.loading,
            )
            Text(stringResource(R.string.signup_verification_note), style = FitnexTheme.typography.bodySmall, color = colors.textSecondary)
            Spacer(Modifier.height(Spacing.xl))
        }
    }
}

@Composable
internal fun SignInScreen(onBack: () -> Unit, onForgotPassword: () -> Unit, viewModel: SignInViewModel = hiltViewModel()) {
    val s by viewModel.state.collectAsStateWithLifecycle()
    AuthScaffold {
        FitnexHeader(stringResource(R.string.signin_title), overline = stringResource(R.string.signin_overline), onBack = onBack)
        Column(Modifier.padding(horizontal = Spacing.gutter), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            FitnexTextField(
                value = s.email, onValueChange = viewModel::onEmail, label = stringResource(R.string.field_email),
                errorText = s.emailError?.let { stringResource(it.messageRes()) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next, autoCorrectEnabled = false),
                modifier = Modifier.testTag("signin_email"),
            )
            FitnexPasswordField(
                value = s.password, onValueChange = viewModel::onPassword, label = stringResource(R.string.field_password),
                errorText = s.passwordError?.let { stringResource(it.messageRes()) },
                modifier = Modifier.testTag("signin_password"),
            )
            s.authError?.let { AuthErrorText(stringResource(it.messageRes())) }
            TextButton(onClick = onForgotPassword, modifier = Modifier.testTag("signin_forgot")) {
                Text(stringResource(R.string.signin_forgot), color = FitnexTheme.colors.accent)
            }
            FitnexPrimaryButton(stringResource(R.string.signin_submit), viewModel::submit, Modifier.fillMaxWidth().testTag("signin_submit"), loading = s.loading)
        }
    }
}

@Composable
internal fun ResetPasswordScreen(onBack: () -> Unit, viewModel: ResetPasswordViewModel = hiltViewModel()) {
    val s by viewModel.state.collectAsStateWithLifecycle()
    val colors = FitnexTheme.colors
    AuthScaffold {
        FitnexHeader(stringResource(R.string.reset_title), overline = stringResource(R.string.reset_overline), onBack = onBack)
        Column(Modifier.padding(horizontal = Spacing.gutter), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(stringResource(R.string.reset_body), style = FitnexTheme.typography.bodyMedium, color = colors.textSecondary)
            FitnexTextField(
                value = s.email, onValueChange = viewModel::onEmail, label = stringResource(R.string.field_email),
                errorText = s.emailError?.let { stringResource(it.messageRes()) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Done, autoCorrectEnabled = false),
                modifier = Modifier.testTag("reset_email"),
            )
            s.authError?.let { AuthErrorText(stringResource(it.messageRes())) }
            if (s.sentTo != null) {
                GlassCard(Modifier.testTag("reset_sent")) {
                    Text(stringResource(R.string.reset_sent, s.sentTo!!), style = FitnexTheme.typography.bodyMedium, color = colors.textPrimary)
                }
                FitnexSecondaryButton(stringResource(R.string.reset_back_to_signin), onBack, Modifier.fillMaxWidth())
            } else {
                FitnexPrimaryButton(stringResource(R.string.reset_submit), viewModel::submit, Modifier.fillMaxWidth().testTag("reset_submit"), loading = s.loading)
            }
        }
    }
}

@Composable
internal fun AuthErrorText(message: String) {
    Text(
        message,
        style = FitnexTheme.typography.bodyMedium,
        color = FitnexTheme.colors.error,
        modifier = Modifier.testTag("auth_error"),
    )
}
