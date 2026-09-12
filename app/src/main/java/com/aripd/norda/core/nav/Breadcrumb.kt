package com.aripd.norda.core.nav

import com.aripd.norda.core.geo.Geo
import com.aripd.norda.core.track.TrackPoint

/**
 * Breadcrumb navigation (docs/MVP.md 9.3): the way back along the recorded
 * track, as opposed to Return to Start's straight line. Where the straight
 * line crosses a ravine, the trail is the path you know exists.
 *
 * A [Trail] wraps the accepted points with their cumulative distance;
 * [Trail.guidance] finds the nearest recorded point to the current position
 * and reports the direction to walk (about [LOOKBACK_M] back along the trail
 * so the pointer does not twitch from point to point), the trail distance that
 * remains to the start, and — when the walker has strayed more than
 * [OFF_TRAIL_M] from the trail — the way back onto it instead.
 */
object Breadcrumb {

    /** Farther than this from the nearest recorded point counts as off the trail. */
    const val OFF_TRAIL_M = 25.0

    /** How far back along the trail the pointer aims while on the trail. */
    const val LOOKBACK_M = 20.0

    /** Closer than this to the start point: arrived. */
    const val ARRIVED_M = 15.0

    class Guidance(
        /** Index of the recorded point nearest to the current position. */
        val nearestIndex: Int,
        /** Index of the point the pointer aims at (on the trail: ~20 m back; off it: the nearest point). */
        val targetIndex: Int,
        /** Distance from the current position to the nearest recorded point. */
        val offTrailM: Double,
        /** Bearing from the current position to the target point. */
        val bearingDeg: Double,
        /** Trail distance from the nearest point back to the start. */
        val remainingM: Double,
        val etaMillis: Long?
    ) {
        val onTrail: Boolean get() = offTrailM <= OFF_TRAIL_M
        val arrived: Boolean get() = remainingM <= 0.0
    }

    class Trail(val points: List<TrackPoint>) {
        /** cumulative[i] = trail distance from the start to point i. */
        private val cumulative: DoubleArray = DoubleArray(points.size).also { c ->
            for (i in 1 until points.size) {
                c[i] = c[i - 1] + Geo.distanceMeters(
                    points[i - 1].latitude, points[i - 1].longitude,
                    points[i].latitude, points[i].longitude
                )
            }
        }

        val lengthM: Double get() = if (points.isEmpty()) 0.0 else cumulative.last()

        fun guidance(lat: Double, lon: Double, paceSecPerKm: Double?): Guidance? {
            if (points.isEmpty()) return null
            var nearest = 0
            var nearestD = Double.MAX_VALUE
            for (i in points.indices) {
                val d = Geo.distanceMeters(lat, lon, points[i].latitude, points[i].longitude)
                if (d < nearestD) { nearestD = d; nearest = i }
            }
            val toStart = Geo.distanceMeters(lat, lon, points[0].latitude, points[0].longitude)
            if (toStart <= ARRIVED_M) {
                val bearing = Geo.initialBearingDeg(lat, lon, points[0].latitude, points[0].longitude)
                return Guidance(nearest, 0, toStart, bearing, 0.0, 0L)
            }
            val target = if (nearestD > OFF_TRAIL_M) nearest else lookBack(nearest)
            val bearing = Geo.initialBearingDeg(lat, lon, points[target].latitude, points[target].longitude)
            val remaining = cumulative[nearest]
            val eta = paceSecPerKm?.let { ((remaining + nearestD) / 1000.0 * it * 1000.0).toLong() }
            return Guidance(nearest, target, nearestD, bearing, remaining, eta)
        }

        /** The first point at least LOOKBACK_M behind [from] along the trail (or the start). */
        private fun lookBack(from: Int): Int {
            var j = from
            while (j > 0 && cumulative[from] - cumulative[j] < LOOKBACK_M) j--
            return j
        }
    }
}
