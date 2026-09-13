package com.aripd.norda

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.GnssStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.Surface
import android.view.View
import android.widget.Button
import android.widget.TextView
import com.aripd.norda.core.geo.Geo
import com.aripd.norda.core.track.Battery
import com.aripd.norda.core.track.GpsFilter
import com.aripd.norda.map.MapPackages
import com.aripd.norda.map.TileStore
import com.aripd.norda.tracking.TrackingService

/**
 * Phase 1's sensor diagnostics screen: raw location and heading data. Kept
 * reachable from Home for field verification. Since Phase 8 it also shows the
 * filter counters while a recording is running — the raw data for threshold
 * calibration.
 */
class DiagnosticsActivity : Activity(), LocationListener, SensorEventListener {

    private lateinit var locationManager: LocationManager
    private lateinit var sensorManager: SensorManager
    private var rotationVector: Sensor? = null

    private lateinit var headingDegrees: TextView
    private lateinit var headingCardinal: TextView
    private lateinit var sensorStatus: TextView
    private lateinit var locationText: TextView
    private lateinit var startText: TextView
    private lateinit var filterLabel: TextView
    private lateinit var filterText: TextView
    private lateinit var batteryText: TextView
    private lateinit var mapText: TextView
    private lateinit var permissionText: TextView
    private lateinit var permissionButton: Button

    private val rotationMatrix = FloatArray(9)
    private val remappedMatrix = FloatArray(9)
    private val orientation = FloatArray(3)

    private var lastFix: Location? = null
    private var lastFixElapsedMs = 0L
    private var startFix: Location? = null

    // Satellite visibility (F-9): what the chip sees is known even without a fix.
    private var gnssCallback: GnssStatus.Callback? = null
    private var satsSeen = 0
    private var satsUsed = 0

    private val handler = Handler(Looper.getMainLooper())
    private val ageTicker = object : Runnable {
        override fun run() {
            renderLocation()
            renderFilter()
            handler.postDelayed(this, 1000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_diagnostics)
        Insets.apply(findViewById(R.id.root))

        headingDegrees = findViewById(R.id.headingDegrees)
        headingCardinal = findViewById(R.id.headingCardinal)
        sensorStatus = findViewById(R.id.sensorStatus)
        locationText = findViewById(R.id.locationText)
        startText = findViewById(R.id.startText)
        filterLabel = findViewById(R.id.filterLabel)
        filterText = findViewById(R.id.filterText)
        batteryText = findViewById(R.id.batteryText)
        mapText = findViewById(R.id.mapText)
        permissionText = findViewById(R.id.permissionText)
        permissionButton = findViewById(R.id.permissionButton)

        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        rotationVector = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        if (rotationVector == null) {
            sensorStatus.text = getString(R.string.sensor_missing)
        }

        permissionButton.setOnClickListener {
            if (shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) ||
                !permissionAskedBefore
            ) {
                requestLocation()
            } else {
                startActivity(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", packageName, null)
                    )
                )
            }
        }

