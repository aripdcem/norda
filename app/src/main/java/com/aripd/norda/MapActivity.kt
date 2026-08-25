package com.aripd.norda

import android.app.Activity
import android.os.Bundle
import com.aripd.norda.map.MapPackages
import com.aripd.norda.map.MapView
import com.aripd.norda.map.TileStore
import com.aripd.norda.storage.ActivityDao
import com.aripd.norda.storage.AppDatabase
import java.io.File

/**
 * Tam ekran harita: bir aktivitenin izi (geçmişten) ya da bir paketin
 * önizlemesi (Haritalar ekranından).
 */
class MapActivity : Activity() {

    private lateinit var mapView: MapView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_map)
        Insets.apply(findViewById(R.id.root))
        mapView = findViewById(R.id.mapView)

        val activityId = intent.getLongExtra(EXTRA_ACTIVITY_ID, -1L)
        if (activityId > 0) {
            showTrack(activityId)
        } else {
            showPackage(intent.getStringExtra(EXTRA_PACKAGE_PATH))
        }
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

    companion object {
        const val EXTRA_ACTIVITY_ID = "activity_id"
        const val EXTRA_PACKAGE_PATH = "package_path"
    }
}
