package com.aripd.norda.core.map

import org.junit.Assert.assertEquals
import org.junit.Test

class MapCoverageTest {

    @Test
    fun `without a package the reason is the missing package`() {
        assertEquals(
            MapCoverage.State.NO_PACKAGE,
            MapCoverage.state(packages = 0, covered = false, tilesRequested = 6, tilesDrawn = 0)
        )
    }

    @Test
    fun `a package that does not cover the position says so`() {
        assertEquals(
            MapCoverage.State.OUT_OF_BOUNDS,
            MapCoverage.state(packages = 1, covered = false, tilesRequested = 6, tilesDrawn = 0)
        )
    }

    @Test
    fun `tiles on screen means nothing to report`() {
        assertEquals(
            MapCoverage.State.OK,
            MapCoverage.state(packages = 1, covered = true, tilesRequested = 6, tilesDrawn = 6)
        )
    }

    @Test
    fun `a single drawn tile is enough - the rest are still decoding`() {
        assertEquals(
            MapCoverage.State.OK,
            MapCoverage.state(packages = 1, covered = true, tilesRequested = 6, tilesDrawn = 1)
        )
    }

    @Test
    fun `covered but not a single tile means the tiles are missing`() {
        assertEquals(
            MapCoverage.State.MISSING_TILES,
            MapCoverage.state(packages = 2, covered = true, tilesRequested = 6, tilesDrawn = 0)
        )
    }

    @Test
    fun `before the first layout nothing is reported`() {
        assertEquals(
            MapCoverage.State.OK,
            MapCoverage.state(packages = 0, covered = false, tilesRequested = 0, tilesDrawn = 0)
        )
    }
}
