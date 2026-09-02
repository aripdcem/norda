package com.aripd.norda.core.db

/**
 * SQLite schema and migration plan as pure data (docs/MVP.md, 8.1). Which DDL
 * runs on a fresh install and which on a migration from which version — all
 * of it lives here and is tested on the JVM; `AppDatabase` merely executes
 * these lists.
 *
 * Rule: when adding a new table/column the version increases and the DDL
 * enters BOTH the createStatements AND the upgradeStatements path. The parity
 * test (SchemaTest) catches a migrated install not arriving at the same
 * schema as a fresh install — the "DAO exists, table doesn't" crash in 0.7.0
 * was exactly a violation of this invariant.
 *
 * Versions: v1 activity + track_point (+ index), v2 + waypoint,
 * v3 + activity.start_battery/end_battery.
 */
object Schema {

    const val VERSION = 3

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

    // v3: per-activity battery measurement — stays NULL if it could not be read.
    private const val ALTER_ACTIVITY_START_BATTERY =
        "ALTER TABLE activity ADD COLUMN start_battery INTEGER"
    private const val ALTER_ACTIVITY_END_BATTERY =
        "ALTER TABLE activity ADD COLUMN end_battery INTEGER"

    /**
     * Fresh install (onCreate) = the v1 base + the migration chain: both
     * install paths feed from the same definition, so parity is preserved
     * structurally too (the test still stands guard — it catches anyone
     * breaking this composition).
     */
    fun createStatements(version: Int = VERSION): List<String> {
        require(version in 1..VERSION) { "unknown schema version: $version" }
        return listOf(CREATE_ACTIVITY, CREATE_TRACK_POINT, CREATE_POINT_INDEX) +
            upgradeStatements(1, version)
    }

    /** Migration (onUpgrade): DDL moving the old version to the target, in order. */
    fun upgradeStatements(oldVersion: Int, newVersion: Int = VERSION): List<String> {
        require(oldVersion in 1..newVersion && newVersion <= VERSION) {
            "invalid migration: v$oldVersion → v$newVersion"
        }
        val out = mutableListOf<String>()
        if (oldVersion < 2 && newVersion >= 2) out += CREATE_WAYPOINT
        if (oldVersion < 3 && newVersion >= 3) {
            out += ALTER_ACTIVITY_START_BATTERY
            out += ALTER_ACTIVITY_END_BATTERY
        }
        return out
    }
}
