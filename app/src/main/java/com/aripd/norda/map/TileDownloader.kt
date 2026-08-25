package com.aripd.norda.map

import com.aripd.norda.core.io.Digests
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Ağa dokunan TEK sınıf (docs/MVP.md, 11. bölüm): paket listesini okur ve
 * paket indirir. Tracking, pusula, navigasyon ve GPX ağ görmeden çalışır.
 *
 * İndirme önce `.part` dosyasına akar, SHA-256 doğrulanır, sonra adlandırılır;
 * yarım ya da bozuk indirme hiçbir zaman geçerli paket gibi görünmez.
 * Çağıran, işlemleri kendi iş parçacığında yürütür.
 */
object TileDownloader {

    private const val INDEX_URL =
        "https://raw.githubusercontent.com/aripdcem/norda/main/docs/maps/index.json"
    private const val TIMEOUT_MS = 15_000

    class RemotePackage(
        val id: String,
        val name: String,
        val sizeBytes: Long,
        val sha256: String,
        val url: String,
        val version: Int
    )

    fun fetchIndex(): List<RemotePackage> {
        val body = (URL(INDEX_URL).openConnection() as HttpURLConnection).run {
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            try {
                inputStream.bufferedReader().use { it.readText() }
            } finally {
                disconnect()
            }
        }
        val packages = JSONObject(body).getJSONArray("packages")
        val out = ArrayList<RemotePackage>(packages.length())
        for (i in 0 until packages.length()) {
            val p = packages.getJSONObject(i)
            out += RemotePackage(
                id = p.getString("id"),
                name = p.getString("name"),
                sizeBytes = p.getLong("sizeBytes"),
                sha256 = p.getString("sha256").lowercase(),
                url = p.getString("url"),
                version = p.optInt("version", 1)
            )
        }
        return out
    }

    /**
     * Paketi [targetDir] altına `<id>.mbtiles` olarak indirir; SHA-256
     * tutmazsa dosya silinir ve hata fırlatılır. [onProgress] 0-100 alır.
     */
    fun download(pkg: RemotePackage, targetDir: File, onProgress: (Int) -> Unit): File {
        val target = File(targetDir, "${pkg.id}.mbtiles")
        val part = File(targetDir, "${pkg.id}.mbtiles.part")
        val digest = MessageDigest.getInstance("SHA-256")
        try {
            val connection = URL(pkg.url).openConnection() as HttpURLConnection
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.instanceFollowRedirects = true
            try {
                val total = if (pkg.sizeBytes > 0) pkg.sizeBytes
                else connection.contentLengthLong
                connection.inputStream.use { input ->
                    part.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var copied = 0L
                        while (true) {
                            val n = input.read(buffer)
                            if (n < 0) break
                            output.write(buffer, 0, n)
                            digest.update(buffer, 0, n)
                            copied += n
                            if (total > 0) {
                                onProgress(((copied * 100) / total).toInt().coerceIn(0, 100))
                            }
                        }
                    }
                }
            } finally {
                connection.disconnect()
            }
            val actual = Digests.hex(digest.digest())
            if (actual != pkg.sha256) {
                throw IllegalStateException("SHA-256 uyuşmuyor")
            }
            if (target.exists()) target.delete()
            if (!part.renameTo(target)) {
                throw IllegalStateException("dosya adlandırılamadı")
            }
            onProgress(100)
            return target
        } finally {
            part.delete()
        }
    }
}
