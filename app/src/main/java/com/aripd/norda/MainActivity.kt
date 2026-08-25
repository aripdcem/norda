package com.aripd.norda

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.RadioButton
import android.widget.TextView
import android.widget.Toast
import com.aripd.norda.core.track.ActivityType
import com.aripd.norda.core.track.ElevationTracker
import com.aripd.norda.core.track.Stats
import com.aripd.norda.storage.ActivityDao
import com.aripd.norda.storage.AppDatabase
import com.aripd.norda.tracking.TrackingService

/**
 * Home (docs/MVP.md, 3.1): olabildiğince boş — tip seç, START'a bas.
 * Açılışta yarım kalmış kayıt varsa (süreç ölümü) geçmişe kurtarılır.
 */
class MainActivity : Activity() {

    private lateinit var dao: ActivityDao
    private lateinit var walkButton: RadioButton
    private lateinit var runButton: RadioButton
    private lateinit var permissionHint: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        Insets.apply(findViewById(R.id.root))

        dao = ActivityDao(AppDatabase.get(this))
        walkButton = findViewById(R.id.typeWalk)
        runButton = findViewById(R.id.typeRun)
        permissionHint = findViewById(R.id.permissionHint)

        findViewById<Button>(R.id.startButton).setOnClickListener { onStartTapped() }
        findViewById<Button>(R.id.historyButton).setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }
        findViewById<Button>(R.id.mapsButton).setOnClickListener {
            startActivity(Intent(this, MapsActivity::class.java))
        }
        findViewById<TextView>(R.id.diagnosticsLink).setOnClickListener {
            startActivity(Intent(this, DiagnosticsActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        recoverUnfinished()
        permissionHint.visibility =
            if (hasLocationPermission()) View.GONE else View.VISIBLE
    }

    private fun onStartTapped() {
        if (!hasLocationPermission()) {
            // Bildirim izni (13+) konumla birlikte istenir: foreground
            // service'in kalıcı bildirimi kaydın görünür yüzüdür. Reddi
            // kaydı engellemez, yalnız bildirim gizli kalır.
            val permissions = mutableListOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                permissions += Manifest.permission.POST_NOTIFICATIONS
            }
            requestPermissions(permissions.toTypedArray(), REQUEST_LOCATION)
            return
        }
        val type = if (runButton.isChecked) ActivityType.RUN else ActivityType.WALK
        startActivity(
            Intent(this, RecordingActivity::class.java)
                .putExtra(RecordingActivity.EXTRA_TYPE, type.name)
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_LOCATION && hasLocationPermission()) {
            permissionHint.visibility = View.GONE
            onStartTapped()
        }
    }

    private fun hasLocationPermission(): Boolean =
        checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Süreç ölümüyle yarım kalan kayıt (end_time NULL) diskteki noktalardan
     * toparlanır: mesafe/yükseklik saf çekirdekle yeniden hesaplanır; süre,
     * duraklatma bilgisi kaybolduğu için nokta aralığından yaklaşıktır.
     */
    private fun recoverUnfinished() {
        // Servis kayıttaysa "yarım" görünen aktivite canlıdır — dokunma.
        if (TrackingService.isRecording) return
        val unfinished = dao.unfinishedActivity() ?: return
        val points = dao.pointsFor(unfinished.id)
        val distance = Stats.totalDistanceMeters(points)
        val elevation = ElevationTracker()
        dao.altitudesFor(unfinished.id).forEach { elevation.onAltitude(it) }
        val endTime = points.lastOrNull()?.timeMillis ?: unfinished.startTimeMillis
        dao.finishActivity(
            com.aripd.norda.core.track.ActivitySummary(
                id = unfinished.id,
                type = unfinished.type,
                startTimeMillis = unfinished.startTimeMillis,
                endTimeMillis = endTime,
                distanceM = distance,
                durationMillis = (endTime - unfinished.startTimeMillis).coerceAtLeast(0),
                elevationGainM = elevation.gainM,
                elevationLossM = elevation.lossM
            )
        )
        Toast.makeText(this, R.string.recovered_toast, Toast.LENGTH_LONG).show()
    }

    private companion object {
        const val REQUEST_LOCATION = 1
    }
}
