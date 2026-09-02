package com.aripd.norda.core.track

import com.aripd.norda.core.geo.Geo

/**
 * Detects a standstill during recording and the real movement after it, so
 * the session can pause by itself (idle time is not counted) and resume by
 * itself (docs/MVP.md, 5.5).
 *
 * Every raw fix is fed in together with whether the filter accepted it: an
 * accepted fix is evidence of movement; no acceptance for a long time while
 * fixes keep arriving means the user has stopped. The resume decision is made
 * from the distance to the anchor point — jitter cannot break the pause.
 */
class AutoPauseDetector(
    private val idleMillis: Long = 20_000L,
    private val resumeDistanceM: Double = 8.0
) {

    enum class Decision { NONE, PAUSE, RESUME }

    private var lastMovementMillis = 0L
    private var anchor: TrackPoint? = null
    private var started = false
    private var autoPaused = false

    fun onFix(fix: TrackPoint, accepted: Boolean): Decision {
        if (!started) {
            started = true
            lastMovementMillis = fix.timeMillis
        }

        if (autoPaused) {
            // A poor-accuracy fix can "jump far away" from the anchor; resuming
            // requires the same accuracy bar as the recording filter.
            if (fix.accuracyM > GpsFilter.MAX_ACCURACY_M) return Decision.NONE
            val from = anchor ?: return Decision.NONE
            val distance = Geo.distanceMeters(
                from.latitude, from.longitude, fix.latitude, fix.longitude
            )
            if (distance >= resumeDistanceM) {
                autoPaused = false
                lastMovementMillis = fix.timeMillis
                anchor = fix
                return Decision.RESUME
            }
            return Decision.NONE
        }

        if (accepted) {
            lastMovementMillis = fix.timeMillis
            anchor = fix
            return Decision.NONE
        }

        if (fix.timeMillis - lastMovementMillis > idleMillis) {
            autoPaused = true
            return Decision.PAUSE
        }
        return Decision.NONE
    }

    /** Forget the history — e.g. when the user manually pauses and resumes. */
    fun reset(at: TrackPoint) {
        started = true
        autoPaused = false
        lastMovementMillis = at.timeMillis
        anchor = at
    }
}
