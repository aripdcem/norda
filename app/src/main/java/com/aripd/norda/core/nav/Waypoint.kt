package com.aripd.norda.core.nav

/** Adlandırılmış nokta (docs/MVP.md, 2.1). Saf model. */
data class Waypoint(
    val id: Long,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double?,
    val createdAtMillis: Long
)
