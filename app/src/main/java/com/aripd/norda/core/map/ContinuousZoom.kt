package com.aripd.norda.core.map

import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Continuous (fractional) zoom (F-14). Tiles exist at integer levels; the map
 * keeps a fractional zoom, reads tiles from the nearest level and draws them
 * scaled by 2^(zoom − level) — between 0.71× and 1.41×, so a pinch glides
 * instead of jumping a whole level at a time.
 */
object ContinuousZoom {

    /** The integer tile level to draw for a fractional zoom, within the allowed range. */
    fun level(zoom: Double, minLevel: Int, maxLevel: Int): Int =
        zoom.roundToInt().coerceIn(minLevel, maxLevel)

    /** Pixels-per-tile factor relative to a tile's native size at [level]. */
    fun scale(zoom: Double, level: Int): Double = 2.0.pow(zoom - level)

    /**
     * New center (one axis, tile units at the current level) after magnifying
     * by [factor] around the map point [focus]: the point under the finger
     * stays where it is on screen.
     */
    fun focalCenter(center: Double, focus: Double, factor: Double): Double =
        focus + (center - focus) / factor
}
