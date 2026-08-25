package com.aripd.norda.core.nav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WaypointNamingTest {

    @Test
    fun firstNameIsOne() {
        assertEquals("Nokta 1", WaypointNaming.nextDefaultName(emptyList(), "Nokta"))
    }

    // Silinen numara yeniden kullanılır: boş İLK numara seçilir.
    @Test
    fun deletedNumberIsReused() {
        val existing = listOf("Nokta 1", "Nokta 3", "Kamp")
        assertEquals("Nokta 2", WaypointNaming.nextDefaultName(existing, "Nokta"))
    }

    @Test
    fun customNamesDoNotBlockNumbers() {
        val existing = listOf("Kamp", "Araba", "Nokta X")
        assertEquals("Nokta 1", WaypointNaming.nextDefaultName(existing, "Nokta"))
    }

    @Test
    fun sanitizeTrimsAndFlattensNewlines() {
        assertEquals("Kamp yeri", WaypointNaming.sanitize("  Kamp\nyeri \r"))
        assertNull(WaypointNaming.sanitize("   \n "))
    }
}
