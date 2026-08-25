package com.aripd.norda.core.track

/**
 * Aktivite başına pil ölçümü (MVP.md 16. bölümdeki "battery drain" riskinin
 * ölçüm kültürü ayağı). Kural: sayı ancak ölçüm temizse üretilir — seviye
 * bilinmiyorsa, okuma aralık dışıysa, kayıtta şarj edildiyse ya da süre oran
 * için fazla kısaysa null döner; uydurma değer asla.
 */
object Battery {

    /** Bundan kısa kayıtta %/saat oranı gürültüden ibarettir. */
    const val MIN_RATE_DURATION_MILLIS = 5 * 60 * 1000L

    /** Harcanan pil yüzdesi; ölçüm kirliyse (şarj dahil) null. */
    fun drainPercent(startPct: Int?, endPct: Int?): Int? {
        if (startPct == null || endPct == null) return null
        if (startPct !in 0..100 || endPct !in 0..100) return null
        val drain = startPct - endPct
        return if (drain >= 0) drain else null
    }

    /**
     * Saat başına tüketim; süre eşiğin altındaysa null.
     *
     * Payda DUVAR saatidir (kayıt başı → sonu), aktif süre değil: GPS
     * duraklatmada da açık kalır ve pil akmaya devam eder. Aktif süreye
     * bölmek oranı şişirir — saha Turu 1'de 2,8 yerine 4,2 gösterirdi (F-1).
     */
    fun drainPerHour(drainPct: Int, wallSpanMillis: Long): Double? {
        if (wallSpanMillis < MIN_RATE_DURATION_MILLIS) return null
        return drainPct * 3_600_000.0 / wallSpanMillis
    }
}
