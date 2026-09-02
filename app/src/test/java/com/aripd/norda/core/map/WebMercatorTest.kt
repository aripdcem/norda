package com.aripd.norda.core.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verification against known fixed points: Greenwich, the equator, Istanbul's
 * z10 tile. We test the projection's definition, not our own output.
 */
class WebMercatorTest {

    @Test
    fun greenwichAndEquatorAreWorldCenter() {
        assertEquals(0.5, WebMercator.xTile(0.0, 0), 1e-12)
        assertEquals(0.5, WebMercator.yTile(0.0, 0), 1e-12)
        assertEquals(512.0, WebMercator.xTile(0.0, 10), 1e-9)
    }

    @Test
    fun dateLineIsWorldEdge() {
        assertEquals(0.0, WebMercator.xTile(-180.0, 5), 1e-12)
        assertEquals(32.0, WebMercator.xTile(180.0, 5), 1e-12)
    }

    // Istanbul (41.0082, 28.9784) falls on tile (594, 383) at z10 — a known
    // value anyone can verify on a map server.
    @Test
    fun istanbulLandsOnKnownTile() {
        val x = WebMercator.xTile(28.9784, 10)
        val y = WebMercator.yTile(41.0082, 10)
        assertEquals(594.0, Math.floor(x), 1e-9)
        assertEquals(383.0, Math.floor(y), 1e-9)
    }

    @Test
    fun roundTripIsIdentity() {
        val lon = 28.9784
        val lat = 41.0082
        assertEquals(lon, WebMercator.lonDeg(WebMercator.xTile(lon, 12), 12), 1e-9)
        assertEquals(lat, WebMercator.latDeg(WebMercator.yTile(lat, 12), 12), 1e-9)
    }

    @Test
    fun latitudeIsClampedToProjectionDomain() {
        assertEquals(WebMercator.MAX_LAT, WebMercator.clampLat(89.9), 1e-12)
        assertEquals(-WebMercator.MAX_LAT, WebMercator.clampLat(-89.9), 1e-12)
        // thanks to the clamp, extreme values give a finite tile coordinate
        assertTrue(WebMercator.yTile(89.9, 10) >= 0.0)
    }

    // MBTiles' TMS row: the y axis is inverted. Forgetting the flip mirrors the
    // map vertically — this test closes that bug for good.
    @Test
    fun tmsRowFlipsVertically() {
        assertEquals(0, WebMercator.tmsRow(0, 0))
        assertEquals(6, WebMercator.tmsRow(1, 3))
        assertEquals(0, WebMercator.tmsRow(7, 3))
        assertEquals(383, WebMercator.tmsRow(640, 10))
    }

    @Test
    fun fitZoomFitsWorldInSmallView() {
        val z = WebMercator.fitZoom(-80.0, -179.0, 80.0, 179.0, 512, 512, 0, 16)
        assertEquals(0, z)
    }

    @Test
    fun fitZoomForCityBoundsIsReasonableAndFits() {
        val z = WebMercator.fitZoom(40.8, 28.6, 41.3, 29.4, 1000, 1400, 3, 16)
        assertTrue("zoom=$z", z in 9..12)
        val wPx = (WebMercator.xTile(29.4, z) - WebMercator.xTile(28.6, z)) * WebMercator.TILE_SIZE
        assertTrue(wPx <= 900.0)
    }
}
