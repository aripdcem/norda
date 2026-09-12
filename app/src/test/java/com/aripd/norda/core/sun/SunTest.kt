package com.aripd.norda.core.sun

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Night mode (docs/MVP.md 2, 3.7) switches on civil twilight: the sun more
 * than 6° below the horizon. Altitude is computed directly from date, time and
 * position — one formula that also behaves at the poles, where sunset tables
 * have no answer.
 */
class SunTest {

    private val istanbulLat = 41.01
    private val istanbulLon = 28.97

    private fun utc(iso: String): Long = java.time.Instant.parse(iso).toEpochMilli()

    @Test
    fun solarNoonOnTheJuneSolsticeInIstanbul() {
        // 12:00 − 28.97°/15 ≈ 10:04 UTC (+ equation of time ≈ −2 min) → 10:06Z
        val alt = Sun.altitudeDeg(istanbulLat, istanbulLon, utc("2026-06-21T10:06:00Z"))
        assertEquals(90.0 - (istanbulLat - 23.44), alt, 0.6)
    }

    @Test
    fun solarMidnightOnTheJuneSolsticeInIstanbul() {
        val alt = Sun.altitudeDeg(istanbulLat, istanbulLon, utc("2026-06-21T22:06:00Z"))
        assertEquals(-(90.0 - istanbulLat - 23.44), alt, 0.7)
    }

    @Test
    fun equinoxNoonAtGreenwichIsNearTheZenithMinusLatitude() {
        val alt = Sun.altitudeDeg(0.0, 0.0, utc("2026-03-20T12:07:00Z"))
        assertTrue("altitude $alt", alt > 88.0)
    }

    @Test
    fun nightBeginsAtCivilDuskNotAtSunset() {
        // Istanbul, June 21: sunset ≈ 17:46Z, civil dusk ≈ 18:22Z
        assertFalse(Sun.isNight(istanbulLat, istanbulLon, utc("2026-06-21T12:00:00Z")))
        assertFalse(Sun.isNight(istanbulLat, istanbulLon, utc("2026-06-21T18:05:00Z")))  // dusk, still light
        assertTrue(Sun.isNight(istanbulLat, istanbulLon, utc("2026-06-21T18:45:00Z")))
        assertTrue(Sun.isNight(istanbulLat, istanbulLon, utc("2026-06-21T22:00:00Z")))
    }

    @Test
    fun midnightSunIsNotNight() {
        assertFalse(Sun.isNight(80.0, 20.0, utc("2026-06-21T23:00:00Z")))
    }

    @Test
    fun polarNightIsNight() {
        assertTrue(Sun.isNight(80.0, 20.0, utc("2026-12-21T12:00:00Z")))
    }
}
