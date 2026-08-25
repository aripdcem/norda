package com.aripd.norda.storage

import android.content.ContentValues
import com.aripd.norda.core.nav.Waypoint

/** Nokta tablosunun aptal erişim katmanı (MVP.md 8.2): yalnız okur/yazar. */
class WaypointDao(private val helper: AppDatabase) {

    fun insert(
        name: String,
        latitude: Double,
        longitude: Double,
        altitude: Double?,
        createdAtMillis: Long
    ): Long =
        helper.writableDatabase.insertOrThrow("waypoint", null, ContentValues().apply {
            put("name", name)
            put("latitude", latitude)
            put("longitude", longitude)
            if (altitude != null) put("altitude", altitude) else putNull("altitude")
            put("created_at", createdAtMillis)
        })

    fun list(): List<Waypoint> =
        helper.readableDatabase.query(
            "waypoint",
            arrayOf("id", "name", "latitude", "longitude", "altitude", "created_at"),
            null, null, null, null, "created_at ASC"
        ).use { c ->
            val out = ArrayList<Waypoint>(c.count)
            while (c.moveToNext()) {
                out += Waypoint(
                    id = c.getLong(0),
                    name = c.getString(1),
                    latitude = c.getDouble(2),
                    longitude = c.getDouble(3),
                    altitude = if (c.isNull(4)) null else c.getDouble(4),
                    createdAtMillis = c.getLong(5)
                )
            }
            out
        }

    fun names(): List<String> = list().map { it.name }

    fun rename(id: Long, name: String) {
        helper.writableDatabase.update(
            "waypoint",
            ContentValues().apply { put("name", name) },
            "id = ?", arrayOf(id.toString())
        )
    }

    fun delete(id: Long) {
        helper.writableDatabase.delete("waypoint", "id = ?", arrayOf(id.toString()))
    }
}
