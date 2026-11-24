package com.fitnex.feature.auth.validation

import com.fitnex.core.model.AgePolicy
import com.fitnex.core.model.ProfileLimits
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle

enum class FieldError {
    REQUIRED, INVALID_EMAIL, PASSWORD_TOO_SHORT, PASSWORD_NEEDS_LETTER_AND_DIGIT, PASSWORDS_DIFFER,
    INVALID_DATE, FUTURE_DATE, TOO_YOUNG, IMPLAUSIBLE_DATE, NAME_TOO_LONG, OUT_OF_RANGE,
}

/** Pure validation rules shared by sign-up, sign-in and onboarding. */
object FormRules {
    const val MIN_PASSWORD_LENGTH = 8
    private val emailPattern = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]{2,}$")
    private val dateFormat = DateTimeFormatter.ofPattern("ddMMuuuu").withResolverStyle(ResolverStyle.STRICT)

    fun email(value: String): FieldError? = when {
        value.isBlank() -> FieldError.REQUIRED
        !emailPattern.matches(value.trim()) -> FieldError.INVALID_EMAIL
        else -> null
    }

    /** Stronger than Firebase's 6-char minimum: 8+ chars with at least one letter and one digit. */
    fun newPassword(value: String): FieldError? = when {
        value.isEmpty() -> FieldError.REQUIRED
        value.length < MIN_PASSWORD_LENGTH -> FieldError.PASSWORD_TOO_SHORT
        value.none(Char::isLetter) || value.none(Char::isDigit) -> FieldError.PASSWORD_NEEDS_LETTER_AND_DIGIT
        else -> null
    }

    fun confirmation(password: String, confirmation: String): FieldError? = when {
        confirmation.isEmpty() -> FieldError.REQUIRED
        confirmation != password -> FieldError.PASSWORDS_DIFFER
        else -> null
    }

    fun required(value: String): FieldError? = if (value.isBlank()) FieldError.REQUIRED else null

    fun name(value: String): FieldError? = when {
        value.isBlank() -> FieldError.REQUIRED
        value.trim().length > ProfileLimits.NAME_MAX_LENGTH -> FieldError.NAME_TOO_LONG
        else -> null
    }

    /** Parses the 8 typed digits (DDMMAAAA) strictly: 31/02 is rejected, not rolled over. */
    fun parseDate(digits: String): LocalDate? =
        if (digits.length != 8) null else runCatching { LocalDate.parse(digits, dateFormat) }.getOrNull()

    fun formatDigits(date: LocalDate): String = date.format(dateFormat)

    fun birthDate(digits: String, today: LocalDate): FieldError? {
        if (digits.isEmpty()) return FieldError.REQUIRED
        val date = parseDate(digits) ?: return FieldError.INVALID_DATE
        return when {
            date.isAfter(today) -> FieldError.FUTURE_DATE
            AgePolicy.ageOn(date, today) > AgePolicy.MAX_AGE -> FieldError.IMPLAUSIBLE_DATE
            !AgePolicy.canRegister(date, today) -> FieldError.TOO_YOUNG
            else -> null
        }
    }

    /** Optional decimal ("72,5" or "72.5"); blank is valid (= not provided). */
    fun optionalDecimal(value: String, range: ClosedFloatingPointRange<Float>): FieldError? {
        if (value.isBlank()) return null
        val number = parseDecimal(value) ?: return FieldError.OUT_OF_RANGE
        return if (number in range) null else FieldError.OUT_OF_RANGE
    }

    fun parseDecimal(value: String): Float? = value.trim().replace(',', '.').toFloatOrNull()?.takeIf { it.isFinite() }
}
