package com.aripd.norda

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ListView
import android.widget.TextView
import com.aripd.norda.core.track.ActivitySummary
import com.aripd.norda.core.track.ActivityType
import com.aripd.norda.core.track.Format
import com.aripd.norda.storage.ActivityDao
import com.aripd.norda.storage.AppDatabase
import java.text.DateFormat
import java.util.Date

/** Bitmiş aktivitelerin listesi; uzun basış siler. Harita detayı Faz 4'te. */
class HistoryActivity : Activity() {

    private lateinit var dao: ActivityDao
    private lateinit var listView: ListView
    private val items = mutableListOf<ActivitySummary>()
    private val dateFormat = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)

    private val adapter = object : BaseAdapter() {
        override fun getCount() = items.size
        override fun getItem(position: Int) = items[position]
        override fun getItemId(position: Int) = items[position].id

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val row = convertView
                ?: layoutInflater.inflate(R.layout.row_activity, parent, false)
            val a = items[position]
            val typeLabel = getString(
                if (a.type == ActivityType.WALK) R.string.walk else R.string.run
            )
            row.findViewById<TextView>(R.id.rowTitle).text =
                getString(R.string.history_row_title, dateFormat.format(Date(a.startTimeMillis)), typeLabel)
            val distance =
                if (a.distanceM < 1000) getString(R.string.distance_m, a.distanceM.toInt())
                else getString(R.string.distance_km, a.distanceM / 1000.0)
            row.findViewById<TextView>(R.id.rowStats).text = getString(
                R.string.history_row_stats,
                distance, Format.duration(a.durationMillis), a.elevationGainM.toInt()
            )
            return row
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)
        Insets.apply(findViewById(R.id.root))

        dao = ActivityDao(AppDatabase.get(this))
        listView = findViewById(R.id.historyList)
        listView.emptyView = findViewById(R.id.emptyText)
        listView.adapter = adapter
        listView.setOnItemClickListener { _, _, position, _ ->
            startActivity(
                android.content.Intent(this, MapActivity::class.java)
                    .putExtra(MapActivity.EXTRA_ACTIVITY_ID, items[position].id)
            )
        }
        listView.setOnItemLongClickListener { _, _, position, _ ->
            confirmDelete(items[position])
            true
        }
    }

    override fun onResume() {
        super.onResume()
        reload()
    }

    private fun reload() {
        items.clear()
        items.addAll(dao.listFinished())
        adapter.notifyDataSetChanged()
    }

    private fun confirmDelete(a: ActivitySummary) {
        AlertDialog.Builder(this)
            .setMessage(getString(R.string.delete_confirm))
            .setPositiveButton(R.string.delete) { _, _ ->
                dao.deleteActivity(a.id)
                reload()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
