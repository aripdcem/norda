package com.aripd.norda.core.geo

import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Pure geodesy: distance, great-circle bearing and angle arithmetic in one
 * module. Touches no Android; all of it is tested on the JVM.
 */
object Geo {

    /** Mean Earth radius (IUGG). */
    private const val EARTH_RADIUS_M = 6_371_008.8

    /** Great-circle distance between two coordinates, in metres (haversine). */
    fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return 2 * EARTH_RADIUS_M * asin(min(1.0, sqrt(a)))
    }

    /**
     * Initial bearing of the great-circle arc from [fromLat],[fromLon] to
     * [toLat],[toLon], 0–360° relative to true north.
     *
     * Δλ runs destination − source; with the sign reversed the result mirrors
     * east–west (see docs/MVP.md, section 9) and the equator test below fails.
     */
    fun initialBearingDeg(fromLat: Double, fromLon: Double, toLat: Double, toLon: Double): Double {
        val phi1 = Math.toRadians(fromLat)
        val phi2 = Math.toRadians(toLat)
        val dLon = Math.toRadians(toLon - fromLon)
        val y = sin(dLon) * cos(phi2)
        val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(dLon)
        return normalizeDeg(Math.toDegrees(atan2(y, x)))
    }

    /** Reduces an angle into the [0, 360) range. */
    fun normalizeDeg(deg: Double): Double {
        val m = deg % 360.0
        return if (m < 0) m + 360.0 else m
    }

    /** Signed shortest turn from angle [from] to angle [to], (-180, 180]. */
    fun signedDifferenceDeg(from: Double, to: Double): Double {
        val d = normalizeDeg(to - from)
        return if (d > 180.0) d - 360.0 else d
    }
}
