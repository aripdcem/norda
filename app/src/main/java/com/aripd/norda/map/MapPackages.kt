package com.aripd.norda.map

import android.content.Context
import java.io.File

/**
 * İndirilen/içe aktarılan harita paketlerinin dosya yönetimi. Paketler
 * uygulama verisinden ayrıdır: silinip yeniden eklenebilir, aktivite
 * veritabanı etkilenmez (docs/MVP.md, 7.3).
 */
object MapPackages {

    fun dir(context: Context): File =
        File(context.filesDir, "maps").apply { mkdirs() }

    fun list(context: Context): List<File> =
        dir(context).listFiles { f -> f.name.endsWith(".mbtiles") }
            ?.sortedBy { it.name }
            ?: emptyList()

    /**
     * Verilen konumu kapsayan ilk paketi açar; kapsayan yoksa ilk paketi.
     * Konum bilinmiyorsa ilk paket.
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
