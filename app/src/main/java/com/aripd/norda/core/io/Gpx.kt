package com.aripd.norda.core.io

import com.aripd.norda.core.nav.Waypoint
import com.aripd.norda.core.track.TrackPoint
import java.io.ByteArrayInputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

/**
 * GPX alışverişi (docs/MVP.md, 10. bölüm): tek dosyada iz (`trk`) ve
 * noktalar (`wpt`). Rakım yalnız geçerli olduğunda yazılır — 0.0 nöbetçi
 * değeri dışarıya sızmaz. Ayrıştırma bozuk girdiyi satır atlayarak tolere
 * eder. Saf JVM (java.xml); Android'e dokunmaz, tamamı testli.
 */
object Gpx {

    class ParsedPoint(val point: TrackPoint, val hasAltitude: Boolean)

    class Parsed(
        val name: String?,
        val points: List<ParsedPoint>,
        val waypoints: List<Waypoint>,
        val report: Report? = null
    )

    /** GPS filtresi karar sayaçları — eşik kalibrasyonunun ham verisi. */
    class FilterCounts(
        val accept: Int,
        val badAccuracy: Int,
        val jitter: Int,
        val teleport: Int,
        val nonMonotonic: Int
    )

    /**
     * Tur telemetrisi (F-3, Saha Turu 1): uygulamanın gördüğü özet, pil ve
     * filtre sayaçları GPX'in İÇİNDE taşınır — saha raporu tek dosyadır,
     * elle not gerekmez. GPX 1.1 `extensions` + kendi ad alanımızla gider;
     * diğer araçlar bloğu yok sayar, içe aktarma bunu veri saymaz
     * (istatistikler her zaman noktalardan yeniden hesaplanır).
     */
    class Report(
        val filter: FilterCounts?,
        val startBatteryPct: Int?,
        val endBatteryPct: Int?,
        val distanceM: Double,
        val activeMillis: Long,
        val gainM: Double,
        val lossM: Double,
        /** Kaydı yazan uygulama sürümü (F-6): dosya kendini tanıtır. */
        val appVersion: String? = null
    )

    private const val NORDA_NS = "https://github.com/aripdcem/norda/gpx/1"

    fun write(
        trackName: String,
        points: List<TrackPoint>,
        altitudeValid: List<Boolean>,
        waypoints: List<Waypoint>,
        report: Report? = null
    ): String {
        require(points.size == altitudeValid.size) { "nokta/rakım listeleri eş boyda olmalı" }
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append("<gpx version=\"1.1\" creator=\"Norda\" ")
        sb.append("xmlns=\"http://www.topografix.com/GPX/1/1\">\n")
        for (w in waypoints) {
            sb.append("<wpt lat=\"").append(w.latitude).append("\" lon=\"")
                .append(w.longitude).append("\">\n")
            w.altitude?.let { sb.append("<ele>").append(it).append("</ele>\n") }
            sb.append("<name>").append(escape(w.name)).append("</name>\n")
            sb.append("</wpt>\n")
        }
        if (points.isNotEmpty()) {
            sb.append("<trk>\n<name>").append(escape(trackName)).append("</name>\n<trkseg>\n")
            for (i in points.indices) {
                val p = points[i]
                sb.append("<trkpt lat=\"").append(p.latitude).append("\" lon=\"")
                    .append(p.longitude).append("\">\n")
                if (altitudeValid[i]) sb.append("<ele>").append(p.altitude).append("</ele>\n")
                if (p.timeMillis > 0) {
                    sb.append("<time>").append(utcFormat().format(Date(p.timeMillis)))
                        .append("</time>\n")
                }
                sb.append("</trkpt>\n")
            }
            sb.append("</trkseg>\n</trk>\n")
        }
        if (report != null) {
            // GPX 1.1 şemasında extensions gpx'in SON çocuğudur.
            sb.append("<extensions>\n<norda:report xmlns:norda=\"")
                .append(NORDA_NS).append("\"")
            report.appVersion?.let { sb.append(" app=\"").append(escape(it)).append("\"") }
            sb.append(">\n")
            sb.append("<norda:summary distanceM=\"").append(report.distanceM)
                .append("\" activeMillis=\"").append(report.activeMillis)
                .append("\" gainM=\"").append(report.gainM)
                .append("\" lossM=\"").append(report.lossM).append("\"/>\n")
            if (report.startBatteryPct != null || report.endBatteryPct != null) {
                sb.append("<norda:battery")
                report.startBatteryPct?.let { sb.append(" startPct=\"").append(it).append("\"") }
                report.endBatteryPct?.let { sb.append(" endPct=\"").append(it).append("\"") }
                sb.append("/>\n")
            }
            report.filter?.let { f ->
                sb.append("<norda:filter accept=\"").append(f.accept)
                    .append("\" badAccuracy=\"").append(f.badAccuracy)
                    .append("\" jitter=\"").append(f.jitter)
                    .append("\" teleport=\"").append(f.teleport)
                    .append("\" nonMonotonic=\"").append(f.nonMonotonic)
                    .append("\"/>\n")
            }
            sb.append("</norda:report>\n</extensions>\n")
        }
        sb.append("</gpx>\n")
        return sb.toString()
    }

