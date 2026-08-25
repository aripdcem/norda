package com.aripd.norda.core.nav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReturnToStartTest {

    // Ekvatorda başlangıç tam doğuda: kerteriz 90° — taslaktaki ayna
    // hatasını kapatan senaryonun kılavuz seviyesindeki karşılığı.
    @Test
    fun bearingAndDistanceToEastStart() {
        val g = ReturnToStart.guidance(0.0, 29.0, 0.0, 29.1, paceSecPerKm = null)
        assertEquals(90.0, g.bearingDeg, 0.01)
        assertEquals(11_119.5, g.distanceM, 30.0)
        assertNull(g.etaMillis)
    }

    // 2 km @ 10 dk/km → 20 dk.
    @Test
    fun etaFromPace() {
        val g = ReturnToStart.guidance(41.0, 29.0, 41.0179866, 29.0, 600.0)
        assertEquals(2_000.0, g.distanceM, 15.0)
        assertEquals(20.0 * 60.0 * 1000.0, g.etaMillis!!.toDouble(), 15_000.0)
    }

    @Test
    fun relativeAngleSignsSteering() {
        // Kuzeye bakıyorsun, hedef 40°'de: 40° sağa.
        assertEquals(30.0, ReturnToStart.relativeAngleDeg(10.0, 40.0), 1e-9)
        // Kuzey geçişi tam tur saydırmaz: 350°'den 10°'ye 20° sağa.
        assertEquals(20.0, ReturnToStart.relativeAngleDeg(350.0, 10.0), 1e-9)
        assertEquals(-20.0, ReturnToStart.relativeAngleDeg(10.0, 350.0), 1e-9)
    }
}
