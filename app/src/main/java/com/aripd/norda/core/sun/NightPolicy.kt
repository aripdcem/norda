package com.aripd.norda.core.sun

/**
 * Night mode decision (docs/MVP.md, 3.7): whether the red low-light filter
 * should cover the screens right now.
 *
 * Three modes: AUTO follows the sun — night begins at civil dusk, when the
 * sun is 6° below the horizon (`Sun.isNight`), and needs a rough position;
 * without one the screen stays in daylight, because a filter that appears
 * for no visible reason is worse than one that has to be switched on by
 * hand. ON and OFF are the user's word and ignore the sun.
 */
object NightPolicy {

    enum class Mode {
        AUTO, ON, OFF;

        /** The Home toggle cycles Auto → Night → Day → Auto. */
        fun next(): Mode = values()[(ordinal + 1) % values().size]

        companion object {
            /** Stored by name; anything unknown (or nothing yet) means AUTO. */
            fun parse(name: String?): Mode = values().firstOrNull { it.name == name } ?: AUTO
        }
    }

    fun tint(mode: Mode, latDeg: Double?, lonDeg: Double?, epochMillis: Long): Boolean =
        when (mode) {
            Mode.ON -> true
            Mode.OFF -> false
            Mode.AUTO ->
                latDeg != null && lonDeg != null && Sun.isNight(latDeg, lonDeg, epochMillis)
        }
}
