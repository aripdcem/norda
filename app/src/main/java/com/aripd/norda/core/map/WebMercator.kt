package com.aripd.norda.core.map

import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sinh
import kotlin.math.tan

/**
 * Web Mercator projection and XYZ tile math (docs/MVP.md, 7.1). Pure Kotlin
 * — no drawing, no Android; all of it is tested on the JVM against known
 * fixed points. Tile coordinates are returned as Double: the integer part is
 * the tile number, the fraction the position within the tile.
 */
object WebMercator {

    const val TILE_SIZE = 256

    /** Latitude limit within which the projection is defined. */
    const val MAX_LAT = 85.05112878

    fun worldTiles(zoom: Int): Int = 1 shl zoom

    fun xTile(lonDeg: Double, zoom: Int): Double =
        (lonDeg + 180.0) / 360.0 * worldTiles(zoom)

    fun yTile(latDeg: Double, zoom: Int): Double {
        val lat = Math.toRadians(clampLat(latDeg))
        // At the limit latitude ln(...)/π should be exactly 1; floating point
        // can overshoot it by a hair and would produce a negative tile
        // coordinate — so it is clamped to the world square.
        val unit = ((1.0 - ln(tan(lat) + 1.0 / cos(lat)) / PI) / 2.0).coerceIn(0.0, 1.0)
        return unit * worldTiles(zoom)
    }

    fun lonDeg(xTile: Double, zoom: Int): Double =
        xTile / worldTiles(zoom) * 360.0 - 180.0

    fun latDeg(yTile: Double, zoom: Int): Double =
        Math.toDegrees(atan(sinh(PI * (1.0 - 2.0 * yTile / worldTiles(zoom)))))

    fun clampLat(latDeg: Double): Double =
        latDeg.coerceIn(-MAX_LAT, MAX_LAT)

    /**
     * MBTiles rows are in TMS order: `tile_row = 2^z − 1 − y`
     * (docs/MVP.md, 7.3). Forgetting the conversion mirrors the map vertically.
     */
    fun tmsRow(yTile: Int, zoom: Int): Int = worldTiles(zoom) - 1 - yTile

    /**
     * The largest zoom that fits the given geographic box into the view (in
     * pixels). For breathing room at the edges, the box must not cover more
     * than 90% of it.
     */
    fun fitZoom(
        latMin: Double, lonMin: Double,
        latMax: Double, lonMax: Double,
        viewWidthPx: Int, viewHeightPx: Int,
        minZoom: Int, maxZoom: Int
    ): Int {
        for (z in maxZoom downTo minZoom) {
            val wPx = (xTile(lonMax, z) - xTile(lonMin, z)) * TILE_SIZE
            val hPx = (yTile(latMin, z) - yTile(latMax, z)) * TILE_SIZE
            if (wPx <= viewWidthPx * 0.9 && hPx <= viewHeightPx * 0.9) return z
        }
        return minZoom
    }
}
