package com.aripd.norda.core.track

/**
 * A stopwatch that does not count pauses. Time is supplied from outside (to
 * stay pure and testable); the Android side passes
 * `SystemClock.elapsedRealtime()` — not the wall clock, because the user can
 * change the clock.
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

    /** After recovery: take over the lost session's duration. */
    fun prime(elapsedMillis: Long) {
        accumulatedMillis = elapsedMillis
    }
}
