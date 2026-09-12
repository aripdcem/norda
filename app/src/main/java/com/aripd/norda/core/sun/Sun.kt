package com.aripd.norda.core.sun

import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Solar altitude for night mode (docs/MVP.md 3.7). Low-precision solar
 * position (the classic Astronomical Almanac approximation, ~0.01° in
 * altitude for this century): enough to say "civil twilight is over" within
 * a minute, and — unlike sunrise/sunset tables — defined everywhere: at 80°N
 * in June the answer is simply "the sun is up".
 */
object Sun {

    /** Civil twilight: the sun 6° below the horizon. Night mode switches here. */
    const val NIGHT_ALTITUDE_DEG = -6.0

    fun isNight(latDeg: Double, lonDeg: Double, epochMillis: Long): Boolean =
        altitudeDeg(latDeg, lonDeg, epochMillis) < NIGHT_ALTITUDE_DEG

    /** Sun altitude above the horizon in degrees (negative = below). */
    fun altitudeDeg(latDeg: Double, lonDeg: Double, epochMillis: Long): Double {
        val n = epochMillis / 86_400_000.0 - 10957.5           // days since J2000.0
        val l = norm360(280.460 + 0.9856474 * n)               // mean longitude
        val g = Math.toRadians(norm360(357.528 + 0.9856003 * n))   // mean anomaly
        val lambda = Math.toRadians(l + 1.915 * sin(g) + 0.020 * sin(2 * g))
        val epsilon = Math.toRadians(23.439 - 0.0000004 * n)
        val declination = asin(sin(epsilon) * sin(lambda))
        val rightAscension = atan2(cos(epsilon) * sin(lambda), cos(lambda))
        val gmstHours = (18.697374558 + 24.06570982441908 * n) % 24.0
        val localSiderealDeg = norm360(gmstHours * 15.0 + lonDeg)
        val hourAngle = Math.toRadians(localSiderealDeg) - rightAscension
        val lat = Math.toRadians(latDeg)
        val sinAlt = sin(lat) * sin(declination) + cos(lat) * cos(declination) * cos(hourAngle)
        return Math.toDegrees(asin(sinAlt.coerceIn(-1.0, 1.0)))
    }

    private fun norm360(deg: Double): Double {
        val r = deg % 360.0
        return if (r < 0) r + 360.0 else r
    }
}
