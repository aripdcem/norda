package com.aripd.norda.core.track

/**
 * Histerezisli yükseklik kazanımı/kaybı (docs/MVP.md, 5.4).
 *
 * GNSS dikey hatası yatayın 2-3 katıdır; ham farkları toplamak düz yolda bile
 * yüzlerce metre hayalet tırmanış üretir. Burada yükseklik, son demirlenen
 * değerden eşik kadar net ayrışmadıkça sayılmaz; ayrışınca fark tek hamlede
 * işlenir ve demir güncellenir.
 *
 * Yalnızca geçerli rakım bildiren fix'lerle beslenmelidir (Android tarafında
 * `Location.hasAltitude()` şartı) — 0.0 nöbetçi değeri hayalet iniş üretirdi.
 */
class ElevationTracker(private val thresholdM: Double = 4.0) {

    var gainM = 0.0
        private set
    var lossM = 0.0
        private set
    private var anchorM: Double? = null

    fun onAltitude(altitudeM: Double) {
        val anchor = anchorM
        if (anchor == null) {
            anchorM = altitudeM
            return
        }
        val diff = altitudeM - anchor
        when {
            diff >= thresholdM -> {
                gainM += diff
                anchorM = altitudeM
            }
            diff <= -thresholdM -> {
                lossM += -diff
                anchorM = altitudeM
            }
        }
    }
}
