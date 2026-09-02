package com.aripd.norda.core.track

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
}
