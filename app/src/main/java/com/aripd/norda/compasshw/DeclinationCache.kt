package com.aripd.norda.compasshw

import android.content.Context
import android.hardware.GeomagneticField
import com.aripd.norda.core.geo.Geo

/**
 * Manyetik sapma ve beklenen alan şiddeti (docs/MVP.md, 6.3). Sapma yüzlerce
 * kilometrede bir derece oynar; 1 km yol alınmadan yeniden hesaplanmaz ve
 * kalıcı saklanır — sonraki açılışta gerçek kuzey anında hazırdır.
 */
object DeclinationCache {

    class Field(val declinationDeg: Float, val expectedMicroTesla: Double)

    private const val PREFS = "compass"
    private const val KEY_DECL = "declination"
    private const val KEY_FIELD = "field_micro_tesla"
    private const val KEY_LAT = "lat"
    private const val KEY_LON = "lon"
    private const val REFRESH_DISTANCE_M = 1_000.0

    /** Son bilinen değerler — konum henüz yokken açılış için. */
    fun cached(context: Context): Field? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.contains(KEY_DECL)) return null
        return Field(
            prefs.getFloat(KEY_DECL, 0f),
            prefs.getFloat(KEY_FIELD, 0f).toDouble()
        )
    }

    /** Konum bilinince: gerekiyorsa yeniden hesaplar, saklar ve döner. */
    fun update(
        context: Context,
        latDeg: Double,
        lonDeg: Double,
        altitudeM: Double,
        nowMillis: Long
    ): Field {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.contains(KEY_DECL)) {
            val movedM = Geo.distanceMeters(
                prefs.getFloat(KEY_LAT, 0f).toDouble(),
                prefs.getFloat(KEY_LON, 0f).toDouble(),
                latDeg, lonDeg
            )
            if (movedM < REFRESH_DISTANCE_M) {
                return Field(
                    prefs.getFloat(KEY_DECL, 0f),
                    prefs.getFloat(KEY_FIELD, 0f).toDouble()
                )
            }
        }
        val geo = GeomagneticField(
            latDeg.toFloat(), lonDeg.toFloat(), altitudeM.toFloat(), nowMillis
        )
        // getFieldStrength nanoTesla döner; sensör µT verir.
        val field = Field(geo.declination, geo.fieldStrength / 1000.0)
        prefs.edit()
            .putFloat(KEY_DECL, field.declinationDeg)
            .putFloat(KEY_FIELD, field.expectedMicroTesla.toFloat())
            .putFloat(KEY_LAT, latDeg.toFloat())
            .putFloat(KEY_LON, lonDeg.toFloat())
            .apply()
        return field
    }
}
