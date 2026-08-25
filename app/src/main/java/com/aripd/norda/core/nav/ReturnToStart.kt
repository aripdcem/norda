package com.aripd.norda.core.nav

import com.aripd.norda.core.geo.Geo

/**
 * Return to Start kılavuzu (docs/MVP.md, 9. bölüm): mevcut konumdan
 * başlangıca kuş uçuşu kerteriz + mesafe + tempo biliniyorsa tahmini süre.
 * Bu bir yol ağı rotası değildir — bilinçli tasarım kararı.
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
     * Hedefe dönmek için işaretli açı: pozitif sağa, negatif sola.
     * (Device heading ile target bearing ayrımı — docs/MVP.md, 6.4.)
     */
    fun relativeAngleDeg(headingDeg: Double, targetBearingDeg: Double): Double =
        Geo.signedDifferenceDeg(headingDeg, targetBearingDeg)
}
