package com.aripd.norda.map

import android.graphics.Bitmap
import android.util.LruCache

/** In-memory cache (LRU) for decoded tile bitmaps. */
class TileCache {

    private val cache = object : LruCache<Long, Bitmap>(maxBytes()) {
        override fun sizeOf(key: Long, value: Bitmap): Int = value.byteCount
    }

    fun get(key: Long): Bitmap? = cache.get(key)
    fun put(key: Long, bitmap: Bitmap) {
        cache.put(key, bitmap)
    }

    companion object {
        /** z<32, x and y < 2^26 — a single collision-free key up to z20. */
        fun key(zoom: Int, x: Int, y: Int): Long =
            (zoom.toLong() shl 52) or (x.toLong() shl 26) or y.toLong()

        private fun maxBytes(): Int {
            val eighth = (Runtime.getRuntime().maxMemory() / 8L)
            return eighth.coerceAtMost(48L * 1024 * 1024).toInt()
        }
    }
}
