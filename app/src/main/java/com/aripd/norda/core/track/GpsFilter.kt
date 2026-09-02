package com.aripd.norda.core.track

import com.aripd.norda.core.geo.Geo

/**
 * Decides whether a raw GPS fix is worth recording. Unfiltered GPS inflates
 * the distance and scribbles the route: cold-fix teleports, accuracy jumps
 * and stationary jitter are weeded out here (docs/MVP.md, 5.2).
 */
object GpsFilter {

    /** Fixes reporting worse horizontal accuracy than this are dropped. */
    const val MAX_ACCURACY_M = 30f

    /**
     * Movement that appears faster than this is a teleport. The initial value
     * was 15; after the first-fix settling jump on two outings (12.7 and
     * 12.85 m/s) slipped just under the ceiling, it was field-calibrated to 10
     * (F-11, MVP 5.2): in a walk/run product, movement above 36 km/h is not
     * running.
     */
    const val MAX_SPEED_MPS = 10.0

    /** Movement below this is not distance travelled but stationary jitter. */
    const val MIN_DISTANCE_M = 2.0

    /**
     * Verdict + reason. The reason is the raw data for filter calibration
     * (Phase 8): to tune thresholds in the field, it must be visible which rule
     * rejected how many fixes.
     */
    enum class Verdict { ACCEPT, BAD_ACCURACY, NON_MONOTONIC, JITTER, TELEPORT }

    fun evaluate(previous: TrackPoint?, candidate: TrackPoint): Verdict {
        // accuracy <= 0 means the device reports no accuracy; on such devices
        // the fix is accepted rather than recording nothing at all.
        if (candidate.accuracyM > MAX_ACCURACY_M) return Verdict.BAD_ACCURACY
        if (previous == null) return Verdict.ACCEPT

        val dtMillis = candidate.timeMillis - previous.timeMillis
        if (dtMillis <= 0) return Verdict.NON_MONOTONIC

        val distance = Geo.distanceMeters(
            previous.latitude, previous.longitude,
            candidate.latitude, candidate.longitude
        )
        if (distance < MIN_DISTANCE_M) return Verdict.JITTER

        val impliedSpeed = distance / (dtMillis / 1000.0)
        if (impliedSpeed > MAX_SPEED_MPS) return Verdict.TELEPORT

        return Verdict.ACCEPT
    }

    fun accept(previous: TrackPoint?, candidate: TrackPoint): Boolean =
        evaluate(previous, candidate) == Verdict.ACCEPT
}
