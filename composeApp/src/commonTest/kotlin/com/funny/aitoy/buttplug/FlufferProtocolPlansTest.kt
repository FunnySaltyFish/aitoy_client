package com.funny.aitoy.buttplug

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertTrue

class FlufferProtocolPlansTest {
    @Test
    fun authPayloadUsesAdvertisementSeedAndSha256Prefix() {
        val advertisement = hex("4a 68 01 02 03 04 05")
        assertContentEquals(hex("6b 6a 6d 6c"), FlufferProtocolPlans.advertisementSeed(advertisement))
        assertContentEquals(
            hex("b2 9b 5a d1 30 54 56 35 6c 84 6b ba e7 31 ed 66"),
            FlufferProtocolPlans.authPayload(advertisement, nonce = hex("01 02 03 04")),
        )
    }

    @Test
    fun commandPayloadsMatchButtplugProtocolImpl() {
        assertContentEquals(
            hex("d9 a0 64 11 f5 e2 4e 7e 05 db 14 1d 95 63 05 d5"),
            FlufferProtocolPlans.enablePayload(),
        )
        assertContentEquals(
            hex("87 b7 be c1 c8 40 05 7e 86 06 f7 5e c0 43 19 f0"),
            FlufferProtocolPlans.keepalivePayload(),
        )
        assertContentEquals(
            hex("88 6d 75 ea e4 c6 ac 56 40 0b 8e 48 c4 d4 f4 49"),
            FlufferProtocolPlans.controlPayload(firstSpeed = 20, secondSpeed = 120),
        )
    }

    @Test
    fun encryptedAckIsRecognized() {
        assertTrue(FlufferProtocolPlans.isAuthSuccess(hex("fb 14 63 04 20 5c 54 79 bb 6b a6 c9 69 fd aa 96")))
    }

    private fun hex(value: String): ByteArray =
        value.trim().split(Regex("\\s+")).map { it.toInt(16).toByte() }.toByteArray()
}
