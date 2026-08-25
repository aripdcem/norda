package com.aripd.norda.core.track

import org.junit.Assert.assertEquals
import org.junit.Test

class ElevationTrackerTest {

    // GNSS dikey gürültüsü: ±3 m salınım hiç tırmanış saymamalı.
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
        // 100 → 200, 5'er metrelik adımlar: her adım eşiği (4 m) aşar
        for (a in 100..200 step 5) e.onAltitude(a.toDouble())
        assertEquals(100.0, e.gainM, 1e-9)
        assertEquals(0.0, e.lossM, 1e-9)
    }

    // Yavaş ama gerçek tırmanış: adımlar eşiğin altında olsa da demirden
    // ayrışma birikince sayılır — eşik toplamı yutmaz, yalnız geciktirir.
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
