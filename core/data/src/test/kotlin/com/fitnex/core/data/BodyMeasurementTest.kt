package com.fitnex.core.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fitnex.core.data.body.BodyMeasurement
import com.fitnex.core.data.body.BodyMeasurementRepository
import com.fitnex.core.database.FitnexDatabase
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BodyMeasurementTest {
    private lateinit var db: FitnexDatabase
    private val clock = Clock.fixed(Instant.parse("2026-11-21T10:00:00Z"), ZoneOffset.UTC)

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), FitnexDatabase::class.java).allowMainThreadQueries().build()
    }

    @After fun tearDown() = db.close()

    @Test
    fun measurementsAreOptionalValidatedPerUserAndSoftDeleted() = runTest {
        val repo = BodyMeasurementRepository(db.bodyDao(), { UUID.randomUUID().toString() }, clock)
        val old = repo.add("u1", BodyMeasurement("", LocalDate.of(2026, 10, 1), waistCm = 84f))!!
        repo.add("u1", BodyMeasurement("", LocalDate.of(2026, 11, 21), waistCm = 81.5f, armCm = 36f, chestCm = 999f)) // 999: typo, dropped
        assertNull("nothing valid → nothing saved", repo.add("u1", BodyMeasurement("", LocalDate.of(2026, 11, 21), thighCm = 2f)))

        val list = repo.observe("u1").first()
        assertEquals(listOf(81.5f, 84f), list.map { it.waistCm }) // newest first
        assertNull(list.first().chestCm)
        assertTrue("other users never see them", repo.observe("u2").first().isEmpty())

        repo.delete(old)
        assertEquals(1, repo.observe("u1").first().size)
    }
}
