package com.aripd.norda.map

import android.graphics.Bitmap
import android.util.LruCache

/** Çözülmüş karo bitmap'leri için bellek önbelleği (LRU). */
class TileCache {

    private val cache = object : LruCache<Long, Bitmap>(maxBytes()) {
        override fun sizeOf(key: Long, value: Bitmap): Int = value.byteCount
    }

    fun get(key: Long): Bitmap? = cache.get(key)
    fun put(key: Long, bitmap: Bitmap) {
        cache.put(key, bitmap)
    }

    companion object {
        /** z<32, x ve y < 2^26 — z20'ye kadar çakışmasız tek anahtar. */
        fun key(zoom: Int, x: Int, y: Int): Long =
            (zoom.toLong() shl 52) or (x.toLong() shl 26) or y.toLong()

        private fun maxBytes(): Int {
            val eighth = (Runtime.getRuntime().maxMemory() / 8L)
            return eighth.coerceAtMost(48L * 1024 * 1024).toInt()
        }
    }
}
