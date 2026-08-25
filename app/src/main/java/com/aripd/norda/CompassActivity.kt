package com.aripd.norda

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.Surface
import android.widget.TextView
import com.aripd.norda.compasshw.DeclinationCache
import com.aripd.norda.compasshw.HeadingProvider
import com.aripd.norda.core.geo.Geo
import com.aripd.norda.core.heading.DisturbanceDetector
import com.aripd.norda.core.nav.ReturnToStart
import com.aripd.norda.core.track.Format
import com.aripd.norda.core.track.Stats
import com.aripd.norda.core.track.TrackPoint
import com.aripd.norda.core.nav.Waypoint
import com.aripd.norda.storage.ActivityDao
import com.aripd.norda.storage.AppDatabase
import com.aripd.norda.storage.WaypointDao
import com.aripd.norda.tracking.TrackingService
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Pusula ve Return to Start (docs/MVP.md, 3.3 + 3.6). Device heading ile
 * target bearing ayrı kavramlardır: kadran cihazın baktığı yönü, altın
 * baklava başlangıcın yönünü gösterir; alt satır ne kadar dönüleceğini söyler.
 * Durum önceliği: manyetik bozulma > kalibrasyon (bozulma görsel ipucu vermez).
 */
class CompassActivity : Activity(), LocationListener {

    private lateinit var compassView: CompassView
    private lateinit var headingText: TextView
    private lateinit var headingLabel: TextView
    private lateinit var statusText: TextView
    private lateinit var targetText: TextView
    private lateinit var waypointText: TextView
    private var waypoints: List<Waypoint> = emptyList()

    private lateinit var locationManager: LocationManager
    private lateinit var headingProvider: HeadingProvider
    private val disturbance = DisturbanceDetector()

