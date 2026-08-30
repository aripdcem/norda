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
    // gelenler kalibrasyon verisi değildir. Oturma kapısındaki adayın kararı
    // ikinci fix'le kesinleşir; hiç kesinleşmeyen aday sayılmaz.
    private val filterCounts = IntArray(GpsFilter.Verdict.entries.size)

    fun filterCount(verdict: GpsFilter.Verdict): Int = filterCounts[verdict.ordinal]

    fun evaluatedFixCount(): Int = filterCounts.sum()

    /** Bu çağrıda kayda giren nokta + kalıcılaştırma bayrağı (çağıran DB'ye yazar). */
    data class Accepted(val point: TrackPoint, val hasAltitude: Boolean)

    // Oturma kapısı (F-11): iki turda ilk fix 13–26 m sapık geldi ve çapa
    // yapıldığı için hayalet mesafe — sıçrayan nokta reddedilse bile — bir
    // sonraki noktayla yine sayıldı. İlk fix bu yüzden çapa değil ADAYdır:
    // ikinci fix'le fiziksel tutarlılık doğrulanana dek kayda girmez;
    // ışınlama çıkarsa suçlu ilk fix'tir — aday değiştirilir.
    private var tentative: Accepted? = null

    // GPS oturana dek geçen süre ekranda görünür olmalı (F-4): kayda nokta
    // girmemişken son/en iyi doğruluk buradan okunur. 0 = cihaz doğruluk
    // bildirmiyor, kaliteye sayılmaz.
    var latestAccuracyM: Float? = null
        private set
    var bestAccuracyM: Float? = null
        private set

    /**
     * Ham fix işlenir; bu çağrıda kayda GİREN noktalar döner (çağıran
     * kalıcılaştırır): boş, tek nokta ya da — aday ikinci fix'le
     * doğrulandığında — aday + fix birlikte. [hasAltitude] yalnız geçerli
     * rakımlarda true olmalı.
     */
    fun onFix(fix: TrackPoint, hasAltitude: Boolean, nowMonotonicMillis: Long): List<Accepted> {
        if (state == State.STOPPED || state == State.PAUSED) return emptyList()

        if (pendingDetectorReset) {
            autoPause.reset(fix)
            pendingDetectorReset = false
        }

        // Kapı yalnız kayıt boşken işler; kurtarmada (prime) devralınan son
        // nokta kayda konduğundan orada oturma sınaması yeniden yapılmaz.
        val gate = recorded.isEmpty()
        val previous = if (gate) tentative?.point else recorded.lastOrNull()
        // accepted: oto-duraklatma dedektörünün gördüğü bayrak (aday da kaliteli
        // harekettir); commitFix: fix'in kendisinin bu çağrıda kayda girmesi.
        var accepted = false
        var commitFix = false
        var confirmTentative = false
        if (state == State.RECORDING) {
            val verdict = GpsFilter.evaluate(previous, fix)
            if (!gate) {
                filterCounts[verdict.ordinal]++
                accepted = verdict == GpsFilter.Verdict.ACCEPT
                commitFix = accepted
            } else when (verdict) {
                GpsFilter.Verdict.ACCEPT -> {
                    accepted = true
                    if (previous == null) {
                        // İlk kaliteli fix: aday olur, kabul sayımı ve kayıt
                        // ikinci fix'le kesinleşir.
                        tentative = Accepted(fix, hasAltitude)
                    } else {
                        confirmTentative = true
                        commitFix = true
                    }
                }
                GpsFilter.Verdict.JITTER -> {
                    // Aday yakınında kıpırdama: çift tutarlı → aday kesinleşir,
                    // fix'in kendisi her zamanki gibi titreme olarak elenir.
                    filterCounts[verdict.ordinal]++
                    confirmTentative = true
                }
                GpsFilter.Verdict.TELEPORT -> {
                    // Çift fiziksel olarak tutarsız; saha kanıtına göre sapık
                    // olan İLK fix'tir. Sayaç düşen adayı anlatır.
                    filterCounts[verdict.ordinal]++
                    tentative = Accepted(fix, hasAltitude)
                }
                else -> filterCounts[verdict.ordinal]++
            }
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
                return if (confirmTentative) listOf(commitTentative()) else emptyList()
            }
            AutoPauseDetector.Decision.RESUME -> {
                state = State.RECORDING
                stopwatch.resume(nowMonotonicMillis)
                return if (confirmTentative) listOf(commitTentative()) else emptyList()
            }
            AutoPauseDetector.Decision.NONE -> Unit
        }

        if (state != State.RECORDING) return emptyList()

        val out = mutableListOf<Accepted>()
        if (confirmTentative) out += commitTentative()
        if (!commitFix) return out

        val prev = recorded.lastOrNull()
        if (prev != null && !breakSegment) {
            distanceM += Geo.distanceMeters(
                prev.latitude, prev.longitude, fix.latitude, fix.longitude
            )
        }
        breakSegment = false
        // Kapı dışındaki kabul yukarıda sayıldı; kapıdaki burada kesinleşir.
        if (gate) filterCounts[GpsFilter.Verdict.ACCEPT.ordinal]++
        recorded += fix
        if (hasAltitude) elevation.onAltitude(fix.altitude)
        out += Accepted(fix, hasAltitude)
        return out
    }

    private fun commitTentative(): Accepted {
        val t = tentative!!
        tentative = null
        filterCounts[GpsFilter.Verdict.ACCEPT.ordinal]++
        recorded += t.point
        if (t.hasAltitude) elevation.onAltitude(t.point.altitude)
        return t
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
