package com.aripd.norda.core.heading

import kotlin.math.abs

/**
 * Magnetic disturbance detection (docs/MVP.md, 6.3): if the measured total
 * field strength deviates from the expected by more than 25% and the
 * deviation lasts 2.5 s without interruption, a warning is raised; it clears
 * once the deviation drops below 15%.
 *
 * The reason for the duration requirement: while the phone is turned in the
 * hand, calibration can produce a transient deviation — a momentary jump is
 * not a warning. The hysteresis band also keeps the warning from flickering
 * at the threshold.
 */
class DisturbanceDetector(
    private val enterRatio: Double = 0.25,
    private val exitRatio: Double = 0.15,
    private val sustainMillis: Long = 2_500L
) {

    private var overSinceMillis: Long? = null

    var isActive = false
        private set

    /** Processes one sample; returns the warning's current state. Fields in µT. */
    fun onSample(
        measuredMicroTesla: Double,
        expectedMicroTesla: Double,
        nowMillis: Long
    ): Boolean {
        if (expectedMicroTesla <= 0.0) {
            overSinceMillis = null
            isActive = false
            return false
        }
        val deviation = abs(measuredMicroTesla - expectedMicroTesla) / expectedMicroTesla
        if (isActive) {
            if (deviation < exitRatio) {
                isActive = false
                overSinceMillis = null
            }
        } else {
            if (deviation > enterRatio) {
                val since = overSinceMillis ?: nowMillis.also { overSinceMillis = it }
                if (nowMillis - since >= sustainMillis) isActive = true
            } else {
                overSinceMillis = null
            }
        }
        return isActive
    }
}
