package com.funny.aitoy.buttplug

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class VibCrafterProtocolPlansTest {
    @Test
    fun authAndControlPayloadsMatchButtplugProtocolImpl() {
        assertContentEquals(
            hex("60 eb 11 1a e8 a8 2d 7c f6 95 46 78 f6 7b 49 04"),
            VibCrafterProtocolPlans.authPayload("ABC123"),
        )
        assertContentEquals(
            hex("cc 47 f4 13 09 7c a6 74 7f 6a af 5c 6b ea 90 90"),
            VibCrafterProtocolPlans.controlPayload(internalSpeed = 20, externalSpeed = 30),
        )
    }

    @Test
    fun notificationDecryptsAndChallengeResponseUsesSha256Prefix() {
        assertEquals(
            "abcd:challenge;",
            VibCrafterProtocolPlans.decryptNotification(hex("25 f5 21 a5 8d 24 2c f5 ab 9d f1 ca ff 31 46 d8")),
        )
        assertEquals("Auth:2dd0;", VibCrafterProtocolPlans.challengeResponseText("abcd:challenge;"))
        assertEquals("OK;", VibCrafterProtocolPlans.decryptNotification(hex("12 0a 6d ef a7 05 e3 6f a0 46 e6 cd f6 09 f6 7a")))
    }

    private fun hex(value: String): ByteArray =
        value.trim().split(Regex("\\s+")).map { it.toInt(16).toByte() }.toByteArray()
}
