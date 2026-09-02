package com.aripd.norda.core.track

import com.aripd.norda.core.geo.Geo

/** Distance, pace and speed over recorded points. Pure, tested on the JVM. */
object Stats {

    /** Below this total distance the pace figure is meaningless noise. */
    const val MIN_PACE_DISTANCE_M = 10.0

    /** Minimum movement required within the live-pace window. */
    const val MIN_WINDOW_DISTANCE_M = 5.0

    fun totalDistanceMeters(points: List<TrackPoint>): Double {
        var total = 0.0
        for (i in 1 until points.size) {
            val a = points[i - 1]
            val b = points[i]
            total += Geo.distanceMeters(a.latitude, a.longitude, b.latitude, b.longitude)
        }
        return total
    }

    /** Average pace, s/km — null if not meaningful. */
    fun avgPaceSecPerKm(distanceMeters: Double, durationMillis: Long): Double? {
        if (distanceMeters < MIN_PACE_DISTANCE_M || durationMillis <= 0) return null
        return (durationMillis / 1000.0) / (distanceMeters / 1000.0)
    }

    fun avgSpeedKmh(distanceMeters: Double, durationMillis: Long): Double {
        if (durationMillis <= 0) return 0.0
        return (distanceMeters / 1000.0) / (durationMillis / 3_600_000.0)
    }

    /**
     * Live pace from the track's last [windowMillis] slice (ending at the last
     * recorded point); null if there is not enough recent movement.
     */
    fun currentPaceSecPerKm(points: List<TrackPoint>, windowMillis: Long = 30_000L): Double? {
        if (points.size < 2) return null
        val cutoff = points.last().timeMillis - windowMillis
        val window = points.filter { it.timeMillis >= cutoff }
        if (window.size < 2) return null
        val distance = totalDistanceMeters(window)
        if (distance < MIN_WINDOW_DISTANCE_M) return null
        val duration = window.last().timeMillis - window.first().timeMillis
        if (duration <= 0) return null
        return (duration / 1000.0) / (distance / 1000.0)
    }
}
