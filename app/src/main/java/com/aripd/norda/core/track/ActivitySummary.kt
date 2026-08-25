package com.aripd.norda.core.track

/**
 * Bitmiş bir aktivitenin özeti — geçmiş listesinin satırı. Pil seviyeleri
 * kayıt başı/sonu okunur; okunamadıysa null kalır (Battery kuralları).
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
