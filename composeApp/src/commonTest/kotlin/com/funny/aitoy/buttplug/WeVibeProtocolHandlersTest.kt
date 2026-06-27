package com.funny.aitoy.buttplug

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class WeVibeProtocolHandlersTest {
    @Test
    fun legacySessionRunsButtplugInitializerAndWritesScalarCommand() = runBlocking {
        val transport = RecordingTransport()
        val session = WeVibeProtocolHandlers.legacy.createSession(match("wevibe", channels = 2), transport)

        session.initialize()
        session.handle(ToyOutputCommand.Scalar(featureIndex = 0, kind = ToyOutputKind.Vibrate, value = 9))
        session.handle(ToyOutputCommand.Scalar(featureIndex = 1, kind = ToyOutputKind.Vibrate, value = 4))

        assertContentEquals(hex("0f 03 00 99 00 03 00 00"), transport.writeBytesAt(0))
        assertContentEquals(hex("0f 00 00 00 00 00 00 00"), transport.writeBytesAt(1))
        assertContentEquals(hex("0f 03 00 90 00 03 00 00"), transport.writeBytesAt(2))
        assertContentEquals(hex("0f 03 00 94 00 03 00 00"), transport.writeBytesAt(3))
    }

    @Test
    fun packedPatternSessionWritesPatternAndDualIntensity() = runBlocking {
        val transport = RecordingTransport()
        val session = WeVibeProtocolHandlers.packedPattern.createSession(match("wevibe", channels = 2), transport)

        session.handle(
            ToyOutputCommand.Pattern(
                patternIndex = 10,
                values = mapOf(0 to 8, 1 to 4),
            ),
        )

        assertEquals(1, transport.operations.size)
        assertContentEquals(hex("0f 11 00 84 00 03 00 00"), transport.writeBytesAt(0))
    }

    @Test
    fun eightBitAndChorusSessionsUseTheirOwnPayloadRules() = runBlocking {
        val eightBitTransport = RecordingTransport()
        val chorusTransport = RecordingTransport()

        val eightBit = WeVibeProtocolHandlers.eightBit.createSession(match("wevibe-8bit", channels = 2), eightBitTransport)
        val chorus = WeVibeProtocolHandlers.chorus.createSession(match("wevibe-chorus", channels = 2), chorusTransport)

        eightBit.handle(ToyOutputCommand.Scalar(featureIndex = 0, kind = ToyOutputKind.Vibrate, value = 9))
        eightBit.handle(ToyOutputCommand.Scalar(featureIndex = 1, kind = ToyOutputKind.Vibrate, value = 4))
        chorus.handle(ToyOutputCommand.Scalar(featureIndex = 0, kind = ToyOutputKind.Vibrate, value = 9))
        chorus.handle(ToyOutputCommand.Scalar(featureIndex = 1, kind = ToyOutputKind.Vibrate, value = 4))

        assertContentEquals(hex("0f 03 00 07 0c 03 00 00"), eightBitTransport.writeBytesAt(1))
        assertContentEquals(hex("0f 03 00 09 04 03 00 00"), chorusTransport.writeBytesAt(1))
    }

    @Test
    fun stopClearsCurrentStateAndWritesStopPayload() = runBlocking {
        val transport = RecordingTransport()
        val session = WeVibeProtocolHandlers.chorus.createSession(match("wevibe-chorus", channels = 2), transport)

        session.handle(ToyOutputCommand.Scalar(featureIndex = 0, kind = ToyOutputKind.Vibrate, value = 9))
        session.stop()

        assertContentEquals(hex("0f 00 00 00 00 00 00 00"), transport.writeBytesAt(1))
    }

    private fun match(protocolId: String, channels: Int): ButtplugDeviceMatch {
        val features = (0 until channels).map { index ->
            ButtplugOutputFeature(
                type = OUTPUT_VIBRATE,
                min = 0,
                max = 15,
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
