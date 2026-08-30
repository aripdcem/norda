package com.aripd.norda.core.track

import org.junit.Assert.assertEquals
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
        // 5 saniyede ~100 m → 20 m/s > 10 m/s tavanı
        assertFalse(GpsFilter.accept(p(0), p(5_000, lat = 41.0 + step10m * 10)))
    }

    // F-11 kalibrasyonu (MVP 5.2 "başlangıç değerleri sahayla kalibre
    // edilir"): iki turda ilk-fix oturma sıçraması (12,7 ve 12,85 m/s) eski
    // 15 m/s tavanının hemen altından geçti. Yürüyüş/koşu ürününde 10 m/s
    // (36 km/h) üstü hareket koşu değil sıçramadır.
    @Test
    fun settlingSpikeFromFieldIsRejected() {
        // saha verisi: 2 sn'de ~25,7 m = 12,85 m/s
        assertEquals(
            GpsFilter.Verdict.TELEPORT,
            GpsFilter.evaluate(p(0), p(2_000, lat = 41.0 + step10m * 2.57))
        )
    }

    @Test
    fun fastDownhillRunStaysAccepted() {
        // 3 sn'de ~27 m = 9 m/s — hızlı iniş koşusu tavanın altında kalmalı
        assertTrue(GpsFilter.accept(p(0), p(3_000, lat = 41.0 + step10m * 2.7)))
    }

    @Test
    fun nonPositiveTimeDeltaIsRejected() {
        assertFalse(GpsFilter.accept(p(5_000), p(5_000, lat = 41.0 + step10m)))
        assertFalse(GpsFilter.accept(p(5_000), p(4_000, lat = 41.0 + step10m)))
    }

    // Faz 8, filtre kalibrasyonu: sahada eşik ayarlamak için yalnız kabul/ret
    // değil, RET NEDENİ de görünür olmalı.
    @Test
    fun evaluateNamesTheRejectionReason() {
        assertEquals(GpsFilter.Verdict.ACCEPT, GpsFilter.evaluate(null, p(0)))
        assertEquals(
            GpsFilter.Verdict.ACCEPT,
            GpsFilter.evaluate(p(0), p(5_000, lat = 41.0 + step10m))
        )
        assertEquals(
            GpsFilter.Verdict.BAD_ACCURACY,
            GpsFilter.evaluate(p(0), p(5_000, lat = 41.0 + step10m, acc = 31f))
        )
        assertEquals(
            GpsFilter.Verdict.NON_MONOTONIC,
            GpsFilter.evaluate(p(5_000), p(4_000, lat = 41.0 + step10m))
        )
        assertEquals(
            GpsFilter.Verdict.JITTER,
            GpsFilter.evaluate(p(0), p(5_000, lat = 41.0 + step10m / 10))
        )
        assertEquals(
            GpsFilter.Verdict.TELEPORT,
            GpsFilter.evaluate(p(0), p(5_000, lat = 41.0 + step10m * 10))
        )
    }

    @Test
    fun acceptStaysConsistentWithEvaluate() {
        val cases = listOf(
            null to p(0),
            p(0) to p(5_000, lat = 41.0 + step10m),
            p(0) to p(5_000, lat = 41.0 + step10m, acc = 31f),
            p(0) to p(5_000, lat = 41.0 + step10m / 10),
            p(0) to p(5_000, lat = 41.0 + step10m * 10)
        )
        for ((prev, cand) in cases) {
            assertEquals(
                GpsFilter.evaluate(prev, cand) == GpsFilter.Verdict.ACCEPT,
                GpsFilter.accept(prev, cand)
            )
        }
    }
}
