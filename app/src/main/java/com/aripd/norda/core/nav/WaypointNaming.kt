package com.aripd.norda.core.nav

/**
 * Varsayılan nokta adları: "Nokta N" — N, listede boş olan İLK numaradır;
 * silinen numaralar yeniden kullanılır (docs/MVP.md, 2.1).
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

    /** Ad temizliği: kırpma + satır sonlarının boşluğa inmesi. Boş kalırsa null. */
    fun sanitize(raw: String): String? {
        val cleaned = raw.replace('\n', ' ').replace('\r', ' ').trim()
        return cleaned.ifEmpty { null }
    }
}
