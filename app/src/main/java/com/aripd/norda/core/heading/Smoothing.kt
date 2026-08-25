package com.aripd.norda.core.heading

import com.aripd.norda.core.geo.Geo
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

/**
 * İbrenin alçak geçiren süzgeci (docs/MVP.md, 6.2).
 *
 * Açı doğrudan değil sin/cos bileşenleri üzerinden süzülür — aksi hâlde
 * 359°→0° geçişinde ibre bir tam tur atardı. Saklanan şey katsayı değil
 * ZAMAN SABİTİDİR; katsayı her örnekte gerçek aralıktan hesaplanır, böylece
 * örnekleme hızı değişse de ibrenin hissi sabit kalır.
 */
class Smoothing(private val timeConstantSeconds: Double) {

    private var sinPart = 0.0
    private var cosPart = 1.0
    private var started = false

    /** Yeni örneği işler ve süzülmüş açıyı (0–360) döner. */
    fun update(angleDeg: Double, dtSeconds: Double): Double {
        val rad = Math.toRadians(angleDeg)
        if (!started) {
            started = true
            sinPart = sin(rad)
            cosPart = cos(rad)
        } else {
            val dt = dtSeconds.coerceAtLeast(1e-4)
            val alpha = 1.0 - exp(-dt / timeConstantSeconds)
            sinPart += alpha * (sin(rad) - sinPart)
            cosPart += alpha * (cos(rad) - cosPart)
        }
        return Geo.normalizeDeg(Math.toDegrees(atan2(sinPart, cosPart)))
    }

    fun reset() {
        started = false
        sinPart = 0.0
        cosPart = 1.0
    }
}
