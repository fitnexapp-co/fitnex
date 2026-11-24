package com.fitnex.feature.auth.ui

import androidx.annotation.StringRes
import com.fitnex.core.auth.AuthError
import com.fitnex.core.model.AgePolicy
import com.fitnex.feature.auth.R
import com.fitnex.feature.auth.validation.FieldError

@StringRes
internal fun FieldError.messageRes(): Int = when (this) {
    FieldError.REQUIRED -> R.string.err_required
    FieldError.INVALID_EMAIL -> R.string.err_invalid_email
    FieldError.PASSWORD_TOO_SHORT -> R.string.err_password_short
    FieldError.PASSWORD_NEEDS_LETTER_AND_DIGIT -> R.string.err_password_mix
    FieldError.PASSWORDS_DIFFER -> R.string.err_passwords_differ
    FieldError.INVALID_DATE -> R.string.err_invalid_date
    FieldError.FUTURE_DATE -> R.string.err_future_date
    FieldError.TOO_YOUNG -> R.string.err_too_young
    FieldError.IMPLAUSIBLE_DATE -> R.string.err_implausible_date
    FieldError.NAME_TOO_LONG -> R.string.err_name_long
    FieldError.OUT_OF_RANGE -> R.string.err_out_of_range
}

@StringRes
internal fun AuthError.messageRes(): Int = when (this) {
    AuthError.InvalidEmail -> R.string.err_invalid_email
    AuthError.WeakPassword -> R.string.err_password_short
    AuthError.EmailAlreadyInUse -> R.string.auth_err_email_in_use
    AuthError.InvalidCredentials -> R.string.auth_err_invalid_credentials
    AuthError.UserDisabled -> R.string.auth_err_disabled
    AuthError.TooManyRequests -> R.string.auth_err_too_many
    AuthError.Network -> R.string.auth_err_network
    AuthError.RequiresRecentLogin -> R.string.auth_err_recent_login
    AuthError.NotSignedIn -> R.string.auth_err_not_signed_in
    is AuthError.Unknown -> R.string.auth_err_unknown
}

internal const val MIN_AGE = AgePolicy.MIN_REGISTRATION_AGE
