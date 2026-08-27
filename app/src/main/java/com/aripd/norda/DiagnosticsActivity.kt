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
import com.aripd.norda.core.track.GpsFilter
import com.aripd.norda.tracking.TrackingService

/**
 * Faz 1'in sensör tanılama ekranı: ham konum ve yön verisi. Saha
 * doğrulaması için Home'dan erişilir durumda tutulur. Faz 8'den beri kayıt
 * sürerken filtre sayaçlarını da gösterir — eşik kalibrasyonunun ham verisi.
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
    private lateinit var permissionText: TextView
    private lateinit var permissionButton: Button

    private val rotationMatrix = FloatArray(9)
    private val remappedMatrix = FloatArray(9)
    private val orientation = FloatArray(3)

    private var lastFix: Location? = null
    private var lastFixElapsedMs = 0L
    private var startFix: Location? = null

    // Uydu görünürlüğü (F-9): fix yokken bile çipin ne gördüğü belli olsun.
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
        handler.post(ageTicker)
    }

    // Pil kuralı: ekran görünmüyorken ne sensör ne konum dinlenir.
    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
        locationManager.removeUpdates(this)
        gnssCallback?.let { locationManager.unregisterGnssStatusCallback(it) }
        gnssCallback = null
        handler.removeCallbacks(ageTicker)
    }

    // ---- İzin ----

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

    // ---- Konum ----

    private fun startLocationUpdates() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        // Mesafe süzgeci bilerek 0 (docs/MVP.md, 5.3'te belgelenen tuzak).
        // Sağlayıcıya kapalıyken de kayıt olunur (0.8.0 servis düzeltmesiyle
        // aynı desen); guard yalnız var olmayan sağlayıcı içindir.
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

    @Deprecated("Framework çağırmaya devam ediyor; API 29 öncesi için gerekli")
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
        // Uydu satırı fix'ten bağımsız: çipin ne gördüğü her durumda görünür
        // (F-9). 0/0 = gökyüzü yok; N görülüp 0 kullanılıyorsa kilit yok.
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
     * Filtre sayaçları — kalibrasyonun ham verisi (Faz 8). Kayıt sürerken
     * canlı; kayıt yokken son kaydın kalıcı sayaçları gösterilir (F-2):
     * tur raporu eve dönünce yazılabilsin.
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

    // ---- Yön ----

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
