package com.aripd.norda.core.heading

import com.aripd.norda.core.geo.Geo
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SmoothingTest {

    @Test
    fun constantInputStaysPut() {
        val s = Smoothing(0.17)
        var out = 0.0
        repeat(50) { out = s.update(45.0, 0.02) }
        assertEquals(45.0, out, 1e-6)
    }

    // What is kept is the time constant: 50 Hz and 16 Hz arrive at the same
    // place in the same amount of time.
    @Test
    fun sampleRateDoesNotChangeTheFeel()  {
        val fast = Smoothing(0.17)
        val slow = Smoothing(0.17)
        fast.update(0.0, 0.02)
        slow.update(0.0, 0.0625)
        var a = 0.0
        var b = 0.0
        repeat(50) { a = fast.update(90.0, 0.02) }      // 1.0 s @ 50 Hz
        repeat(16) { b = slow.update(90.0, 0.0625) }    // 1.0 s @ 16 Hz
        assertTrue("50Hz=$a 16Hz=$b", abs(a - b) < 3.0)
        assertTrue(a in 60.0..90.0)
    }

    // On the 359°→1° crossing the needle does not travel around the dial: the
    // output always stays near north.
    @Test
    fun northCrossingDoesNotSpinTheDial() {
        val s = Smoothing(0.17)
        s.update(359.0, 0.02)
        repeat(30) {
            val out = s.update(1.0, 0.02)
            assertTrue("out=$out", abs(Geo.signedDifferenceDeg(out, 0.0)) < 5.0)
        }
    }

    @Test
    fun smallerTimeConstantConvergesFaster() {
        val agile = Smoothing(0.08)
        val calm = Smoothing(0.35)
        agile.update(0.0, 0.02)
        calm.update(0.0, 0.02)
        var a = 0.0
        var c = 0.0
        repeat(10) { a = agile.update(90.0, 0.02) }
        repeat(10) { c = calm.update(90.0, 0.02) }
        assertTrue("agile=$a calm=$c", a > c)
    }
}
