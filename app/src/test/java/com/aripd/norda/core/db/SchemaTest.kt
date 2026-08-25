package com.aripd.norda.core.db

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 0.7.0 saha çökmesinin nöbetçileri. Orada `WaypointDao` vardı ama şemada
 * `CREATE TABLE waypoint` yoktu; DB'si eski sürümde açılmış her cihazda
 * pusula, kayıt, harita ve nokta ekranları açılır açılmaz düşüyordu.
 * Bu testler iki şeyi kalıcı garanti eder: DAO'ların kullandığı her tablo
 * güncel şemada vardır, ve göçle gelen kurulum sıfırdan kurulumla aynı
 * şemaya varır.
 */
class SchemaTest {

    @Test
    fun currentSchemaContainsEveryTableTheDaosUse() {
        val all = Schema.createStatements().joinToString("\n")
        assertTrue("activity eksik", all.contains("CREATE TABLE activity("))
        assertTrue("track_point eksik", all.contains("CREATE TABLE track_point("))
        assertTrue("waypoint eksik", all.contains("CREATE TABLE waypoint("))
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

    /** Göç zinciri sıfırdan kurulumla aynı yere varmalı — 0.7.0'ı düşüren
     *  değişmezin kendisi: tablo önce create'e girip göçten unutulamaz. */
    @Test
    fun upgradedInstallEndsWithFreshInstallSchema() {
        for (old in 1 until Schema.VERSION) {
            assertEquals(
                "v$old'dan göç sıfırdan kurulumdan farklı şemaya vardı",
                Schema.createStatements().toSet(),
                (Schema.createStatements(old) + Schema.upgradeStatements(old)).toSet()
            )
        }
    }

    // Faz 8: aktivite başı pil ölçümü — sürüm 3 sütunları.
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

    @Test
    fun waypointColumnsMatchDaoContract() {
        val ddl = Schema.createStatements().first { it.contains("CREATE TABLE waypoint(") }
        for (col in listOf("id", "name", "latitude", "longitude", "altitude", "created_at")) {
            assertTrue("kolon eksik: $col", ddl.contains(col))
        }
    }
}
