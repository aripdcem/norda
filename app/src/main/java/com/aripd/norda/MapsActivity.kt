package com.aripd.norda

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import com.aripd.norda.map.MapPackages
import com.aripd.norda.map.TileDownloader
import java.io.File

/**
 * Harita paketleri (docs/MVP.md, 3.5): indirilenler + index.json'daki
 * indirilebilir paketler tek listede. İndirme SHA-256 ile doğrulanır.
 * Elle içe aktarma (SAF) çevrimdışı yedek yol olarak durur.
 */
class MapsActivity : Activity() {

    private sealed class Row {
        class Local(val file: File) : Row()
        class Remote(val pkg: TileDownloader.RemotePackage) : Row()
    }

    private val items = mutableListOf<Row>()
    private var remoteIndex: List<TileDownloader.RemotePackage> = emptyList()
    private val downloadProgress = HashMap<String, Int>()
    private lateinit var indexHint: TextView

    private val adapter = object : BaseAdapter() {
        override fun getCount() = items.size
        override fun getItem(position: Int) = items[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val row = convertView
                ?: layoutInflater.inflate(R.layout.row_activity, parent, false)
            val title = row.findViewById<TextView>(R.id.rowTitle)
            val stats = row.findViewById<TextView>(R.id.rowStats)
            when (val item = items[position]) {
                is Row.Local -> {
                    title.text = item.file.name
                    stats.text = getString(
                        R.string.map_row_size, item.file.length() / 1024.0 / 1024.0
                    )
                }
                is Row.Remote -> {
                    title.text = item.pkg.name
                    val progress = downloadProgress[item.pkg.id]
                    stats.text = if (progress != null) {
                        getString(R.string.map_downloading, progress)
                    } else {
                        getString(
                            R.string.map_remote_stats,
                            item.pkg.sizeBytes / 1024.0 / 1024.0, item.pkg.version
                        )
                    }
                }
            }
            return row
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_maps)
        Insets.apply(findViewById(R.id.root))
        indexHint = findViewById(R.id.indexHint)

        val list = findViewById<ListView>(R.id.mapList)
        list.emptyView = findViewById(R.id.mapsEmpty)
        list.adapter = adapter
        list.setOnItemClickListener { _, _, position, _ ->
            when (val item = items[position]) {
                is Row.Local -> startActivity(
                    Intent(this, MapActivity::class.java)
                        .putExtra(MapActivity.EXTRA_PACKAGE_PATH, item.file.absolutePath)
                )
                is Row.Remote -> confirmDownload(item.pkg)
            }
        }
        list.setOnItemLongClickListener { _, _, position, _ ->
            (items[position] as? Row.Local)?.let { confirmDelete(it.file) }
            true
        }

        findViewById<Button>(R.id.importButton).setOnClickListener {
            @Suppress("DEPRECATION")
            startActivityForResult(
                Intent(Intent.ACTION_OPEN_DOCUMENT)
                    .addCategory(Intent.CATEGORY_OPENABLE)
                    .setType("*/*"),
                REQUEST_IMPORT
            )
        }
    }

    override fun onResume() {
        super.onResume()
        rebuild()
        fetchIndex()
    }

    private fun rebuild() {
        val locals = MapPackages.list(this)
        items.clear()
        items.addAll(locals.map { Row.Local(it) })
        items.addAll(
            remoteIndex
                .filter { pkg -> locals.none { it.name == "${pkg.id}.mbtiles" } }
                .map { Row.Remote(it) }
        )
        adapter.notifyDataSetChanged()
    }

    /** Liste ağdan gelir; ağ yoksa yalnız yerel paketler görünür. */
    private fun fetchIndex() {
        Thread {
            val fetched = try {
                TileDownloader.fetchIndex()
            } catch (_: Exception) {
                null
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (fetched != null) {
                    remoteIndex = fetched
                    indexHint.visibility = View.GONE
                } else {
                    indexHint.visibility = View.VISIBLE
                }
                rebuild()
            }
        }.start()
    }

    private fun confirmDownload(pkg: TileDownloader.RemotePackage) {
        if (downloadProgress.containsKey(pkg.id)) return
        AlertDialog.Builder(this)
            .setMessage(
                getString(
                    R.string.map_download_confirm, pkg.name, pkg.sizeBytes / 1024.0 / 1024.0
                )
            )
            .setPositiveButton(R.string.map_download) { _, _ -> startDownload(pkg) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun startDownload(pkg: TileDownloader.RemotePackage) {
        downloadProgress[pkg.id] = 0
        adapter.notifyDataSetChanged()
        Thread {
            val error = try {
                TileDownloader.download(pkg, MapPackages.dir(this)) { percent ->
                    runOnUiThread {
                        downloadProgress[pkg.id] = percent
                        adapter.notifyDataSetChanged()
                    }
                }
                null
            } catch (e: Exception) {
                e
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                downloadProgress.remove(pkg.id)
                Toast.makeText(
                    this,
                    if (error == null) R.string.map_downloaded else R.string.map_download_failed,
                    Toast.LENGTH_SHORT
                ).show()
                rebuild()
            }
        }.start()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_IMPORT || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        importPackage(uri)
    }

    private fun importPackage(uri: Uri) {
        val name = displayName(uri) ?: "paket-${System.currentTimeMillis()}.mbtiles"
        val target = File(
            MapPackages.dir(this),
            if (name.endsWith(".mbtiles")) name else "$name.mbtiles"
        )
        try {
            contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } ?: return
            Toast.makeText(this, R.string.map_imported, Toast.LENGTH_SHORT).show()
            rebuild()
        } catch (e: Exception) {
            target.delete()
            Toast.makeText(this, R.string.map_import_failed, Toast.LENGTH_LONG).show()
        }
    }

    private fun displayName(uri: Uri): String? =
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }

    private fun confirmDelete(file: File) {
        AlertDialog.Builder(this)
            .setMessage(getString(R.string.map_delete_confirm, file.name))
            .setPositiveButton(R.string.delete) { _, _ ->
                file.delete()
                rebuild()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private companion object {
        const val REQUEST_IMPORT = 2
    }
}
