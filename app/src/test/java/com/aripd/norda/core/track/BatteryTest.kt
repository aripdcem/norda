package com.aripd.norda.core.track

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Battery measurement culture (MVP.md section 16, the "battery drain" risk):
 * a number is produced only when the measurement is clean — an unknown
 * level, an out-of-range reading, charging during the recording or a
 * duration too short for a rate returns null, not a made-up value.
 */
class BatteryTest {

    @Test
    fun drainIsStartMinusEnd() {
        assertEquals(7, Battery.drainPercent(93, 86))
        assertEquals(0, Battery.drainPercent(50, 50))
    }

    @Test
    fun unknownOrOutOfRangeLevelsGiveNoDrain() {
        assertNull(Battery.drainPercent(null, 80))
        assertNull(Battery.drainPercent(80, null))
        assertNull(Battery.drainPercent(120, 80))
        assertNull(Battery.drainPercent(80, -1))
    }

    @Test
    fun chargingDuringRecordingGivesNoDrain() {
        // If the phone was plugged in during the recording the measurement is
        // dirty; reporting a negative "drain" would be misleading.
        assertNull(Battery.drainPercent(40, 70))
    }

    @Test
    fun ratePerHourNormalizesByDuration() {
        assertEquals(2.5, Battery.drainPerHour(5, 2 * 60 * 60 * 1000L)!!, 1e-9)
        assertEquals(4.0, Battery.drainPerHour(1, 15 * 60 * 1000L)!!, 1e-9)
    }

    /**
     * Field Tour 1 data (F-1): 2% drain, 42:46 wall clock → 2.8 %/h.
     * The denominator is WALL-CLOCK time, not the active recording time — the
     * battery drains during pauses too; dividing by active time inflates the
     * rate (the same tour would have shown 4.2).
     */
    @Test
    fun rateDenominatorIsWallClockNotActiveDuration() {
        assertEquals(2.81, Battery.drainPerHour(2, 2_566_000L)!!, 0.01)
    }

    @Test
    fun tooShortDurationGivesNoRate() {
        // A 1% rounding error turns into a huge rate over a short duration.
        assertNull(Battery.drainPerHour(1, Battery.MIN_RATE_DURATION_MILLIS - 1))
        assertNull(Battery.drainPerHour(1, 0))
    }
}
