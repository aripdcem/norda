package com.aripd.norda.core.sun

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NightPolicyTest {

    // Istanbul, 2026-06-21: solar noon ≈ 10:06Z, solar midnight ≈ 22:06Z.
    private val lat = 41.0082
    private val lon = 28.9784
    private val noon = 1_782_209_160_000L
    private val midnight = noon + 12 * 3_600_000L

    @Test
    fun `forced on tints even at noon`() {
        assertTrue(NightPolicy.tint(NightPolicy.Mode.ON, lat, lon, noon))
    }

    @Test
    fun `forced off never tints even at midnight`() {
        assertFalse(NightPolicy.tint(NightPolicy.Mode.OFF, lat, lon, midnight))
    }

    @Test
    fun `automatic follows the sun`() {
        assertFalse(NightPolicy.tint(NightPolicy.Mode.AUTO, lat, lon, noon))
        assertTrue(NightPolicy.tint(NightPolicy.Mode.AUTO, lat, lon, midnight))
    }

    @Test
    fun `automatic without a position stays day`() {
        assertFalse(NightPolicy.tint(NightPolicy.Mode.AUTO, null, null, midnight))
    }

    @Test
    fun `forced on and off ignore a missing position`() {
        assertTrue(NightPolicy.tint(NightPolicy.Mode.ON, null, null, noon))
        assertFalse(NightPolicy.tint(NightPolicy.Mode.OFF, null, null, midnight))
    }

    @Test
    fun `modes cycle auto, on, off, auto`() {
        assertEquals(NightPolicy.Mode.ON, NightPolicy.Mode.AUTO.next())
        assertEquals(NightPolicy.Mode.OFF, NightPolicy.Mode.ON.next())
        assertEquals(NightPolicy.Mode.AUTO, NightPolicy.Mode.OFF.next())
    }

    @Test
    fun `modes round-trip through their stored names and unknown names fall back to auto`() {
        NightPolicy.Mode.values().forEach { assertEquals(it, NightPolicy.Mode.parse(it.name)) }
        assertEquals(NightPolicy.Mode.AUTO, NightPolicy.Mode.parse(null))
        assertEquals(NightPolicy.Mode.AUTO, NightPolicy.Mode.parse("garbage"))
    }
}
