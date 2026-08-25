package com.aripd.norda.core.geo

import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Saf jeodezi: mesafe, büyük daire kerterizi ve açı aritmetiği tek modülde.
 * Android'e dokunmaz; tamamı JVM'de test edilir.
 */
object Geo {

    /** Ortalama Dünya yarıçapı (IUGG). */
    private const val EARTH_RADIUS_M = 6_371_008.8

    /** İki koordinat arasındaki büyük daire mesafesi, metre (haversine). */
    fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return 2 * EARTH_RADIUS_M * asin(min(1.0, sqrt(a)))
    }

    /**
     * [fromLat],[fromLon] noktasından [toLat],[toLon] noktasına giden büyük
     * daire yayının çıkış kerterizi, gerçek kuzeye göre 0–360°.
     *
     * Δλ hedef − kaynak yönündedir; işaret ters alınırsa sonuç doğu–batı
     * aynalanır (bkz. docs/MVP.md, 9. bölüm) ve aşağıdaki ekvator testi düşer.
     */
    fun initialBearingDeg(fromLat: Double, fromLon: Double, toLat: Double, toLon: Double): Double {
        val phi1 = Math.toRadians(fromLat)
        val phi2 = Math.toRadians(toLat)
        val dLon = Math.toRadians(toLon - fromLon)
        val y = sin(dLon) * cos(phi2)
        val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(dLon)
        return normalizeDeg(Math.toDegrees(atan2(y, x)))
    }

    /** Açıyı [0, 360) aralığına indirger. */
    fun normalizeDeg(deg: Double): Double {
        val m = deg % 360.0
        return if (m < 0) m + 360.0 else m
    }

    /** [from] açısından [to] açısına işaretli en kısa dönüş, (-180, 180]. */
    fun signedDifferenceDeg(from: Double, to: Double): Double {
        val d = normalizeDeg(to - from)
        return if (d > 180.0) d - 360.0 else d
    }
}
