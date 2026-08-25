package com.aripd.norda.core.io

import org.junit.Assert.assertEquals
import org.junit.Test

class DigestsTest {

    // NIST'in yayımlanmış test vektörü: kendi çıktımızı değil standardı sınar.
    @Test
    fun sha256MatchesKnownVector() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            Digests.sha256Hex("abc".toByteArray())
        )
    }

    @Test
    fun sha256OfEmptyInput() {
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            Digests.sha256Hex(ByteArray(0))
        )
    }

    @Test
    fun hexIsLowercaseAndPadded() {
        assertEquals("000aff", Digests.hex(byteArrayOf(0x00, 0x0A, 0xFF.toByte())))
    }
}
