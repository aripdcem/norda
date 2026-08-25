package com.aripd.norda.core.track

/** Kaydedilmiş tek bir GPS fix'i. Saf model — Android bağımlılığı yok. */
data class TrackPoint(
    val timeMillis: Long,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double,
    val accuracyM: Float,
    val speedMps: Float,
    val bearingDeg: Float
)
