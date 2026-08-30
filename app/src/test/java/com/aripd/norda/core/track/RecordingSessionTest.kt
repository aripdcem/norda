package com.aripd.norda.core.track

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Durum makinesinin uçtan uca sınanması: sentetik bir yürüyüş beslenir,
 * mesafe/süre/yükseklik ve durum geçişleri doğrulanır. Testlerde monotonik
 * saat ile fix zamanı aynı değerdir (sadelik için).
 *
 * onFix bu çağrıda kayda GİREN noktaları döndürür (0/1/2 eleman): oturma
 * kapısı (F-11) yüzünden ilk fix aday olarak bekler ve ikinci fix'le
 * doğrulanınca ikisi birden dönebilir.
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
        s.onFix(fix(0, 0.0), false, 0)                          // aday
        s.onFix(fix(5_000, 1.0), false, 5_000)                  // doğrular: 2 kabul
        s.onFix(fix(10_000, 1.05), false, 10_000)               // ~0,5 m titreme
        s.onFix(fix(15_000, 1.0, acc = 99f), false, 15_000)     // kötü doğruluk
        assertEquals(2, s.filterCount(GpsFilter.Verdict.ACCEPT))
        assertEquals(1, s.filterCount(GpsFilter.Verdict.JITTER))
        assertEquals(1, s.filterCount(GpsFilter.Verdict.BAD_ACCURACY))
        assertEquals(0, s.filterCount(GpsFilter.Verdict.TELEPORT))
        assertEquals(0, s.filterCount(GpsFilter.Verdict.NON_MONOTONIC))
    }

    // F-4 (tenis kortu denemesi): GPS oturmadan geçen süre ekranda görünür
    // olmalı — kayda nokta girmemişken son/en iyi doğruluk gözlemlenebilir.
    @Test
    fun gpsQualityIsObservableBeforeFirstAccept() {
        val s = session()
        assertEquals(null, s.latestAccuracyM)
        assertEquals(null, s.bestAccuracyM)
        assertEquals(0, s.evaluatedFixCount())
        s.onFix(fix(0, 0.0, acc = 48f), false, 0)          // kötü doğruluk
        s.onFix(fix(1_000, 0.0, acc = 35f), false, 1_000)  // hâlâ kötü
        assertEquals(35f, s.latestAccuracyM)
        assertEquals(35f, s.bestAccuracyM)
        assertEquals(2, s.evaluatedFixCount())
        assertEquals(0, s.points.size)
    }

    @Test
    fun unknownAccuracyDoesNotPolluteGpsQuality() {
        val s = session()
        s.onFix(fix(0, 0.0, acc = 0f), false, 0)                // doğruluk bilinmiyor → aday
        s.onFix(fix(5_000, 1.0, acc = 0f), false, 5_000)        // doğrular
        assertEquals(null, s.bestAccuracyM)
        assertEquals(null, s.latestAccuracyM)
        assertEquals(2, s.evaluatedFixCount())
        assertEquals(2, s.points.size)
    }

    @Test
    fun manualPauseDoesNotPolluteFilterCounts() {
        val s = session()
        s.onFix(fix(0, 0.0), false, 0)
        s.onFix(fix(2_000, 1.0), false, 2_000)   // aday + bu fix kayda girer
        s.pauseManual(3_000)
        s.onFix(fix(5_000, 2.0), false, 5_000)   // duraklatmada gelen fix sayılmaz
        assertEquals(2, s.filterCount(GpsFilter.Verdict.ACCEPT))
    }

    @Test
    fun recordsAcceptedFixesAndAccumulatesDistance() {
        val s = session()
        assertEquals(0, s.onFix(fix(0, 0.0), hasAltitude = false, nowMonotonicMillis = 0).size)
        assertEquals(2, s.onFix(fix(5_000, 1.0), false, 5_000).size)  // aday + kendisi
        assertEquals(1, s.onFix(fix(10_000, 2.0), false, 10_000).size)
        assertEquals(20.0, s.distanceM, 0.5)
        assertEquals(10_000, s.durationMillis(10_000))
        assertEquals(3, s.points.size)
    }

    @Test
    fun rejectedJitterAddsNothing() {
        val s = session()
        s.onFix(fix(0, 0.0), false, 0)
        // ~0,5 m kıpırdama: adayı doğrular (yakın = tutarlı) ama kendisi girmez
        val r = s.onFix(fix(5_000, 0.05), false, 5_000)
        assertEquals(1, r.size)
        assertEquals(41.0, r[0].point.latitude, 1e-12)
        assertEquals(0.0, s.distanceM, 1e-9)
        assertEquals(1, s.points.size)
    }

    // F-11 (Tur 2t + gece koşusu, iki saha kanıtı): ilk fix "oturma"
    // sırasında 13–26 m sapık gelebiliyor; çapa yapılırsa sıçrayan nokta
    // reddedilse bile hayalet mesafe bir SONRAKİ noktayla yine sayılıyor.
    // İlk fix bu yüzden çapa değil ADAYdır: ikinci fix'le fiziksel
    // tutarlılık doğrulanana dek kayda girmez; ışınlama çıkarsa suçlu ilk
    // fix'tir — aday değiştirilir, hayalet mesafe hiç doğmaz.
    @Test
    fun settlingFirstFixIsReplacedNotAnchored() {
        val s = session()
        assertEquals(0, s.onFix(fix(0, 0.0), false, 0).size)          // aday
        // saha verisi: 2 sn'de ~25,7 m = 12,85 m/s — oturma sıçraması
        assertEquals(0, s.onFix(fix(2_000, 2.57), false, 2_000).size) // aday değişir
        assertEquals(1, s.filterCount(GpsFilter.Verdict.TELEPORT))
        assertEquals(0, s.points.size)
        // yeni adayın yakınındaki üçüncü fix adayı doğrular
        val confirmed = s.onFix(fix(4_000, 2.62), false, 4_000)
        assertEquals(1, confirmed.size)
        assertEquals(41.0 + 2.57 * step10m, confirmed[0].point.latitude, 1e-12)
        assertEquals(0.0, s.distanceM, 1e-9)                          // hayalet mesafe yok
        assertEquals(1, s.points.size)
        // normal akış devam eder
        assertEquals(1, s.onFix(fix(9_000, 3.57), false, 9_000).size)
        assertEquals(10.0, s.distanceM, 0.5)
        assertEquals(2, s.filterCount(GpsFilter.Verdict.ACCEPT))
        assertEquals(1, s.filterCount(GpsFilter.Verdict.JITTER))
    }

    // Hiç doğrulanamayan aday kayda girmez: tek fix'lik "kayıt" veri değil.
    @Test
    fun unresolvedTentativeFixNeverEntersTrack() {
        val s = session()
        assertEquals(0, s.onFix(fix(0, 0.0), false, 0).size)
        s.stop(1_000)
        assertEquals(0, s.points.size)
        assertEquals(0, s.filterCount(GpsFilter.Verdict.ACCEPT))
    }

    @Test
    fun autoPausesAndFreezesDuration() {
        val s = session()
        s.onFix(fix(0, 0.0), false, 0)
        // aynı yerde bekleme: kabul edilmeyen fix'ler
        s.onFix(fix(10_000, 0.0), false, 10_000)
        s.onFix(fix(20_000, 0.0), false, 20_000)
        assertEquals(0, s.onFix(fix(21_000, 0.0), false, 21_000).size)
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
        assertTrue(s.onFix(fix(35_000, 2.0), false, 35_000).isNotEmpty())
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
        assertEquals(0, s.onFix(fix(10_000, 5.0), false, 10_000).size)
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
        // aday + devam fix'i birlikte girer; aradaki mesafe sayılmaz
        assertEquals(2, s.onFix(fix(55_000, 10.0), false, 55_000).size)
        assertEquals(0.0, s.distanceM, 1e-9)
        assertEquals(1, s.onFix(fix(60_000, 11.0), false, 60_000).size)
        assertEquals(10.0, s.distanceM, 0.5)
    }

    @Test
    fun stopFreezesAndSummarises() {
        val s = session()
        s.onFix(fix(0, 0.0, alt = 100.0), true, 0)
        s.onFix(fix(5_000, 1.0, alt = 105.0), true, 5_000)
        s.stop(6_000)
        assertEquals(0, s.onFix(fix(10_000, 2.0), false, 10_000).size)
        val sum = s.summary(id = 7, endWallMillis = 1_006_000, nowMonotonicMillis = 60_000)
        assertEquals(ActivityType.WALK, sum.type)
        assertEquals(1_000_000, sum.startTimeMillis)
        assertEquals(1_006_000, sum.endTimeMillis)
        assertEquals(10.0, sum.distanceM, 0.5)
        assertEquals(6_000, sum.durationMillis)
        assertEquals(5.0, sum.elevationGainM, 1e-9)
        assertEquals(0.0, sum.elevationLossM, 1e-9)
    }

    // Kalıcılaştırma bayrağı noktaya aittir: rakımı geçerli aday ile rakımsız
    // doğrulayıcı fix aynı çağrıda dönerken bayraklar karışmamalı.
    @Test
    fun committedPointsCarryTheirOwnAltitudeFlag() {
        val s = session()
        s.onFix(fix(0, 0.0, alt = 100.0), true, 0)
        val pair = s.onFix(fix(5_000, 1.0, alt = 0.0), false, 5_000)
        assertEquals(2, pair.size)
        assertTrue(pair[0].hasAltitude)
        assertTrue(!pair[1].hasAltitude)
        assertEquals(0.0, s.elevationGainM, 1e-9)
    }

    // Servis öldürülüp yeniden başlatıldığında kayıt diskten devralınır:
    // mesafe/süre korunur, yeni fix eski son noktadan ölçülür, yükseklik
    // saklanan rakımlardan aynı histerezisle yeniden kurulur. Devralınan son
    // nokta zaten kayıtta olduğundan oturma kapısı işlemez.
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
        assertEquals(1, s.onFix(fix(5_000, 1.0), false, 5_000).size)
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
