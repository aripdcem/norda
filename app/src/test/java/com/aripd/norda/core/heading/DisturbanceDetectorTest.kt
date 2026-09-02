package com.aripd.norda.core.heading

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DisturbanceDetectorTest {

    // Istanbul's expected field is ~48.5 µT; the real disturbance case on the
    // desk was ~63 µT.
    private val expected = 48.5

    @Test
    fun shortSpikeDoesNotAlarm() {
        val d = DisturbanceDetector()
        assertFalse(d.onSample(63.0, expected, 0))
        assertFalse(d.onSample(63.0, expected, 2_000))
        assertFalse(d.onSample(49.0, expected, 2_400))   // deviation over, counter reset
        assertFalse(d.onSample(63.0, expected, 3_000))
        assertFalse(d.onSample(63.0, expected, 5_000))   // new window: 2 s
    }

    @Test
    fun sustainedDeviationAlarms() {
        val d = DisturbanceDetector()
        d.onSample(63.0, expected, 0)
        d.onSample(63.0, expected, 1_000)
        d.onSample(63.0, expected, 2_000)
        assertTrue(d.onSample(63.0, expected, 2_600))
    }

    // Hysteresis: the alarm stays on in the 15%-25% band and turns off below 15%.
    @Test
    fun hysteresisKeepsAlarmSteadyNearThreshold() {
        val d = DisturbanceDetector()
        d.onSample(63.0, expected, 0)
        d.onSample(63.0, expected, 2_600)
        assertTrue(d.isActive)
        assertTrue(d.onSample(58.0, expected, 3_000))    // ~20% deviation: stays on
        assertFalse(d.onSample(53.0, expected, 3_500))   // ~9%: turns off
    }

    @Test
    fun unknownExpectedFieldNeverAlarms() {
        val d = DisturbanceDetector()
        assertFalse(d.onSample(63.0, 0.0, 0))
        assertFalse(d.onSample(63.0, 0.0, 10_000))
    }
}
