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
import java.io.File

/**
 * Harita paketleri (docs/MVP.md, 3.5). Faz 4'te paketler dosyadan içe
 * aktarılır (SAF); indirme ve `index.json` listesi Faz 5'te geliyor.
 */
class MapsActivity : Activity() {

    private val items = mutableListOf<File>()

    private val adapter = object : BaseAdapter() {
        override fun getCount() = items.size
        override fun getItem(position: Int) = items[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val row = convertView
                ?: layoutInflater.inflate(R.layout.row_activity, parent, false)
            val f = items[position]
            row.findViewById<TextView>(R.id.rowTitle).text = f.name
            row.findViewById<TextView>(R.id.rowStats).text =
                getString(R.string.map_row_size, f.length() / 1024.0 / 1024.0)
            return row
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_maps)
        Insets.apply(findViewById(R.id.root))

        val list = findViewById<ListView>(R.id.mapList)
        list.emptyView = findViewById(R.id.mapsEmpty)
        list.adapter = adapter
        list.setOnItemClickListener { _, _, position, _ ->
            startActivity(
                Intent(this, MapActivity::class.java)
                    .putExtra(MapActivity.EXTRA_PACKAGE_PATH, items[position].absolutePath)
            )
        }
        list.setOnItemLongClickListener { _, _, position, _ ->
            confirmDelete(items[position])
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
        reload()
    }

    private fun reload() {
        items.clear()
        items.addAll(MapPackages.list(this))
        adapter.notifyDataSetChanged()
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
            reload()
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
                reload()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private companion object {
        const val REQUEST_IMPORT = 2
    }
}
