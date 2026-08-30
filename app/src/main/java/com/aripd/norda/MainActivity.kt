package com.aripd.norda

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.location.GnssStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.RadioButton
import android.widget.TextView
import android.widget.Toast
import com.aripd.norda.core.track.ActivityType
import com.aripd.norda.core.track.ElevationTracker
import com.aripd.norda.core.track.GpsFilter
import com.aripd.norda.core.track.Stats
import com.aripd.norda.storage.ActivityDao
import com.aripd.norda.storage.AppDatabase
import com.aripd.norda.tracking.TrackingService

/**
 * Home (docs/MVP.md, 3.1): olabildiğince boş — tip seç, START'a bas.
 * Açılışta yarım kalmış kayıt varsa (süreç ölümü) geçmişe kurtarılır.
 *
 * İzin UX (Faz 8): ret sonrası neden ekranda kalır; kalıcı ret Ayarlar'a
 * götüren diyalog açar. Konum servisi kapalıysa START bunu kayıt boş
 * kaldıktan sonra değil, baştan söyler.
 *
 * GPS ön-ısıtma (F-6): çip ancak biri GPS istediğinde aramaya başlar; Home
 * açıkken dinlemeye başlanır ki kullanıcı tip seçerken kilitlensin, START'a
 * kör bekleyişle değil "GPS hazır" satırını görerek basılsın. Ekrandan
 * ayrılınca bırakılır (pil kuralı, MVP 14); kayıt sürerken servis dinliyor.
 */
class MainActivity : Activity(), LocationListener {

    private lateinit var dao: ActivityDao
    private lateinit var walkButton: RadioButton
    private lateinit var runButton: RadioButton
    private lateinit var startButton: Button
    private lateinit var permissionHint: TextView
    private lateinit var gpsHint: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        Insets.apply(findViewById(R.id.root))

        dao = ActivityDao(AppDatabase.get(this))
        walkButton = findViewById(R.id.typeWalk)
        runButton = findViewById(R.id.typeRun)
        permissionHint = findViewById(R.id.permissionHint)
        gpsHint = findViewById(R.id.gpsHint)

        // Yüklü sürüm ekranda görünür (F-7): "hangi sürümdeyim" sorusu
        // telefona değil ekrana sorulur.
        findViewById<TextView>(R.id.versionText).text =
            getString(R.string.version_label, BuildConfig.VERSION_NAME)

