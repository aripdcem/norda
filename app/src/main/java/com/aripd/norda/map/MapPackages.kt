package com.aripd.norda.map

import android.content.Context
import java.io.File

/**
 * File management for downloaded/imported map packages. Packages are separate
 * from the app data: they can be deleted and re-added, and the activity
 * database is unaffected (docs/MVP.md, 7.3).
 */
object MapPackages {

    fun dir(context: Context): File =
        File(context.filesDir, "maps").apply { mkdirs() }

    fun list(context: Context): List<File> =
        dir(context).listFiles { f -> f.name.endsWith(".mbtiles") }
            ?.sortedBy { it.name }
            ?: emptyList()

    /**
     * Opens the first package covering the given location; if none covers it,
     * the first package. If the location is unknown, the first package.
     */
    fun openBest(context: Context, latDeg: Double?, lonDeg: Double?): TileStore? {
        val files = list(context)
        if (latDeg != null && lonDeg != null) {
            for (f in files) {
                val store = TileStore.open(f) ?: continue
                if (store.contains(latDeg, lonDeg)) return store
                store.close()
            }
        }
        return files.firstOrNull()?.let { TileStore.open(it) }
    }
}
