package com.aripd.norda.core.map

import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sinh
import kotlin.math.tan

/**
 * Web Mercator projeksiyonu ve XYZ karo matematiği (docs/MVP.md, 7.1).
 * Saf Kotlin — çizim yok, Android yok; tamamı bilinen sabit noktalarla
 * JVM'de test edilir. Karo koordinatları Double döner: tam kısmı karo
 * numarası, kesri karo içindeki konumdur.
 */
object WebMercator {

    const val TILE_SIZE = 256

    /** Projeksiyonun tanımlı olduğu enlem sınırı. */
    const val MAX_LAT = 85.05112878

    fun worldTiles(zoom: Int): Int = 1 shl zoom

    fun xTile(lonDeg: Double, zoom: Int): Double =
        (lonDeg + 180.0) / 360.0 * worldTiles(zoom)

    fun yTile(latDeg: Double, zoom: Int): Double {
        val lat = Math.toRadians(clampLat(latDeg))
        // Sınır enleminde ln(...)/π tam 1 olmalıdır; kayan nokta bunu kıl payı
        // aşabilir ve eksi karo koordinatı üretirdi — dünya karesine sabitlenir.
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
     * MBTiles satırı TMS düzenindedir: `tile_row = 2^z − 1 − y`
     * (docs/MVP.md, 7.3). Çevirmeyi unutmak haritayı dikeyde aynalar.
     */
    fun tmsRow(yTile: Int, zoom: Int): Int = worldTiles(zoom) - 1 - yTile

    /**
     * Verilen coğrafi kutuyu, ekrana (piksel) sığdıran en büyük zoom.
     * Kenarlarda nefes payı için kutunun %90'dan fazlasını kaplamaması
     * istenir.
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
