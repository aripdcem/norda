package com.aripd.norda.storage

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.aripd.norda.core.db.Schema

/**
 * SQLite açma/göç kabuğu (docs/MVP.md, 8. bölüm). İnce katman kuralı: burada
 * ve DAO'da mantık yoktur — DDL ve göç planı dahil her karar saf `Schema`'da
 * yaşar ve JVM'de test edilir; bu sınıf yalnız listeleri yürütür.
 *
 * WAL: fix başına tek INSERT ile çökmeye dayanıklı kayıt (8.3).
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
