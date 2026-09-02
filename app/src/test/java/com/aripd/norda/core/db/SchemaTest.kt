package com.aripd.norda.core.db

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Sentinels for the 0.7.0 field crash. `WaypointDao` existed there, but the
 * schema had no `CREATE TABLE waypoint`; on every device whose DB had been
 * opened by an older version, the compass, recording, map and waypoints
 * screens crashed the moment they opened. These tests permanently guarantee
 * two things: every table the DAOs use exists in the current schema, and an
 * install that arrives via migration ends up with the same schema as a fresh
 * install.
 */
class SchemaTest {

    @Test
    fun currentSchemaContainsEveryTableTheDaosUse() {
        val all = Schema.createStatements().joinToString("\n")
        assertTrue("activity missing", all.contains("CREATE TABLE activity("))
        assertTrue("track_point missing", all.contains("CREATE TABLE track_point("))
        assertTrue("waypoint missing", all.contains("CREATE TABLE waypoint("))
    }

    @Test
    fun upgradeFromV1CreatesWaypointTable() {
        assertTrue(
            Schema.upgradeStatements(1).any { it.contains("CREATE TABLE waypoint(") }
        )
    }

    @Test
    fun upgradeFromCurrentVersionIsEmpty() {
        assertTrue(Schema.upgradeStatements(Schema.VERSION).isEmpty())
    }

    /** The migration chain must arrive where a fresh install does — the very
     *  invariant that brought 0.7.0 down: a table cannot go into create first
     *  and then be forgotten in the migration. */
    @Test
    fun upgradedInstallEndsWithFreshInstallSchema() {
        for (old in 1 until Schema.VERSION) {
            assertEquals(
                "migration from v$old reached a schema different from a fresh install",
                Schema.createStatements().toSet(),
                (Schema.createStatements(old) + Schema.upgradeStatements(old)).toSet()
            )
        }
    }

    // Phase 8: per-activity battery measurement — the version 3 columns.
    @Test
    fun currentSchemaCarriesBatteryColumns() {
        val all = Schema.createStatements().joinToString("\n")
        assertTrue(all.contains("start_battery"))
        assertTrue(all.contains("end_battery"))
    }

    @Test
    fun upgradeFromV2AddsOnlyBatteryColumns() {
        val ddl = Schema.upgradeStatements(2).joinToString("\n")
        assertTrue(ddl.contains("start_battery"))
        assertTrue(ddl.contains("end_battery"))
        assertFalse(ddl.contains("CREATE TABLE waypoint("))
    }

    /**
     * Mutation-round finding (v0.9.0): a DDL statement that leaks into the base
     * list enters every version's install, so the parity test cannot see it —
     * both sides get polluted together. The statement count per version is
     * frozen as a golden table; adding a new version means deliberately adding
     * a line here.
     */
    @Test
    fun statementCountPerVersionIsFrozen() {
        assertEquals(3, Schema.createStatements(1).size)
        assertEquals(4, Schema.createStatements(2).size)
        assertEquals(6, Schema.createStatements(3).size)
    }

    @Test
    fun waypointColumnsMatchDaoContract() {
        val ddl = Schema.createStatements().first { it.contains("CREATE TABLE waypoint(") }
        for (col in listOf("id", "name", "latitude", "longitude", "altitude", "created_at")) {
            assertTrue("column missing: $col", ddl.contains(col))
        }
    }
}
