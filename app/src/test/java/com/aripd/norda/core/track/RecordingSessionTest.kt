package com.aripd.norda.core.track

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Durum makinesinin uçtan uca sınanması: sentetik bir yürüyüş beslenir,
 * mesafe/süre/yükseklik ve durum geçişleri doğrulanır. Testlerde monotonik
 * saat ile fix zamanı aynı değerdir (sadelik için).
 */
class RecordingSessionTest {

    private val step10m = 0.00008993

    private fun fix(t: Long, latSteps: Double, alt: Double = 0.0, acc: Float = 10f) =
        TrackPoint(t, 41.0 + latSteps * step10m, 29.0, alt, acc, 0f, 0f)

    private fun session() = RecordingSession(
        type = ActivityType.WALK,
        startWallMillis = 1_000_000,
        startMonotonicMillis = 0
    )

    @Test
    fun filterCountsFeedCalibration() {
        val s = session()
        s.onFix(fix(0, 0.0), false, 0)                          // kabul
        s.onFix(fix(5_000, 1.0), false, 5_000)                  // kabul
        s.onFix(fix(10_000, 1.05), false, 10_000)               // ~0,5 m titreme
        s.onFix(fix(15_000, 1.0, acc = 99f), false, 15_000)     // kötü doğruluk
        assertEquals(2, s.filterCount(GpsFilter.Verdict.ACCEPT))
        assertEquals(1, s.filterCount(GpsFilter.Verdict.JITTER))
        assertEquals(1, s.filterCount(GpsFilter.Verdict.BAD_ACCURACY))
        assertEquals(0, s.filterCount(GpsFilter.Verdict.TELEPORT))
        assertEquals(0, s.filterCount(GpsFilter.Verdict.NON_MONOTONIC))
    }

    @Test
    fun manualPauseDoesNotPolluteFilterCounts() {
        val s = session()
        s.onFix(fix(0, 0.0), false, 0)
        s.pauseManual(1_000)
        s.onFix(fix(5_000, 1.0), false, 5_000)   // duraklatmada gelen fix sayılmaz
        assertEquals(1, s.filterCount(GpsFilter.Verdict.ACCEPT))
    }

    @Test
    fun recordsAcceptedFixesAndAccumulatesDistance() {
        val s = session()
        assertNotNull(s.onFix(fix(0, 0.0), hasAltitude = false, nowMonotonicMillis = 0))
        assertNotNull(s.onFix(fix(5_000, 1.0), false, 5_000))
        assertNotNull(s.onFix(fix(10_000, 2.0), false, 10_000))
        assertEquals(20.0, s.distanceM, 0.5)
        assertEquals(10_000, s.durationMillis(10_000))
        assertEquals(3, s.points.size)
    }

    @Test
    fun rejectedJitterAddsNothing() {
        val s = session()
        s.onFix(fix(0, 0.0), false, 0)
        assertNull(s.onFix(fix(5_000, 0.05), false, 5_000))   // ~0,5 m kıpırdama
        assertEquals(0.0, s.distanceM, 1e-9)
        assertEquals(1, s.points.size)
    }

    @Test
    fun autoPausesAndFreezesDuration() {
        val s = session()
        s.onFix(fix(0, 0.0), false, 0)
        // aynı yerde bekleme: kabul edilmeyen fix'ler
        s.onFix(fix(10_000, 0.0), false, 10_000)
        s.onFix(fix(20_000, 0.0), false, 20_000)
        assertNull(s.onFix(fix(21_000, 0.0), false, 21_000))
        assertEquals(RecordingSession.State.AUTO_PAUSED, s.state)
        // süre duraklatma anında donar
        assertEquals(21_000, s.durationMillis(60_000))
    }

    @Test
    fun autoResumesOnRealMovement() {
        val s = session()
        s.onFix(fix(0, 0.0), false, 0)
        s.onFix(fix(21_000, 0.0), false, 21_000)              // AUTO_PAUSED
        s.onFix(fix(30_000, 1.0), false, 30_000)              // 10 m → RESUME
        assertEquals(RecordingSession.State.RECORDING, s.state)
        // devamdan sonraki kabul edilen nokta mesafeye normal eklenir
        assertNotNull(s.onFix(fix(35_000, 2.0), false, 35_000))
        assertEquals(20.0, s.distanceM, 0.5)
        // duraklamada geçen 9 sn süreye sayılmaz: 21 + 5
        assertEquals(26_000, s.durationMillis(35_000))
    }

