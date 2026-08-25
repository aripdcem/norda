package com.aripd.norda.core.track

import com.aripd.norda.core.track.AutoPauseDetector.Decision
import org.junit.Assert.assertEquals
import org.junit.Test

class AutoPauseDetectorTest {

    private fun p(
        t: Long,
        lat: Double = 41.0,
        acc: Float = 10f
    ) = TrackPoint(t, lat, 29.0, 0.0, acc, 0f, 0f)

    // ~10 m'lik enlem adımı.
    private val step10m = 0.00008993

    @Test
    fun pausesAfterIdleWindow() {
        val d = AutoPauseDetector()
        assertEquals(Decision.NONE, d.onFix(p(0), accepted = true))
        assertEquals(Decision.NONE, d.onFix(p(10_000), accepted = false))
        assertEquals(Decision.NONE, d.onFix(p(20_000), accepted = false))
        assertEquals(Decision.PAUSE, d.onFix(p(21_000), accepted = false))
    }

    @Test
    fun acceptedFixKeepsItAwake() {
        val d = AutoPauseDetector()
        d.onFix(p(0), accepted = true)
        d.onFix(p(15_000, lat = 41.0 + step10m), accepted = true)
        // hareketten yalnızca 10 sn geçti — duraklatma yok
        assertEquals(Decision.NONE, d.onFix(p(25_000), accepted = false))
        assertEquals(Decision.PAUSE, d.onFix(p(36_000), accepted = false))
    }

    @Test
    fun resumesOnlyBeyondAnchorDistance() {
        val d = AutoPauseDetector()
        d.onFix(p(0), accepted = true)
        d.onFix(p(21_000), accepted = false)              // PAUSE düştü
        // 5 m kıpırdama devam ettirmez (8 m eşiği)
        assertEquals(Decision.NONE, d.onFix(p(25_000, lat = 41.0 + step10m / 2), accepted = false))
        assertEquals(Decision.RESUME, d.onFix(p(30_000, lat = 41.0 + step10m), accepted = false))
    }

    // Kötü doğruluklu fix çapadan "uzağa sıçrayabilir" — devam ettirmemeli.
    @Test
    fun poorAccuracyCannotResume() {
        val d = AutoPauseDetector()
        d.onFix(p(0), accepted = true)
        d.onFix(p(21_000), accepted = false)
        assertEquals(Decision.NONE, d.onFix(p(25_000, lat = 41.0 + step10m * 5, acc = 50f), accepted = false))
        assertEquals(Decision.RESUME, d.onFix(p(30_000, lat = 41.0 + step10m * 5, acc = 15f), accepted = false))
    }

    @Test
    fun resetForgetsHistory() {
        val d = AutoPauseDetector()
        d.onFix(p(0), accepted = true)
        d.onFix(p(21_000), accepted = false)              // PAUSE
        d.reset(p(30_000))
        // sıfırlamadan sonra duraklatılmış sayılmaz ve süre baştan işler
        assertEquals(Decision.NONE, d.onFix(p(45_000), accepted = false))
        assertEquals(Decision.PAUSE, d.onFix(p(51_000), accepted = false))
    }
}
