package com.fitnex.feature.profile

import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test

class SyncedAgoTest {
    private val now = Instant.parse("2026-11-20T15:00:00Z")

    @Test
    fun formatsInSpanish() {
        assertEquals("hace un momento", syncedAgo(now.minusSeconds(20), now, ZoneOffset.UTC))
        assertEquals("hace 5 min", syncedAgo(now.minusSeconds(300), now, ZoneOffset.UTC))
        assertEquals("hoy a las 09:40", syncedAgo(Instant.parse("2026-11-20T09:40:00Z"), now, ZoneOffset.UTC))
        assertEquals("18 nov, 21:05", syncedAgo(Instant.parse("2026-11-18T21:05:00Z"), now, ZoneOffset.UTC))
    }
}
