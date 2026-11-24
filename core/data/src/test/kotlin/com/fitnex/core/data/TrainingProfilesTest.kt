package com.fitnex.core.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fitnex.core.auth.AuthResult
import com.fitnex.core.data.profile.RoomUserProfileRepository
import com.fitnex.core.data.profile.StoredActiveProfile
import com.fitnex.core.data.profile.TrainingProfileDraft
import com.fitnex.core.data.profile.TrainingProfilesRepository
import com.fitnex.core.data.session.SessionRepository
import com.fitnex.core.database.FitnexDatabase
import com.fitnex.core.database.entity.SyncMeta
import com.fitnex.core.database.entity.WaterLogEntity
import com.fitnex.core.database.sync.SyncStore
import com.fitnex.core.datastore.UserPreferencesDataSource
import com.fitnex.core.model.AgeProfile
import com.fitnex.core.testing.FakeAuthRepository
import com.fitnex.core.testing.FakeCloudSync
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

/** "Entrenar juntos": extra profiles inside one account, each with its own data. */
@RunWith(AndroidJUnit4::class)
class TrainingProfilesTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var db: FitnexDatabase
    private val clock = Clock.fixed(Instant.parse("2026-01-20T10:00:00Z"), ZoneOffset.UTC)
    private var nextId = 0

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), FitnexDatabase::class.java)
            .addCallback(FitnexDatabase.syncCallback).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun profilesHaveTheirOwnDataSyncWithTheAccountAndCanBeDeleted() = runTest {
        val prefs = UserPreferencesDataSource(PreferenceDataStoreFactory.create(scope = backgroundScope) { File(tmp.root, "p.preferences_pb") })
        val auth = FakeAuthRepository()
        val profiles = RoomUserProfileRepository(db.userDao(), db.accountDao(), clock)
        val session = SessionRepository(auth, profiles, FakeCloudSync(), StoredActiveProfile(prefs, db.userDao()))
        val together = TrainingProfilesRepository(session, db.userDao(), db.accountDao(), prefs, { "id${nextId++}" }, clock)

        val uid = (auth.signUp("julian@example.com", "secreto123") as AuthResult.Success).value.uid
        profiles.createAtSignUp(uid, "julian@example.com", LocalDate.of(2000, 5, 1))
        assertEquals(uid, session.currentUserId.first())

        // Only name, age, height and weight are asked; a 16-year-old gets no weight stored.
        val jesus = together.create(TrainingProfileDraft("Jesús", 22, 178f, 75f))
        val jose = together.create(TrainingProfileDraft("José", 16, 170f, 60f))
        assertTrue(jesus.startsWith("$uid~"))
        assertEquals(listOf(uid, jesus, jose), together.profiles.first().map { it.id })
        assertEquals(75f, profiles.get(jesus)!!.weightKg)
        assertNull(profiles.get(jose)!!.weightKg)
        assertEquals(AgeProfile.MINOR, profiles.get(jose)!!.ageProfile(LocalDate.now(clock)))
        assertEquals(22, java.time.Period.between(profiles.get(jesus)!!.birthDate, LocalDate.now(clock)).years)

        // Switching changes whose data the whole app reads and writes.
        together.switchTo(jesus)
        assertEquals(jesus, session.currentUserId.filter { it == jesus }.first())
        db.nutritionDao().upsertWater(WaterLogEntity("w1", jesus, 500, clock.instant(), SyncMeta(clock.instant())))
        assertEquals(uid, session.accountUserId.first())

        // The account's sync covers its profiles.
        val store = SyncStore(db)
        assertEquals(listOf(uid, jesus, jose), store.scopeIds(uid))
        val pending = store.pendingChanges(jesus, 50)
        assertTrue(pending.any { it.table == "water_logs" && it.id == "w1" })
        store.markPushed(pending) // as if the cloud had stored them

        // Deleting the profile on screen goes back to the owner; its deletions still get uploaded.
        together.delete(jesus)
        assertEquals(uid, session.currentUserId.filter { it == uid }.first())
        assertNull(profiles.get(jesus))
        assertNull(db.nutritionDao().getWater("w1"))
        assertTrue(jesus in store.scopeIds(uid))

        // A foreign id can never be switched to.
        together.switchTo("someone-else~x")
        assertEquals(uid, session.currentUserId.first())
    }
}
