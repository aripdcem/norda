package com.aripd.norda.core.geo

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Geoid separation (Y-1): the receiver reports height above the WGS84
 * ellipsoid, maps and signposts use height above mean sea level, and in
 * Istanbul the two differ by ~37 m. The table is data, so the arithmetic over
 * it is tested on its own and the shipped table is spot-checked against known
 * EGM96 values.
 */
class GeoidTest {

    /**
     * A 3×4 grid: rows at +90°, 0°, −90°, columns at −180°, −90°, 0°, +90°.
     * Values are decimetres, so 10 reads as 1.0 m.
     */
    private fun sample(): Geoid = Geoid.decode(
        encode(
            rows = 3, cols = 4,
            decimetres = shortArrayOf(
                10, 20, 30, 40,
                50, 60, 70, 80,
                90, 100, 110, 120
            )
        )
    )!!

    private fun encode(rows: Int, cols: Int, decimetres: ShortArray): ByteArray {
        val out = ByteArray(8 + decimetres.size * 2)
        out[0] = 'N'.code.toByte()
        out[1] = 'G'.code.toByte()
        out[2] = '1'.code.toByte()
        out[3] = 1
        out[4] = (rows shr 8).toByte()
        out[5] = rows.toByte()
        out[6] = (cols shr 8).toByte()
        out[7] = cols.toByte()
        for (i in decimetres.indices) {
            out[8 + i * 2] = (decimetres[i].toInt() shr 8).toByte()
            out[9 + i * 2] = decimetres[i].toByte()
        }
        return out
    }

    @Test
    fun `nodes come back exactly`() {
        val g = sample()
        assertEquals(1.0, g.separationM(90.0, -180.0), 1e-9)
        assertEquals(7.0, g.separationM(0.0, 0.0), 1e-9)
        assertEquals(11.0, g.separationM(-90.0, 0.0), 1e-9)
    }

    @Test
    fun `between nodes it interpolates in both directions`() {
        val g = sample()
        assertEquals(6.5, g.separationM(0.0, -45.0), 1e-9)
        assertEquals(5.0, g.separationM(45.0, 0.0), 1e-9)
        assertEquals(4.5, g.separationM(45.0, -45.0), 1e-9)
    }

    @Test
    fun `longitude wraps around the date line`() {
        val g = sample()
        // +180° is the same meridian as −180°: the first column.
        assertEquals(5.0, g.separationM(0.0, 180.0), 1e-9)
        // Halfway between the last column (+90° → 8.0) and the wrap (5.0).
        assertEquals(6.5, g.separationM(0.0, 135.0), 1e-9)
    }

    @Test
    fun `latitudes outside the grid are clamped instead of crashing`() {
        val g = sample()
        assertEquals(1.0, g.separationM(95.0, -180.0), 1e-9)
        assertEquals(11.0, g.separationM(-95.0, 0.0), 1e-9)
    }

    @Test
    fun `a table that is not one is refused`() {
        assertNull("truncated header", Geoid.decode(ByteArray(4)))
        val good = encode(1, 1, shortArrayOf(0))
        val badMagic = good.copyOf().also { it[0] = 'X'.code.toByte() }
        assertNull("wrong magic", Geoid.decode(badMagic))
        val badVersion = good.copyOf().also { it[3] = 9 }
        assertNull("unknown version", Geoid.decode(badVersion))
        assertNull("short body", Geoid.decode(encode(3, 4, shortArrayOf(1, 2, 3))))
    }

    /**
     * The shipped table (`app/src/main/res/raw/geoid_egm96_1deg.bin`) against
     * published EGM96 values: 17.2 m on the equator at the prime meridian,
     * ~37.4 m in Istanbul. Skipped where the resource file is not reachable
     * from the working directory (the standalone JVM harness).
     */
    @Test
    fun `the shipped table matches known EGM96 values`() {
        val file = listOf(
            "src/main/res/raw/geoid_egm96_1deg.bin",
            "app/src/main/res/raw/geoid_egm96_1deg.bin",
            "../app/src/main/res/raw/geoid_egm96_1deg.bin"
        ).map(::File).firstOrNull { it.isFile }
        assumeTrue("geoid table not reachable from ${File(".").absolutePath}", file != null)
        val g = Geoid.decode(file!!.readBytes())
        assertNotNull("the shipped table did not decode", g)
        g!!
        assertEquals(181, g.rows)
        assertEquals(360, g.cols)
        assertEquals(17.2, g.separationM(0.0, 0.0), 0.5)
        assertEquals(37.4, g.separationM(41.0082, 28.9784), 0.5)
        // The seaside walk of Sept 13: a median elevation of 39 m became 1.5 m.
        assertEquals(37.5, g.separationM(40.923, 29.120), 0.5)
        // Two landmarks of the model, which also catch a flipped axis: the
        // Indian Ocean geoid low and the high over Iceland.
        assertEquals(-83.3, g.separationM(-8.0, 78.0), 2.0)
        assertEquals(66.3, g.separationM(64.0, -22.0), 2.0)
    }
}
