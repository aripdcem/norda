package com.aripd.norda.core.track

import com.aripd.norda.core.geo.Geo

/**
 * Kayıt durum makinesi: filtre, oto-duraklatma, kronometre, mesafe ve
 * yükseklik tek çatı altında. Saf Kotlin — Android tarafı yalnızca fix'leri
 * iletir ve kabul edilen noktaları kalıcılaştırır.
 *
 * İki ayrı zaman tabanı bilinçli: kronometre işlemleri monotonik saatle
 * çağrılır (kullanıcı duvar saatini değiştirebilir), oto-duraklatma ise
 * fix'lerin kendi zaman damgalarıyla karar verir.
 */
class RecordingSession(
    val type: ActivityType,
    val startWallMillis: Long,
    startMonotonicMillis: Long,
    private val autoPause: AutoPauseDetector = AutoPauseDetector(),
    private val elevation: ElevationTracker = ElevationTracker()
) {

    enum class State { RECORDING, PAUSED, AUTO_PAUSED, STOPPED }

    var state = State.RECORDING
        private set

    private val stopwatch = PauseAwareStopwatch().apply { start(startMonotonicMillis) }
    private val recorded = mutableListOf<TrackPoint>()
    val points: List<TrackPoint> get() = recorded

    var distanceM = 0.0
        private set
    val elevationGainM get() = elevation.gainM
    val elevationLossM get() = elevation.lossM

    // Elle duraklatma sonrası: aradaki yol mesafeye sayılmaz (kullanıcı o
    // bölümü bilerek dışladı) ve dedektör yeni konumla sıfırlanır.
    private var breakSegment = false
    private var pendingDetectorReset = false

    // Filtre kalibrasyonunun ham verisi (Faz 8): karar başına sayaç. Yalnız
    // RECORDING durumunda değerlendirilen fix'ler sayılır — duraklatmada
    // gelenler kalibrasyon verisi değildir.
    private val filterCounts = IntArray(GpsFilter.Verdict.entries.size)

    fun filterCount(verdict: GpsFilter.Verdict): Int = filterCounts[verdict.ordinal]

    fun evaluatedFixCount(): Int = filterCounts.sum()

    // GPS oturana dek geçen süre ekranda görünür olmalı (F-4): kayda nokta
    // girmemişken son/en iyi doğruluk buradan okunur. 0 = cihaz doğruluk
    // bildirmiyor, kaliteye sayılmaz.
    var latestAccuracyM: Float? = null
        private set
    var bestAccuracyM: Float? = null
        private set

    /**
     * Ham fix işlenir; kayda giren nokta döner (çağıran kalıcılaştırır),
     * girmeyen için null. [hasAltitude] yalnız geçerli rakımlarda true olmalı.
     */
    fun onFix(fix: TrackPoint, hasAltitude: Boolean, nowMonotonicMillis: Long): TrackPoint? {
        if (state == State.STOPPED || state == State.PAUSED) return null

        if (pendingDetectorReset) {
            autoPause.reset(fix)
            pendingDetectorReset = false
        }

        val previous = recorded.lastOrNull()
        var accepted = false
        if (state == State.RECORDING) {
            val verdict = GpsFilter.evaluate(previous, fix)
            filterCounts[verdict.ordinal]++
            accepted = verdict == GpsFilter.Verdict.ACCEPT
            if (fix.accuracyM > 0f) {
                latestAccuracyM = fix.accuracyM
                val best = bestAccuracyM
                if (best == null || fix.accuracyM < best) bestAccuracyM = fix.accuracyM
            }
        }

        when (autoPause.onFix(fix, accepted)) {
            AutoPauseDetector.Decision.PAUSE -> {
                state = State.AUTO_PAUSED
                stopwatch.pause(nowMonotonicMillis)
                return null
            }
            AutoPauseDetector.Decision.RESUME -> {
                state = State.RECORDING
                stopwatch.resume(nowMonotonicMillis)
                return null
            }
            AutoPauseDetector.Decision.NONE -> Unit
        }

        if (state != State.RECORDING || !accepted) return null

        if (previous != null && !breakSegment) {
            distanceM += Geo.distanceMeters(
                previous.latitude, previous.longitude, fix.latitude, fix.longitude
            )
        }
        breakSegment = false
        recorded += fix
        if (hasAltitude) elevation.onAltitude(fix.altitude)
        return fix
    }

    /**
     * Süreç ölümü sonrası kurtarma: diskteki yarım kayıttan devralınan durum.
     * Yalnızca kurgudan hemen sonra, ilk fix'ten önce çağrılır. Mesafe ve
     * süre devralınır; yükseklik, saklanan rakımlar sırayla beslenerek
     * aynı histerezisle yeniden hesaplanır; son nokta filtreye "önceki"
     * olarak verilir.
     */
    fun prime(
        recoveredDistanceM: Double,
        recoveredDurationMillis: Long,
        lastPoint: TrackPoint?,
        altitudes: List<Double>
    ) {
        distanceM = recoveredDistanceM
        stopwatch.prime(recoveredDurationMillis)
        lastPoint?.let { recorded += it }
        altitudes.forEach { elevation.onAltitude(it) }
    }

    fun pauseManual(nowMonotonicMillis: Long) {
        if (state == State.RECORDING || state == State.AUTO_PAUSED) {
            state = State.PAUSED
            stopwatch.pause(nowMonotonicMillis)
        }
    }

    fun resumeManual(nowMonotonicMillis: Long) {
        if (state == State.PAUSED) {
            state = State.RECORDING
            stopwatch.resume(nowMonotonicMillis)
            breakSegment = true
            pendingDetectorReset = true
        }
    }

    fun stop(nowMonotonicMillis: Long) {
        if (state == State.STOPPED) return
        stopwatch.pause(nowMonotonicMillis)
        state = State.STOPPED
    }

    fun durationMillis(nowMonotonicMillis: Long): Long =
        stopwatch.elapsedMillis(nowMonotonicMillis)

    fun currentPaceSecPerKm(): Double? = Stats.currentPaceSecPerKm(recorded)

    fun summary(id: Long, endWallMillis: Long, nowMonotonicMillis: Long): ActivitySummary =
        ActivitySummary(
            id = id,
            type = type,
            startTimeMillis = startWallMillis,
            endTimeMillis = endWallMillis,
            distanceM = distanceM,
            durationMillis = durationMillis(nowMonotonicMillis),
            elevationGainM = elevationGainM,
            elevationLossM = elevationLossM
        )
}
