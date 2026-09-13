package com.aripd.norda.core.geo

import kotlin.math.floor

/**
 * Geoid separation: how far mean sea level sits above the WGS84 ellipsoid at
 * a point (docs/MVP.md, 5.7).
 *
 * The GNSS receiver reports height above the **ellipsoid** — a smooth
 * mathematical figure — while maps, signposts, DEMs and every other tool use
 * height above the **geoid**, which is mean sea level. The two differ by up
 * to ±107 m on Earth and by ~37 m in Istanbul: two seaside walks put the
 * median elevation at the water's edge at 39 m (field item Y-1). Subtracting
 * the separation turns the receiver's number into the one a person expects.
 *
 * The data is a table of the EGM96 model at a 1° step, shipped as a resource
 * and read here as decimetres (`tools/geoid/EGM96-PROVENANCE.md`). This class
 * is only the arithmetic over it: bilinear interpolation between the four
 * surrounding nodes, longitude wrapping at the date line, latitude clamped at
 * the poles. A constant offset does not touch elevation gain or loss, and the
 * gradient is so gentle — 1.7 m across the whole Istanbul map pack — that one
 * value per outing would do; it is cheap enough to compute per fix anyway.
 */
class Geoid private constructor(
    val rows: Int,
    val cols: Int,
    private val decimetres: ShortArray
) {

    /** Degrees between two rows: row 0 is +90°, the last row is −90°. */
    private val latStepDeg = 180.0 / (rows - 1)

    /** Degrees between two columns: column 0 is −180°, wrapping eastwards. */
    private val lonStepDeg = 360.0 / cols

    /** Height of mean sea level above the ellipsoid, in metres. */
    fun separationM(latDeg: Double, lonDeg: Double): Double {
        val y = ((90.0 - latDeg) / latStepDeg).coerceIn(0.0, (rows - 1).toDouble())
        val x = ((lonDeg + 180.0).mod(360.0)) / lonStepDeg
        val y0 = floor(y).toInt().coerceIn(0, rows - 1)
        val y1 = (y0 + 1).coerceAtMost(rows - 1)
        val x0 = floor(x).toInt().mod(cols)
        val x1 = (x0 + 1).mod(cols)
        val fy = y - y0
        val fx = x - x0
        val top = at(y0, x0) * (1.0 - fx) + at(y0, x1) * fx
        val bottom = at(y1, x0) * (1.0 - fx) + at(y1, x1) * fx
        return (top * (1.0 - fy) + bottom * fy) / 10.0
    }

    private fun at(row: Int, col: Int): Double = decimetres[row * cols + col].toDouble()

    companion object {
        private const val HEADER_BYTES = 8
        private const val VERSION = 1

        /**
         * Reads the table format described in `tools/geoid/build_geoid_table.py`.
         * Anything that is not that table returns null, and the caller keeps
         * reporting the raw ellipsoid height — a wrong correction would be
         * worse than none.
         */
        fun decode(bytes: ByteArray): Geoid? {
            if (bytes.size < HEADER_BYTES) return null
            if (bytes[0] != 'N'.code.toByte() ||
                bytes[1] != 'G'.code.toByte() ||
                bytes[2] != '1'.code.toByte() ||
                bytes[3].toInt() != VERSION
            ) {
                return null
            }
            val rows = u16(bytes, 4)
            val cols = u16(bytes, 6)
            if (rows < 2 || cols < 2) return null
            val count = rows * cols
            if (bytes.size < HEADER_BYTES + count * 2) return null
            val values = ShortArray(count)
            for (i in 0 until count) {
                val hi = bytes[HEADER_BYTES + i * 2].toInt() shl 8
                val lo = bytes[HEADER_BYTES + i * 2 + 1].toInt() and 0xFF
                values[i] = (hi or lo).toShort()
            }
            return Geoid(rows, cols, values)
        }

        private fun u16(bytes: ByteArray, offset: Int): Int =
            ((bytes[offset].toInt() and 0xFF) shl 8) or (bytes[offset + 1].toInt() and 0xFF)
    }
}
