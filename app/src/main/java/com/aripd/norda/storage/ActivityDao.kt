package com.aripd.norda.storage

import android.content.ContentValues
import com.aripd.norda.core.track.ActivitySummary
import com.aripd.norda.core.track.ActivityType
import com.aripd.norda.core.track.TrackPoint

/** Yarım kalmış (end_time NULL) bir kaydın kimliği — kurtarma için. */
data class UnfinishedActivity(
    val id: Long,
    val type: ActivityType,
    val startTimeMillis: Long
)

/** Aptal veri erişim katmanı: yalnız okur ve yazar, karar vermez (MVP.md 8.2). */
class ActivityDao(private val helper: AppDatabase) {

    fun startActivity(type: ActivityType, startTimeMillis: Long): Long =
        helper.writableDatabase.insertOrThrow("activity", null, ContentValues().apply {
            put("type", type.name)
            put("start_time", startTimeMillis)
        })

    fun appendPoint(activityId: Long, p: TrackPoint, hasAltitude: Boolean) {
        helper.writableDatabase.insertOrThrow("track_point", null, ContentValues().apply {
            put("activity_id", activityId)
            put("timestamp", p.timeMillis)
            put("latitude", p.latitude)
            put("longitude", p.longitude)
            if (hasAltitude) put("altitude", p.altitude) else putNull("altitude")
            put("accuracy", p.accuracyM)
            put("speed", p.speedMps)
            put("bearing", p.bearingDeg)
        })
    }

    fun finishActivity(summary: ActivitySummary) {
        helper.writableDatabase.update("activity", ContentValues().apply {
            put("end_time", summary.endTimeMillis)
            put("distance_m", summary.distanceM)
            put("duration_ms", summary.durationMillis)
            put("elevation_gain_m", summary.elevationGainM)
            put("elevation_loss_m", summary.elevationLossM)
        }, "id = ?", arrayOf(summary.id.toString()))
    }

    fun unfinishedActivity(): UnfinishedActivity? =
        helper.readableDatabase.query(
            "activity", arrayOf("id", "type", "start_time"),
            "end_time IS NULL", null, null, null, "start_time ASC", "1"
        ).use { c ->
            if (!c.moveToFirst()) return null
            UnfinishedActivity(c.getLong(0), ActivityType.fromName(c.getString(1)), c.getLong(2))
        }

    fun pointsFor(activityId: Long): List<TrackPoint> =
        helper.readableDatabase.query(
            "track_point",
            arrayOf("timestamp", "latitude", "longitude", "altitude", "accuracy", "speed", "bearing"),
            "activity_id = ?", arrayOf(activityId.toString()), null, null, "timestamp ASC"
        ).use { c ->
            val out = ArrayList<TrackPoint>(c.count)
            while (c.moveToNext()) {
                out += TrackPoint(
                    timeMillis = c.getLong(0),
                    latitude = c.getDouble(1),
                    longitude = c.getDouble(2),
                    altitude = if (c.isNull(3)) 0.0 else c.getDouble(3),
                    accuracyM = c.getFloat(4),
                    speedMps = c.getFloat(5),
                    bearingDeg = c.getFloat(6)
                )
            }
            out
        }

    /** Noktalar + rakımın geçerli olup olmadığı bilgisi — GPX dışa aktarma için. */
    fun pointsDetailed(activityId: Long): List<Pair<TrackPoint, Boolean>> =
        helper.readableDatabase.query(
            "track_point",
            arrayOf("timestamp", "latitude", "longitude", "altitude", "accuracy", "speed", "bearing"),
            "activity_id = ?", arrayOf(activityId.toString()), null, null, "timestamp ASC"
        ).use { c ->
            val out = ArrayList<Pair<TrackPoint, Boolean>>(c.count)
            while (c.moveToNext()) {
                val hasAltitude = !c.isNull(3)
                out += TrackPoint(
                    timeMillis = c.getLong(0),
                    latitude = c.getDouble(1),
                    longitude = c.getDouble(2),
                    altitude = if (hasAltitude) c.getDouble(3) else 0.0,
                    accuracyM = c.getFloat(4),
                    speedMps = c.getFloat(5),
                    bearingDeg = c.getFloat(6)
                ) to hasAltitude
            }
            out
        }

    /** Geçerli rakım bildirmiş noktaların rakımları, zaman sırasıyla. */
    fun altitudesFor(activityId: Long): List<Double> =
        helper.readableDatabase.query(
            "track_point", arrayOf("altitude"),
            "activity_id = ? AND altitude IS NOT NULL", arrayOf(activityId.toString()),
            null, null, "timestamp ASC"
        ).use { c ->
            val out = ArrayList<Double>(c.count)
            while (c.moveToNext()) out += c.getDouble(0)
            out
        }

    fun listFinished(): List<ActivitySummary> =
        helper.readableDatabase.query(
            "activity",
            arrayOf(
                "id", "type", "start_time", "end_time",
                "distance_m", "duration_ms", "elevation_gain_m", "elevation_loss_m"
            ),
            "end_time IS NOT NULL", null, null, null, "start_time DESC"
        ).use { c ->
            val out = ArrayList<ActivitySummary>(c.count)
            while (c.moveToNext()) {
                out += ActivitySummary(
                    id = c.getLong(0),
                    type = ActivityType.fromName(c.getString(1)),
                    startTimeMillis = c.getLong(2),
                    endTimeMillis = c.getLong(3),
                    distanceM = c.getDouble(4),
                    durationMillis = c.getLong(5),
                    elevationGainM = c.getDouble(6),
                    elevationLossM = c.getDouble(7)
                )
            }
            out
        }

    fun deleteActivity(activityId: Long) {
        val db = helper.writableDatabase
        db.beginTransaction()
        try {
            db.delete("track_point", "activity_id = ?", arrayOf(activityId.toString()))
            db.delete("activity", "id = ?", arrayOf(activityId.toString()))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }
}
