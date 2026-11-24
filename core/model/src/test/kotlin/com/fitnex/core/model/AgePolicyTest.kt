package com.fitnex.core.model

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgePolicyTest {
    private val today = LocalDate.of(2026, 11, 19)

    @Test
    fun `user turning 18 today is an adult`() {
        val profile = AgePolicy.profileFor(LocalDate.of(2008, 11, 19), today)
        assertEquals(AgeProfile.ADULT, profile)
        assertTrue(profile.allowWeightLossGoals)
    }

    @Test
    fun `user turning 18 tomorrow is still a minor and gets no deficit goals`() {
        val profile = AgePolicy.profileFor(LocalDate.of(2008, 11, 20), today)
        assertEquals(AgeProfile.MINOR, profile)
        assertFalse(profile.allowWeightLossGoals)
        assertFalse(profile.showCalorieEstimates)
    }

    @Test
    fun `unknown birth date is treated conservatively`() {
        val profile = AgePolicy.profileFor(null, today)
        assertFalse(profile.allowWeightLossGoals)
    }

    @Test
    fun `registration requires at least 14 years`() {
        assertTrue(AgePolicy.canRegister(LocalDate.of(2012, 11, 19), today)) // turns 14 today
        assertFalse(AgePolicy.canRegister(LocalDate.of(2012, 11, 20), today)) // 13 until tomorrow
        assertFalse(AgePolicy.canRegister(LocalDate.of(2027, 1, 1), today)) // future
        assertFalse(AgePolicy.canRegister(LocalDate.of(1026, 1, 1), today)) // typo year
    }

    @Test
    fun `a registered 14 year old gets the protected minor experience`() {
        val profile = AgePolicy.profileFor(LocalDate.of(2012, 11, 19), today)
        assertEquals(AgeProfile.MINOR, profile)
        assertFalse(profile.allowWeightLossGoals)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `future birth date is rejected`() {
        AgePolicy.ageOn(LocalDate.of(2027, 1, 1), today)
    }
}
