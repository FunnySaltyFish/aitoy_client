package com.funny.aitoy.buttplug

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ScalarProtocolPlansTest {
    @Test
    fun scalarPlansMatchPortedButtplugPayloads() {
        assertWrites("bananasome", intArrayOf(0, 0, 0), command(0, ToyOutputKind.Oscillate, 0xC0), "a0 03 c0 00 00")
        assertWrites("bananasome", intArrayOf(0, 0xC0, 0), command(2, ToyOutputKind.Vibrate, 0x40), "a0 03 00 c0 40")
        assertWrites("feelingso", intArrayOf(0x0F, 0), command(1, ToyOutputKind.Oscillate, 0x0A), "aa 40 03 0f 0a 14 19")
        assertWrites("itoys", intArrayOf(), command(0, ToyOutputKind.Vibrate, 0x02), "a0 01 00 00 02 ff")
        assertWrites("itoys", intArrayOf(), command(1, ToyOutputKind.Oscillate, 0x80), "a0 06 01 00 01 80")
        assertWrites("itoys", intArrayOf(), command(1, ToyOutputKind.Rotate, -3), "a0 08 01 00 03 ff")
        assertWrites("xibao", intArrayOf(), command(0, ToyOutputKind.Oscillate, 0x32), "66 3a 00 06 00 06 01 02 00 02 04 32 e7")
        assertWrites("synchro", intArrayOf(), command(0, ToyOutputKind.Rotate, 3), "a1 01 03 77 55")
        assertWrites("synchro", intArrayOf(), command(0, ToyOutputKind.Rotate, -6), "a1 01 86 77 55")
        assertWrites("cowgirl", intArrayOf(0xC0, 0), command(1, ToyOutputKind.Rotate, 0x80), "00 01 c0 80", true)
        assertWrites("motorbunny", intArrayOf(), command(0, ToyOutputKind.Vibrate, 0x80), "ff 80 14 80 14 80 14 80 14 80 14 80 14 80 14 0c ec")
        assertWrites("motorbunny", intArrayOf(), command(1, ToyOutputKind.Rotate, -0xC0), "af 29 c0 29 c0 29 c0 29 c0 29 c0 29 c0 29 c0 5f ec")
        assertWrites("nexus-revo", intArrayOf(), command(0, ToyOutputKind.Vibrate, 0x05), "aa 01 01 00 01 05", true)
        assertWrites("nexus-revo", intArrayOf(), command(1, ToyOutputKind.Rotate, 0x01), "aa 01 02 00 03 00", true)
        assertWrites("nexus-revo", intArrayOf(), command(1, ToyOutputKind.Rotate, -0x02), "aa 01 02 00 02 00", true)
        assertWrites("sakuraneko", intArrayOf(), command(0, ToyOutputKind.Vibrate, 0x32), "a1 08 01 00 00 00 64 32 00 64 df 55")
        assertWrites("sakuraneko", intArrayOf(), command(1, ToyOutputKind.Rotate, 0x32), "a2 08 01 00 00 00 64 32 00 32 df 55")
        assertWrites("tryfun-meta2", intArrayOf(), command(0, ToyOutputKind.Oscillate, 0x64), "00 02 00 05 21 05 0b 64 6b")
        assertWrites("tryfun-blackhole", intArrayOf(), command(0, ToyOutputKind.Oscillate, 0x64), "00 02 00 03 0c 64 90")
        assertWrites("utimi", intArrayOf(0, 0, 0, 0, 0), command(1, ToyOutputKind.Oscillate, 0x32), "a0 03 00 32 00 00 00 aa")
        assertWrites("sexverse-v2", intArrayOf(), command(1, ToyOutputKind.Oscillate, 0x0A), "aa 03 01 02 64 0a", true)
        assertWrites("sexverse-v3", intArrayOf(), command(1, ToyOutputKind.Rotate, -1), "a1 04 ff 02", true)
        assertWrites("sexverse-lg389", intArrayOf(0x03, 0), command(1, ToyOutputKind.Oscillate, 0x08), "aa 05 03 14 01 00 04 00 08 00", true)
        assertWrites("galaku-pump", intArrayOf(0, 0), command(0, ToyOutputKind.Oscillate, 0x32), "23 81 bb ab d2 9b 44 61 3b a3 3b 44", true)

        assertEquals(100, ScalarProtocolPlans.forProtocolId("sexverse-v3")?.keepaliveIntervalMs)
        assertContentEquals(hex("aa 04"), requireNotNull(ScalarProtocolPlans.forProtocolId("sexverse-v2")).initWrites.single().bytes)
    }

    @Test
    fun scalarHandlerWritesThroughToyTransport() = runBlocking {
        val transport = RecordingTransport()
        val handler = requireNotNull(ScalarProtocolHandlers.forProtocolId("cowgirl"))
        val session = handler.createSession(match("cowgirl"), transport)

        session.handle(ToyOutputCommand.Scalar(featureIndex = 0, kind = ToyOutputKind.Vibrate, value = 0xC0))
        session.handle(ToyOutputCommand.Scalar(featureIndex = 1, kind = ToyOutputKind.Rotate, value = 0x80))
        session.stop()

        assertContentEquals(hex("00 01 c0 00"), transport.writeBytesAt(0))
        assertContentEquals(hex("00 01 c0 80"), transport.writeBytesAt(1))
        assertContentEquals(hex("00 01 00 80"), transport.writeBytesAt(2))
        assertContentEquals(hex("00 01 00 00"), transport.writeBytesAt(3))
    }

    @Test
    fun scalarHandlerRunsInitAndPacketSequence() = runBlocking {
        val initTransport = RecordingTransport()
        val sexverseV2 = requireNotNull(ScalarProtocolHandlers.forProtocolId("sexverse-v2"))
        sexverseV2.createSession(match("sexverse-v2"), initTransport).initialize()

        assertContentEquals(hex("aa 04"), initTransport.writeBytesAt(0))

        val tryfunTransport = RecordingTransport()
        val tryfun = requireNotNull(ScalarProtocolHandlers.forProtocolId("tryfun-meta2"))
        val session = tryfun.createSession(match("tryfun-meta2"), tryfunTransport)

        session.handle(ToyOutputCommand.Scalar(featureIndex = 0, kind = ToyOutputKind.Oscillate, value = 0x64))
        session.handle(ToyOutputCommand.Scalar(featureIndex = 1, kind = ToyOutputKind.Vibrate, value = 0x64))

        assertContentEquals(hex("00 02 00 05 21 05 0b 64 6b"), tryfunTransport.writeBytesAt(0))
        assertContentEquals(hex("01 02 00 05 21 05 08 64 6e"), tryfunTransport.writeBytesAt(1))
    }

    private fun assertWrites(
        protocolId: String,
        initialState: IntArray,
        command: ScalarProtocolCommand,
        expectedHex: String,
        withResponse: Boolean = false,
    ) {
        val plan = requireNotNull(ScalarProtocolPlans.forProtocolId(protocolId))
        val state = IntArray(maxOf(plan.stateSize, initialState.size))
        initialState.copyInto(state)
        val writes = plan.writes(state, command)
        assertEquals(1, writes.size)
        assertEquals(withResponse, writes.single().withResponse)
        assertContentEquals(hex(expectedHex), writes.single().bytes)
        assertTrue(protocolId in ScalarProtocolPlans.protocolIds)
        assertFalse(ScalarProtocolHandlers.forProtocolId(protocolId) == null)
    }

    private fun command(featureIndex: Int, kind: ToyOutputKind, value: Int): ScalarProtocolCommand =
        ScalarProtocolCommand(featureIndex = featureIndex, kind = kind, value = value)

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
                    ButtplugOutputFeature(OUTPUT_VIBRATE, 0, 255, 0, "Vibrate"),
                    ButtplugOutputFeature(OUTPUT_OSCILLATE, 0, 255, 1, "Oscillate"),
                    ButtplugOutputFeature(OUTPUT_ROTATE, -255, 255, 2, "Rotate"),
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
