package com.aripd.norda.core.track

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pil ölçüm kültürü (MVP.md 16. bölüm "battery drain" riski): sayı ancak
 * ölçüm temizse üretilir — bilinmeyen seviye, aralık dışı okuma, kayıtta
 * şarj ya da oran için fazla kısa süre null döner, uydurma değer değil.
 */
class BatteryTest {

    @Test
    fun drainIsStartMinusEnd() {
        assertEquals(7, Battery.drainPercent(93, 86))
        assertEquals(0, Battery.drainPercent(50, 50))
    }

    @Test
    fun unknownOrOutOfRangeLevelsGiveNoDrain() {
        assertNull(Battery.drainPercent(null, 80))
        assertNull(Battery.drainPercent(80, null))
        assertNull(Battery.drainPercent(120, 80))
        assertNull(Battery.drainPercent(80, -1))
    }

    @Test
    fun chargingDuringRecordingGivesNoDrain() {
        // Kayıt sırasında prize takıldıysa ölçüm kirlidir; negatif "tüketim"
        // raporlamak yanıltıcı olur.
        assertNull(Battery.drainPercent(40, 70))
    }

    @Test
    fun ratePerHourNormalizesByDuration() {
        assertEquals(2.5, Battery.drainPerHour(5, 2 * 60 * 60 * 1000L)!!, 1e-9)
        assertEquals(4.0, Battery.drainPerHour(1, 15 * 60 * 1000L)!!, 1e-9)
    }

    /**
     * Saha Turu 1 verisi (F-1): %2 tüketim, 42:46 duvar saati → 2,8 %/sa.
     * Payda DUVAR saatidir, aktif kayıt süresi değil — pil duraklatmada da
     * tükenir; aktif süreye bölmek oranı şişirir (aynı turda 4,2 görünürdü).
     */
    @Test
    fun rateDenominatorIsWallClockNotActiveDuration() {
        assertEquals(2.81, Battery.drainPerHour(2, 2_566_000L)!!, 0.01)
    }

    @Test
    fun tooShortDurationGivesNoRate() {
        // %1'lik yuvarlama hatası kısa sürede devasa orana dönüşür.
        assertNull(Battery.drainPerHour(1, Battery.MIN_RATE_DURATION_MILLIS - 1))
        assertNull(Battery.drainPerHour(1, 0))
    }
}
