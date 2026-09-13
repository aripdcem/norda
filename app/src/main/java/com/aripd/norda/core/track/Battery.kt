package com.aripd.norda.core.track

import kotlin.math.roundToLong

/**
 * Per-activity battery measurement (the measurement-culture leg of the
 * "battery drain" risk in MVP.md section 16). Rule: a number is produced only
 * when the measurement is clean — if the level is unknown, the reading is out
 * of range, the device was charged during the recording, or the span is too
 * short for a rate, null is returned; never a made-up value.
 */
object Battery {

    /** In a recording shorter than this, the %/hour rate is nothing but noise. */
    const val MIN_RATE_DURATION_MILLIS = 5 * 60 * 1000L

    /** Battery percentage spent; null if the measurement is dirty (incl. charging). */
    fun drainPercent(startPct: Int?, endPct: Int?): Int? {
        if (startPct == null || endPct == null) return null
        if (startPct !in 0..100 || endPct !in 0..100) return null
        val drain = startPct - endPct
        return if (drain >= 0) drain else null
    }

    /**
     * Drain per hour; null if the span is below the threshold.
     *
     * The denominator is the WALL clock (recording start → end), not the
     * active time: GPS stays on during pauses too and the battery keeps
     * draining. Dividing by active time inflates the rate — on Field Run 1 it
     * would have shown 4.2 instead of 2.8 (F-1).
     */
    fun drainPerHour(drainPct: Int, wallSpanMillis: Long): Double? {
        if (wallSpanMillis < MIN_RATE_DURATION_MILLIS) return null
        return drainPct * 3_600_000.0 / wallSpanMillis
    }

    /** Same rule for the fractional percentage the charge counter produces. */
    fun drainPerHour(drainPct: Double, wallSpanMillis: Long): Double? {
        if (wallSpanMillis < MIN_RATE_DURATION_MILLIS) return null
        return drainPct * 3_600_000.0 / wallSpanMillis
    }

    // ---- Charge counter ----
    //
    // The integer percentage is too coarse for an outing. On the September 12
    // night walk the gauge read 80% at both ends of 38:50, so History showed
    // 0 %/h — a true reading of a useless number: the gauge sits on a level
    // and then drops several points at once (B-1). `BatteryManager`'s charge
    // counter is in µAh and moves with every milliamp-hour, so a 30-minute
    // walk gets a real measurement. Not every device serves it; when it does
    // not, these functions return null and the percentage remains the source.

    /** A counter reading is data only when positive; 0 or a sentinel is not. */
    private fun counter(uah: Long?): Long? = uah?.takeIf { it > 0 }

    /**
     * Charge spent, in mAh. Null if a reading is missing or the device was
     * charging (the counter went up) — the same cleanliness rule as the
     * percentage. A counter that did not move is an honest zero.
     */
    fun chargeDrainMah(startUah: Long?, endUah: Long?): Double? {
        val start = counter(startUah) ?: return null
        val end = counter(endUah) ?: return null
        val drainUah = start - end
        return if (drainUah >= 0) drainUah / 1000.0 else null
    }

    /**
     * Full-charge capacity estimated from one counter reading at a known
     * level: 3 200 000 µAh at 80% is a ~4000 mAh battery. This is what turns
     * mAh into a percentage without asking the system for a capacity it does
     * not reliably report.
     */
    fun fullChargeUah(chargeUah: Long?, pct: Int?): Long? {
        val charge = counter(chargeUah) ?: return null
        if (pct == null || pct !in 1..100) return null
        return (charge * 100.0 / pct).roundToLong()
    }

    /**
     * Which source to believe for this recording. The counter, normally — it
     * is finer and it is why this exists. The exception: a counter that did
     * not move at all while the percentage gauge did is not live on this
     * device, and then the coarse gauge is the only thing that measured
     * anything. No counter, or charging during the recording, also leaves the
     * percentage as the source.
     */
    fun preferCharge(startUah: Long?, endUah: Long?, startPct: Int?, endPct: Int?): Boolean {
        val mah = chargeDrainMah(startUah, endUah) ?: return false
        if (mah > 0.0) return true
        val gaugeDrain = drainPercent(startPct, endPct) ?: return true
        return gaugeDrain == 0
    }

    /** Percentage spent, fractional, from the counter and the capacity estimate. */
    fun chargeDrainPercent(startUah: Long?, endUah: Long?, fullChargeUah: Long?): Double? {
        val drainMah = chargeDrainMah(startUah, endUah) ?: return null
        val full = fullChargeUah?.takeIf { it > 0 } ?: return null
        return drainMah * 1000.0 * 100.0 / full
    }
}
