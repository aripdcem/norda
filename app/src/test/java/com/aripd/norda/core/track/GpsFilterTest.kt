package com.aripd.norda.core.track

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GpsFilterTest {

    private fun p(
        t: Long,
        lat: Double = 41.0,
        lon: Double = 29.0,
        acc: Float = 10f
    ) = TrackPoint(t, lat, lon, 0.0, acc, 0f, 0f)

    // ~10 m'lik enlem adımı.
    private val step10m = 0.00008993

    @Test
    fun firstFixIsAccepted() {
        assertTrue(GpsFilter.accept(null, p(0)))
    }

    @Test
    fun normalWalkIsAccepted() {
        assertTrue(GpsFilter.accept(p(0), p(5_000, lat = 41.0 + step10m)))
    }

    @Test
    fun poorAccuracyIsRejected() {
        assertFalse(GpsFilter.accept(p(0), p(5_000, lat = 41.0 + step10m, acc = 31f)))
    }

    // accuracy <= 0: cihaz doğruluk bildirmiyor — reddetmek hiç kayıt
    // yapmamak olurdu.
    @Test
    fun missingAccuracyIsAccepted() {
        assertTrue(GpsFilter.accept(p(0), p(5_000, lat = 41.0 + step10m, acc = 0f)))
    }

    @Test
    fun standstillJitterIsRejected() {
        // 1 m kıpırdama < 2 m eşiği
        assertFalse(GpsFilter.accept(p(0), p(5_000, lat = 41.0 + step10m / 10)))
    }

    @Test
    fun teleportIsRejected() {
        // 5 saniyede ~100 m → 20 m/s > 15 m/s tavanı
        assertFalse(GpsFilter.accept(p(0), p(5_000, lat = 41.0 + step10m * 10)))
    }

    @Test
    fun nonPositiveTimeDeltaIsRejected() {
        assertFalse(GpsFilter.accept(p(5_000), p(5_000, lat = 41.0 + step10m)))
        assertFalse(GpsFilter.accept(p(5_000), p(4_000, lat = 41.0 + step10m)))
    }
}
