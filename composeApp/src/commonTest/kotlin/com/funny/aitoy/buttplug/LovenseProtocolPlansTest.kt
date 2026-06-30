package com.funny.aitoy.buttplug

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class LovenseProtocolPlansTest {
    @Test
    fun deviceTypeResolverPromotesFlexerFirmware3() {
        assertEquals("EI-FW3", LovenseProtocolPlans.deviceTypeFromResponse("EI:3;"))
        assertEquals("EI", LovenseProtocolPlans.deviceTypeFromResponse("EI:2;"))
    }

    @Test
    fun deviceTypeResolverReadsBleNamePrefix() {
        assertEquals("BA", LovenseProtocolPlans.deviceTypeFromBleName("LVS-BA001"))
        assertEquals("H", LovenseProtocolPlans.deviceTypeFromBleName("LOVE-H123"))
    }

    @Test
    fun maxConstrictUsesAirLevelCommand() {
        val profile = LovenseProtocolPlans.profile("B", listOf(vibrateFeature(0), constrictFeature(1)))
        val writes = LovenseProtocolPlans.scalarWrites(profile, IntArray(2), constrictFeature(1), 3)

        assertContentEquals("Air:Level:3;".encodeToByteArray(), writes.single().bytes)
    }

    @Test
    fun rotateVibratorSendsDirectionChangeForNegativeSpeed() {
        val profile = LovenseProtocolPlans.profile("A", listOf(vibrateFeature(0), rotateFeature(1)))
        val writes = LovenseProtocolPlans.scalarWrites(profile, IntArray(2), rotateFeature(1), -7)

        assertContentEquals("RotateChange;".encodeToByteArray(), writes[0].bytes)
        assertContentEquals("Rotate:7;".encodeToByteArray(), writes[1].bytes)
    }

    @Test
    fun multiActuatorUsesMplyStatePacket() {
        val features = listOf(vibrateFeature(0), vibrateFeature(1), rotateFeature(2, min = 0))
        val profile = LovenseProtocolPlans.profile("EI-FW3", features)
        val state = IntArray(3)

        LovenseProtocolPlans.scalarWrites(profile, state, vibrateFeature(0), 4)
        val writes = LovenseProtocolPlans.scalarWrites(profile, state, rotateFeature(2, min = 0), 9)

        assertContentEquals("Mply:4:0:9;".encodeToByteArray(), writes.single().bytes)
    }

    @Test
    fun strokerLinearUsesFSetSiteCommand() {
        val profile = LovenseProtocolPlans.profile("BA", listOf(linearFeature(0), oscillateFeature(0)))
        val writes = LovenseProtocolPlans.linearWrites(profile, 88)

        assertContentEquals("FSetSite:88;".encodeToByteArray(), writes.single().bytes)
    }

    private fun vibrateFeature(index: Int) =
        ButtplugOutputFeature(OUTPUT_VIBRATE, 0, 20, index, "")

    private fun oscillateFeature(index: Int) =
        ButtplugOutputFeature(OUTPUT_OSCILLATE, 0, 20, index, "")

    private fun rotateFeature(index: Int, min: Int = -20) =
        ButtplugOutputFeature(OUTPUT_ROTATE, min, 20, index, "")

    private fun constrictFeature(index: Int) =
        ButtplugOutputFeature(OUTPUT_CONSTRICT, 0, 3, index, "")

    private fun linearFeature(index: Int) =
        ButtplugOutputFeature(OUTPUT_HW_POSITION_WITH_DURATION, 0, 100, index, "")
}
