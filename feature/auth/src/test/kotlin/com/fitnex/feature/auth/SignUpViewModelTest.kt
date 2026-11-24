package com.fitnex.feature.auth

import com.fitnex.core.auth.AuthError
import com.fitnex.core.testing.FakeAuthRepository
import com.fitnex.core.testing.FakeUserProfileRepository
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SignUpViewModelTest {
    private val clock = Clock.fixed(Instant.parse("2026-11-19T10:00:00Z"), ZoneOffset.UTC)
    private lateinit var auth: FakeAuthRepository
    private lateinit var profiles: FakeUserProfileRepository
    private lateinit var vm: SignUpViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        auth = FakeAuthRepository()
        profiles = FakeUserProfileRepository()
        vm = SignUpViewModel(auth, profiles, clock)
    }

    @After fun tearDown() = Dispatchers.resetMain()

    private fun fill(birth: String, email: String = "ana@example.com", pass: String = "secreto123") {
        vm.onBirthDigits(birth); vm.onEmail(email); vm.onPassword(pass); vm.onConfirmation(pass)
    }

    @Test
    fun validFormCreatesAccountAndProfileWithBirthDate() = runTest {
        fill("01052000")
        vm.submit()
        val user = auth.currentUser.first()
        assertNotNull(user)
        assertEquals(LocalDate.of(2000, 5, 1), profiles.get(user!!.uid)!!.birthDate)
        assertFalse(profiles.get(user.uid)!!.hasCompletedOnboarding)
    }

    @Test
    fun underAgeNeverCallsTheServer() = runTest {
        fill("20112012") // 13 years old
        vm.submit()
        assertTrue(vm.state.value.blockedByAge)
        assertNull(auth.currentUser.first())
    }

    @Test
    fun invalidFieldsShowErrorsWithoutSubmitting() = runTest {
        fill("01052000", email = "ana@", pass = "short")
        vm.submit()
        assertNotNull(vm.state.value.emailError)
        assertNotNull(vm.state.value.passwordError)
        assertNull(auth.currentUser.first())
    }

    @Test
    fun serverErrorsAreSurfaced() = runTest {
        auth.signUp("ana@example.com", "secreto123"); auth.signOut()
        fill("01052000")
        vm.submit()
        assertEquals(AuthError.EmailAlreadyInUse, vm.state.value.authError)

        auth.forcedError = AuthError.Network
        vm.onEmail("otra@example.com")
        vm.submit()
        assertEquals(AuthError.Network, vm.state.value.authError)
        assertFalse(vm.state.value.loading)
    }
}
