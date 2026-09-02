package com.aripd.norda.core.heading

import com.aripd.norda.core.geo.Geo
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

/**
 * The needle's low-pass filter (docs/MVP.md, 6.2).
 *
 * The angle is filtered through its sin/cos components, not directly —
 * otherwise the needle would spin a full turn at the 359°→0° crossing. What
 * is stored is not a coefficient but the TIME CONSTANT; the coefficient is
 * computed from the real interval on every sample, so the needle's feel stays
 * the same even if the sampling rate changes.
 */
class Smoothing(private val timeConstantSeconds: Double) {

    private var sinPart = 0.0
    private var cosPart = 1.0
    private var started = false

    /** Processes a new sample and returns the filtered angle (0–360). */
    fun update(angleDeg: Double, dtSeconds: Double): Double {
        val rad = Math.toRadians(angleDeg)
        if (!started) {
            started = true
            sinPart = sin(rad)
            cosPart = cos(rad)
        } else {
            val dt = dtSeconds.coerceAtLeast(1e-4)
            val alpha = 1.0 - exp(-dt / timeConstantSeconds)
            sinPart += alpha * (sin(rad) - sinPart)
            cosPart += alpha * (cos(rad) - cosPart)
        }
        return Geo.normalizeDeg(Math.toDegrees(atan2(sinPart, cosPart)))
    }

    fun reset() {
        started = false
        sinPart = 0.0
        cosPart = 1.0
    }
}
