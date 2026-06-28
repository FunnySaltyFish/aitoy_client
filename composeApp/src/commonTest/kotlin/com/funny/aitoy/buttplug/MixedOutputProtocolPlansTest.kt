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
    fun vorzePlanMatchesPortedButtplugVariants() {
        val plan = requireNotNull(MixedOutputProtocolPlans.forProtocolId("vorze-sa"))

        val bach = plan.createState(match("vorze-sa", "Vorze Bach", listOf(ButtplugOutputFeature(OUTPUT_VIBRATE, 0, 100, 0, "Vibrate"))))
        val bachWrite = plan.scalarWrites(bach, ScalarProtocolCommand(0, ToyOutputKind.Vibrate, 10)).single()
        assertContentEquals(hex("06 03 0a"), bachWrite.bytes)
        assertTrue(bachWrite.withResponse)

        val cyclone = plan.createState(match("vorze-sa", "Vorze A10 Cyclone SA", listOf(ButtplugOutputFeature(OUTPUT_ROTATE, -99, 99, 0, "Rotate"))))
        assertContentEquals(hex("01 01 87"), plan.scalarWrites(cyclone, ScalarProtocolCommand(0, ToyOutputKind.Rotate, 7)).single().bytes)
        assertContentEquals(hex("01 01 07"), plan.scalarWrites(cyclone, ScalarProtocolCommand(0, ToyOutputKind.Rotate, -7)).single().bytes)

        val omorfi = plan.createState(
            match(
                "vorze-sa",
                "Vorze Omorfi",
                listOf(
                    ButtplugOutputFeature(OUTPUT_VIBRATE, 0, 99, 0, "Vibrate 1"),
                    ButtplugOutputFeature(OUTPUT_VIBRATE, 0, 99, 1, "Vibrate 2"),
                ),
            ),
        )
        assertContentEquals(hex("09 05 00"), plan.scalarWrites(omorfi, ScalarProtocolCommand(0, ToyOutputKind.Vibrate, 5)).single().bytes)
        assertContentEquals(hex("09 05 08"), plan.scalarWrites(omorfi, ScalarProtocolCommand(1, ToyOutputKind.Vibrate, 8)).single().bytes)

        val ufoTw = plan.createState(
            match(
                "vorze-sa",
                "Vorze UFO TW",
                listOf(
                    ButtplugOutputFeature(OUTPUT_ROTATE, -99, 99, 0, "Rotate 1"),
                    ButtplugOutputFeature(OUTPUT_ROTATE, -99, 99, 1, "Rotate 2"),
                ),
            ),
        )
        assertContentEquals(hex("05 07 00"), plan.scalarWrites(ufoTw, ScalarProtocolCommand(0, ToyOutputKind.Rotate, -7)).single().bytes)
        assertContentEquals(hex("05 07 89"), plan.scalarWrites(ufoTw, ScalarProtocolCommand(1, ToyOutputKind.Rotate, 9)).single().bytes)

        val piston = plan.createState(
            match(
                "vorze-sa",
                "Vorze Piston",
                listOf(ButtplugOutputFeature(OUTPUT_HW_POSITION_WITH_DURATION, 0, 99, 0, "Linear")),
            ),
        )
        assertContentEquals(hex("03 32 01"), plan.linearWrites(piston, LinearProtocolCommand(0, position = 50, durationMs = 1000)).single().bytes)
        assertEquals(
            setOf(ToyOutputKind.Vibrate, ToyOutputKind.Rotate, ToyOutputKind.Linear),
            MixedOutputProtocolPlans.supportedKinds("vorze-sa"),
        )
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
        return match(
            protocolId,
            protocolId,
            listOf(
                ButtplugOutputFeature(OUTPUT_VIBRATE, 0, 100, 0, "Vibrate"),
                ButtplugOutputFeature(OUTPUT_HW_POSITION_WITH_DURATION, 0, 99, 1, "Linear"),
            ),
        )
    }

    private fun match(
        protocolId: String,
        deviceName: String,
        features: List<ButtplugOutputFeature>,
    ): ButtplugDeviceMatch {
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
                name = deviceName,
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
