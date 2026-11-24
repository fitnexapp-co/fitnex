package com.fitnex.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitnex.core.auth.AuthError
import com.fitnex.core.auth.AuthRepository
import com.fitnex.core.auth.AuthResult
import com.fitnex.core.data.profile.UserProfileRepository
import com.fitnex.feature.auth.validation.FieldError
import com.fitnex.feature.auth.validation.FormRules
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SignUpUiState(
    val birthDigits: String = "",
    val email: String = "",
    val password: String = "",
    val confirmation: String = "",
    /** Errors are only shown after the first submit, so the form isn't red while typing. */
    val showErrors: Boolean = false,
    val loading: Boolean = false,
    val authError: AuthError? = null,
    /** Under the minimum age: nothing was sent anywhere and no account exists. */
    val blockedByAge: Boolean = false,
) {
    fun birthError(today: LocalDate) = FormRules.birthDate(birthDigits, today).takeIf { showErrors }
    val emailError get() = FormRules.email(email).takeIf { showErrors }
    val passwordError get() = FormRules.newPassword(password).takeIf { showErrors }
    val confirmationError get() = FormRules.confirmation(password, confirmation).takeIf { showErrors }
}

@HiltViewModel
class SignUpViewModel @Inject constructor(
    private val auth: AuthRepository,
    private val profiles: UserProfileRepository,
    private val clock: Clock,
) : ViewModel() {
    private val _state = MutableStateFlow(SignUpUiState())
    val state: StateFlow<SignUpUiState> = _state.asStateFlow()
    val today: LocalDate get() = LocalDate.now(clock)

    fun onBirthDigits(v: String) = _state.update { it.copy(birthDigits = v, authError = null, blockedByAge = false) }
    fun onEmail(v: String) = _state.update { it.copy(email = v, authError = null) }
    fun onPassword(v: String) = _state.update { it.copy(password = v, authError = null) }
    fun onConfirmation(v: String) = _state.update { it.copy(confirmation = v, authError = null) }

    fun submit() {
        val s = _state.value
        if (s.loading) return
        _state.update { it.copy(showErrors = true) }
        val birthError = FormRules.birthDate(s.birthDigits, today)
        // Age gate runs BEFORE any network call: an under-age user never creates an account.
        if (birthError == FieldError.TOO_YOUNG) {
            _state.update { it.copy(blockedByAge = true) }
            return
        }
        val invalid = listOf(
            birthError,
            FormRules.email(s.email),
            FormRules.newPassword(s.password),
            FormRules.confirmation(s.password, s.confirmation),
        ).any { it != null }
        if (invalid) return

        val birthDate = FormRules.parseDate(s.birthDigits) ?: return
        _state.update { it.copy(loading = true, authError = null) }
        viewModelScope.launch {
            when (val result = auth.signUp(s.email.trim(), s.password)) {
                is AuthResult.Success -> {
                    profiles.createAtSignUp(result.value.uid, result.value.email, birthDate)
                    // Session flow now switches the app to onboarding; nothing else to do here.
                    _state.update { it.copy(loading = false) }
                }
                is AuthResult.Failure -> _state.update { it.copy(loading = false, authError = result.error) }
            }
        }
    }
}

data class SignInUiState(
    val email: String = "",
    val password: String = "",
    val showErrors: Boolean = false,
    val loading: Boolean = false,
    val authError: AuthError? = null,
) {
    val emailError get() = FormRules.email(email).takeIf { showErrors }
    val passwordError get() = FormRules.required(password).takeIf { showErrors }
}

@HiltViewModel
class SignInViewModel @Inject constructor(
    private val auth: AuthRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(SignInUiState())
    val state: StateFlow<SignInUiState> = _state.asStateFlow()

    fun onEmail(v: String) = _state.update { it.copy(email = v, authError = null) }
    fun onPassword(v: String) = _state.update { it.copy(password = v, authError = null) }

    fun submit() {
        val s = _state.value
        if (s.loading) return
        _state.update { it.copy(showErrors = true) }
        if (FormRules.email(s.email) != null || FormRules.required(s.password) != null) return
        _state.update { it.copy(loading = true, authError = null) }
        viewModelScope.launch {
            val result = auth.signIn(s.email.trim(), s.password)
            _state.update { it.copy(loading = false, authError = (result as? AuthResult.Failure)?.error) }
        }
    }
}

data class ResetUiState(
    val email: String = "",
    val showErrors: Boolean = false,
    val loading: Boolean = false,
    val sentTo: String? = null,
    val authError: AuthError? = null,
) {
    val emailError get() = FormRules.email(email).takeIf { showErrors }
}

@HiltViewModel
class ResetPasswordViewModel @Inject constructor(
    private val auth: AuthRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(ResetUiState())
    val state: StateFlow<ResetUiState> = _state.asStateFlow()

    fun onEmail(v: String) = _state.update { it.copy(email = v, authError = null, sentTo = null) }

    fun submit() {
        val s = _state.value
        if (s.loading) return
        _state.update { it.copy(showErrors = true) }
        if (FormRules.email(s.email) != null) return
        _state.update { it.copy(loading = true) }
        viewModelScope.launch {
            when (val result = auth.sendPasswordReset(s.email.trim())) {
                is AuthResult.Success -> _state.update { it.copy(loading = false, sentTo = s.email.trim()) }
                is AuthResult.Failure -> _state.update { it.copy(loading = false, authError = result.error) }
            }
        }
    }
}
