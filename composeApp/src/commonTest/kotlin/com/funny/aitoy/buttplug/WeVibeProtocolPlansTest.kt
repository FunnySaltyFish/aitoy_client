package com.funny.aitoy.buttplug

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class WeVibeProtocolPlansTest {
    @Test
    fun packedPatternPayloadUsesWeVibeModeTableAndDualIntensityNibbles() {
        assertContentEquals(hex("0f 03 00 ff 00 03 00 00"), WeVibeProtocolPlans.packedPatternPayload(1, 15, 15))
        assertContentEquals(hex("0f 05 00 84 00 03 00 00"), WeVibeProtocolPlans.packedPatternPayload(3, 8, 4))
        assertContentEquals(hex("0f 07 00 72 00 03 00 00"), WeVibeProtocolPlans.packedPatternPayload(5, 7, 2))
        assertContentEquals(hex("0f 10 00 f0 00 02 00 00"), WeVibeProtocolPlans.packedPatternPayload(9, 15, 0))
        assertContentEquals(hex("0f 11 00 0f 00 01 00 00"), WeVibeProtocolPlans.packedPatternPayload(10, 0, 15))
    }

    @Test
    fun stopPayloadIsSharedByWeVibeVariants() {
        val stop = hex("0f 00 00 00 00 00 00 00")

        assertContentEquals(stop, WeVibeProtocolPlans.stopPayload())
        assertContentEquals(stop, WeVibeProtocolPlans.packedPatternPayload(1, 0, 0))
        assertContentEquals(stop, WeVibeProtocolPlans.legacyPayload(2, 0, 0))
        assertContentEquals(stop, WeVibeProtocolPlans.eightBitPayload(2, 0, 0))
        assertContentEquals(stop, WeVibeProtocolPlans.chorusPayload(2, 0, 0))
    }

    @Test
    fun legacyInitializePayloadsMatchButtplugWeVibeInitializer() {
        val payloads = WeVibeProtocolPlans.legacyInitializePayloads()

        assertEquals(2, payloads.size)
        assertContentEquals(hex("0f 03 00 99 00 03 00 00"), payloads[0])
        assertContentEquals(hex("0f 00 00 00 00 00 00 00"), payloads[1])
    }

    @Test
    fun legacyPayloadPacksExternalIntoLowNibbleAndInternalIntoHighNibble() {
        assertContentEquals(hex("0f 03 00 94 00 03 00 00"), WeVibeProtocolPlans.legacyPayload(2, 9, 4))
        assertContentEquals(hex("0f 03 00 99 00 03 00 00"), WeVibeProtocolPlans.legacyPayload(1, 9, 4))
    }

    @Test
    fun eightBitAndChorusPayloadsKeepButtplugMotorOrder() {
        assertContentEquals(hex("0f 03 00 07 0c 03 00 00"), WeVibeProtocolPlans.eightBitPayload(2, 9, 4))
        assertContentEquals(hex("0f 03 00 09 04 03 00 00"), WeVibeProtocolPlans.chorusPayload(2, 9, 4))
        assertContentEquals(hex("0f 03 00 0c 0c 03 00 00"), WeVibeProtocolPlans.eightBitPayload(1, 9, 4))
        assertContentEquals(hex("0f 03 00 09 09 03 00 00"), WeVibeProtocolPlans.chorusPayload(1, 9, 4))
    }

    private fun hex(value: String): ByteArray =
        value.split(" ").map { it.toInt(16).toByte() }.toByteArray()
}
