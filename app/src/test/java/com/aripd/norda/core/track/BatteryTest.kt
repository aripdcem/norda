package com.aripd.norda.core.track

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    // ---- Charge counter (v1.4.0) ----
    //
    // Integer percent is too coarse for a walk: on the Sept 12 night tour the
    // gauge said 80% at both ends of 38:50, so the History row read 0 %/h —
    // a true reading of a useless number (B-1). `BatteryManager`'s charge
    // counter is in µAh and does not wait for a whole percent to tick.

    @Test
    fun chargeDrainIsTheCounterDifferenceInMilliampHours() {
        assertEquals(120.0, Battery.chargeDrainMah(3_250_000L, 3_130_000L)!!, 1e-9)
        // A counter that did not move is an honest zero, not a missing value.
        assertEquals(0.0, Battery.chargeDrainMah(3_250_000L, 3_250_000L)!!, 1e-9)
    }

    @Test
    fun unreadableOrChargingCounterGivesNoDrain() {
        assertNull(Battery.chargeDrainMah(null, 3_130_000L))
        assertNull(Battery.chargeDrainMah(3_250_000L, null))
        // Devices without the counter answer 0 or a sentinel; neither is data.
        assertNull(Battery.chargeDrainMah(0L, 0L))
        assertNull(Battery.chargeDrainMah(3_250_000L, -1L))
        // Plugged in during the recording: the measurement is dirty.
        assertNull(Battery.chargeDrainMah(3_130_000L, 3_250_000L))
    }

    @Test
    fun fullChargeIsEstimatedFromTheCounterAndTheLevel() {
        // 3 200 000 µAh read at 80% → a ~4000 mAh battery.
        assertEquals(4_000_000L, Battery.fullChargeUah(3_200_000L, 80))
        assertNull(Battery.fullChargeUah(3_200_000L, 0))
        assertNull(Battery.fullChargeUah(3_200_000L, null))
        assertNull(Battery.fullChargeUah(null, 80))
        assertNull(Battery.fullChargeUah(3_200_000L, 101))
    }

    @Test
    fun chargePercentIsFractionalAndNeedsTheFullCharge() {
        assertEquals(
            2.9,
            Battery.chargeDrainPercent(3_200_000L, 3_084_000L, 4_000_000L)!!,
            1e-9
        )
        assertNull(Battery.chargeDrainPercent(3_200_000L, 3_084_000L, null))
        assertNull(Battery.chargeDrainPercent(3_200_000L, 3_084_000L, 0L))
        assertNull(Battery.chargeDrainPercent(null, 3_084_000L, 4_000_000L))
    }

    @Test
    fun fractionalRateSharesTheWallClockRule() {
        assertEquals(4.5, Battery.drainPerHour(2.25, 1_800_000L)!!, 1e-9)
        assertNull(Battery.drainPerHour(2.25, Battery.MIN_RATE_DURATION_MILLIS - 1))
    }

    /**
     * The tour that made this necessary (B-1, Sept 12 night walk): 38:50 with
     * the gauge stuck on 80%. Had the counter been recorded — 3 200 000 →
     * 3 084 000 µAh — the same tour would have reported 116 mAh, 2.9% and
     * 4.5 %/h: inside the field band instead of a flat zero.
     */
    @Test
    fun theNightWalkWouldHaveHadARate() {
        val startUah = 3_200_000L
        val endUah = 3_084_000L
        val wallSpan = 2_330_000L
        val full = Battery.fullChargeUah(startUah, 80)!!
        assertEquals(116.0, Battery.chargeDrainMah(startUah, endUah)!!, 1e-9)
        val percent = Battery.chargeDrainPercent(startUah, endUah, full)!!
        assertEquals(2.9, percent, 1e-9)
        assertEquals(4.48, Battery.drainPerHour(percent, wallSpan)!!, 0.01)
        // The integer gauge saw nothing at all over the same outing.
        assertEquals(0, Battery.drainPercent(80, 80))
    }

    /**
     * Which source to believe. The counter, normally — but a counter that did
     * not move at all while the gauge did is not live on this device, and
     * then the coarse percentage is the only thing that measured anything.
     */
    @Test
    fun theCounterIsPreferredUnlessItIsNotLive() {
        assertTrue(Battery.preferCharge(3_250_000L, 3_130_000L, 84, 81))
        // No counter, or charging: the percentage is the source.
        assertFalse(Battery.preferCharge(null, null, 84, 81))
        assertFalse(Battery.preferCharge(3_130_000L, 3_250_000L, 84, 81))
        // Counter flat and gauge flat: consistent, keep the finer source.
        assertTrue(Battery.preferCharge(3_250_000L, 3_250_000L, 80, 80))
        // Counter flat while the gauge dropped 3 points: the counter is dead.
        assertFalse(Battery.preferCharge(3_250_000L, 3_250_000L, 84, 81))
    }

    /**
     * Field tour (Sept 13 seaside walk, v1.4.0): the phone was fed during the
     * recording — gauge 16% → 57%, counter 627 165 → 2 174 172 µAh. Both
     * sources correctly refuse to produce a number, and the screen should say
     * why rather than showing an empty line.
     */
    @Test
    fun chargingDuringTheRecordingIsDetectableFromEitherSource() {
        assertTrue(Battery.wasCharging(16, 57, 627_165L, 2_174_172L))
        // The gauge alone is enough — older recordings have no counter.
        assertTrue(Battery.wasCharging(16, 57, null, null))
        // The counter alone is enough — it moves before the whole percent does.
        assertTrue(Battery.wasCharging(57, 57, 2_174_172L, 2_200_000L))
        // A normal outing is not charging.
        assertFalse(Battery.wasCharging(84, 81, 3_250_000L, 3_130_000L))
        assertFalse(Battery.wasCharging(80, 80, 3_250_000L, 3_250_000L))
        // Nothing measured is not a claim about charging.
        assertFalse(Battery.wasCharging(null, null, null, null))
    }
}
