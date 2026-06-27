package com.funny.aitoy.buttplug

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SimpleVibrateProtocolPlansTest {
    @Test
    fun simplePlansMatchPortedButtplugPayloads() {
        assertPayload("activejoy", 1, 7, false, "b0 01 00 01 01 07")
        assertPayload("activejoy", 0, 0, false, "b0 01 00 00 00 00")
        assertPayload("adrienlastic", 0, 7, true, "4d 6f 74 6f 72 56 61 6c 75 65 3a 30 37 3b")
        assertPayload("amorelie-joy", 0, 7, false, "01 01 07")
        assertPayload("aneros", 1, 8, false, "f2 08")
        assertPayload("cupido", 0, 9, false, "b0 03 00 00 00 09 aa")
        assertPayload("deepsire", 0, 10, false, "55 04 01 00 00 0a aa")
        assertPayload("fox", 0, 3, false, "03 01 01 fe 03")
        assertPayload("kiiroo-prowand", 0, 9, false, "00 00 64 ff 09 09")
        assertPayload("kiiroo-prowand", 0, 0, false, "00 00 64 00 00 00")
        assertPayload("kiiroo-spot", 0, 9, false, "00 ff 00 00 00 09")
        assertPayload("lovedistance", 0, 9, false, "f3 00 09")
        assertPayload("magic-motion-3", 0, 9, false, "0b ff 04 0a 46 46 00 04 08 09 64 00")
        assertPayload("mannuo", 0, 3, true, "aa 55 06 01 01 01 03 fa 01")
        assertPayload("maxpro", 0, 9, false, "55 04 07 ff ff 3f 09 5f 09 0e")
        assertPayload("meese", 1, 3, true, "01 80 02 03")
        assertPayload("mymuselinkplus", 0, 0, false, "aa 55 06 aa 00 00 00 00")
        assertPayload("mymuselinkplus", 0, 3, false, "aa 55 06 01 01 01 03 ff")
        assertPayload("nobra", 0, 0, false, "70")
        assertPayload("nobra", 0, 9, false, "69")
        assertPayload("omobo", 0, 9, true, "a1 04 04 01 09 ff 55")
        assertPayload("picobong", 0, 0, false, "01 ff 00")
        assertPayload("picobong", 0, 9, false, "01 01 09")
        assertPayload("prettylove", 0, 3, true, "00 03")
        assertPayload("realov", 0, 9, false, "c5 55 09 aa")
        assertPayload("sexverse-v4", 0, 9, true, "bb 01 09 66")
        assertPayload("sexverse-v5", 0, 9, false, "aa 03 03 09 00 00 00")
        assertPayload("svakom-alex", 0, 0, false, "12 01 03 00 ff 00")
        assertPayload("svakom-alex", 0, 3, false, "12 01 03 00 03 00")
        assertPayload("svakom-alex-v2", 0, 3, false, "55 03 03 00 03 08")
        assertPayload("svakom-dice", 0, 9, false, "55 04 00 00 01 09 aa")
        assertPayload("svakom-pulse", 0, 0, false, "55 03 03 00 00 01")
        assertPayload("svakom-pulse", 0, 9, false, "55 03 03 00 01 0a")
        assertPayload("svakom-suitcase", 0, 10, false, "55 03 00 00 01 0a")
        assertPayload("svakom-suitcase", 1, 1, false, "55 09 00 00 01 00")
        assertPayload("svakom-tarax", 0, 0, false, "55 03 00 00 01 01")
        assertPayload("svakom-tarax", 1, 3, false, "55 09 00 00 03 00")
        assertPayload("svakom-v1", 0, 9, false, "55 04 03 00 01 09")
        assertPayload("svakom-v2", 0, 9, true, "55 03 03 00 01 09")
        assertPayload("svakom-v2", 1, 5, true, "55 06 01 00 05 05")
        assertPayload("wetoy", 0, 0, true, "80 03")
        assertPayload("wetoy", 0, 9, true, "b2 08")
        assertPayload("xiuxiuda", 0, 9, false, "00 00 00 00 65 3a 30 09 64")
        assertPayload("youcups", 0, 9, false, "24 53 59 53 2c 39 3f")
    }

    @Test
    fun simpleHandlerWritesThroughToyTransport() = runBlocking {
        val transport = RecordingTransport()
        val handler = requireNotNull(SimpleVibrateProtocolHandlers.forProtocolId("aneros"))
        val session = handler.createSession(match("aneros", channels = 2), transport)

        session.handle(ToyOutputCommand.Scalar(featureIndex = 1, kind = ToyOutputKind.Vibrate, value = 8))
        session.stop()

        assertContentEquals(hex("f2 08"), transport.writeBytesAt(0))
        assertContentEquals(hex("f1 00"), transport.writeBytesAt(1))
        assertContentEquals(hex("f2 00"), transport.writeBytesAt(2))
    }

    @Test
    fun simpleHandlerRunsInitPayloads() = runBlocking {
        val transport = RecordingTransport()
        val handler = requireNotNull(SimpleVibrateProtocolHandlers.forProtocolId("lovedistance"))
        val session = handler.createSession(match("lovedistance", channels = 1), transport)

        session.initialize()

        assertContentEquals(hex("f3 00 00"), transport.writeBytesAt(0))
        assertContentEquals(hex("f4 01"), transport.writeBytesAt(1))
    }

    private fun assertPayload(
        protocolId: String,
        featureIndex: Int,
        speed: Int,
        withResponse: Boolean,
        expectedHex: String,
    ) {
        val plan = requireNotNull(SimpleVibrateProtocolPlans.forProtocolId(protocolId))
        assertEquals(withResponse, plan.withResponse)
        assertContentEquals(hex(expectedHex), plan.payload(featureIndex, speed))
        assertTrue(protocolId in SimpleVibrateProtocolPlans.protocolIds)
        assertFalse(SimpleVibrateProtocolHandlers.forProtocolId(protocolId) == null)
    }

    private fun match(protocolId: String, channels: Int): ButtplugDeviceMatch {
        val features = (0 until channels).map { index ->
            ButtplugOutputFeature(
                type = OUTPUT_VIBRATE,
                min = 0,
                max = 127,
                featureIndex = index,
                description = "Vibrator $index",
            )
        }
        val definition = ButtplugProtocolDefinition(
            id = protocolId,
            displayName = protocolId,
            communicationTypes = setOf(COMMUNICATION_BTLE),
            communicationNames = emptyList(),
            endpoints = listOf(
                ButtplugEndpoint(
                    serviceUuid = "service",
                    characteristics = mapOf("tx" to "tx"),
                ),
            ),
            defaultDevice = ButtplugDeviceDefinition(
                identifiers = emptyList(),
                name = protocolId,
                features = features,
            ),
            configurations = emptyList(),
        )
        return ButtplugDeviceMatch(
            protocol = definition,
            device = definition.defaultDevice,
            endpoint = definition.endpoints.first(),
        )
    }

    private class RecordingTransport : ToyTransport {
        val operations = mutableListOf<HardwareOperation>()

        override suspend fun execute(operation: HardwareOperation): HardwareResult {
            operations += operation
            return HardwareResult.Success
        }

        fun writeBytesAt(index: Int): ByteArray =
            (operations[index] as HardwareOperation.Write).bytes
    }

    private fun hex(value: String): ByteArray =
        value.split(" ").map { it.toInt(16).toByte() }.toByteArray()
}
