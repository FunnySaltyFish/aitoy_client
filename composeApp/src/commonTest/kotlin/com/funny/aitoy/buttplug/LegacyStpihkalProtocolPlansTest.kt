package com.funny.aitoy.buttplug

import kotlin.test.Test
import kotlin.test.assertContentEquals

class LegacyStpihkalProtocolPlansTest {
    @Test
    fun cuemePacksMotorIndexAndStrengthIntoOneByte() {
        assertContentEquals(byteArrayOf(0x1F), LegacyStpihkalProtocolPlans.cuemePayload(motorIndex = 0, speed = 15))
        assertContentEquals(byteArrayOf(0x80.toByte()), LegacyStpihkalProtocolPlans.cuemePayload(motorIndex = 7, speed = 0))
    }

    @Test
    fun kiirooV1UsesAsciiLevelCommaNewline() {
        assertContentEquals("4,\n".encodeToByteArray(), LegacyStpihkalProtocolPlans.kiirooV1Payload(4))
    }

    @Test
    fun museUsesSpeedAndTimedModeByte() {
        assertContentEquals(byteArrayOf(0x09, 0x01), LegacyStpihkalProtocolPlans.musePayload(9))
        assertContentEquals(byteArrayOf(0x00, 0x00), LegacyStpihkalProtocolPlans.musePayload(0))
    }

    @Test
    fun sayberXUsesAsciiSpeedCommand() {
        assertContentEquals("AT+SPD40".encodeToByteArray(), LegacyStpihkalProtocolPlans.sayberXPayload(4))
    }
}
