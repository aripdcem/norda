package com.aripd.norda.core.map

/**
 * Why the map is empty (docs/MVP.md, 7.4). A map that shows the procedural
 * grid looks the same whatever the reason — no package installed, a package
 * that ends before this area, or a tile the package never got. The field
 * cannot tell them apart, so the screen has to say which one it is; the same
 * discipline as the GPS status line (F-4, F-5, F-9).
 */
object MapCoverage {

    enum class State {
        /** Tiles are on screen, or the view has not drawn yet. */
        OK,

        /** No package installed at all — Maps screen, download or import. */
        NO_PACKAGE,

        /** Packages exist, but none of them covers this position. */
        OUT_OF_BOUNDS,

        /** A package covers the position, yet not one tile came back. */
        MISSING_TILES
    }

    /**
     * [packages] installed package count, [covered] whether the chosen package
     * claims this position, [tilesRequested] tiles the last frame needed and
     * [tilesDrawn] how many of them were actually painted. A single painted
     * tile is enough for OK: the rest are decoding, and a half-drawn map is
     * not a fault to report.
     */
    fun state(packages: Int, covered: Boolean, tilesRequested: Int, tilesDrawn: Int): State = when {
        tilesRequested <= 0 -> State.OK
        tilesDrawn > 0 -> State.OK
        packages <= 0 -> State.NO_PACKAGE
        !covered -> State.OUT_OF_BOUNDS
        else -> State.MISSING_TILES
    }
}
