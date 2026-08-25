package com.aripd.norda.core.track

import com.aripd.norda.core.geo.Geo

/**
 * Kayıt sırasında duraksamayı ve sonrasındaki gerçek hareketi algılar; oturum
 * kendiliğinden duraklayıp (boş süre sayılmaz) kendiliğinden devam edebilsin
 * diye (docs/MVP.md, 5.5).
 *
 * Her ham fix, filtrenin kabul edip etmediği bilgisiyle beslenir: kabul
 * edilen fix hareket kanıtıdır; fix'ler gelmeye devam ederken uzun süre kabul
 * çıkmaması, kullanıcının durduğu anlamına gelir. Devam kararı çapa noktasına
 * uzaklıktan verilir — titreme duraklatmayı bozamaz.
 */
class AutoPauseDetector(
    private val idleMillis: Long = 20_000L,
    private val resumeDistanceM: Double = 8.0
) {

    enum class Decision { NONE, PAUSE, RESUME }

    private var lastMovementMillis = 0L
    private var anchor: TrackPoint? = null
    private var started = false
    private var autoPaused = false

    fun onFix(fix: TrackPoint, accepted: Boolean): Decision {
        if (!started) {
            started = true
            lastMovementMillis = fix.timeMillis
        }

        if (autoPaused) {
            // Kötü doğruluklu bir fix çapadan "uzağa sıçrayabilir"; devam için
            // kayıt filtresiyle aynı doğruluk çıtası aranır.
            if (fix.accuracyM > GpsFilter.MAX_ACCURACY_M) return Decision.NONE
            val from = anchor ?: return Decision.NONE
            val distance = Geo.distanceMeters(
                from.latitude, from.longitude, fix.latitude, fix.longitude
            )
            if (distance >= resumeDistanceM) {
                autoPaused = false
                lastMovementMillis = fix.timeMillis
                anchor = fix
                return Decision.RESUME
            }
            return Decision.NONE
        }

        if (accepted) {
            lastMovementMillis = fix.timeMillis
            anchor = fix
            return Decision.NONE
        }

        if (fix.timeMillis - lastMovementMillis > idleMillis) {
            autoPaused = true
            return Decision.PAUSE
        }
        return Decision.NONE
    }

    /** Geçmişi unut — örn. kullanıcı elle duraklatıp devam ettiğinde. */
    fun reset(at: TrackPoint) {
        started = true
        autoPaused = false
        lastMovementMillis = at.timeMillis
        anchor = at
    }
}
