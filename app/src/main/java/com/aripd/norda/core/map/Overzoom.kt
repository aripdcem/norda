package com.aripd.norda.core.map

/**
 * Zooming past a pack's ceiling (F-13). A pack rendered to z13 shows about
 * 19 m per pixel — fine for orientation, useless for "which side of the
 * street am I on". Instead of shipping four times the tiles per extra level,
 * the map keeps zooming and draws each ceiling tile scaled up: at z+1 a
 * quarter of it, at z+3 a 32-pixel patch stretched to a full tile. Lines get
 * softer with every level, which is the honest cost of not having the data.
 */
object Overzoom {

    /** Levels above the pack ceiling the map may zoom (8× magnification at +3). */
    const val LEVELS = 3

    /** Web Mercator's practical cap; nothing is stored or drawn beyond it. */
    const val MAX_ZOOM = 20

    /**
     * Which ceiling tile to read for tile (z, x, y), and which square of it —
     * [offsetX], [offsetY], [size] in source pixels — covers the requested tile.
     */
    data class Source(
        val zoom: Int, val x: Int, val y: Int,
        val offsetX: Int, val offsetY: Int, val size: Int
    )

    fun ceiling(packMaxZoom: Int): Int = (packMaxZoom + LEVELS).coerceAtMost(MAX_ZOOM)

    fun source(z: Int, x: Int, y: Int, maxZoom: Int, tile: Int): Source {
        require(z >= maxZoom) { "not an overzoom level: z$z under the ceiling z$maxZoom" }
        val shift = z - maxZoom
        val part = tile shr shift
        val mask = (1 shl shift) - 1
        return Source(maxZoom, x shr shift, y shr shift, (x and mask) * part, (y and mask) * part, part)
    }
}