    fun parse(xml: String): Parsed {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)))
        val points = ArrayList<ParsedPoint>()
        val waypoints = ArrayList<Waypoint>()
        var name: String? = null

        val trackNames = doc.getElementsByTagName("name")
        for (i in 0 until trackNames.length) {
            val el = trackNames.item(i) as Element
            if (el.parentNode?.nodeName == "trk") {
                name = el.textContent?.trim()
                break
            }
        }
        val trkpts = doc.getElementsByTagName("trkpt")
        for (i in 0 until trkpts.length) {
            val el = trkpts.item(i) as Element
            val lat = el.getAttribute("lat").toDoubleOrNull() ?: continue
            val lon = el.getAttribute("lon").toDoubleOrNull() ?: continue
            val ele = childText(el, "ele")?.toDoubleOrNull()
            points += ParsedPoint(
                TrackPoint(
                    timeMillis = parseTimeMillis(childText(el, "time")),
                    latitude = lat,
                    longitude = lon,
                    altitude = ele ?: 0.0,
                    accuracyM = 0f,
                    speedMps = 0f,
                    bearingDeg = 0f
                ),
                hasAltitude = ele != null
            )
        }
        val wpts = doc.getElementsByTagName("wpt")
        for (i in 0 until wpts.length) {
            val el = wpts.item(i) as Element
            val lat = el.getAttribute("lat").toDoubleOrNull() ?: continue
            val lon = el.getAttribute("lon").toDoubleOrNull() ?: continue
            waypoints += Waypoint(
                id = 0,
                name = childText(el, "name")?.trim().orEmpty(),
                latitude = lat,
                longitude = lon,
                altitude = childText(el, "ele")?.toDoubleOrNull(),
                createdAtMillis = 0
            )
        }
        return Parsed(name, points, waypoints, parseReport(doc))
    }

    /** `norda:report` bloğu — yoksa ya da bozuksa null (tolerans kuralı). */
    private fun parseReport(doc: org.w3c.dom.Document): Report? {
        val reports = doc.getElementsByTagName("norda:report")
        if (reports.length == 0) return null
        val el = reports.item(0) as Element
        val summary = firstChildElement(el, "norda:summary") ?: return null
        return Report(
            filter = parseFilter(firstChildElement(el, "norda:filter")),
            startBatteryPct = firstChildElement(el, "norda:battery")
                ?.getAttribute("startPct")?.toIntOrNull(),
            endBatteryPct = firstChildElement(el, "norda:battery")
                ?.getAttribute("endPct")?.toIntOrNull(),
            distanceM = summary.getAttribute("distanceM").toDoubleOrNull() ?: return null,
            activeMillis = summary.getAttribute("activeMillis").toLongOrNull() ?: return null,
            gainM = summary.getAttribute("gainM").toDoubleOrNull() ?: return null,
            lossM = summary.getAttribute("lossM").toDoubleOrNull() ?: return null,
            appVersion = el.getAttribute("app").takeIf { it.isNotEmpty() }
        )
    }

    private fun parseFilter(el: Element?): FilterCounts? {
        el ?: return null
        return FilterCounts(
            accept = el.getAttribute("accept").toIntOrNull() ?: return null,
            badAccuracy = el.getAttribute("badAccuracy").toIntOrNull() ?: return null,
            jitter = el.getAttribute("jitter").toIntOrNull() ?: return null,
            teleport = el.getAttribute("teleport").toIntOrNull() ?: return null,
            nonMonotonic = el.getAttribute("nonMonotonic").toIntOrNull() ?: return null
        )
    }

    private fun firstChildElement(parent: Element, tag: String): Element? {
        val children = parent.getElementsByTagName(tag)
        return if (children.length == 0) null else children.item(0) as Element
    }

    private fun childText(parent: Element, tag: String): String? {
        val children = parent.getElementsByTagName(tag)
        for (i in 0 until children.length) {
            val el = children.item(i)
            if (el.parentNode === parent) return el.textContent
        }
        return null
    }

    private fun parseTimeMillis(text: String?): Long {
        if (text == null) return 0
        for (pattern in arrayOf(
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"
        )) {
            try {
                val fmt = SimpleDateFormat(pattern, Locale.US)
                fmt.timeZone = TimeZone.getTimeZone("UTC")
                return fmt.parse(text.trim())?.time ?: 0
            } catch (_: Exception) {
                // sıradaki biçim denenir
            }
        }
        return 0
    }

    private fun utcFormat(): SimpleDateFormat =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

    private fun escape(text: String): String = buildString(text.length) {
        for (c in text) {
            when (c) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&apos;")
                else -> append(c)
            }
        }
    }
}
