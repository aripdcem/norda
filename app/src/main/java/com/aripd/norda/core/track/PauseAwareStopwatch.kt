package com.aripd.norda.core.track

/**
 * Duraklatmaları saymayan kronometre. Zaman dışarıdan verilir (saf ve test
 * edilebilir kalsın diye); Android tarafı `SystemClock.elapsedRealtime()`
 * geçirir — duvar saati değil, çünkü kullanıcı saati değiştirebilir.
 */
class PauseAwareStopwatch {

    private var runningSince = 0L
    private var accumulatedMillis = 0L
    var isRunning = false
        private set

    fun start(nowMillis: Long) {
        if (isRunning) return
        runningSince = nowMillis
        isRunning = true
    }

    fun pause(nowMillis: Long) {
        if (!isRunning) return
        accumulatedMillis += nowMillis - runningSince
        isRunning = false
    }

    fun resume(nowMillis: Long) = start(nowMillis)

    fun elapsedMillis(nowMillis: Long): Long =
        accumulatedMillis + if (isRunning) nowMillis - runningSince else 0L

    /** Kurtarma sonrası: kaybolan oturumun süresini devral. */
    fun prime(elapsedMillis: Long) {
        accumulatedMillis = elapsedMillis
    }
}
