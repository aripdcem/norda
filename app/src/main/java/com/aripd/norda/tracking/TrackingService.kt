package com.aripd.norda.tracking

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.BatteryManager
import android.os.Bundle
import android.os.IBinder
import android.os.SystemClock
import android.widget.Toast
import com.aripd.norda.R
import com.aripd.norda.RecordingActivity
import com.aripd.norda.core.track.ActivityType
import com.aripd.norda.core.track.Format
import com.aripd.norda.core.track.GpsFilter
import com.aripd.norda.core.track.RecordingSession
import com.aripd.norda.core.track.Stats
import com.aripd.norda.core.track.TrackPoint
import com.aripd.norda.storage.ActivityDao
import com.aripd.norda.storage.AppDatabase

/**
 * Kayıt artık ekranın değil bu foreground service'in malı (docs/MVP.md, 5.6):
 * ekran kapansa da, kullanıcı başka uygulamaya geçse de sürer. Kabul edilen
 * her nokta anında diske yazılır; sistem süreci öldürüp servisi yeniden
 * başlatırsa (START_STICKY) kayıt diskteki yarım aktiviteden devralınır.
 */
class TrackingService : Service(), LocationListener {

    private lateinit var locationManager: LocationManager
    private lateinit var dao: ActivityDao
    private var lastNotifiedMillis = 0L

