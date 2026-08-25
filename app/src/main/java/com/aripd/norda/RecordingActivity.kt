package com.aripd.norda

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.widget.Button
import android.widget.TextView
import com.aripd.norda.core.track.Format
import com.aripd.norda.core.track.RecordingSession
import android.widget.Toast
import com.aripd.norda.core.nav.WaypointNaming
import com.aripd.norda.map.MapPackages
import com.aripd.norda.map.MapView
import com.aripd.norda.storage.AppDatabase
import com.aripd.norda.storage.WaypointDao
import com.aripd.norda.tracking.TrackingService

/**
 * Kayıt ekranı — Faz 3'ten itibaren yalnızca bir gösterge: kaydın sahibi
 * TrackingService. Geri tuşu kaydı bitirmez; kayıt arka planda sürer ve
 * bildirimden geri dönülür.
 */
class RecordingActivity : Activity() {

    private lateinit var stateText: TextView
    private lateinit var durationText: TextView
    private lateinit var distanceText: TextView
    private lateinit var paceText: TextView
    private lateinit var elevationText: TextView
    private lateinit var pauseButton: Button
    private lateinit var stopButton: Button
    private lateinit var liveMap: MapView
    private var mapStoreLoaded = false

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
        liveMap = findViewById(R.id.liveMap)
        liveMap.follow = true
        liveMap.interactive = false

        // Servis zaten kayıttaysa (bildirimden ya da Home'dan dönüş) yeni
        // kayıt açılmaz; yalnızca gösterilir.
        if (!TrackingService.isRecording) {
            startForegroundService(
                Intent(this, TrackingService::class.java)
                    .putExtra(TrackingService.EXTRA_TYPE, intent.getStringExtra(EXTRA_TYPE))
            )
        }

        pauseButton.setOnClickListener {
            when (TrackingService.session?.state) {
                RecordingSession.State.PAUSED -> TrackingService.resumeManual()
                else -> TrackingService.pauseManual()
            }
            render()
        }
        stopButton.setOnClickListener { confirmStop() }
        findViewById<Button>(R.id.compassButton).setOnClickListener {
            startActivity(android.content.Intent(this, CompassActivity::class.java))
        }
        findViewById<Button>(R.id.addWaypointButton).setOnClickListener { addWaypointHere() }
    }

    override fun onStart() {
        super.onStart()
        refreshWaypoints()
        handler.post(ticker)
    }

    private fun refreshWaypoints() {
        liveMap.setWaypoints(WaypointDao(AppDatabase.get(this)).list())
    }

    /** Kayıt sırasında tek dokunuşla nokta: son kabul edilen konuma (MVP 2.1). */
    private fun addWaypointHere() {
        val last = TrackingService.session?.points?.lastOrNull()
        if (last == null) {
            Toast.makeText(this, R.string.rts_waiting_fix, Toast.LENGTH_SHORT).show()
            return
        }
        val dao = WaypointDao(AppDatabase.get(this))
        val name = WaypointNaming.nextDefaultName(dao.names(), getString(R.string.waypoint_prefix))
        dao.insert(name, last.latitude, last.longitude, null, System.currentTimeMillis())
        Toast.makeText(this, getString(R.string.waypoint_added, name), Toast.LENGTH_SHORT).show()
        refreshWaypoints()
    }

    override fun onStop() {
        super.onStop()
        handler.removeCallbacks(ticker)
    }

    private fun render() {
        val session = TrackingService.session
        if (session == null) {
            // Kayıt başka yerden bitirildi ya da servis kapandı.
            finish()
            return
        }
        val now = SystemClock.elapsedRealtime()
        val points = session.points
        liveMap.setTrack(points)
        points.lastOrNull()?.let { last ->
            if (!mapStoreLoaded) {
                // İlk konumla birlikte, o konumu kapsayan paket seçilir.
                mapStoreLoaded = true
                liveMap.setStore(MapPackages.openBest(this, last.latitude, last.longitude))
            }
            liveMap.setCurrentLocation(last.latitude, last.longitude)
        }
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
            .setPositiveButton(R.string.finish) { _, _ ->
                TrackingService.finishRecording()
                startActivity(Intent(this, HistoryActivity::class.java))
                finish()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    companion object {
        const val EXTRA_TYPE = "type"
    }
}
