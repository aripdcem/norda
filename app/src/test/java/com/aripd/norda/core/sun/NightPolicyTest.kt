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

    // F-16 (field, v1.5.0): "the screen was blazing red; with astigmatism it
    // was very hard to read". The deep red that protects dark adaptation best
    // is the hardest to focus, so the filter gets three strengths and the
    // gentlest amber is no longer a special case but a choice.

    @Test
    fun `strengths cycle soft, medium, strong, soft`() {
        assertEquals(NightPolicy.Strength.MEDIUM, NightPolicy.Strength.SOFT.next())
        assertEquals(NightPolicy.Strength.STRONG, NightPolicy.Strength.MEDIUM.next())
        assertEquals(NightPolicy.Strength.SOFT, NightPolicy.Strength.STRONG.next())
    }

    @Test
    fun `strengths round-trip through their stored names and default to medium`() {
        NightPolicy.Strength.values().forEach {
            assertEquals(it, NightPolicy.Strength.parse(it.name))
        }
        assertEquals(NightPolicy.Strength.MEDIUM, NightPolicy.Strength.parse(null))
        assertEquals(NightPolicy.Strength.MEDIUM, NightPolicy.Strength.parse("garbage"))
    }

    @Test
    fun `strength does not decide whether the filter is on`() {
        // The two settings are independent: when to tint is the mode's
        // business, how hard to tint is the strength's.
        assertTrue(NightPolicy.tint(NightPolicy.Mode.ON, lat, lon, noon))
        assertFalse(NightPolicy.tint(NightPolicy.Mode.OFF, lat, lon, midnight))
    }
}
