package com.aripd.norda.map

import android.database.sqlite.SQLiteDatabase
import com.aripd.norda.core.map.WebMercator
import java.io.File

/**
 * Reads a single MBTiles package (docs/MVP.md, 7.3). Standard schema:
 * `metadata(name, value)` + `tiles(zoom_level, tile_column, tile_row,
 * tile_data)`; rows are in TMS order and are converted here. Read-only —
 * producing packages is the CI pipeline's job.
 */
class TileStore private constructor(
    private val db: SQLiteDatabase,
    val file: File
) {

    val metadata: Map<String, String> = buildMap {
        try {
            db.rawQuery("SELECT name, value FROM metadata", null).use { c ->
                while (c.moveToNext()) put(c.getString(0), c.getString(1))
            }
        } catch (_: Exception) {
            // without a metadata table the package can still serve tiles
        }
    }

    val name: String = metadata["name"] ?: file.name
    val minZoom: Int = metadata["minzoom"]?.toIntOrNull() ?: 0
    val maxZoom: Int = metadata["maxzoom"]?.toIntOrNull() ?: 16

    /** metadata.bounds = "left,bottom,right,top" (lon/lat). */
    private val bounds: DoubleArray? = metadata["bounds"]
        ?.split(",")
        ?.mapNotNull { it.trim().toDoubleOrNull() }
        ?.takeIf { it.size == 4 }
        ?.toDoubleArray()

    fun contains(latDeg: Double, lonDeg: Double): Boolean {
        val b = bounds ?: return false
        return lonDeg >= b[0] && latDeg >= b[1] && lonDeg <= b[2] && latDeg <= b[3]
    }

    fun boundsCenter(): Pair<Double, Double>? {
        val b = bounds ?: return null
        return (b[1] + b[3]) / 2.0 to (b[0] + b[2]) / 2.0
    }

    fun tile(zoom: Int, x: Int, y: Int): ByteArray? =
        try {
            db.rawQuery(
                "SELECT tile_data FROM tiles WHERE zoom_level = ? AND tile_column = ? AND tile_row = ?",
                arrayOf(
                    zoom.toString(),
                    x.toString(),
                    WebMercator.tmsRow(y, zoom).toString()
                )
            ).use { c -> if (c.moveToFirst()) c.getBlob(0) else null }
        } catch (_: Exception) {
            null
        }

    fun close() {
        db.close()
    }

    companion object {
        fun open(file: File): TileStore? = try {
            TileStore(
                SQLiteDatabase.openDatabase(
                    file.absolutePath, null, SQLiteDatabase.OPEN_READONLY
                ),
                file
            )
        } catch (_: Exception) {
            null
        }
    }
}
