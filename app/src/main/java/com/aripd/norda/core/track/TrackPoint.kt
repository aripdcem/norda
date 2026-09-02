package com.aripd.norda.core.track

/** A single recorded GPS fix. Pure model — no Android dependency. */
data class TrackPoint(
    val timeMillis: Long,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double,
    val accuracyM: Float,
    val speedMps: Float,
    val bearingDeg: Float
)
