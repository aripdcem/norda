package com.aripd.norda.core.track

/**
 * Elevation gain/loss with hysteresis (docs/MVP.md, 5.4).
 *
 * GNSS vertical error is 2-3 times the horizontal; summing raw differences
 * produces hundreds of metres of phantom climb even on flat ground. Here the
 * altitude is not counted until it clearly separates from the last anchored
 * value by the threshold; once it does, the difference is booked in one step
 * and the anchor is updated.
 *
 * Must be fed only with fixes reporting a valid altitude (the
 * `Location.hasAltitude()` condition on the Android side) — the 0.0 sentinel
 * value would produce phantom descent.
 */
class ElevationTracker(private val thresholdM: Double = 4.0) {

    var gainM = 0.0
        private set
    var lossM = 0.0
        private set
    private var anchorM: Double? = null

    fun onAltitude(altitudeM: Double) {
        val anchor = anchorM
        if (anchor == null) {
            anchorM = altitudeM
            return
        }
        val diff = altitudeM - anchor
        when {
            diff >= thresholdM -> {
                gainM += diff
                anchorM = altitudeM
            }
            diff <= -thresholdM -> {
                lossM += -diff
                anchorM = altitudeM
            }
        }
    }
}
