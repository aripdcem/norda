package com.aripd.norda

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import com.aripd.norda.core.io.Gpx
import com.aripd.norda.core.nav.WaypointNaming
import com.aripd.norda.map.MapPackages
import com.aripd.norda.map.MapView
import com.aripd.norda.map.TileStore
import com.aripd.norda.storage.ActivityDao
import com.aripd.norda.storage.AppDatabase
import com.aripd.norda.storage.WaypointDao
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Tam ekran harita: bir aktivitenin izi (geçmişten, GPX dışa aktarmalı) ya da
 * bir paketin önizlemesi. Haritaya uzun basmak nokta ekler (MVP 2.1).
 */
class MapActivity : Activity() {

    private lateinit var mapView: MapView
    private lateinit var waypointDao: WaypointDao
    private var activityId = -1L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_map)
        Insets.apply(findViewById(R.id.root))
        mapView = findViewById(R.id.mapView)
        waypointDao = WaypointDao(AppDatabase.get(this))

        activityId = intent.getLongExtra(EXTRA_ACTIVITY_ID, -1L)
        if (activityId > 0) {
            showTrack(activityId)
        } else {
            showPackage(intent.getStringExtra(EXTRA_PACKAGE_PATH))
        }

        mapView.onLongPressLatLon = { lat, lon -> addWaypointDialog(lat, lon) }

        val exportButton = findViewById<Button>(R.id.gpxExportButton)
        exportButton.visibility = if (activityId > 0) View.VISIBLE else View.GONE
        exportButton.setOnClickListener {
            @Suppress("DEPRECATION")
            startActivityForResult(
                Intent(Intent.ACTION_CREATE_DOCUMENT)
                    .addCategory(Intent.CATEGORY_OPENABLE)
                    .setType("application/gpx+xml")
                    .putExtra(Intent.EXTRA_TITLE, exportFileName()),
                REQUEST_EXPORT
            )
        }
    }

    override fun onResume() {
        super.onResume()
        mapView.setWaypoints(waypointDao.list())
    }

    private fun showTrack(activityId: Long) {
        val points = ActivityDao(AppDatabase.get(this)).pointsFor(activityId)
        mapView.setTrack(points)
        val mid = points.getOrNull(points.size / 2)
        mapView.setStore(MapPackages.openBest(this, mid?.latitude, mid?.longitude))
        if (points.isNotEmpty()) {
            mapView.post { mapView.fitToTrack() }
        }
    }

    private fun showPackage(path: String?) {
        val store = path?.let { TileStore.open(File(it)) }
            ?: MapPackages.openBest(this, null, null)
        val center = store?.boundsCenter()
        val minZoom = store?.minZoom
        mapView.setStore(store)
        if (center != null && minZoom != null) {
            mapView.setZoom(minZoom + 1)
            mapView.setCenter(center.first, center.second)
        }
    }

    private fun addWaypointDialog(lat: Double, lon: Double) {
        val defaultName =
            WaypointNaming.nextDefaultName(waypointDao.names(), getString(R.string.waypoint_prefix))
        val input = EditText(this).apply { setText(defaultName) }
        AlertDialog.Builder(this)
            .setTitle(R.string.add_waypoint_title)
            .setView(input)
            .setPositiveButton(R.string.waypoint_save) { _, _ ->
                val name = WaypointNaming.sanitize(input.text.toString()) ?: defaultName
                waypointDao.insert(name, lat, lon, null, System.currentTimeMillis())
                mapView.setWaypoints(waypointDao.list())
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    // ---- GPX dışa aktarma: tek dosyada iz + tüm noktalar (MVP 10. bölüm) ----

    private fun exportFileName(): String {
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        return "norda-$date.gpx"
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_EXPORT || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        try {
            val dao = ActivityDao(AppDatabase.get(this))
            val detailed = dao.pointsDetailed(activityId)
            val xml = Gpx.write(
                trackName = "Norda ${SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())}",
                points = detailed.map { it.first },
                altitudeValid = detailed.map { it.second },
                waypoints = waypointDao.list(),
                report = buildReport(dao)
            )
            contentResolver.openOutputStream(uri)?.use { out ->
                out.write(xml.toByteArray(Charsets.UTF_8))
            } ?: throw IllegalStateException("çıkış akışı açılamadı")
            Toast.makeText(this, R.string.gpx_exported, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, R.string.gpx_export_failed, Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Tur telemetrisi (F-3): özet + pil DB'den; filtre sayaçları yalnız son
     * kayıt BU aktiviteyse eklenir — sayaçlar aktiviteye değil son kayda ait,
     * eski bir izi dışa aktarırken yanlış sayaç gömülmemeli.
     */
    private fun buildReport(dao: ActivityDao): Gpx.Report? {
        val s = dao.summary(activityId) ?: return null
        val prefs = getSharedPreferences(
            com.aripd.norda.tracking.TrackingService.FILTER_STATS_PREFS, MODE_PRIVATE
        )
        val filter = if (prefs.getLong("activity_id", -1L) == activityId) {
            Gpx.FilterCounts(
                accept = prefs.getInt("accept", 0),
                badAccuracy = prefs.getInt("bad_accuracy", 0),
                jitter = prefs.getInt("jitter", 0),
                teleport = prefs.getInt("teleport", 0),
                nonMonotonic = prefs.getInt("non_monotonic", 0)
            )
        } else null
        return Gpx.Report(
            filter = filter,
            startBatteryPct = s.startBatteryPct,
            endBatteryPct = s.endBatteryPct,
            distanceM = s.distanceM,
            activeMillis = s.durationMillis,
            gainM = s.elevationGainM,
            lossM = s.elevationLossM,
            appVersion = BuildConfig.VERSION_NAME
        )
    }

    companion object {
        const val EXTRA_ACTIVITY_ID = "activity_id"
        const val EXTRA_PACKAGE_PATH = "package_path"
        private const val REQUEST_EXPORT = 3
    }
}
