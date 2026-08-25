package com.aripd.norda.core.track

import com.aripd.norda.core.geo.Geo

/**
 * Ham bir GPS fix'inin kayda değer olup olmadığına karar verir. Filtresiz GPS
 * mesafeyi şişirir ve rotayı çizik çizik yapar: soğuk fix ışınlamaları,
 * doğruluk sıçramaları ve durağan titreme burada elenir (docs/MVP.md, 5.2).
 */
object GpsFilter {

    /** Bundan kötü yatay doğruluk bildiren fix'ler atılır. */
    const val MAX_ACCURACY_M = 30f

    /** Bundan hızlı görünen hareket (elit sprint ~12 m/s) ışınlanmadır. */
    const val MAX_SPEED_MPS = 15.0

    /** Bunun altındaki kıpırdama yol değil, durağan titremedir. */
    const val MIN_DISTANCE_M = 2.0

    /**
     * Karar + neden. Neden, filtre kalibrasyonunun ham verisidir (Faz 8):
     * sahada eşik ayarlamak için hangi kuralın kaç fix elediği görünür olmalı.
     */
    enum class Verdict { ACCEPT, BAD_ACCURACY, NON_MONOTONIC, JITTER, TELEPORT }

    fun evaluate(previous: TrackPoint?, candidate: TrackPoint): Verdict {
        // accuracy <= 0, cihazın doğruluk bildirmediği anlamına gelir; böyle
        // cihazlarda hiç kayıt yapmamaktansa fix kabul edilir.
        if (candidate.accuracyM > MAX_ACCURACY_M) return Verdict.BAD_ACCURACY
        if (previous == null) return Verdict.ACCEPT

        val dtMillis = candidate.timeMillis - previous.timeMillis
        if (dtMillis <= 0) return Verdict.NON_MONOTONIC

        val distance = Geo.distanceMeters(
            previous.latitude, previous.longitude,
            candidate.latitude, candidate.longitude
        )
        if (distance < MIN_DISTANCE_M) return Verdict.JITTER

        val impliedSpeed = distance / (dtMillis / 1000.0)
        if (impliedSpeed > MAX_SPEED_MPS) return Verdict.TELEPORT

        return Verdict.ACCEPT
    }

    fun accept(previous: TrackPoint?, candidate: TrackPoint): Boolean =
        evaluate(previous, candidate) == Verdict.ACCEPT
}
