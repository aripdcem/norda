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

    /**
     * How hard the filter bites (F-16). Deep red protects dark adaptation
     * best and is the hardest to read: red focuses differently from green in
     * the eye, and an astigmatic eye blurs it further — the first field night
     * reported exactly that. So the strength is the user's choice and the
     * default is the middle one, amber, rather than the deepest red.
     */
    enum class Strength {
        /** Warm white: blue removed, green kept. Easiest to read. */
        SOFT,

        /** Amber. The default: a real night filter that stays legible. */
        MEDIUM,

        /** Deep red. Best for dark adaptation, hardest to focus. */
        STRONG;

        /** Long-pressing the Home line walks through the three. */
        fun next(): Strength = values()[(ordinal + 1) % values().size]

        companion object {
            /** Stored by name; anything unknown (or nothing yet) means MEDIUM. */
            fun parse(name: String?): Strength = values().firstOrNull { it.name == name } ?: MEDIUM
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
