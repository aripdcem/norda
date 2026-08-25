package com.aripd.norda.core.track

/** Bitmiş bir aktivitenin özeti — geçmiş listesinin satırı. */
data class ActivitySummary(
    val id: Long,
    val type: ActivityType,
    val startTimeMillis: Long,
    val endTimeMillis: Long,
    val distanceM: Double,
    val durationMillis: Long,
    val elevationGainM: Double,
    val elevationLossM: Double
)
