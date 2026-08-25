package com.aripd.norda.core.heading

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DisturbanceDetectorTest {

    // İstanbul'un beklenen alanı ~48,5 µT; masadaki gerçek bozulma vakası ~63 µT.
    private val expected = 48.5

    @Test
    fun shortSpikeDoesNotAlarm() {
        val d = DisturbanceDetector()
        assertFalse(d.onSample(63.0, expected, 0))
        assertFalse(d.onSample(63.0, expected, 2_000))
        assertFalse(d.onSample(49.0, expected, 2_400))   // sapma bitti, sayaç sıfır
        assertFalse(d.onSample(63.0, expected, 3_000))
        assertFalse(d.onSample(63.0, expected, 5_000))   // yeni pencere: 2 sn
    }

    @Test
    fun sustainedDeviationAlarms() {
        val d = DisturbanceDetector()
        d.onSample(63.0, expected, 0)
        d.onSample(63.0, expected, 1_000)
        d.onSample(63.0, expected, 2_000)
        assertTrue(d.onSample(63.0, expected, 2_600))
    }

    // Histerezis: uyarı %15-%25 bandında açık kalır, %15 altında kapanır.
    @Test
    fun hysteresisKeepsAlarmSteadyNearThreshold() {
        val d = DisturbanceDetector()
        d.onSample(63.0, expected, 0)
        d.onSample(63.0, expected, 2_600)
        assertTrue(d.isActive)
        assertTrue(d.onSample(58.0, expected, 3_000))    // ~%20 sapma: açık kalır
        assertFalse(d.onSample(53.0, expected, 3_500))   // ~%9: kapanır
    }

    @Test
    fun unknownExpectedFieldNeverAlarms() {
        val d = DisturbanceDetector()
        assertFalse(d.onSample(63.0, 0.0, 0))
        assertFalse(d.onSample(63.0, 0.0, 10_000))
    }
}