        startButton = findViewById(R.id.startButton)
        startButton.setOnClickListener { onStartTapped() }
        findViewById<Button>(R.id.compassButton).setOnClickListener {
            startActivity(Intent(this, CompassActivity::class.java))
        }
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
        renderPermissionHint()
        renderStartButton()
        startGpsWarmup()
    }

    /**
     * Kayıt sürerken düğme "yeni kayıt" gibi okunmasın (F-8): BAŞLAT →
     * KAYDA DÖN olur, tip seçimi kilitlenir. Zaten teknik olarak da yeni
     * kayıt açılmıyordu (servis korumalı); artık ekran da bunu söylüyor.
     */
    private fun renderStartButton() {
        val recording = TrackingService.isRecording
        startButton.text =
            getString(if (recording) R.string.return_to_recording else R.string.start)
        walkButton.isEnabled = !recording
        runButton.isEnabled = !recording
    }

    override fun onPause() {
        super.onPause()
        val locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        locationManager.removeUpdates(this)
        gnssCallback?.let { locationManager.unregisterGnssStatusCallback(it) }
        gnssCallback = null
    }

    // Uydu görünürlüğü (F-9): "GPS aranıyor" tek başına neden söylemez —
    // uydu 0/0 = gökyüzü yok, uydu 7/0 = gökyüzü var ama kilit yok.
    private var gnssCallback: GnssStatus.Callback? = null
    private var satsSeen = 0
    private var satsUsed = 0
    private var hasGpsFix = false

    /** GPS ön-ısıtma (F-6): fix'ler kayda girmez, yalnız hazırlık göstergesini besler. */
    private fun startGpsWarmup() {
        gpsHint.visibility = View.GONE
        // İzin kontrolü bilerek satır içi: lint'in MissingPermission akış
        // analizi yardımcı fonksiyonun içini göremiyor (depodaki desen bu).
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED || TrackingService.isRecording
        ) {
            return
        }
        val locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        if (LocationManager.GPS_PROVIDER !in locationManager.allProviders) return
        hasGpsFix = false
        satsSeen = 0
        satsUsed = 0
        renderSearchingHint()
        gpsHint.visibility = View.VISIBLE
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, this)
        // Ağ-tohumlu ısıtma (F-10): ağ sağlayıcısını istemek birçok cihazda
        // GNSS motoruna kaba konum tohumlar, kilidi dakikalardan saniyelere
        // indirir (saha kanıtı: Pusula/Haritalar ziyaretleri). Ağ fix'i
        // göstergeye ve kayda ASLA girmez — onLocationChanged sağlayıcıyı süzer.
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
                if (!hasGpsFix) renderSearchingHint()
            }
        }
        gnssCallback = callback
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            locationManager.registerGnssStatusCallback(mainExecutor, callback)
        } else {
            @Suppress("DEPRECATION")
            locationManager.registerGnssStatusCallback(callback, Handler(Looper.getMainLooper()))
        }
    }

    /** Fix yokken: aranıyor + uydu sayısı (0/0 = gökyüzü yok; 7/0 = kilit yok). */
    private fun renderSearchingHint() {
        gpsHint.text =
            if (satsSeen > 0) getString(R.string.gps_searching_sats, satsUsed, satsSeen)
            else getString(R.string.gps_searching)
    }

    override fun onLocationChanged(location: Location) {
        // Yalnız gerçek GPS göstergeyi ilerletir; ağ fix'i sadece tohumdur.
        if (location.provider != LocationManager.GPS_PROVIDER) return
        hasGpsFix = true
        val acc = if (location.hasAccuracy()) location.accuracy else 0f
        gpsHint.text = when {
            acc > 0f && acc <= GpsFilter.MAX_ACCURACY_M ->
                getString(R.string.gps_ready, acc.toInt())
            acc > 0f -> getString(R.string.gps_accuracy_live, acc.toInt())
            else -> getString(R.string.gps_searching)
        }
    }

    @Deprecated("Framework çağırmaya devam ediyor; API 29 öncesi için gerekli")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
    override fun onProviderEnabled(provider: String) = Unit
    override fun onProviderDisabled(provider: String) = Unit

    private fun onStartTapped() {
        if (TrackingService.isRecording) {
            // Süren kayda dönüş: izin/konum diyaloglarına gerek yok.
            startActivity(Intent(this, RecordingActivity::class.java))
            return
        }
        if (!hasLocationPermission()) {
            if (permissionDeniedForever()) {
                // requestPermissions burada sessizce reddedilir; tek çıkış
                // sistem ayarları. Kullanıcıya döngü değil, kapı gösterilir.
                AlertDialog.Builder(this)
                    .setMessage(R.string.permission_denied_forever)
                    .setPositiveButton(R.string.open_settings) { _, _ ->
                        startActivity(
                            Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.fromParts("package", packageName, null)
                            )
                        )
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
                return
            }
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
            prefs().edit().putBoolean(KEY_LOCATION_ASKED, true).apply()
            requestPermissions(permissions.toTypedArray(), REQUEST_LOCATION)
            return
        }

        val locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            // Konum kapalıyken başlayan kayıt sessizce boş kalır; kullanıcı
            // bunu yürüyüşün sonunda değil, başında öğrenmeli. "Yine de
            // başlat" açık kalır: servis kayıtlı bekler, konum açılınca akar.
            AlertDialog.Builder(this)
                .setMessage(R.string.location_disabled)
                .setPositiveButton(R.string.location_enable) { _, _ ->
                    startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                }
                .setNegativeButton(R.string.start_anyway) { _, _ -> startRecording() }
                .show()
            return
        }
        startRecording()
    }

    private fun startRecording() {
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
        if (requestCode != REQUEST_LOCATION) return
        if (hasLocationPermission()) {
            permissionHint.visibility = View.GONE
            startGpsWarmup()
            onStartTapped()
        } else {
            renderPermissionHint()
        }
    }

    private fun hasLocationPermission(): Boolean =
        checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /** Sorulmuş + gerekçe artık gösterilmiyor = kullanıcı "bir daha sorma" dedi. */
    private fun permissionDeniedForever(): Boolean =
        prefs().getBoolean(KEY_LOCATION_ASKED, false) &&
            !shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)

    private fun renderPermissionHint() {
        if (hasLocationPermission()) {
            permissionHint.visibility = View.GONE
            return
        }
        permissionHint.setText(
            if (permissionDeniedForever()) R.string.permission_denied_forever
            else R.string.permission_needed
        )
        permissionHint.visibility = View.VISIBLE
    }

    private fun prefs() = getSharedPreferences("ui", MODE_PRIVATE)

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
        if (points.isEmpty()) {
            // Tek nokta bile girmemiş yarım kayıt: geçmişe gürültü olarak
            // kurtarılmaz, sessizce silinir.
            dao.deleteActivity(unfinished.id)
            return
        }
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
        const val KEY_LOCATION_ASKED = "location_asked"
    }
}
