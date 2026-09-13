package com.aripd.norda.geo

import android.content.Context
import com.aripd.norda.R
import com.aripd.norda.core.geo.Geoid

/**
 * The shipped geoid table (docs/MVP.md, 5.7): loaded once from
 * `res/raw/geoid_egm96_1deg.bin`, then it is pure arithmetic (`core/geo/Geoid`).
 *
 * The database always keeps what the receiver said, height above the WGS84
 * ellipsoid. The conversion happens at the edges where a human or another
 * tool reads the number: the Diagnostics screen and GPX. That way elevation
 * gain and loss are untouched, past recordings gain the correction for free,
 * and the raw record stays raw.
 */
object Geoids {

    @Volatile
    private var table: Geoid? = null

    @Volatile
    private var attempted = false

    /** The table, or null if this build could not read it (then nothing is corrected). */
    fun table(context: Context): Geoid? {
        table?.let { return it }
        if (attempted) return null
        synchronized(this) {
            table?.let { return it }
            attempted = true
            val loaded = try {
                context.resources.openRawResource(R.raw.geoid_egm96_1deg)
                    .use { Geoid.decode(it.readBytes()) }
            } catch (e: Exception) {
                null
            }
            table = loaded
            return loaded
        }
    }

    /** Height of mean sea level above the ellipsoid; null if there is no table. */
    fun separationM(context: Context, latDeg: Double, lonDeg: Double): Double? =
        table(context)?.separationM(latDeg, lonDeg)

    /** Receiver height (ellipsoid) → height above mean sea level. */
    fun toMsl(context: Context, latDeg: Double, lonDeg: Double, ellipsoidM: Double): Double =
        ellipsoidM - (separationM(context, latDeg, lonDeg) ?: 0.0)

    /** Height above mean sea level → receiver height (ellipsoid), for GPX import. */
    fun toEllipsoid(context: Context, latDeg: Double, lonDeg: Double, mslM: Double): Double =
        mslM + (separationM(context, latDeg, lonDeg) ?: 0.0)
}
