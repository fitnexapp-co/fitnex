package com.fitnex.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fitnex.core.database.entity.AiConversationEntity
import com.fitnex.core.database.entity.AiMessageEntity
import com.fitnex.core.database.entity.MealEntity
import com.fitnex.core.database.entity.SyncMeta
import com.fitnex.core.database.entity.WaterLogEntity
import com.fitnex.core.database.entity.WorkoutEntity
import com.fitnex.core.database.entity.WorkoutExerciseEntity
import com.fitnex.core.database.entity.WorkoutSessionEntity
import com.fitnex.core.database.sync.SyncRecord
import com.fitnex.core.database.sync.SyncStore
import com.fitnex.core.model.AiRole
import com.fitnex.core.model.AnalysisStatus
import com.fitnex.core.model.MealType
import com.fitnex.core.model.SessionStatus
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SyncStoreTest {
    private lateinit var a: FitnexDatabase
    private lateinit var b: FitnexDatabase
    private lateinit var storeA: SyncStore
    private lateinit var storeB: SyncStore
    private val t0 = Instant.parse("2026-11-20T10:00:00Z")

    private fun newDb() = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), FitnexDatabase::class.java)
        .addCallback(FitnexDatabase.syncCallback).allowMainThreadQueries().build()

    @Before
    fun setUp() {
        a = newDb(); b = newDb()
        storeA = SyncStore(a); storeB = SyncStore(b)
    }

    @After
    fun tearDown() { a.close(); b.close() }

    @Test
    fun pendingRowsAreCollectedAndMarkedSyncedOnlyIfUnchanged() = runTest {
        a.nutritionDao().upsertWater(water("w1", 250, t0))
        val pending = storeA.pendingChanges("u1", 100)
        assertEquals(listOf("water_logs" to "w1"), pending.map { it.table to it.id })
        assertEquals(250L, pending.single().data!!["quantity_ml"]!!.jsonPrimitive.content.toLong())
        assertFalse("sync_status is local bookkeeping, not data", "sync_status" in pending.single().data!!)

        a.nutritionDao().upsertWater(water("w1", 500, t0.plusSeconds(5))) // edited again before the ack
        storeA.markPushed(pending)
        assertEquals("the newer edit stays pending", 1, storeA.pendingCount("u1"))
        storeA.markPushed(storeA.pendingChanges("u1", 100))
        assertEquals(0, storeA.pendingCount("u1"))
    }

    @Test
    fun hardDeletesLeaveTombstonesThatTravelAsDeletions() = runTest {
        a.workoutDao().upsertWorkout(workout("r1", "Pierna", t0))
        storeA.markPushed(storeA.pendingChanges("u1", 100))
        a.workoutDao().hardDelete("r1")

        val pending = storeA.pendingChanges("u1", 100)
        assertEquals(1, pending.size)
        assertTrue(pending.single().deleted)
        assertNull(pending.single().data)
        storeA.markPushed(pending)
        assertEquals(0, storeA.pendingCount("u1"))
    }

    @Test
    fun applyRemoteRestoresRowsWithTheirChildrenOnAnotherDevice() = runTest {
        a.workoutDao().upsertWorkout(workout("r1", "Pierna", t0))
        a.workoutDao().upsertWorkoutExercises(listOf(item("e1", "r1", "squat", 0), item("e2", "r1", "lunge", 1)))
        a.sessionDao().upsertSession(session("s1", "r1", t0))
        a.nutritionDao().upsertMeal(meal("m1", t0))

        val report = storeB.applyRemote(storeA.pendingChanges("u1", 100))

        assertEquals(3, report.applied)
        val restored = b.workoutDao().observeWorkout("r1").first()!!
        assertEquals("Pierna", restored.workout.name)
        assertEquals(listOf("squat", "lunge"), restored.exercises.sortedBy { it.position }.map { it.exerciseId })
        assertEquals(0, storeB.pendingCount("u1"))
        assertEquals("applying never creates tombstones or pending rows", 0, storeB.pendingChanges("u1", 100).size)
    }

    @Test
    fun lastWriterWinsAndRemoteDeletionsDoNotEcho() = runTest {
        a.nutritionDao().upsertWater(water("w1", 250, t0))
        storeB.applyRemote(storeA.pendingChanges("u1", 100))

        // B edits later than A's next edit: B's pending row must survive A's older version.
        b.nutritionDao().upsertWater(water("w1", 750, t0.plusSeconds(60)))
        a.nutritionDao().upsertWater(water("w1", 500, t0.plusSeconds(30)))
        val report = storeB.applyRemote(storeA.pendingChanges("u1", 100))
        assertEquals(1, report.skippedOlder)
        assertEquals(750, waterMl(b, "w1"))

        // A remote deletion newer than the local copy removes it without a tombstone echo.
        val deletion = SyncRecord("water_logs", "w1", t0.plusSeconds(120).toEpochMilli(), deleted = true, data = null)
        storeB.applyRemote(listOf(deletion))
        assertNull(waterMl(b, "w1"))
        assertEquals(0, storeB.pendingCount("u1"))
    }

    @Test
    fun danglingOptionalReferenceIsNulledInsteadOfLosingHistory() = runTest {
        a.workoutDao().upsertWorkout(workout("r1", "Pierna", t0))
        a.sessionDao().upsertSession(session("s1", "r1", t0))
        val onlySession = storeA.pendingChanges("u1", 100).filter { it.table == "workout_sessions" }

        storeB.applyRemote(onlySession)

        val s = b.sessionDao().getSession("s1")!!
        assertNull(s.workoutId)
        assertEquals("Pierna", s.workoutNameSnapshot)
    }

    @Test
    fun exportContainsEverythingTheUserOwnsIncludingLocalOnlyChats() = runTest {
        a.nutritionDao().upsertWater(water("w1", 250, t0))
        a.nutritionDao().upsertWater(water("w2", 250, t0).copy(userId = "someone-else"))
        a.aiDao().upsertConversation(AiConversationEntity("c1", "u1", null, t0, SyncMeta(t0)))
        a.aiDao().upsertMessage(AiMessageEntity("msg1", "c1", AiRole.USER, "Hola", null, t0))

        val tables = storeA.export("u1")["tables"]!!.jsonObject
        assertEquals(1, tables["water_logs"]!!.jsonArray.size)
        assertEquals("Hola", tables["ai_messages"]!!.jsonArray.single().jsonObject["content"]!!.jsonPrimitive.content)
    }

    private suspend fun waterMl(db: FitnexDatabase, id: String): Int? = db.nutritionDao().getWater(id)?.quantityMl

    private fun water(id: String, ml: Int, at: Instant) = WaterLogEntity(id, "u1", ml, at, SyncMeta(at))
    private fun workout(id: String, name: String, at: Instant) =
        WorkoutEntity(id, "u1", name, emptySet(), isTemplate = false, aiRationale = null, createdAt = at, sync = SyncMeta(at))
    private fun item(id: String, workoutId: String, exercise: String, pos: Int) =
        WorkoutExerciseEntity(id, workoutId, exercise, pos, 3, 10, null, 90, SyncMeta(t0))
    private fun session(id: String, workoutId: String, at: Instant) = WorkoutSessionEntity(
        id = id, userId = "u1", workoutId = workoutId, workoutNameSnapshot = "Pierna", startedAt = at, finishedAt = at.plusSeconds(1800),
        durationSeconds = 1800, pausedSeconds = 0, status = SessionStatus.FINISHED, currentExerciseIndex = 0, sync = SyncMeta(at),
    )
    private fun meal(id: String, at: Instant) = MealEntity(
        id, "u1", MealType.LUNCH, LocalDate.of(2026, 11, 20), at, null, null, AnalysisStatus.MANUAL, SyncMeta(at),
    )

    @Test
    fun resetTrainingHistoryErasesSessionsOfTheAccountAndProfilesOnly() = runTest {
        val db = a.openHelper.writableDatabase
        fun session(id: String, user: String) = db.execSQL(
            "INSERT INTO workout_sessions (id, user_id, workout_id, workout_name_snapshot, started_at, finished_at, duration_seconds, " +
                "paused_seconds, status, current_exercise_index, plan_json, paused_at, updated_at, sync_status, deleted_at) " +
                "VALUES ('$id', '$user', NULL, 'Pierna', 1, 2, 60, 0, 'FINISHED', 0, NULL, NULL, 1, 'SYNCED', NULL)",
        )
        session("s1", "u1"); session("s2", "u1~p1"); session("s3", "u2")
        db.execSQL(
            "INSERT INTO exercise_sets (id, session_id, exercise_id, exercise_name_snapshot, exercise_position, set_number, repetitions, " +
                "weight_kg, rest_seconds, source, completed_at, duration_seconds, distance_m, updated_at, sync_status, deleted_at) " +
                "VALUES ('x1', 's2', 'squat', 'Sentadilla', 0, 1, 8, 60.0, 90, 'MANUAL', 1, NULL, NULL, 1, 'SYNCED', NULL)",
        )
        a.workoutDao().upsertWorkout(workout("r1", "Pierna", t0)) // routines are not statistics

        a.accountDao().resetTrainingHistory(listOf("u1", "u1~p1"), t0)

        fun pendingDelete(table: String, id: String) = db.query("SELECT deleted_at IS NOT NULL, sync_status FROM $table WHERE id = '$id'").use {
            it.moveToFirst(); it.getInt(0) == 1 && it.getString(1) == "PENDING_DELETE"
        }
        assertTrue("account session", pendingDelete("workout_sessions", "s1"))
        assertTrue("profile session", pendingDelete("workout_sessions", "s2"))
        assertTrue("profile set", pendingDelete("exercise_sets", "x1"))
        assertFalse("another account is untouched", pendingDelete("workout_sessions", "s3"))
        assertFalse("routines stay", pendingDelete("workouts", "r1"))
    }

    /** Every table the app uploads must be accepted by the deployed sync server (backend/worker). */
    @Test
    fun serverAcceptsEveryTableTheAppUploads() {
        var root = java.io.File("").absoluteFile
        while (!java.io.File(root, "settings.gradle.kts").exists()) root = root.parentFile ?: error("project root not found")
        val js = java.io.File(root, "backend/worker/src/sync.js").readText()
        val block = js.substringAfter("SYNC_TABLES = new Set([").substringBefore("]);")
        val server = Regex("\"([a-z_]+)\"").findAll(block).map { it.groupValues[1] }.toSet()
        val app = com.fitnex.core.database.sync.SyncSchema.tables.map { it.name }.toSet()
        assertEquals("tablas que el servidor rechazaría", emptySet<String>(), app - server)
    }
}