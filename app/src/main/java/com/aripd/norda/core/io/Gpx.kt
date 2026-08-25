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
        val waypoints: List<Waypoint>
    )

    fun write(
        trackName: String,
        points: List<TrackPoint>,
        altitudeValid: List<Boolean>,
        waypoints: List<Waypoint>
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
        return Parsed(name, points, waypoints)
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
