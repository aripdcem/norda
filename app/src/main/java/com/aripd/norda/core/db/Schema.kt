package com.aripd.norda.core.db

/**
 * SQLite şeması ve göç planı saf veri olarak (docs/MVP.md, 8.1). Hangi DDL
 * sıfırdan kurulumda, hangisi hangi sürümden göçte çalışır — hepsi burada
 * yaşar ve JVM'de test edilir; `AppDatabase` yalnız bu listeleri yürütür.
 *
 * Kural: yeni tablo/sütun eklerken sürüm artar ve DDL HEM createStatements
 * HEM upgradeStatements yoluna girer. Parite testi (SchemaTest) göçle gelen
 * kurulumun sıfırdan kurulumla aynı şemaya varmadığını yakalar — 0.7.0'daki
 * "DAO var, tablo yok" çökmesi tam olarak bu değişmezin ihlaliydi.
 *
 * Sürümler: v1 activity + track_point (+ indeks), v2 + waypoint.
 */
object Schema {

    const val VERSION = 2

    private const val CREATE_ACTIVITY = """CREATE TABLE activity(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                type TEXT NOT NULL,
                start_time INTEGER NOT NULL,
                end_time INTEGER,
                distance_m REAL NOT NULL DEFAULT 0,
                duration_ms INTEGER NOT NULL DEFAULT 0,
                elevation_gain_m REAL NOT NULL DEFAULT 0,
                elevation_loss_m REAL NOT NULL DEFAULT 0
            )"""

    private const val CREATE_TRACK_POINT = """CREATE TABLE track_point(
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

    private const val CREATE_POINT_INDEX =
        "CREATE INDEX idx_point_activity ON track_point(activity_id)"

    private const val CREATE_WAYPOINT = """CREATE TABLE waypoint(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                latitude REAL NOT NULL,
                longitude REAL NOT NULL,
                altitude REAL,
                created_at INTEGER NOT NULL
            )"""

    /** Sıfırdan kurulum (onCreate): verilen sürümün tam şeması. */
    fun createStatements(version: Int = VERSION): List<String> {
        require(version in 1..VERSION) { "bilinmeyen şema sürümü: $version" }
        val out = mutableListOf(CREATE_ACTIVITY, CREATE_TRACK_POINT, CREATE_POINT_INDEX)
        if (version >= 2) out += CREATE_WAYPOINT
        return out
    }

    /** Göç (onUpgrade): eski sürümü hedef sürüme taşıyan DDL, sırayla. */
    fun upgradeStatements(oldVersion: Int, newVersion: Int = VERSION): List<String> {
        require(oldVersion in 1..newVersion && newVersion <= VERSION) {
            "geçersiz göç: v$oldVersion → v$newVersion"
        }
        val out = mutableListOf<String>()
        if (oldVersion < 2 && newVersion >= 2) out += CREATE_WAYPOINT
        return out
    }
}