    // Ekranlar aynı süreçte; bağlanma törenine gerek yok, denetim
    // companion'daki statik yüzeyden geçer.
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        dao = ActivityDao(AppDatabase.get(this))
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (session == null) {
            if (intent != null) {
                startNewRecording(ActivityType.fromName(intent.getStringExtra(EXTRA_TYPE)))
            } else if (!resumeUnfinishedRecording()) {
                // Sticky yeniden başlatma ama devralınacak kayıt yok.
                stopSelf()
                return START_NOT_STICKY
            }
        }
        startForeground(NOTIFICATION_ID, buildNotification())
        startLocationUpdates()
        return START_STICKY
    }

    private fun startNewRecording(type: ActivityType) {
        val s = RecordingSession(
            type = type,
            startWallMillis = System.currentTimeMillis(),
            startMonotonicMillis = SystemClock.elapsedRealtime()
        )
        activityId = dao.startActivity(type, s.startWallMillis, batteryPercent())
        session = s
    }

    /** Pil yüzdesi; okunamıyorsa null — Battery kuralları uydurma değer yasaklar. */
    private fun batteryPercent(): Int? =
        (getSystemService(BATTERY_SERVICE) as BatteryManager)
            .getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            .takeIf { it in 0..100 }

    /** Sistem servisi öldürüp geri getirdiyse: diskteki yarım kayda devam et. */
    private fun resumeUnfinishedRecording(): Boolean {
        val unfinished = dao.unfinishedActivity() ?: return false
        val points = dao.pointsFor(unfinished.id)
        val lastPoint = points.lastOrNull()
        val endTime = lastPoint?.timeMillis ?: unfinished.startTimeMillis
        val s = RecordingSession(
            type = unfinished.type,
            startWallMillis = unfinished.startTimeMillis,
            startMonotonicMillis = SystemClock.elapsedRealtime()
        )
        s.prime(
            recoveredDistanceM = Stats.totalDistanceMeters(points),
            // Duraklatma bilgisi diske yazılmıyor; devralınan süre nokta
            // aralığından yaklaşıktır.
            recoveredDurationMillis = (endTime - unfinished.startTimeMillis).coerceAtLeast(0),
            lastPoint = lastPoint,
            altitudes = dao.altitudesFor(unfinished.id)
        )
        activityId = unfinished.id
        session = s
        return true
    }

    private fun startLocationUpdates() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            stopRecording(discard = false)
            return
        }
        // Mesafe süzgeci bilerek 0 (docs/MVP.md, 5.3). Sağlayıcı o an kapalı
        // olsa da kayıt olunur: kullanıcı konumu sonradan açarsa fix'ler
        // akmaya başlar; isProviderEnabled'a bağlanmak kaydı sessizce boş
        // bırakırdı. Var olmayan sağlayıcıya kayıt olmaksa fırlatır — guard o.
        if (LocationManager.GPS_PROVIDER in locationManager.allProviders) {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, this)
        }
    }

    override fun onLocationChanged(location: Location) {
        val s = session ?: return
        val stateBefore = s.state
        val point = TrackPoint(
            timeMillis = location.time,
            latitude = location.latitude,
            longitude = location.longitude,
            altitude = location.altitude,
            accuracyM = if (location.hasAccuracy()) location.accuracy else 0f,
            speedMps = if (location.hasSpeed()) location.speed else 0f,
            bearingDeg = if (location.hasBearing()) location.bearing else 0f
        )
        val accepted = s.onFix(point, location.hasAltitude(), SystemClock.elapsedRealtime())
        if (accepted != null) {
            dao.appendPoint(activityId, accepted, location.hasAltitude())
        }
        // Bildirim her fix'te değil: durum değişince hemen, yoksa en az 10 sn arayla.
        val now = SystemClock.elapsedRealtime()
        if (s.state != stateBefore || now - lastNotifiedMillis >= 10_000L) {
            updateNotification()
        }
    }

    @Deprecated("Framework çağırmaya devam ediyor; API 29 öncesi için gerekli")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
    override fun onProviderEnabled(provider: String) = Unit
    override fun onProviderDisabled(provider: String) = Unit

    private fun stopRecording(discard: Boolean) {
        val s = session
        if (s != null) saveFilterStats(s)
        if (s != null && !discard) {
            val now = SystemClock.elapsedRealtime()
            s.stop(now)
            if (s.points.isEmpty()) {
                // Tek nokta bile girmemiş kayıt geçmişte gürültüdür: satır
                // silinir ve NEDENİYLE söylenir (F-4): hiç fix mi gelmedi,
                // yoksa doğruluk mu hiç eşiğin altına inmedi?
                dao.deleteActivity(activityId)
                val best = s.bestAccuracyM
                var message =
                    if (s.evaluatedFixCount() == 0 || best == null)
                        getString(R.string.discarded_no_fix)
                    else getString(
                        R.string.discarded_poor_accuracy,
                        best.toInt(), GpsFilter.MAX_ACCURACY_M.toInt()
                    )
                // Güç tasarrufu birçok cihazda GPS'i kısar (F-5): boş kaydın
                // olası nedeni olarak açıkça söylenir.
                if ((getSystemService(POWER_SERVICE) as android.os.PowerManager).isPowerSaveMode) {
                    message += " " + getString(R.string.power_save_hint)
                }
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            } else {
                dao.finishActivity(
                    s.summary(activityId, System.currentTimeMillis(), now)
                        .copy(endBatteryPct = batteryPercent())
                )
            }
        }
        session = null
        locationManager.removeUpdates(this)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /**
     * Son kaydın filtre sayaçları kalıcı saklanır (F-2, Saha Turu 1 bulgusu):
     * tur bittikten SONRA da Tanılama'dan okunabilsin — yürüyüş ortasında
     * not almak gerekmesin. Boş/atılan kayıtta da yazılır: "kayıt neden boş
     * kaldı"nın cevabı çoğu zaman bu sayaçlardadır.
     */
    private fun saveFilterStats(s: RecordingSession) {
        getSharedPreferences(FILTER_STATS_PREFS, MODE_PRIVATE).edit()
            .putLong("activity_id", activityId)
            .putInt("accept", s.filterCount(GpsFilter.Verdict.ACCEPT))
            .putInt("bad_accuracy", s.filterCount(GpsFilter.Verdict.BAD_ACCURACY))
            .putInt("jitter", s.filterCount(GpsFilter.Verdict.JITTER))
            .putInt("teleport", s.filterCount(GpsFilter.Verdict.TELEPORT))
            .putInt("non_monotonic", s.filterCount(GpsFilter.Verdict.NON_MONOTONIC))
            .putLong("saved_at", System.currentTimeMillis())
            .apply()
    }

    override fun onDestroy() {
        locationManager.removeUpdates(this)
        instance = null
        super.onDestroy()
    }

    // ---- Bildirim ----

    private fun createChannel() {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = getString(R.string.notif_channel_desc) }
        )
    }

    private fun buildNotification(): Notification {
        val s = session
        val title = getString(
            if (s?.type == ActivityType.RUN) R.string.run else R.string.walk
        )
        val text = if (s == null) "" else {
            val duration = Format.duration(s.durationMillis(SystemClock.elapsedRealtime()))
            val d = s.distanceM
            val distance =
                if (d < 1000) getString(R.string.distance_m, d.toInt())
                else getString(R.string.distance_km, d / 1000.0)
            val state = when (s.state) {
                RecordingSession.State.PAUSED -> getString(R.string.recording_state_paused)
                RecordingSession.State.AUTO_PAUSED -> getString(R.string.recording_state_auto)
                else -> getString(R.string.recording_state_recording)
            }
            getString(R.string.notif_text, state, duration, distance)
        }
        val contentIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, RecordingActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_norda)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun updateNotification() {
        lastNotifiedMillis = SystemClock.elapsedRealtime()
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification())
    }

    companion object {
        const val EXTRA_TYPE = "type"
        /** Tanılama ekranı son kaydın sayaçlarını buradan okur. */
        const val FILTER_STATS_PREFS = "filter_stats"
        private const val CHANNEL_ID = "tracking"
        private const val NOTIFICATION_ID = 1

        private var instance: TrackingService? = null

        /** Ekranların "kayıt sürüyor mu" sorusuna hızlı cevabı. */
        var session: RecordingSession? = null
            private set
        var activityId = 0L
            private set
        val isRecording: Boolean get() = session != null

        fun pauseManual() {
            session?.pauseManual(SystemClock.elapsedRealtime())
            instance?.updateNotification()
        }

        fun resumeManual() {
            session?.resumeManual(SystemClock.elapsedRealtime())
            instance?.updateNotification()
        }

        /** Kaydı bitirir, özeti yazar ve servisi kapatır. */
        fun finishRecording() {
            instance?.stopRecording(discard = false)
        }
    }
}