    @Test
    fun manualPauseBlocksEverything() {
        val s = session()
        s.onFix(fix(0, 0.0), false, 0)
        s.pauseManual(5_000)
        assertEquals(RecordingSession.State.PAUSED, s.state)
        assertNull(s.onFix(fix(10_000, 5.0), false, 10_000))
        assertEquals(0.0, s.distanceM, 1e-9)
        assertEquals(5_000, s.durationMillis(60_000))
    }

    // Elle duraklatmada yürünen yol kullanıcının bilerek dışladığı bölümdür:
    // devamdan sonraki ilk nokta kaydedilir ama aradaki mesafe sayılmaz.
    @Test
    fun manualResumeSkipsGapDistance() {
        val s = session()
        s.onFix(fix(0, 0.0), false, 0)
        s.pauseManual(5_000)
        s.resumeManual(50_000)
        assertNotNull(s.onFix(fix(55_000, 10.0), false, 55_000))
        assertEquals(0.0, s.distanceM, 1e-9)
        assertNotNull(s.onFix(fix(60_000, 11.0), false, 60_000))
        assertEquals(10.0, s.distanceM, 0.5)
    }

    @Test
    fun stopFreezesAndSummarises() {
        val s = session()
        s.onFix(fix(0, 0.0, alt = 100.0), true, 0)
        s.onFix(fix(5_000, 1.0, alt = 105.0), true, 5_000)
        s.stop(6_000)
        assertNull(s.onFix(fix(10_000, 2.0), false, 10_000))
        val sum = s.summary(id = 7, endWallMillis = 1_006_000, nowMonotonicMillis = 60_000)
        assertEquals(ActivityType.WALK, sum.type)
        assertEquals(1_000_000, sum.startTimeMillis)
        assertEquals(1_006_000, sum.endTimeMillis)
        assertEquals(10.0, sum.distanceM, 0.5)
        assertEquals(6_000, sum.durationMillis)
        assertEquals(5.0, sum.elevationGainM, 1e-9)
        assertEquals(0.0, sum.elevationLossM, 1e-9)
    }

    // Servis öldürülüp yeniden başlatıldığında kayıt diskten devralınır:
    // mesafe/süre korunur, yeni fix eski son noktadan ölçülür, yükseklik
    // saklanan rakımlardan aynı histerezisle yeniden kurulur.
    @Test
    fun primeRestoresRecoveredState() {
        val s = session()
        s.prime(
            recoveredDistanceM = 500.0,
            recoveredDurationMillis = 600_000,
            lastPoint = fix(0, 0.0, alt = 100.0),
            altitudes = listOf(100.0, 110.0, 105.0)
        )
        assertEquals(500.0, s.distanceM, 1e-9)
        assertEquals(610_000, s.durationMillis(10_000))
        assertEquals(10.0, s.elevationGainM, 1e-9)
        assertEquals(5.0, s.elevationLossM, 1e-9)
        assertNotNull(s.onFix(fix(5_000, 1.0), false, 5_000))
        assertEquals(510.0, s.distanceM, 0.5)
    }

    // Rakım bildirmeyen fix yükseklik hesabına girmez — 0.0 nöbetçi değeri
    // hayalet iniş üretirdi.
    @Test
    fun invalidAltitudeIsIgnored() {
        val s = session()
        s.onFix(fix(0, 0.0, alt = 100.0), true, 0)
        s.onFix(fix(5_000, 1.0, alt = 0.0), false, 5_000)     // hasAltitude=false
        s.onFix(fix(10_000, 2.0, alt = 110.0), true, 10_000)
        assertEquals(10.0, s.elevationGainM, 1e-9)
        assertEquals(0.0, s.elevationLossM, 1e-9)
    }
}
