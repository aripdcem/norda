package com.aripd.norda.core.track

/** Duration and pace formatting. Pure; locale-independent (digits and colons). */
object Format {

    /** 38:21 or 1:02:03 — hours are hidden when zero. */
    fun duration(durationMillis: Long): String {
        val totalSec = durationMillis / 1000
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }

    /** 374 s/km → "6:14". */
    fun pace(secPerKm: Double): String {
        val total = secPerKm.toInt()
        return "%d:%02d".format(total / 60, total % 60)
    }
}
