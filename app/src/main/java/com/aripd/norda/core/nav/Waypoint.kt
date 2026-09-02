package com.aripd.norda.core.nav

/** A named point (docs/MVP.md, 2.1). Pure model. */
data class Waypoint(
    val id: Long,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double?,
    val createdAtMillis: Long
)
