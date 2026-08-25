package com.aripd.norda.core.io

import com.aripd.norda.core.nav.Waypoint
import com.aripd.norda.core.track.TrackPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GpxTest {

    private fun p(t: Long, lat: Double, lon: Double, alt: Double) =
        TrackPoint(t, lat, lon, alt, 0f, 0f, 0f)

    // Yazdığımızı geri okuyabilmeliyiz: iz + noktalar, rakım bayrakları ve
    // XML kaçışlı adlar korunur.
    @Test
    fun roundTripPreservesTrackAndWaypoints() {
        val points = listOf(
            p(1_700_000_000_000, 41.00001, 29.00001, 105.5),
            p(1_700_000_005_000, 41.00010, 29.00010, 0.0)
        )
        val wpt = Waypoint(0, "Çeşme & Pınar", 41.0005, 29.0005, 210.0, 0)
        val xml = Gpx.write("Deneme <izi>", points, listOf(true, false), listOf(wpt))
        assertTrue(xml.contains("&amp;"))
        assertFalse(xml.substringAfter("<trkseg>").contains("<ele>0.0</ele>"))

        val parsed = Gpx.parse(xml)
        assertEquals("Deneme <izi>", parsed.name)
        assertEquals(2, parsed.points.size)
        assertTrue(parsed.points[0].hasAltitude)
        assertEquals(105.5, parsed.points[0].point.altitude, 1e-9)
        assertFalse(parsed.points[1].hasAltitude)
        assertEquals(1_700_000_000_000, parsed.points[0].point.timeMillis)
        assertEquals(41.00010, parsed.points[1].point.latitude, 1e-9)
        assertEquals(1, parsed.waypoints.size)
        assertEquals("Çeşme & Pınar", parsed.waypoints[0].name)
        assertEquals(210.0, parsed.waypoints[0].altitude!!, 1e-9)
    }

    // Bozuk girdi satır atlanarak tolere edilir (docs/MVP.md, 10. bölüm).
    @Test
    fun brokenPointsAreSkippedOthersSurvive() {
        val xml = """<?xml version="1.0"?><gpx>
            <trk><trkseg>
            <trkpt lat="abc" lon="29.0"></trkpt>
            <trkpt lat="41.0" lon="29.0"><ele>100</ele></trkpt>
            </trkseg></trk>
            <wpt lat="41.1" lon="xyz"><name>Bozuk</name></wpt>
            <wpt lat="41.1" lon="29.1"><name>Sağlam</name></wpt>
            </gpx>"""
        val parsed = Gpx.parse(xml)
        assertEquals(1, parsed.points.size)
        assertEquals(1, parsed.waypoints.size)
        assertEquals("Sağlam", parsed.waypoints[0].name)
    }

    @Test
    fun missingTimeAndEleUseDefaults() {
        val xml = """<gpx><trk><trkseg>
            <trkpt lat="41.0" lon="29.0"></trkpt>
            </trkseg></trk></gpx>"""
        val parsed = Gpx.parse(xml)
        assertEquals(0, parsed.points[0].point.timeMillis)
        assertFalse(parsed.points[0].hasAltitude)
    }

    @Test
    fun waypointsOnlyFileHasNoTrack() {
        val xml = Gpx.write("Ad", emptyList(), emptyList(), listOf(
            Waypoint(0, "Kamp", 41.0, 29.0, null, 0)
        ))
        assertFalse(xml.contains("<trk>"))
        val parsed = Gpx.parse(xml)
        assertEquals(0, parsed.points.size)
        assertEquals(1, parsed.waypoints.size)
        assertEquals(null, parsed.waypoints[0].altitude)
    }

    @Test
    fun millisTimestampVariantIsParsed() {
        val xml = """<gpx><trk><trkseg>
            <trkpt lat="41.0" lon="29.0"><time>2026-08-25T10:00:00.500Z</time></trkpt>
            </trkseg></trk></gpx>"""
        val t = Gpx.parse(xml).points[0].point.timeMillis
        assertTrue("t=$t", t > 1_700_000_000_000)
    }
}
