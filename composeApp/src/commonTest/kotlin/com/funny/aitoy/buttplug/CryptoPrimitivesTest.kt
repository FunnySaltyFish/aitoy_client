package com.funny.aitoy.buttplug

import kotlin.test.Test
import kotlin.test.assertContentEquals

class CryptoPrimitivesTest {
    @Test
    fun aes128EcbPkcs7MatchesKnownVector() {
        val key = "jdk#Cra%f5Vib28r".encodeToByteArray()
        val encrypted = Aes128EcbPkcs7.encrypt(key, "Auth:ABC123;".encodeToByteArray())

        assertContentEquals(hex("60 eb 11 1a e8 a8 2d 7c f6 95 46 78 f6 7b 49 04"), encrypted)
        assertContentEquals("Auth:ABC123;".encodeToByteArray(), Aes128EcbPkcs7.decrypt(key, encrypted))
    }

    @Test
    fun sha256MatchesKnownVector() {
        assertContentEquals(
            hex("2d d0 0b d7 7e 02 22 ce d8 82 66 54 81 a9 c1 d9 f9 07 30 9d 16 e0 5e d0 07 a1 ea 63 92 84 77 a9"),
            Sha256Digest.digest("challenge".encodeToByteArray()),
        )
    }

    private fun hex(value: String): ByteArray =
        value.trim().split(Regex("\\s+")).map { it.toInt(16).toByte() }.toByteArray()
}
