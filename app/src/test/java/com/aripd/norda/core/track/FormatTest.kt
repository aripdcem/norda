package com.aripd.norda.core.track

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatTest {

    @Test
    fun durationHidesZeroHours() {
        assertEquals("38:21", Format.duration(2_301_000))
        assertEquals("0:59", Format.duration(59_000))
        assertEquals("0:00", Format.duration(0))
    }

    @Test
    fun durationShowsHoursWhenNeeded() {
        assertEquals("1:02:03", Format.duration(3_723_000))
    }

    @Test
    fun paceMinutesAndSeconds() {
        assertEquals("6:14", Format.pace(374.4))
        assertEquals("0:59", Format.pace(59.9))
        assertEquals("10:00", Format.pace(600.0))
    }
}
