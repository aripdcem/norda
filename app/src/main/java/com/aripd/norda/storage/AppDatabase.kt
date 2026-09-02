package com.aripd.norda.storage

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.aripd.norda.core.db.Schema

/**
 * SQLite open/migration shell (docs/MVP.md, section 8). Thin-layer rule:
 * there is no logic here or in the DAOs — every decision, DDL and migration
 * plan included, lives in the pure `Schema` and is tested on the JVM; this
 * class merely executes the lists.
 *
 * WAL: crash-resilient recording with a single INSERT per fix (8.3).
 */
class AppDatabase(context: Context) :
    SQLiteOpenHelper(context.applicationContext, "norda.db", null, Schema.VERSION) {

    init {
        setWriteAheadLoggingEnabled(true)
    }

    override fun onConfigure(db: SQLiteDatabase) {
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        Schema.createStatements().forEach(db::execSQL)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        Schema.upgradeStatements(oldVersion, newVersion).forEach(db::execSQL)
    }

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: AppDatabase(context).also { instance = it }
            }
    }
}