    private var field: DeclinationCache.Field? = null
    private var startPoint: TrackPoint? = null
    private var lastFix: Location? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_compass)
        Insets.apply(findViewById(R.id.root))

        compassView = findViewById(R.id.compassView)
        headingText = findViewById(R.id.compassHeading)
        headingLabel = findViewById(R.id.compassLabel)
        statusText = findViewById(R.id.compassStatus)
        targetText = findViewById(R.id.compassTarget)
        waypointText = findViewById(R.id.compassWaypoints)
        waypointText.setOnClickListener {
            startActivity(android.content.Intent(this, WaypointsActivity::class.java))
        }

        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        headingProvider = HeadingProvider(this, ::displayRotation, ::onHeading)
        if (!headingProvider.hasRotationVector) {
            statusText.text = getString(R.string.sensor_missing)
        }
    }

    private fun displayRotation(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            display?.rotation ?: Surface.ROTATION_0
        } else {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.rotation
        }

    override fun onResume() {
        super.onResume()
        field = DeclinationCache.cached(this)
        startPoint = resolveStartPoint()
        waypoints = WaypointDao(AppDatabase.get(this)).list()
        headingProvider.start()
        startLocationUpdates()
    }

    // Pil kuralı: ekran görünmüyorken ne sensör ne konum dinlenir.
    override fun onPause() {
        super.onPause()
        headingProvider.stop()
        locationManager.removeUpdates(this)
    }

    /** Aktif kaydın ilk noktası; kayıt yoksa en son aktivitenin başlangıcı. */
    private fun resolveStartPoint(): TrackPoint? {
        TrackingService.session?.points?.firstOrNull()?.let { return it }
        val dao = ActivityDao(AppDatabase.get(this))
        val last = dao.listFinished().firstOrNull() ?: return null
        return dao.pointsFor(last.id).firstOrNull()
    }

    private fun startLocationUpdates() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, this)
        }
        if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 5000L, 0f, this)
        }
    }

    override fun onLocationChanged(location: Location) {
        lastFix = location
        field = DeclinationCache.update(
            this, location.latitude, location.longitude,
            location.altitude, System.currentTimeMillis()
        )
    }

    @Deprecated("Framework çağırmaya devam ediyor; API 29 öncesi için gerekli")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
    override fun onProviderEnabled(provider: String) = Unit
    override fun onProviderDisabled(provider: String) = Unit

    private fun onHeading(magneticDeg: Double, accuracyLow: Boolean, fieldMicroTesla: Double?) {
        val declination = field?.declinationDeg?.toDouble()
        val heading =
            if (declination != null) Geo.normalizeDeg(magneticDeg + declination) else magneticDeg
        compassView.setHeading(heading)
        headingText.text = getString(R.string.heading_degrees, heading.roundToInt() % 360)
        headingLabel.text = getString(
            if (declination != null) R.string.heading_label_true
            else R.string.heading_label_magnetic
        )
        renderStatus(accuracyLow, fieldMicroTesla)
        renderTarget(heading)
        renderWaypoints()
    }

    /** En yakın iki nokta: kadranda içi boş baklava en yakını gösterir. */
    private fun renderWaypoints() {
        val fix = lastFix
        if (fix == null || waypoints.isEmpty()) {
            compassView.setWaypointBearing(null)
            waypointText.text = getString(
                if (waypoints.isEmpty()) R.string.waypoints_hint else R.string.rts_waiting_fix
            )
            return
        }
        val nearest = waypoints
            .map { w ->
                Triple(
                    w,
                    Geo.distanceMeters(fix.latitude, fix.longitude, w.latitude, w.longitude),
                    Geo.initialBearingDeg(fix.latitude, fix.longitude, w.latitude, w.longitude)
                )
            }
            .sortedBy { it.second }
            .take(2)
        compassView.setWaypointBearing(nearest.firstOrNull()?.third)
        waypointText.text = nearest.joinToString("   ") { (w, distance, bearing) ->
            val distanceText =
                if (distance < 1000) getString(R.string.distance_m, distance.toInt())
                else getString(R.string.distance_km, distance / 1000.0)
            getString(R.string.wpt_line, w.name, bearing.roundToInt() % 360, distanceText)
        }
    }

    private fun renderStatus(accuracyLow: Boolean, fieldMicroTesla: Double?) {
        val expected = field?.expectedMicroTesla ?: 0.0
        val disturbed = fieldMicroTesla != null &&
            disturbance.onSample(fieldMicroTesla, expected, SystemClock.elapsedRealtime())
        statusText.text = when {
            disturbed -> getString(
                R.string.status_disturbance,
                fieldMicroTesla!!.roundToInt(), expected.roundToInt()
            )
            accuracyLow -> getString(R.string.sensor_calibration_needed)
            else -> ""
        }
    }

    private fun renderTarget(headingDeg: Double) {
        val start = startPoint
        val fix = lastFix
        if (start == null || fix == null) {
            compassView.setTargetBearing(null)
            targetText.text = getString(
                if (start == null) R.string.rts_no_start else R.string.rts_waiting_fix
            )
            return
        }
        val guidance = ReturnToStart.guidance(
            fix.latitude, fix.longitude, start.latitude, start.longitude, currentPace()
        )
        compassView.setTargetBearing(guidance.bearingDeg)
        val distance =
            if (guidance.distanceM < 1000) getString(R.string.distance_m, guidance.distanceM.toInt())
            else getString(R.string.distance_km, guidance.distanceM / 1000.0)
        var line = getString(R.string.rts_line, guidance.bearingDeg.roundToInt() % 360, distance)
        guidance.etaMillis?.let { line += getString(R.string.rts_eta, Format.duration(it)) }
        val relative = ReturnToStart.relativeAngleDeg(headingDeg, guidance.bearingDeg)
        val steer = when {
            abs(relative) <= 5.0 -> getString(R.string.rts_on_course)
            relative > 0 -> getString(R.string.rts_turn_right, relative.roundToInt())
            else -> getString(R.string.rts_turn_left, (-relative).roundToInt())
        }
        targetText.text = "$line\n$steer"
    }

    private fun currentPace(): Double? =
        TrackingService.session?.let { session ->
            session.currentPaceSecPerKm() ?: Stats.avgPaceSecPerKm(
                session.distanceM,
                session.durationMillis(SystemClock.elapsedRealtime())
            )
        }
}
