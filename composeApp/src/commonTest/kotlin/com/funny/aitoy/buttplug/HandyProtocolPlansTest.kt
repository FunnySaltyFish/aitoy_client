package com.funny.aitoy.buttplug

import kotlin.test.Test
import kotlin.test.assertContentEquals

class HandyProtocolPlansTest {
    @Test
    fun handyPayloadsMatchProtobufLayoutUsedByButtplug() {
        assertContentEquals(
            hex("0a 06 b2 06 03 08 e7 07"),
            HandyProtocolPlans.handyPingPayload(),
        )
        assertContentEquals(
            hex("0a 13 9a 19 10 08 02 1a 0c 10 e8 07 19 00 00 00 00 00 00 e0 3f"),
            HandyProtocolPlans.handyLinearPayload(position = 50, durationMs = 1000),
        )
    }

    @Test
    fun handyV3PayloadsMatchProtobufLayoutUsedByButtplug() {
        assertContentEquals(
            hex("08 01 12 05 10 01 b2 2c 00"),
            HandyProtocolPlans.handyV3BatteryRequestPayload(id = 1),
        )
        assertContentEquals(
            hex("08 01 12 05 10 02 ca 2c 00"),
            HandyProtocolPlans.handyV3CapabilitiesRequestPayload(id = 2),
        )
        assertContentEquals(
            hex("10 02 ca 2c 00"),
            HandyProtocolPlans.handyV3KeepalivePayload(id = 2),
        )
        assertContentEquals(
            hex("08 01 12 0f 10 03 c2 2e 0a 0d 00 00 00 3f 10 e8 07 18 01"),
            HandyProtocolPlans.handyV3LinearPayload(id = 3, position = 50, durationMs = 1000),
        )
        assertContentEquals(
            hex("08 01 12 0c 10 04 a2 38 07 0d cd cc 4c 3e 10 64"),
            HandyProtocolPlans.handyV3VibratePayload(id = 4, speed = 20),
        )
        assertContentEquals(
            hex("08 01 12 05 10 05 aa 38 00"),
            HandyProtocolPlans.handyV3VibratePayload(id = 5, speed = 0),
        )
    }

    private fun hex(value: String): ByteArray =
        value.trim().split(Regex("\\s+")).map { it.toInt(16).toByte() }.toByteArray()
}
