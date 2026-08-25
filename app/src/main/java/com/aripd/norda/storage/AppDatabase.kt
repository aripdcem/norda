package com.aripd.norda.storage

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * SQLite şeması (docs/MVP.md, 8. bölüm). İnce katman kuralı: burada ve
 * DAO'da mantık yoktur — filtre/istatistik/yükseklik saf çekirdekte yaşar
 * ve JVM'de test edilir; bu katman yalnız okur ve yazar.
 *
 * WAL: fix başına tek INSERT ile çökmeye dayanıklı kayıt (8.3).
 */
class AppDatabase(context: Context) :
    SQLiteOpenHelper(context.applicationContext, "norda.db", null, 1) {

    init {
        setWriteAheadLoggingEnabled(true)
    }

    override fun onConfigure(db: SQLiteDatabase) {
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE activity(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                type TEXT NOT NULL,
                start_time INTEGER NOT NULL,
                end_time INTEGER,
                distance_m REAL NOT NULL DEFAULT 0,
                duration_ms INTEGER NOT NULL DEFAULT 0,
                elevation_gain_m REAL NOT NULL DEFAULT 0,
                elevation_loss_m REAL NOT NULL DEFAULT 0
            )"""
        )
        db.execSQL(
            """CREATE TABLE track_point(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                activity_id INTEGER NOT NULL REFERENCES activity(id) ON DELETE CASCADE,
                timestamp INTEGER NOT NULL,
                latitude REAL NOT NULL,
                longitude REAL NOT NULL,
                altitude REAL,
                accuracy REAL NOT NULL,
                speed REAL NOT NULL,
                bearing REAL NOT NULL
            )"""
        )
        db.execSQL("CREATE INDEX idx_point_activity ON track_point(activity_id)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Şema göçleri sürüm numarasıyla buraya gelir; v1'de göç yok.
    }

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: AppDatabase(context).also { instance = it }
            }
    }
}
