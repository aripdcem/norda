package com.aripd.norda.core.track

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class StatsTest {

    // A latitude step of ~10 m.
    private val step10m = 0.00008993

    private fun p(t: Long, latSteps: Int) =
        TrackPoint(t, 41.0 + latSteps * step10m, 29.0, 0.0, 10f, 0f, 0f)

    @Test
    fun totalDistanceSumsSegments() {
        val points = listOf(p(0, 0), p(5_000, 1), p(10_000, 2), p(15_000, 3))
        assertEquals(30.0, Stats.totalDistanceMeters(points), 0.5)
    }

    @Test
    fun avgPaceKnownValue() {
        // 1 km / 300 s → 300 s/km (5:00)
        assertEquals(300.0, Stats.avgPaceSecPerKm(1_000.0, 300_000)!!, 1e-9)
    }

    @Test
    fun avgPaceMeaninglessUnderMinDistance() {
        assertNull(Stats.avgPaceSecPerKm(9.0, 60_000))
        assertNull(Stats.avgPaceSecPerKm(1_000.0, 0))
    }

    @Test
    fun avgSpeedKnownValue() {
        assertEquals(6.0, Stats.avgSpeedKmh(3_000.0, 1_800_000), 1e-9)
    }

    @Test
    fun currentPaceUsesTrailingWindowOnly() {
        // The first 30 s fast (10 m / 5 s), the last 30 s slow (10 m / 15 s):
        // the live pace must see only the trailing window.
        val points = listOf(
            p(0, 0), p(5_000, 1), p(10_000, 2), p(15_000, 3),
            p(30_000, 4), p(45_000, 5), p(60_000, 6)
        )
        val pace = Stats.currentPaceSecPerKm(points)!!
        // window: t >= 30_000 → 20 m / 30 s = 1500 s/km
        assertEquals(1_500.0, pace, 40.0)
    }

    @Test
    fun currentPaceNullWithoutRecentMovement() {
        val standing = listOf(p(0, 0), p(10_000, 1), p(35_000, 1), p(40_000, 1))
        assertNull(Stats.currentPaceSecPerKm(standing))
        assertNotNull(Stats.currentPaceSecPerKm(listOf(p(0, 0), p(10_000, 1))))
    }
}
