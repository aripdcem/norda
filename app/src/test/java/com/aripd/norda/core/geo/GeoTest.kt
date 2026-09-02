package com.aripd.norda.core.geo

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The tests rest on physical constants: they verify geodesy, not our own
 * output (docs/MVP.md, 13.1 rule 3).
 */
class GeoTest {

    // At the equator, 1° of longitude = Earth's circumference / 360 ≈ 111.2 km.
    @Test
    fun equatorOneDegreeLongitude() {
        assertEquals(111_195.0, Geo.distanceMeters(0.0, 0.0, 0.0, 1.0), 200.0)
    }

    // 1 degree of latitude has the same length at every longitude (a meridian
    // arc on a sphere).
    @Test
    fun oneDegreeLatitude() {
        assertEquals(111_195.0, Geo.distanceMeters(40.0, 29.0, 41.0, 29.0), 200.0)
    }

    @Test
    fun zeroDistanceForSamePoint() {
        assertEquals(0.0, Geo.distanceMeters(41.0, 29.0, 41.0, 29.0), 1e-6)
    }

    @Test
    fun distanceIsSymmetric() {
        val ab = Geo.distanceMeters(41.0, 29.0, 21.4, 39.8)
        val ba = Geo.distanceMeters(21.4, 39.8, 41.0, 29.0)
        assertEquals(ab, ba, 1e-6)
    }

    // At the equator the bearing to a target due east is 90°. The sign error in
    // the MVP draft (Δλ reversed) gave 270 here — this test closes that bug for
    // good.
    @Test
    fun bearingDueEastOnEquator() {
        assertEquals(90.0, Geo.initialBearingDeg(0.0, 29.0, 0.0, 29.1), 0.01)
    }

    @Test
    fun bearingDueWestOnEquator() {
        assertEquals(270.0, Geo.initialBearingDeg(0.0, 29.1, 0.0, 29.0), 0.01)
    }

    @Test
    fun bearingDueNorth() {
        assertEquals(0.0, Geo.initialBearingDeg(40.0, 29.0, 41.0, 29.0), 0.01)
    }

    // Bearing from Istanbul to the Kaaba ≈ 151.6° — a known geographic
    // reference value.
    @Test
    fun bearingIstanbulToKaaba() {
        val b = Geo.initialBearingDeg(41.0082, 28.9784, 21.4225, 39.8262)
        assertEquals(151.6, b, 0.5)
    }

    @Test
    fun normalizeWrapsBothDirections() {
        assertEquals(1.0, Geo.normalizeDeg(361.0), 1e-9)
        assertEquals(359.0, Geo.normalizeDeg(-1.0), 1e-9)
        assertEquals(0.0, Geo.normalizeDeg(720.0), 1e-9)
    }

    @Test
    fun signedDifferenceCrossesNorthWithoutFullTurn() {
        assertEquals(2.0, Geo.signedDifferenceDeg(359.0, 1.0), 1e-9)
        assertEquals(-2.0, Geo.signedDifferenceDeg(1.0, 359.0), 1e-9)
        assertEquals(180.0, Geo.signedDifferenceDeg(0.0, 180.0), 1e-9)
    }
}
