package com.aripd.norda.core.nav

import com.aripd.norda.core.track.TrackPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Breadcrumb navigation (docs/MVP.md 9.3): follow the recorded track back to
 * the start instead of the straight line. The trail is the list of accepted
 * points; guidance is computed from the current position.
 */
class BreadcrumbTest {

    private val step10m = 0.00009000     // ~10.008 m of latitude (a hair over, so 2 steps clear 20 m)

    private fun p(latSteps: Double, lonSteps: Double = 0.0) =
        TrackPoint(0L, 41.0 + latSteps * step10m, 29.0 + lonSteps * step10m / Math.cos(Math.toRadians(41.0)), 0.0, 5f, 0f, 0f)

    // a straight trail heading north: 0, 10, 20 … 100 m
    private fun northTrail(n: Int = 11) = List(n) { p(it.toDouble()) }

    @Test
    fun emptyTrailGivesNoGuidance() {
        assertNull(Breadcrumb.Trail(emptyList()).guidance(41.0, 29.0, null))
    }

    @Test
    fun atTheFarEndTheWholeTrailRemainsAndTheWayBackIsSouth() {
        val trail = Breadcrumb.Trail(northTrail())
        val g = trail.guidance(p(10.0).latitude, p(10.0).longitude, paceSecPerKm = 600.0)!!
        assertTrue(g.onTrail)
        assertEquals(10, g.nearestIndex)
        assertEquals(100.0, g.remainingM, 1.0)
        assertEquals(180.0, g.bearingDeg, 2.0)          // walk back south
        assertEquals(60_000.0, g.etaMillis!!.toDouble(), 100.0)   // ~100 m at 10 min/km
        assertFalse(g.arrived)
    }

    @Test
    fun theTargetIsAboutTwentyMetresBackAlongTheTrailNotTheNextPoint() {
        val trail = Breadcrumb.Trail(northTrail())
        val g = trail.guidance(p(10.0).latitude, p(10.0).longitude, null)!!
        assertEquals(8, g.targetIndex)                   // 20 m back: index 10 → 8
    }

    @Test
    fun offTrailGuidancePointsBackToTheTrail() {
        val trail = Breadcrumb.Trail(northTrail())
        // 40 m east of the 50 m point
        val here = p(5.0, 4.0)
        val g = trail.guidance(here.latitude, here.longitude, null)!!
        assertFalse(g.onTrail)
        assertEquals(5, g.nearestIndex)
        assertEquals(40.0, g.offTrailM, 1.0)
        assertEquals(270.0, g.bearingDeg, 2.0)          // west, back to the trail
        assertEquals(50.0, g.remainingM, 1.0)           // from the 50 m point back to the start
    }

    @Test
    fun nearTheStartTheGuidanceSaysArrived() {
        val trail = Breadcrumb.Trail(northTrail())
        val g = trail.guidance(p(0.3).latitude, p(0.3).longitude, null)!!
        assertTrue(g.arrived)
        assertEquals(0.0, g.remainingM, 1e-9)
    }

    @Test
    fun aLoopTakesTheShortcutTheNearestPassOffers() {
        // out 100 m north, then 30 m east, then back south parallel: the two
        // legs run 30 m apart; standing 5 m from the outbound leg at its 50 m
        // point, the nearest point is on the outbound leg, and only 50 m remain.
        val out = List(11) { p(it.toDouble()) }
        val across = List(3) { p(10.0, it.toDouble()) }
        val back = List(11) { p(10.0 - it, 3.0) }
        val trail = Breadcrumb.Trail(out + across + back)
        val here = p(5.0, 0.5)
        val g = trail.guidance(here.latitude, here.longitude, null)!!
        assertEquals(5, g.nearestIndex)
        assertEquals(50.0, g.remainingM, 1.0)
    }

    @Test
    fun trailLengthIsTheSumOfItsLegs() {
        assertEquals(100.0, Breadcrumb.Trail(northTrail()).lengthM, 1.0)
        assertNotNull(Breadcrumb.Trail(listOf(p(0.0))).guidance(41.0, 29.0, null))
    }
}
