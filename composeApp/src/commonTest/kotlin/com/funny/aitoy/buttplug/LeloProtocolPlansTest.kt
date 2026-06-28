package com.funny.aitoy.buttplug

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class LeloProtocolPlansTest {
    @Test
    fun harmonyPayloadMatchesButtplugProtocolImpl() {
        assertContentEquals(
            byteArrayOf(0x0A, 0x12, 0x01, 0x08, 0, 0, 0, 0, 0x32, 0),
            LeloProtocolPlans.harmonyPayload(featureIndex = 0, speed = 0x32),
        )
        assertContentEquals(
            byteArrayOf(0x0A, 0x12, 0x02, 0x08, 0, 0, 0, 0, 0x64, 0),
            LeloProtocolPlans.harmonyPayload(featureIndex = 1, speed = 0x64),
        )
    }

    @Test
    fun harmonyDeclaresVibrateAndRotateOutputs() {
        assertEquals(
            setOf(ToyOutputKind.Vibrate, ToyOutputKind.Rotate),
            LeloProtocolPlans.supportedKinds("lelo-harmony"),
        )
    }

    @Test
    fun f1sV2PayloadMatchesButtplugProtocolImpl() {
        assertContentEquals(
            byteArrayOf(0x01, 0x20, 0x30),
            LeloProtocolPlans.f1sPayload(internalSpeed = 0x20, externalSpeed = 0x30),
        )
    }

    @Test
    fun f1sV2DeclaresVibrateOutput() {
        assertEquals(
            setOf(ToyOutputKind.Vibrate),
            LeloProtocolPlans.supportedKinds("lelo-f1sv2"),
        )
    }
}
