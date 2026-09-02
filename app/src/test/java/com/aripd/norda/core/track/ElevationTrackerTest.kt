package com.aripd.norda.core.track

import org.junit.Assert.assertEquals
import org.junit.Test

class ElevationTrackerTest {

    // GNSS vertical noise: a ±3 m oscillation must count no climb at all.
    @Test
    fun flatNoiseAccumulatesNothing() {
        val e = ElevationTracker()
        listOf(100.0, 103.0, 98.0, 101.0, 97.5, 100.5, 99.0).forEach { e.onAltitude(it) }
        assertEquals(0.0, e.gainM, 1e-9)
        assertEquals(0.0, e.lossM, 1e-9)
    }

    @Test
    fun steadyClimbIsCounted() {
        val e = ElevationTracker()
        // 100 → 200 in 5 m steps: every step exceeds the threshold (4 m)
        for (a in 100..200 step 5) e.onAltitude(a.toDouble())
        assertEquals(100.0, e.gainM, 1e-9)
        assertEquals(0.0, e.lossM, 1e-9)
    }

    // A slow but real climb: even though the steps are below the threshold,
    // the divergence from the anchor is counted once it accumulates — the
    // threshold does not swallow the total, it only delays it.
    @Test
    fun slowClimbStillAccumulates() {
        val e = ElevationTracker()
        for (a in 0..20 step 2) e.onAltitude(a.toDouble())
        assertEquals(20.0, e.gainM, 1e-9)
    }

    @Test
    fun descentCountsAsLoss() {
        val e = ElevationTracker()
        for (a in 200 downTo 100 step 5) e.onAltitude(a.toDouble())
        assertEquals(0.0, e.gainM, 1e-9)
        assertEquals(100.0, e.lossM, 1e-9)
    }

    @Test
    fun rollingHillCountsBothSides() {
        val e = ElevationTracker()
        for (a in 100..150 step 5) e.onAltitude(a.toDouble())
        for (a in 150 downTo 100 step 5) e.onAltitude(a.toDouble())
        assertEquals(50.0, e.gainM, 1e-9)
        assertEquals(50.0, e.lossM, 1e-9)
    }
}
