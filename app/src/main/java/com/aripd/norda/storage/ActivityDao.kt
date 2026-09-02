package com.aripd.norda.storage

import android.content.ContentValues
import com.aripd.norda.core.track.ActivitySummary
import com.aripd.norda.core.track.ActivityType
import com.aripd.norda.core.track.TrackPoint

/** Identity of an unfinished (end_time NULL) recording — for recovery. */
data class UnfinishedActivity(
    val id: Long,
    val type: ActivityType,
    val startTimeMillis: Long
)

/** Dumb data access layer: only reads and writes, makes no decisions (MVP.md 8.2). */
class ActivityDao(private val helper: AppDatabase) {

    fun startActivity(
        type: ActivityType,
        startTimeMillis: Long,
        startBatteryPct: Int? = null
    ): Long =
        helper.writableDatabase.insertOrThrow("activity", null, ContentValues().apply {
            put("type", type.name)
            put("start_time", startTimeMillis)
            if (startBatteryPct != null) put("start_battery", startBatteryPct)
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
            if (summary.endBatteryPct != null) put("end_battery", summary.endBatteryPct)
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

    /** Points + whether the altitude is valid — for GPX export. */
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

    /** Altitudes of the points that reported a valid altitude, in time order. */
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

    fun summary(activityId: Long): ActivitySummary? =
        helper.readableDatabase.query(
            "activity",
            arrayOf(
                "id", "type", "start_time", "end_time",
                "distance_m", "duration_ms", "elevation_gain_m", "elevation_loss_m",
                "start_battery", "end_battery"
            ),
            "id = ? AND end_time IS NOT NULL", arrayOf(activityId.toString()),
            null, null, null, "1"
        ).use { c ->
            if (!c.moveToFirst()) return null
            ActivitySummary(
                id = c.getLong(0),
                type = ActivityType.fromName(c.getString(1)),
                startTimeMillis = c.getLong(2),
                endTimeMillis = c.getLong(3),
                distanceM = c.getDouble(4),
                durationMillis = c.getLong(5),
                elevationGainM = c.getDouble(6),
                elevationLossM = c.getDouble(7),
                startBatteryPct = if (c.isNull(8)) null else c.getInt(8),
                endBatteryPct = if (c.isNull(9)) null else c.getInt(9)
            )
        }

    fun listFinished(): List<ActivitySummary> =
        helper.readableDatabase.query(
            "activity",
            arrayOf(
                "id", "type", "start_time", "end_time",
                "distance_m", "duration_ms", "elevation_gain_m", "elevation_loss_m",
                "start_battery", "end_battery"
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
                    elevationLossM = c.getDouble(7),
                    startBatteryPct = if (c.isNull(8)) null else c.getInt(8),
                    endBatteryPct = if (c.isNull(9)) null else c.getInt(9)
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
