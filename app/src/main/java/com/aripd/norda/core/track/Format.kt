package com.aripd.norda.core.track

/** Süre ve tempo biçimleme. Saf; yerelden bağımsız (rakam ve iki nokta). */
object Format {

    /** 38:21 ya da 1:02:03 — saat sıfırsa gizlenir. */
    fun duration(durationMillis: Long): String {
        val totalSec = durationMillis / 1000
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }

    /** 374 sn/km → "6:14". */
    fun pace(secPerKm: Double): String {
        val total = secPerKm.toInt()
        return "%d:%02d".format(total / 60, total % 60)
    }
}
