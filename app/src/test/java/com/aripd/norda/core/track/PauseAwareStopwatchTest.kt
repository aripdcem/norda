package com.aripd.norda.core.track

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PauseAwareStopwatchTest {

    @Test
    fun countsOnlyRunningTime() {
        val w = PauseAwareStopwatch()
        w.start(1_000)
        assertEquals(5_000, w.elapsedMillis(6_000))
        w.pause(6_000)
        assertEquals(5_000, w.elapsedMillis(60_000))
        w.resume(60_000)
        assertEquals(7_000, w.elapsedMillis(62_000))
    }

    @Test
    fun pauseAndStartAreIdempotent() {
        val w = PauseAwareStopwatch()
        w.start(0)
        w.start(500)
        w.pause(1_000)
        w.pause(2_000)
        assertEquals(1_000, w.elapsedMillis(9_000))
        assertFalse(w.isRunning)
        w.resume(10_000)
        assertTrue(w.isRunning)
    }

    @Test
    fun primeAdoptsRecoveredElapsed() {
        val w = PauseAwareStopwatch()
        w.prime(120_000)
        w.start(1_000)
        assertEquals(125_000, w.elapsedMillis(6_000))
    }
}