        if (!hasLocationPermission()) requestLocation()
    }

    override fun onResume() {
        super.onResume()
        rotationVector?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
        startLocationUpdates()
        renderPermission()
        renderLocation()
        renderStart()
        renderFilter()
        renderBattery()
        renderMapPackages()
        handler.post(ageTicker)
    }

    // Battery rule: neither sensors nor location are listened to while the
    // screen is not visible.
    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
        locationManager.removeUpdates(this)
        gnssCallback?.let { locationManager.unregisterGnssStatusCallback(it) }
        gnssCallback = null
        handler.removeCallbacks(ageTicker)
    }

    // ---- Permission ----

    private var permissionAskedBefore = false

    private fun hasLocationPermission(): Boolean =
        checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private fun requestLocation() {
        permissionAskedBefore = true
        requestPermissions(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ),
            REQUEST_LOCATION
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_LOCATION) {
            renderPermission()
            startLocationUpdates()
        }
    }

    private fun renderPermission() {
        if (hasLocationPermission()) {
            permissionText.text = getString(R.string.permission_granted)
            permissionButton.visibility = View.GONE
        } else {
            val deniedForever = permissionAskedBefore &&
                !shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)
            permissionText.text = getString(
                if (deniedForever) R.string.permission_denied_forever
                else R.string.permission_needed
            )
            permissionButton.text = getString(
                if (deniedForever) R.string.open_settings else R.string.grant_permission
            )
            permissionButton.visibility = View.VISIBLE
        }
    }

    /**
     * Battery measurement sources (B-1): the level the system reports, and
     * whether this device serves the µAh charge counter at all. The counter is
     * what gives a half-hour outing a real consumption figure; if it is
     * missing here, History falls back to whole percent and a 0% row is the
     * gauge sitting still, not the app using no power.
     */
    private fun renderBattery() {
        val manager = getSystemService(BATTERY_SERVICE) as BatteryManager
        val pct = manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            .takeIf { it in 0..100 }
        val chargeUah = manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
            .takeIf { it > 0 }?.toLong()
        val full = Battery.fullChargeUah(chargeUah, pct)
        batteryText.text = when {
            pct == null -> getString(R.string.battery_unknown)
            chargeUah == null -> getString(R.string.battery_no_counter, pct)
            else -> getString(
                R.string.battery_counter,
                pct,
                (chargeUah / 1000L).toInt(),
                ((full ?: chargeUah) / 1000L).toInt()
            )
        }
    }

    /**
     * Installed map packages with their zoom range and bounds (F-15). "The map
     * does not show" is answered here: whether a package is installed at all,
     * how far up its zoom goes, and whether its bounds hold the area walked.
     */
    private fun renderMapPackages() {
        val files = MapPackages.list(this)
        if (files.isEmpty()) {
            mapText.setText(R.string.map_none)
            return
        }
        mapText.text = files.joinToString("\n") { file ->
            val store = TileStore.open(file)
            val line = if (store == null) {
                getString(R.string.map_pack_unreadable, file.name)
            } else {
                getString(
                    R.string.map_pack_line,
                    store.name,
                    store.minZoom,
                    store.maxZoom,
                    file.length() / (1024.0 * 1024.0),
                    store.metadata["bounds"] ?: getString(R.string.placeholder_dash)
                )
            }
            store?.close()
            line
        }
    }

    // ---- Location ----

    private fun startLocationUpdates() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        // Distance filter deliberately 0 (the trap documented in docs/MVP.md, 5.3).
        // We subscribe to the provider even while it is disabled (same pattern
        // as the 0.8.0 service fix); the guard is only for a provider that
        // does not exist.
        if (LocationManager.GPS_PROVIDER in locationManager.allProviders) {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, this)
        }
        if (LocationManager.NETWORK_PROVIDER in locationManager.allProviders) {
            locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 5000L, 0f, this)
        }
        val callback = object : GnssStatus.Callback() {
            override fun onSatelliteStatusChanged(status: GnssStatus) {
                satsSeen = status.satelliteCount
                var used = 0
                for (i in 0 until status.satelliteCount) {
                    if (status.usedInFix(i)) used++
                }
                satsUsed = used
                renderLocation()
            }
        }
        gnssCallback = callback
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            locationManager.registerGnssStatusCallback(mainExecutor, callback)
        } else {
            @Suppress("DEPRECATION")
            locationManager.registerGnssStatusCallback(callback, handler)
        }
    }

    override fun onLocationChanged(location: Location) {
        lastFix = location
        lastFixElapsedMs = SystemClock.elapsedRealtime()
        if (startFix == null && location.accuracy > 0f && location.accuracy <= 30f) {
            startFix = location
        }
        renderLocation()
        renderStart()
    }

    @Deprecated("The framework still calls this; needed for API < 29")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
    override fun onProviderEnabled(provider: String) = Unit
    override fun onProviderDisabled(provider: String) {
        renderLocation()
    }

    private fun renderLocation() {
        val fix = lastFix
        if (!hasLocationPermission()) {
            locationText.text = getString(R.string.location_needs_permission)
            return
        }
        // The satellite line is independent of the fix: what the chip sees is
        // visible in every case (F-9). 0/0 = no sky; N seen but 0 used = no lock.
        val satellites = getString(R.string.diag_satellites, satsUsed, satsSeen)
        if (fix == null) {
            locationText.text = getString(R.string.location_waiting) + "\n" + satellites
            return
        }
        val ageSec = ((SystemClock.elapsedRealtime() - lastFixElapsedMs) / 1000L).toInt()
        val speedKmh = fix.speed * 3.6f
        locationText.text = listOf(
            getString(R.string.location_coords, fix.latitude, fix.longitude),
            getString(R.string.location_accuracy, fix.accuracy.toInt()),
            getString(R.string.location_altitude, fix.altitude.toInt()),
            getString(R.string.location_speed, speedKmh),
            getString(R.string.location_meta, fix.provider ?: "?", ageSec),
            satellites
        ).joinToString("\n")
    }

    /**
     * Filter counters — the raw data for calibration (Phase 8). Live while a
     * recording is running; without one, the persisted counters of the last
     * recording are shown (F-2): so the outing report can be written after
     * getting home.
     */
    private fun renderFilter() {
        val s = TrackingService.session
        if (s != null) {
            filterLabel.text = getString(R.string.section_filter)
            filterText.text = getString(
                R.string.diag_filter_line,
                s.filterCount(GpsFilter.Verdict.ACCEPT),
                s.filterCount(GpsFilter.Verdict.BAD_ACCURACY),
                s.filterCount(GpsFilter.Verdict.JITTER),
                s.filterCount(GpsFilter.Verdict.TELEPORT),
                s.filterCount(GpsFilter.Verdict.NON_MONOTONIC)
            )
            filterLabel.visibility = View.VISIBLE
            filterText.visibility = View.VISIBLE
            return
        }
        val prefs = getSharedPreferences(TrackingService.FILTER_STATS_PREFS, MODE_PRIVATE)
        if (!prefs.contains("saved_at")) {
            filterLabel.visibility = View.GONE
            filterText.visibility = View.GONE
            return
        }
        filterLabel.text = getString(R.string.section_filter_last)
        filterText.text = getString(
            R.string.diag_filter_line,
            prefs.getInt("accept", 0),
            prefs.getInt("bad_accuracy", 0),
            prefs.getInt("jitter", 0),
            prefs.getInt("teleport", 0),
            prefs.getInt("non_monotonic", 0)
        )
        filterLabel.visibility = View.VISIBLE
        filterText.visibility = View.VISIBLE
    }

    private fun renderStart() {
        val fix = lastFix
        val start = startFix
        if (start == null || fix == null) {
            startText.text = getString(R.string.start_waiting)
            return
        }
        val distance = Geo.distanceMeters(
            fix.latitude, fix.longitude, start.latitude, start.longitude
        )
        val bearing = Geo.initialBearingDeg(
            fix.latitude, fix.longitude, start.latitude, start.longitude
        )
        val distanceText =
            if (distance < 1000) getString(R.string.distance_m, distance.toInt())
            else getString(R.string.distance_km, distance / 1000.0)
        startText.text = getString(R.string.start_line, bearing.toInt(), distanceText)
    }

    // ---- Heading ----

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ROTATION_VECTOR) return
        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)

        val rotation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            display?.rotation ?: Surface.ROTATION_0
        } else {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.rotation
        }
        val (axisX, axisY) = when (rotation) {
            Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
            Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
            Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
            else -> SensorManager.AXIS_X to SensorManager.AXIS_Y
        }
        SensorManager.remapCoordinateSystem(rotationMatrix, axisX, axisY, remappedMatrix)
        SensorManager.getOrientation(remappedMatrix, orientation)

        val azimuth = Geo.normalizeDeg(Math.toDegrees(orientation[0].toDouble()))
        headingDegrees.text = getString(R.string.heading_degrees, azimuth.toInt())
        val cardinals = resources.getStringArray(R.array.cardinals_8)
        headingCardinal.text = cardinals[(((azimuth + 22.5) / 45.0).toInt()) % 8]
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
        if (sensor.type != Sensor.TYPE_ROTATION_VECTOR) return
        sensorStatus.text = when (accuracy) {
            SensorManager.SENSOR_STATUS_UNRELIABLE,
            SensorManager.SENSOR_STATUS_ACCURACY_LOW ->
                getString(R.string.sensor_calibration_needed)
            else -> ""
        }
    }

    private companion object {
        const val REQUEST_LOCATION = 1
    }
}
