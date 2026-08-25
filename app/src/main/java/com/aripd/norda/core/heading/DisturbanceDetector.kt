package com.aripd.norda.core.heading

import kotlin.math.abs

/**
 * Manyetik bozulma algılama (docs/MVP.md, 6.3): ölçülen toplam alan şiddeti
 * beklenenden %25'ten fazla saparsa ve bu sapma kesintisiz 2,5 sn sürerse
 * uyarı verilir; %15'in altına inince kalkar.
 *
 * Süre şartının sebebi: telefon elde çevrilirken kalibrasyon geçici sapma
 * üretebilir — anlık sıçrama uyarı değildir. Histerezis bandı da uyarının
 * eşikte titremesini önler.
 */
class DisturbanceDetector(
    private val enterRatio: Double = 0.25,
    private val exitRatio: Double = 0.15,
    private val sustainMillis: Long = 2_500L
) {

    private var overSinceMillis: Long? = null

    var isActive = false
        private set

    /** Bir örnek işler; uyarının o anki durumunu döner. Alanlar µT. */
    fun onSample(
        measuredMicroTesla: Double,
        expectedMicroTesla: Double,
        nowMillis: Long
    ): Boolean {
        if (expectedMicroTesla <= 0.0) {
            overSinceMillis = null
            isActive = false
            return false
        }
        val deviation = abs(measuredMicroTesla - expectedMicroTesla) / expectedMicroTesla
        if (isActive) {
            if (deviation < exitRatio) {
                isActive = false
                overSinceMillis = null
            }
        } else {
            if (deviation > enterRatio) {
                val since = overSinceMillis ?: nowMillis.also { overSinceMillis = it }
                if (nowMillis - since >= sustainMillis) isActive = true
            } else {
                overSinceMillis = null
            }
        }
        return isActive
    }
}
