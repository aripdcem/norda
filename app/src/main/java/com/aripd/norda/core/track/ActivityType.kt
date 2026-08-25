package com.aripd.norda.core.track

enum class ActivityType {
    WALK,
    RUN;

    companion object {
        fun fromName(name: String?): ActivityType =
            entries.firstOrNull { it.name == name } ?: WALK
    }
}
