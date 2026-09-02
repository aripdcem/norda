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

    // A latitude step of ~10 m.
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

    // accuracy <= 0: the device reports no accuracy — rejecting would mean
    // never recording at all.
    @Test
    fun missingAccuracyIsAccepted() {
        assertTrue(GpsFilter.accept(p(0), p(5_000, lat = 41.0 + step10m, acc = 0f)))
    }

    @Test
    fun standstillJitterIsRejected() {
        // 1 m of jitter < 2 m threshold
        assertFalse(GpsFilter.accept(p(0), p(5_000, lat = 41.0 + step10m / 10)))
    }

    @Test
    fun teleportIsRejected() {
        // ~100 m in 5 seconds → 20 m/s > 10 m/s ceiling
        assertFalse(GpsFilter.accept(p(0), p(5_000, lat = 41.0 + step10m * 10)))
    }

    // F-11 calibration (MVP 5.2 "initial values are calibrated in the
    // field"): on two tours the first-fix settling spike (12.7 and 12.85 m/s)
    // passed just under the old 15 m/s ceiling. In a walking/running product,
    // movement above 10 m/s (36 km/h) is a jump, not a run.
    @Test
    fun settlingSpikeFromFieldIsRejected() {
        // field data: ~25.7 m in 2 s = 12.85 m/s
        assertEquals(
            GpsFilter.Verdict.TELEPORT,
            GpsFilter.evaluate(p(0), p(2_000, lat = 41.0 + step10m * 2.57))
        )
    }

    @Test
    fun fastDownhillRunStaysAccepted() {
        // ~27 m in 3 s = 9 m/s — a fast downhill run must stay under the ceiling
        assertTrue(GpsFilter.accept(p(0), p(3_000, lat = 41.0 + step10m * 2.7)))
    }

    @Test
    fun nonPositiveTimeDeltaIsRejected() {
        assertFalse(GpsFilter.accept(p(5_000), p(5_000, lat = 41.0 + step10m)))
        assertFalse(GpsFilter.accept(p(5_000), p(4_000, lat = 41.0 + step10m)))
    }

    // Phase 8, filter calibration: to tune thresholds in the field, not only
    // accept/reject but the REASON FOR REJECTION must be visible too.
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
