package com.aripd.norda.core.map

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * F-14 (field: "zooming is not smooth, it goes step by step"): pinch zoom
 * used to jump whole levels. The map now carries a fractional zoom; tiles come
 * from the nearest integer level and are drawn scaled by 2^(fraction). This is
 * the pure arithmetic behind it.
 */
class ContinuousZoomTest {

    @Test
    fun nearestLevelWithinTheAllowedRange() {
        assertEquals(13, ContinuousZoom.level(13.4, 8, 16))
        assertEquals(14, ContinuousZoom.level(13.5, 8, 16))
        assertEquals(13, ContinuousZoom.level(12.9, 8, 16))
        assertEquals(8, ContinuousZoom.level(3.0, 8, 16))     // clamped below
        assertEquals(16, ContinuousZoom.level(19.0, 8, 16))   // clamped above
    }

    @Test
    fun scaleIsTwoToTheFractionAndStaysNearOne() {
        assertEquals(1.0, ContinuousZoom.scale(13.0, 13), 1e-12)
        assertEquals(Math.pow(2.0, 0.4), ContinuousZoom.scale(13.4, 13), 1e-12)
        assertEquals(Math.pow(2.0, -0.5), ContinuousZoom.scale(13.5, 14), 1e-12)
    }

    // Pinching around a finger keeps the map point under the finger still.
    @Test
    fun focalZoomKeepsThePointUnderTheFocusFixed() {
        val tilePx = 256.0
        val center = 1000.0          // tile units at the current level
        val viewCenterPx = 540.0
        val focusPx = 300.0
        val underFinger = center + (focusPx - viewCenterPx) / tilePx
        val factor = 1.3
        val newCenter = ContinuousZoom.focalCenter(center, underFinger, factor)
        // the same map point must sit at the same screen position at the new scale
        val screenAfter = (underFinger - newCenter) * tilePx * factor + viewCenterPx
        assertEquals(focusPx, screenAfter, 1e-9)
    }

    @Test
    fun zoomingAroundTheViewCenterDoesNotMoveIt() {
        assertEquals(1000.0, ContinuousZoom.focalCenter(1000.0, 1000.0, 2.0), 1e-12)
    }
}
