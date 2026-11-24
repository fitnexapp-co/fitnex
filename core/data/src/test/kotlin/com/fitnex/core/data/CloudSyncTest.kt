package com.fitnex.core.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fitnex.core.auth.AuthResult
import com.fitnex.core.data.profile.RoomUserProfileRepository
import com.fitnex.core.data.sync.BackendCloudSync
import com.fitnex.core.data.sync.SyncProblem
import com.fitnex.core.database.FitnexDatabase
import com.fitnex.core.database.entity.SyncMeta
import com.fitnex.core.database.entity.WaterLogEntity
import com.fitnex.core.database.entity.WorkoutEntity
import com.fitnex.core.database.sync.SyncStore
import com.fitnex.core.network.ChatRequest
import com.fitnex.core.network.FitnexBackendApi
import com.fitnex.core.network.FoodPhotoRequest
import com.fitnex.core.network.NetworkMonitor
import com.fitnex.core.network.SyncEraseResponse
import com.fitnex.core.network.SyncPullRequest
import com.fitnex.core.network.SyncPullResponse
import com.fitnex.core.network.SyncPushRequest
import com.fitnex.core.network.SyncPushResponse
import com.fitnex.core.network.SyncRecordDto
import com.fitnex.core.testing.FakeAuthRepository
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Two devices of the same user against an in-memory server with the same rules as backend/worker. */
@RunWith(AndroidJUnit4::class)
class CloudSyncTest {
    private val server = FakeSyncServer()
    // In the past: tombstones are stamped by SQLite's real clock, which must come after these edits.
    private val clock = Clock.fixed(Instant.parse("2026-01-20T10:00:00Z"), ZoneOffset.UTC)
    private lateinit var phone: Device
    private lateinit var tablet: Device
    private lateinit var uid: String

    private inner class Device {
        val db: FitnexDatabase = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), FitnexDatabase::class.java)
            .addCallback(FitnexDatabase.syncCallback).allowMainThreadQueries().build()
        val auth = FakeAuthRepository()
        val profiles = RoomUserProfileRepository(db.userDao(), db.accountDao(), clock)
        val sync = BackendCloudSync(server, SyncStore(db), db.syncDao(), db.userDao(), Online, auth, clock)
    }

    @Before
    fun setUp() {
        phone = Device()
        tablet = Device()
        uid = kotlinx.coroutines.runBlocking { (phone.auth.signUp("ana@example.com", "secreto123") as AuthResult.Success).value.uid }
    }

    @After
    fun tearDown() { phone.db.close(); tablet.db.close() }

    @Test
    fun newDeviceGetsProfileAndHistoryAndChangesFlowBothWays() = runTest {
        phone.profiles.createAtSignUp(uid, "ana@example.com", LocalDate.of(2000, 5, 1))
        phone.profiles.saveAndCompleteOnboarding(phone.profiles.get(uid)!!.copy(name = "Ana"))
        phone.db.workoutDao().upsertWorkout(WorkoutEntity("r1", uid, "Pierna", emptySet(), false, null, clock.instant(), SyncMeta(clock.instant())))
        phone.db.nutritionDao().upsertWater(WaterLogEntity("w1", uid, 250, clock.instant(), SyncMeta(clock.instant())))
        assertNull(phone.sync.syncNow(uid))

        assertNull(tablet.sync.syncNow(uid))
        assertEquals("Ana", tablet.profiles.get(uid)!!.name)
        assertEquals(250, tablet.db.nutritionDao().getWater("w1")!!.quantityMl)

        // Tablet deletes the routine; phone receives the deletion on its next sync.
        tablet.db.workoutDao().hardDelete("r1")
        assertNull(tablet.sync.syncNow(uid))
        assertNull(phone.sync.syncNow(uid))
        assertNull(phone.db.workoutDao().observeWorkout("r1").first())
        assertNotNull(phone.db.nutritionDao().getWater("w1"))
        assertEquals(0, SyncStore(phone.db).pendingCount(uid))
    }

    @Test
    fun offlineKeepsChangesPendingUntilTheConnectionReturns() = runTest {
        phone.db.nutritionDao().upsertWater(WaterLogEntity("w1", uid, 250, clock.instant(), SyncMeta(clock.instant())))
        server.offline = true
        assertEquals(SyncProblem.OFFLINE, phone.sync.syncNow(uid))
        assertEquals(1, SyncStore(phone.db).pendingCount(uid))

        server.offline = false
        assertNull(phone.sync.syncNow(uid))
        assertEquals(0, SyncStore(phone.db).pendingCount(uid))
        assertEquals(1, server.rows.size)
    }

    private object Online : NetworkMonitor { override val isOnline = flowOf(true) }

    /** Same contract as backend/worker/src/sync.js: per-record sequence, LWW, deletions win ties. */
    private class FakeSyncServer : FitnexBackendApi {
        data class Row(val dto: SyncRecordDto, val seq: Long)
        val rows = mutableMapOf<Pair<String, String>, Row>()
        var seq = 0L
        var offline = false

        override suspend fun syncPush(request: SyncPushRequest): SyncPushResponse {
            if (offline) throw IOException("offline")
            var stored = 0
            for (r in request.records) {
                val key = r.t to r.id
                val old = rows[key]?.dto
                if (old == null || r.u > old.u || (r.u == old.u && (r.d || !old.d))) {
                    rows[key] = Row(r, ++seq); stored++
                }
            }
            return SyncPushResponse(stored)
        }

        override suspend fun syncPull(request: SyncPullRequest): SyncPullResponse {
            if (offline) throw IOException("offline")
            val page = rows.values.filter { it.seq > request.since }.sortedBy { it.seq }.take(request.limit)
            val next = page.lastOrNull()?.seq ?: request.since
            return SyncPullResponse(page.map { it.dto.copy(seq = it.seq) }, next, hasMore = rows.values.any { it.seq > next })
        }

        override suspend fun syncErase(): SyncEraseResponse { rows.clear(); return SyncEraseResponse(true) }
        override suspend fun health() = error("not used")
        override suspend fun chat(request: ChatRequest) = error("not used")
        override suspend fun analyzeFood(request: FoodPhotoRequest) = error("not used")
        override suspend fun routine(request: com.fitnex.core.network.RoutineRequestDto) = error("not used")
        override suspend fun plan(request: com.fitnex.core.network.PlanRequestDto) = error("not used")
    }
}
