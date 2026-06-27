package com.funny.aitoy.buttplug

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MixedOutputProtocolPlansTest {
    @Test
    fun mixedPlansMatchPortedButtplugPayloads() {
        val plan = requireNotNull(MixedOutputProtocolPlans.forProtocolId("kiiroo-v21"))
        val state = IntArray(plan.stateSize)

        val vibrate = plan.scalarWrites(state, ScalarProtocolCommand(0, ToyOutputKind.Vibrate, 10)).single()
        val linear = plan.linearWrites(state, LinearProtocolCommand(1, position = 50, durationMs = 1000)).single()
        val secondLinear = plan.linearWrites(state, LinearProtocolCommand(1, position = 99, durationMs = 500)).single()

        assertContentEquals(hex("01 0a"), vibrate.bytes)
        assertContentEquals(hex("03 00 09 32"), linear.bytes)
        assertContentEquals(hex("03 00 13 63"), secondLinear.bytes)
        assertFalse(vibrate.withResponse)
        assertTrue("kiiroo-v21" in MixedOutputProtocolPlans.protocolIds)
        assertEquals(setOf(ToyOutputKind.Vibrate, ToyOutputKind.Linear), MixedOutputProtocolPlans.supportedKinds("kiiroo-v21"))
    }

    @Test
    fun mixedHandlerWritesThroughToyTransport() = runBlocking {
        val transport = RecordingTransport()
        val handler = requireNotNull(MixedOutputProtocolHandlers.forProtocolId("kiiroo-v21"))
        val session = handler.createSession(match("kiiroo-v21"), transport)

        session.handle(ToyOutputCommand.Scalar(featureIndex = 0, kind = ToyOutputKind.Vibrate, value = 10))
        session.handle(ToyOutputCommand.Linear(featureIndex = 1, position = 50, durationMs = 1000))
        session.stop()

        assertContentEquals(hex("01 0a"), transport.writeBytesAt(0))
        assertContentEquals(hex("03 00 09 32"), transport.writeBytesAt(1))
        assertContentEquals(hex("01 00"), transport.writeBytesAt(2))
        assertContentEquals(hex("03 00 09 00"), transport.writeBytesAt(3))
    }

    private fun match(protocolId: String): ButtplugDeviceMatch {
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
                features = listOf(
                    ButtplugOutputFeature(OUTPUT_VIBRATE, 0, 100, 0, "Vibrate"),
                    ButtplugOutputFeature(OUTPUT_HW_POSITION_WITH_DURATION, 0, 99, 1, "Linear"),
                ),
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
