package com.funny.aitoy.buttplug

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LinearProtocolPlansTest {
    @Test
    fun linearPlansMatchPortedButtplugPayloads() {
        assertWrites("fleshy-thrust", intArrayOf(), command(position = 54, durationMs = 1000), "36 03 e8")
        assertWrites("loob", intArrayOf(), command(position = 510, durationMs = 200), "01 fe 00 c8")
        assertWrites("loob", intArrayOf(), command(position = 1000, durationMs = 50), "03 e8 00 32")
        assertWrites("loob", intArrayOf(), command(position = 0, durationMs = 500), "00 01 01 f4")
        assertWrites("serveu", intArrayOf(0), command(position = 30, durationMs = 1000), "01 1e 0f")
        assertWrites("serveu", intArrayOf(30), command(position = 80, durationMs = 200), "01 50 4b")
        assertWrites("serveu", intArrayOf(80), command(position = 100, durationMs = 10), "01 64 fa")
        assertWrites("kiiroo-v2", intArrayOf(0), command(position = 50, durationMs = 1000), "32 09")
        assertWrites("kiiroo-v21-initialized", intArrayOf(0), command(position = 50, durationMs = 1000), "03 00 09 32")
        assertWrites("kiiroo-v21-initialized", intArrayOf(50), command(position = 99, durationMs = 500), "03 00 13 63")
        assertWrites("kiiroo-v3", intArrayOf(0), command(position = 50, durationMs = 1000), "03 00 09 32")
        assertWrites("fredorch", intArrayOf(0), command(position = 5, durationMs = 1000), "01 10 00 6b 00 05 0a 00 07 00 07 00 32 00 32 00 01 68 62")

        val loob = requireNotNull(LinearProtocolPlans.forProtocolId("loob"))
        assertContentEquals(hex("00 01 01 f4"), loob.initWrites.single().bytes)
        assertTrue(loob.initWrites.single().withResponse)

        val kiirooV2 = requireNotNull(LinearProtocolPlans.forProtocolId("kiiroo-v2"))
        assertEquals("firmware", kiirooV2.initWrites.single().endpointRole)
        assertContentEquals(hex("00"), kiirooV2.initWrites.single().bytes)
        assertTrue(kiirooV2.initWrites.single().withResponse)

        val kiirooV21Initialized = requireNotNull(LinearProtocolPlans.forProtocolId("kiiroo-v21-initialized"))
        assertContentEquals(hex("03 00 64 19"), kiirooV21Initialized.initWrites[0].bytes)
        assertContentEquals(hex("03 00 64 00"), kiirooV21Initialized.initWrites[1].bytes)
        assertTrue(kiirooV21Initialized.initWrites.all { it.withResponse })

        val fredorch = requireNotNull(LinearProtocolPlans.forProtocolId("fredorch"))
        assertEquals("rx", fredorch.initSubscriptions.single())
        assertContentEquals(hex("01 06 00 64 00 01 d5 09"), fredorch.initWrites[0].bytes)
        assertContentEquals(hex("01 06 00 69 00 00 d6 59"), fredorch.initWrites[1].bytes)
        assertContentEquals(hex("01 10 00 6b 00 05 0a 00 05 00 05 00 00 00 00 00 01 c3 c0"), fredorch.initWrites[2].bytes)
        assertContentEquals(hex("01 06 00 69 00 01 16 98"), fredorch.initWrites[3].bytes)
        assertContentEquals(hex("01 06 00 6a 00 01 16 68"), fredorch.initWrites[4].bytes)
    }

    @Test
    fun linearHandlerWritesThroughToyTransport() = runBlocking {
        val transport = RecordingTransport()
        val handler = requireNotNull(LinearProtocolHandlers.forProtocolId("serveu"))
        val session = handler.createSession(match("serveu", max = 100), transport)

        session.handle(ToyOutputCommand.Linear(featureIndex = 0, position = 30, durationMs = 1000))
        session.handle(ToyOutputCommand.Linear(featureIndex = 0, position = 80, durationMs = 200))
        session.stop()

        assertContentEquals(hex("01 1e 0f"), transport.writeBytesAt(0))
        assertContentEquals(hex("01 50 4b"), transport.writeBytesAt(1))
        assertContentEquals(hex("01 00 21"), transport.writeBytesAt(2))
    }

    @Test
    fun linearHandlerRunsInitSubscriptions() = runBlocking {
        val transport = RecordingTransport()
        val handler = requireNotNull(LinearProtocolHandlers.forProtocolId("fredorch"))
        val session = handler.createSession(match("fredorch", max = 15, endpointCharacteristics = mapOf("tx" to "tx", "rx" to "rx")), transport)

        session.initialize()
        session.handle(ToyOutputCommand.Linear(featureIndex = 0, position = 5, durationMs = 1000))

        assertEquals(HardwareOperation.Subscribe("rx"), transport.operations[0])
        assertContentEquals(hex("01 06 00 64 00 01 d5 09"), transport.writeBytesAt(1))
        assertContentEquals(hex("01 10 00 6b 00 05 0a 00 07 00 07 00 32 00 32 00 01 68 62"), transport.writeBytesAt(6))
    }

    private fun assertWrites(
        protocolId: String,
        initialState: IntArray,
        command: LinearProtocolCommand,
        expectedHex: String,
        withResponse: Boolean = false,
    ) {
        val plan = requireNotNull(LinearProtocolPlans.forProtocolId(protocolId))
        val state = IntArray(maxOf(plan.stateSize, initialState.size))
        initialState.copyInto(state)
        val writes = plan.writes(state, command)
        assertEquals(1, writes.size)
        assertEquals(withResponse, writes.single().withResponse)
        assertContentEquals(hex(expectedHex), writes.single().bytes)
        assertTrue(protocolId in LinearProtocolPlans.protocolIds)
        assertFalse(LinearProtocolHandlers.forProtocolId(protocolId) == null)
    }

    private fun command(position: Int, durationMs: Int): LinearProtocolCommand =
        LinearProtocolCommand(featureIndex = 0, position = position, durationMs = durationMs)

    private fun match(
        protocolId: String,
        max: Int,
        endpointCharacteristics: Map<String, String> = mapOf("tx" to "tx"),
    ): ButtplugDeviceMatch {
        val definition = ButtplugProtocolDefinition(
            id = protocolId,
            displayName = protocolId,
            communicationTypes = setOf(COMMUNICATION_BTLE),
            communicationNames = emptyList(),
            endpoints = listOf(
                ButtplugEndpoint(
                    serviceUuid = "service",
                    characteristics = endpointCharacteristics,
                ),
            ),
            defaultDevice = ButtplugDeviceDefinition(
                identifiers = emptyList(),
                name = protocolId,
                features = listOf(
                    ButtplugOutputFeature(OUTPUT_HW_POSITION_WITH_DURATION, 0, max, 0, "Linear"),
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
