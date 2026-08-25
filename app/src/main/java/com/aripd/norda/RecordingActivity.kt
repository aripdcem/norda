package com.aripd.norda

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.widget.Button
import android.widget.TextView
import com.aripd.norda.core.track.ActivityType
import com.aripd.norda.core.track.Format
import com.aripd.norda.core.track.RecordingSession
import com.aripd.norda.core.track.TrackPoint
import com.aripd.norda.storage.ActivityDao
import com.aripd.norda.storage.AppDatabase

/**
 * Faz 2 kayıt ekranı. Kayıt bu fazda ekran açıkken sürer (keepScreenOn);
 * arka plan kaydı Faz 3'te foreground service ile gelir. Kabul edilen her
 * nokta anında diske yazılır — süreç ölürse Home açılışta kurtarır.
 */
class RecordingActivity : Activity(), LocationListener {

    private lateinit var locationManager: LocationManager
    private lateinit var dao: ActivityDao
    private lateinit var session: RecordingSession
    private var activityId = 0L

    private lateinit var stateText: TextView
    private lateinit var durationText: TextView
    private lateinit var distanceText: TextView
    private lateinit var paceText: TextView
    private lateinit var elevationText: TextView
    private lateinit var pauseButton: Button
    private lateinit var stopButton: Button

    private val handler = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() {
            render()
            handler.postDelayed(this, 1000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_recording)
        Insets.apply(findViewById(R.id.root))

        stateText = findViewById(R.id.stateText)
        durationText = findViewById(R.id.durationText)
        distanceText = findViewById(R.id.distanceText)
        paceText = findViewById(R.id.paceText)
        elevationText = findViewById(R.id.elevationText)
        pauseButton = findViewById(R.id.pauseButton)
        stopButton = findViewById(R.id.stopButton)

        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        dao = ActivityDao(AppDatabase.get(this))

        val type = ActivityType.fromName(intent.getStringExtra(EXTRA_TYPE))
        session = RecordingSession(
            type = type,
            startWallMillis = System.currentTimeMillis(),
            startMonotonicMillis = SystemClock.elapsedRealtime()
        )
        activityId = dao.startActivity(type, session.startWallMillis)

        pauseButton.setOnClickListener {
            val now = SystemClock.elapsedRealtime()
            when (session.state) {
                RecordingSession.State.PAUSED -> session.resumeManual(now)
                else -> session.pauseManual(now)
            }
            render()
        }
        stopButton.setOnClickListener { confirmStop() }
    }

    override fun onStart() {
        super.onStart()
        startLocationUpdates()
        handler.post(ticker)
    }

    override fun onStop() {
        super.onStop()
        locationManager.removeUpdates(this)
        handler.removeCallbacks(ticker)
    }

    private fun startLocationUpdates() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            finish()
            return
        }
        // Mesafe süzgeci bilerek 0 (docs/MVP.md, 5.3).
        if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, this)
        }
    }

    override fun onLocationChanged(location: Location) {
        val point = TrackPoint(
            timeMillis = location.time,
            latitude = location.latitude,
            longitude = location.longitude,
            altitude = location.altitude,
            accuracyM = if (location.hasAccuracy()) location.accuracy else 0f,
            speedMps = if (location.hasSpeed()) location.speed else 0f,
            bearingDeg = if (location.hasBearing()) location.bearing else 0f
        )
        val accepted = session.onFix(point, location.hasAltitude(), SystemClock.elapsedRealtime())
        if (accepted != null) {
            dao.appendPoint(activityId, accepted, location.hasAltitude())
        }
        render()
    }

    @Deprecated("Framework çağırmaya devam ediyor; API 29 öncesi için gerekli")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
    override fun onProviderEnabled(provider: String) = Unit
    override fun onProviderDisabled(provider: String) = Unit

    private fun render() {
        val now = SystemClock.elapsedRealtime()
        durationText.text = Format.duration(session.durationMillis(now))
        val d = session.distanceM
        distanceText.text =
            if (d < 1000) getString(R.string.distance_m, d.toInt())
            else getString(R.string.distance_km, d / 1000.0)
        val pace = session.currentPaceSecPerKm()
        paceText.text =
            if (pace != null) getString(R.string.pace_per_km, Format.pace(pace))
            else getString(R.string.placeholder_dash)
        elevationText.text = getString(
            R.string.elevation_line,
            session.elevationGainM.toInt(), session.elevationLossM.toInt()
        )
        stateText.text = getString(
            when (session.state) {
                RecordingSession.State.RECORDING -> R.string.recording_state_recording
                RecordingSession.State.PAUSED -> R.string.recording_state_paused
                RecordingSession.State.AUTO_PAUSED -> R.string.recording_state_auto
                RecordingSession.State.STOPPED -> R.string.recording_state_stopped
            }
        )
        pauseButton.text = getString(
            if (session.state == RecordingSession.State.PAUSED) R.string.resume
            else R.string.pause
        )
    }

    private fun confirmStop() {
        AlertDialog.Builder(this)
            .setMessage(getString(R.string.finish_confirm))
            .setPositiveButton(R.string.finish) { _, _ -> finishRecording() }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun finishRecording() {
        val now = SystemClock.elapsedRealtime()
        session.stop(now)
        dao.finishActivity(session.summary(activityId, System.currentTimeMillis(), now))
        startActivity(android.content.Intent(this, HistoryActivity::class.java))
        finish()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Geri = sessiz veri kaybı olmasın: bitir ya da kayda dön.
        confirmStop()
    }

    companion object {
        const val EXTRA_TYPE = "type"
    }
}
