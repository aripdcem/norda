package com.aripd.norda.core.nav

import com.aripd.norda.core.geo.Geo

/**
 * Return to Start guidance (docs/MVP.md, section 9): straight-line bearing +
 * distance from the current position to the start, plus an estimated time if
 * the pace is known. This is not a road-network route — a deliberate design
 * decision.
 */
object ReturnToStart {

    class Guidance(
        val bearingDeg: Double,
        val distanceM: Double,
        val etaMillis: Long?
    )

    fun guidance(
        currentLat: Double, currentLon: Double,
        startLat: Double, startLon: Double,
        paceSecPerKm: Double?
    ): Guidance {
        val distance = Geo.distanceMeters(currentLat, currentLon, startLat, startLon)
        val bearing = Geo.initialBearingDeg(currentLat, currentLon, startLat, startLon)
        val eta = paceSecPerKm?.let { (distance / 1000.0 * it * 1000.0).toLong() }
        return Guidance(bearing, distance, eta)
    }

    /**
     * Signed angle to turn towards the target: positive right, negative left.
     * (The device heading vs. target bearing distinction — docs/MVP.md, 6.4.)
     */
    fun relativeAngleDeg(headingDeg: Double, targetBearingDeg: Double): Double =
        Geo.signedDifferenceDeg(headingDeg, targetBearingDeg)
}
