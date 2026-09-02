package com.aripd.norda.core.track

/**
 * Summary of a finished activity — a row of the history list. Battery levels
 * are read at recording start/end; they stay null if unreadable (Battery
 * rules).
 */
data class ActivitySummary(
    val id: Long,
    val type: ActivityType,
    val startTimeMillis: Long,
    val endTimeMillis: Long,
    val distanceM: Double,
    val durationMillis: Long,
    val elevationGainM: Double,
    val elevationLossM: Double,
    val startBatteryPct: Int? = null,
    val endBatteryPct: Int? = null
)
