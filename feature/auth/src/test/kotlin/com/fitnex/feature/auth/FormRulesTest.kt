package com.fitnex.feature.auth

import com.fitnex.core.model.ProfileLimits
import com.fitnex.feature.auth.validation.FieldError
import com.fitnex.feature.auth.validation.FormRules
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FormRulesTest {
    private val today = LocalDate.of(2026, 11, 19)

    @Test
    fun email() {
        assertNull(FormRules.email("ana@example.com"))
        assertNull(FormRules.email("  ana.p+fit@mail.co  "))
        assertEquals(FieldError.REQUIRED, FormRules.email(" "))
        assertEquals(FieldError.INVALID_EMAIL, FormRules.email("ana@"))
        assertEquals(FieldError.INVALID_EMAIL, FormRules.email("ana example.com"))
    }

    @Test
    fun passwordNeedsLengthLettersAndDigits() {
        assertNull(FormRules.newPassword("secreto123"))
        assertEquals(FieldError.PASSWORD_TOO_SHORT, FormRules.newPassword("abc12"))
        assertEquals(FieldError.PASSWORD_NEEDS_LETTER_AND_DIGIT, FormRules.newPassword("abcdefgh"))
        assertEquals(FieldError.PASSWORD_NEEDS_LETTER_AND_DIGIT, FormRules.newPassword("12345678"))
        assertEquals(FieldError.PASSWORDS_DIFFER, FormRules.confirmation("secreto123", "secreto124"))
    }

    @Test
    fun birthDateIsStrict() {
        assertNull(FormRules.birthDate("01052000", today))
        assertEquals(FieldError.REQUIRED, FormRules.birthDate("", today))
        assertEquals(FieldError.INVALID_DATE, FormRules.birthDate("0105200", today))
        assertEquals(FieldError.INVALID_DATE, FormRules.birthDate("31022000", today)) // no 31 Feb
        assertNull(FormRules.birthDate("29022000", today)) // leap year
        assertEquals(FieldError.FUTURE_DATE, FormRules.birthDate("01012030", today))
        assertEquals(FieldError.TOO_YOUNG, FormRules.birthDate("20112012", today)) // 13 until tomorrow
        assertNull(FormRules.birthDate("19112012", today)) // turns 14 today
        assertEquals(FieldError.IMPLAUSIBLE_DATE, FormRules.birthDate("01011890", today))
    }

    @Test
    fun optionalDecimalsAcceptCommaAndBlank() {
        assertNull(FormRules.optionalDecimal("", ProfileLimits.HEIGHT_CM))
        assertNull(FormRules.optionalDecimal("172,5", ProfileLimits.HEIGHT_CM))
        assertEquals(172.5f, FormRules.parseDecimal("172,5"))
        assertEquals(FieldError.OUT_OF_RANGE, FormRules.optionalDecimal("17", ProfileLimits.HEIGHT_CM))
        assertEquals(FieldError.OUT_OF_RANGE, FormRules.optionalDecimal("abc", ProfileLimits.WEIGHT_KG))
    }

    @Test
    fun digitsRoundTrip() {
        val d = LocalDate.of(1999, 12, 3)
        assertEquals(d, FormRules.parseDate(FormRules.formatDigits(d)))
    }
}
