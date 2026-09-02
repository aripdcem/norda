package com.aripd.norda.core.nav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReturnToStartTest {

    // At the equator the start is due east: bearing 90° — the guidance-level
    // counterpart of the scenario that closed the mirror bug in the draft.
    @Test
    fun bearingAndDistanceToEastStart() {
        val g = ReturnToStart.guidance(0.0, 29.0, 0.0, 29.1, paceSecPerKm = null)
        assertEquals(90.0, g.bearingDeg, 0.01)
        assertEquals(11_119.5, g.distanceM, 30.0)
        assertNull(g.etaMillis)
    }

    // 2 km @ 10 min/km → 20 min.
    @Test
    fun etaFromPace() {
        val g = ReturnToStart.guidance(41.0, 29.0, 41.0179866, 29.0, 600.0)
        assertEquals(2_000.0, g.distanceM, 15.0)
        assertEquals(20.0 * 60.0 * 1000.0, g.etaMillis!!.toDouble(), 15_000.0)
    }

    @Test
    fun relativeAngleSignsSteering() {
        // You are facing 10°, the target is at 40°: 30° to the right.
        assertEquals(30.0, ReturnToStart.relativeAngleDeg(10.0, 40.0), 1e-9)
        // Crossing north does not count a full turn: from 350° to 10° is 20°
        // to the right.
        assertEquals(20.0, ReturnToStart.relativeAngleDeg(350.0, 10.0), 1e-9)
        assertEquals(-20.0, ReturnToStart.relativeAngleDeg(10.0, 350.0), 1e-9)
    }
}
