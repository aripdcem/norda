package com.aripd.norda

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import com.aripd.norda.core.nav.Waypoint
import com.aripd.norda.core.nav.WaypointNaming
import com.aripd.norda.storage.AppDatabase
import com.aripd.norda.storage.WaypointDao

/** Waypoint list: tap → rename, long-press → delete. */
class WaypointsActivity : Activity() {

    private lateinit var dao: WaypointDao
    private val items = mutableListOf<Waypoint>()

    private val adapter = object : BaseAdapter() {
        override fun getCount() = items.size
        override fun getItem(position: Int) = items[position]
        override fun getItemId(position: Int) = items[position].id

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val row = convertView
                ?: layoutInflater.inflate(R.layout.row_activity, parent, false)
            val w = items[position]
            row.findViewById<TextView>(R.id.rowTitle).text = w.name
            row.findViewById<TextView>(R.id.rowStats).text =
                getString(R.string.waypoint_coords, w.latitude, w.longitude)
            return row
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_waypoints)
        Insets.apply(findViewById(R.id.root))
        dao = WaypointDao(AppDatabase.get(this))

        val list = findViewById<ListView>(R.id.waypointList)
        list.emptyView = findViewById(R.id.waypointsEmpty)
        list.adapter = adapter
        list.setOnItemClickListener { _, _, position, _ -> renameDialog(items[position]) }
        list.setOnItemLongClickListener { _, _, position, _ ->
            deleteDialog(items[position])
            true
        }
    }

    override fun onResume() {
        super.onResume()
        reload()
    }

    private fun reload() {
        items.clear()
        items.addAll(dao.list())
        adapter.notifyDataSetChanged()
    }

    private fun renameDialog(w: Waypoint) {
        val input = EditText(this).apply { setText(w.name) }
        AlertDialog.Builder(this)
            .setTitle(R.string.waypoint_rename)
            .setView(input)
            .setPositiveButton(R.string.waypoint_save) { _, _ ->
                WaypointNaming.sanitize(input.text.toString())?.let {
                    dao.rename(w.id, it)
                    reload()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun deleteDialog(w: Waypoint) {
        AlertDialog.Builder(this)
            .setMessage(getString(R.string.map_delete_confirm, w.name))
            .setPositiveButton(R.string.delete) { _, _ ->
                dao.delete(w.id)
                reload()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
