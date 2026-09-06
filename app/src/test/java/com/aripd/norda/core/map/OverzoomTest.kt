package com.aripd.norda.core.map

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * F-13 (field: "colors are nice but I could not zoom"): a pack ends at z13,
 * about 19 m per pixel — too coarse for street level. Levels above the pack
 * ceiling are drawn by scaling the ceiling tiles; this is the pure tile
 * mapping behind that.
 */
class OverzoomTest {

    @Test
    fun atTheCeilingTheSourceIsTheTileItself() {
        val s = Overzoom.source(13, 4756, 3071, maxZoom = 13, tile = 256)
        assertEquals(Overzoom.Source(13, 4756, 3071, 0, 0, 256), s)
    }

    @Test
    fun oneLevelAboveReadsAQuarterOfTheParent() {
        // the four children of parent (13, 100, 200) cover its four quadrants
        assertEquals(Overzoom.Source(13, 100, 200, 0, 0, 128), Overzoom.source(14, 200, 400, 13, 256))
        assertEquals(Overzoom.Source(13, 100, 200, 128, 0, 128), Overzoom.source(14, 201, 400, 13, 256))
        assertEquals(Overzoom.Source(13, 100, 200, 0, 128, 128), Overzoom.source(14, 200, 401, 13, 256))
        assertEquals(Overzoom.Source(13, 100, 200, 128, 128, 128), Overzoom.source(14, 201, 401, 13, 256))
    }

    @Test
    fun threeLevelsAboveReadsA32PixelPatch() {
        val s = Overzoom.source(16, 8 * 4756 + 5, 8 * 3071 + 7, 13, 256)
        assertEquals(Overzoom.Source(13, 4756, 3071, 5 * 32, 7 * 32, 32), s)
    }

    @Test
    fun ceilingIsThePackMaximumPlusTheOverzoomLevels() {
        assertEquals(16, Overzoom.ceiling(13))
        assertEquals(20, Overzoom.ceiling(19))   // never past the Web Mercator practical cap
    }
}
