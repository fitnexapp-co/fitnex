package com.fitnex.core.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.fitnex.core.auth.AuthError
import com.fitnex.core.auth.AuthResult
import com.fitnex.core.data.profile.RoomUserProfileRepository
import com.fitnex.core.data.session.AccountManager
import com.fitnex.core.data.session.SessionRepository
import com.fitnex.core.data.session.SessionState
import com.fitnex.core.database.FitnexDatabase
import com.fitnex.core.database.entity.MealEntity
import com.fitnex.core.database.entity.SyncMeta
import com.fitnex.core.model.AnalysisStatus
import com.fitnex.core.model.ExperienceLevel
import com.fitnex.core.model.MealType
import com.fitnex.core.testing.FakeAuthRepository
import com.fitnex.core.testing.FakeCloudSync
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AccountAndSessionTest {
    private lateinit var db: FitnexDatabase
    private lateinit var auth: FakeAuthRepository
    private lateinit var profiles: RoomUserProfileRepository
    private lateinit var session: SessionRepository
    private lateinit var accounts: AccountManager
    private lateinit var cloud: FakeCloudSync
    private val clock = Clock.fixed(Instant.parse("2026-11-19T10:00:00Z"), ZoneOffset.UTC)
    private val birth = LocalDate.of(2000, 5, 1)

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), FitnexDatabase::class.java)
            .allowMainThreadQueries().build()
        auth = FakeAuthRepository()
        profiles = RoomUserProfileRepository(db.userDao(), db.accountDao(), clock)
        cloud = FakeCloudSync()
        session = SessionRepository(auth, profiles, cloud)
        accounts = AccountManager(auth, profiles, cloud)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun sessionGoesSignedOutThenOnboardingThenReady() = runTest {
        session.state.test {
            assertEquals(SessionState.SignedOut, awaitItem())

            val user = (auth.signUp("ana@example.com", "secreto123") as AuthResult.Success).value
            profiles.createAtSignUp(user.uid, user.email, birth)
            var state = awaitItem()
            while (state !is SessionState.NeedsOnboarding || state.profile == null) state = awaitItem()
            assertEquals(birth, state.profile!!.birthDate)

            profiles.saveAndCompleteOnboarding(state.profile!!.copy(name = "Ana", experience = ExperienceLevel.BEGINNER))
            var ready = awaitItem()
            while (ready !is SessionState.Ready) ready = awaitItem()
            assertEquals("Ana", ready.profile.name)
            assertEquals(clock.instant(), ready.profile.onboardingCompletedAt)

            auth.signOut()
            assertEquals(SessionState.SignedOut, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun createAtSignUpIsIdempotent() = runTest {
        profiles.createAtSignUp("u1", "a@b.c", birth)
        profiles.saveAndCompleteOnboarding(profiles.get("u1")!!.copy(name = "Ana"))
        profiles.createAtSignUp("u1", "a@b.c", LocalDate.of(1990, 1, 1)) // retried sign-up
        val p = profiles.get("u1")!!
        assertEquals("Ana", p.name)
        assertEquals(birth, p.birthDate)
    }

    @Test
    fun aiConsentCanBeGrantedAndWithdrawn() = runTest {
        profiles.createAtSignUp("u1", null, birth)
        profiles.setAiConsent("u1", true)
        assertEquals(clock.instant(), profiles.get("u1")!!.aiConsentAt)
        profiles.setAiConsent("u1", false)
        assertNull(profiles.get("u1")!!.aiConsentAt)
    }

    @Test
    fun deletingAccountWipesOnlyThatUsersData() = runTest {
        val user = (auth.signUp("ana@example.com", "secreto123") as AuthResult.Success).value
        profiles.createAtSignUp(user.uid, user.email, birth)
        profiles.createAtSignUp("someone-else", "x@y.z", birth)
        db.nutritionDao().upsertMeal(meal("m1", user.uid))
        db.nutritionDao().upsertMeal(meal("m2", "someone-else"))

        val result = accounts.deleteAccount(user.uid, "secreto123")

        assertTrue(result is AuthResult.Success)
        assertNull(profiles.get(user.uid))
        assertNotNull(profiles.get("someone-else"))
        val day = LocalDate.of(2026, 11, 19)
        assertEquals(0, db.nutritionDao().observeMeals(user.uid, day, day).first().size)
        assertEquals(1, db.nutritionDao().observeMeals("someone-else", day, day).first().size)
    }

    @Test
    fun failedRemoteDeletionKeepsLocalData() = runTest {
        val user = (auth.signUp("ana@example.com", "secreto123") as AuthResult.Success).value
        profiles.createAtSignUp(user.uid, user.email, birth)

        val wrongPassword = accounts.deleteAccount(user.uid, "incorrecta")
        assertEquals(AuthResult.Failure(AuthError.InvalidCredentials), wrongPassword)
        assertNotNull(profiles.get(user.uid))

        auth.forcedError = AuthError.Network
        assertEquals(AuthResult.Failure(AuthError.Network), accounts.deleteAccount(user.uid, "secreto123"))
        assertNotNull(profiles.get(user.uid))
    }

    @Test
    fun accountDeletionErasesCloudCopyFirstAndReuploadsIfAccountSurvives() = runTest {
        val user = (auth.signUp("ana@example.com", "secreto123") as AuthResult.Success).value
        profiles.createAtSignUp(user.uid, user.email, birth)

        cloud.eraseSucceeds = false // server unreachable: nothing is deleted anywhere
        assertEquals(AuthResult.Failure(AuthError.Network), accounts.deleteAccount(user.uid, "secreto123"))
        assertNotNull(profiles.get(user.uid))

        cloud.eraseSucceeds = true
        accounts.deleteAccount(user.uid, "incorrecta") // cloud erased, but the account survives
        assertEquals(listOf(user.uid), cloud.reuploaded)
        assertNotNull(profiles.get(user.uid))

        assertTrue(accounts.deleteAccount(user.uid, "secreto123") is AuthResult.Success)
        assertEquals(listOf(user.uid, user.uid), cloud.erased)
        assertNull(profiles.get(user.uid))
    }

    @Test
    fun signInOnNewDeviceShowsRestoringUntilCloudDataArrives() = runTest {
        val user = (auth.signUp("ana@example.com", "secreto123") as AuthResult.Success).value
        cloud.restoringUserId.value = user.uid
        session.state.test {
            var s = awaitItem()
            while (s !is SessionState.Restoring) s = awaitItem()
            // The restored profile lands while restoring: straight to the app, no onboarding.
            profiles.createAtSignUp(user.uid, user.email, birth)
            profiles.saveAndCompleteOnboarding(profiles.get(user.uid)!!.copy(name = "Ana"))
            cloud.restoringUserId.value = null
            var ready = awaitItem()
            while (ready !is SessionState.Ready) ready = awaitItem()
            assertEquals("Ana", ready.profile.name)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun meal(id: String, userId: String) = MealEntity(
        id = id, userId = userId, mealType = MealType.LUNCH, date = LocalDate.of(2026, 11, 19),
        loggedAt = clock.instant(), description = "Arroz", imageReference = null,
        analysisStatus = AnalysisStatus.MANUAL, sync = SyncMeta(updatedAt = clock.instant()),
    )
}
