package com.aripd.norda.core.nav

/**
 * Default waypoint names: "Point N" — N is the FIRST number free in the list;
 * deleted numbers are reused (docs/MVP.md, 2.1).
 */
object WaypointNaming {

    fun nextDefaultName(existingNames: List<String>, prefix: String): String {
        val used = HashSet<Int>()
        val head = "$prefix "
        for (name in existingNames) {
            if (name.startsWith(head)) {
                name.removePrefix(head).trim().toIntOrNull()?.let { used += it }
            }
        }
        var n = 1
        while (n in used) n++
        return "$prefix $n"
    }

    /** Name cleanup: trimming + line breaks collapsed to spaces. Null if left empty. */
    fun sanitize(raw: String): String? {
        val cleaned = raw.replace('\n', ' ').replace('\r', ' ').trim()
        return cleaned.ifEmpty { null }
    }
}
