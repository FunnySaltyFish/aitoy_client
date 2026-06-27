package com.funny.aitoy.buttplug

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StatefulVibrateProtocolPlansTest {
    @Test
    fun statefulPlansMatchPortedButtplugPayloads() {
        assertWrites("htk_bm", intArrayOf(0, 0), 0, "0f", false)
        assertWrites("htk_bm", intArrayOf(3, 0), 0, "0c", false)
        assertWrites("htk_bm", intArrayOf(3, 4), 1, "0b", false)
        assertWrites("kiiroo-powershot", intArrayOf(4, 7), 1, "01 00 00 04 07 00", true)
        assertWrites("kiiroo-v2-vibrator", intArrayOf(1, 2, 3), 2, "01 02 03", false)
        assertWrites("lelo-f1s", intArrayOf(75, 50), 1, "01 4b 32", false)
        assertWrites("libo-elle", intArrayOf(3, 0), 0, "03", false, endpointRole = "txmode")
        assertWrites("libo-elle", intArrayOf(0, 4), 1, "31", false)
        assertWrites("libo-elle", intArrayOf(0, 8), 1, "04", false)
        assertWrites("libo-shark", intArrayOf(2, 3), 1, "23", false)
        assertWrites("libo-vibes", intArrayOf(4, 0), 0, "04", false)
        assertWrites("libo-vibes", intArrayOf(0, 5), 1, "05", false, endpointRole = "txmode")
        assertWrites("lovehoney-desire", intArrayOf(7), 0, "f3 00 07", true)
        assertWrites("lovehoney-desire", intArrayOf(7, 7), 1, "f3 00 07", true)
        assertWrites(
            protocolId = "lovehoney-desire",
            speeds = intArrayOf(7, 8),
            changedFeatureIndex = 1,
            expectedHex = "f3 01 07|f3 02 08",
            withResponse = true,
        )
        assertWrites("patoo", intArrayOf(7, 0), 0, "07|04", true, endpointRoles = listOf("tx", "txmode"))
        assertWrites("patoo", intArrayOf(0, 8), 1, "08|80", true, endpointRoles = listOf("tx", "txmode"))
        assertInitWrite("mysteryvibe", "43 02 00", true, endpointRole = "txmode")
        assertWrites("mysteryvibe", intArrayOf(7, 8), 1, "07 08", false, endpointRole = "txvibrate")
        assertInitWrite("mysteryvibe-v2", "03 02 40", true, endpointRole = "txmode")
        assertWrites("mysteryvibe-v2", intArrayOf(7), 0, "07", false, endpointRole = "txvibrate")
        assertWrites(
            protocolId = "magic-motion-4",
            speeds = intArrayOf(8, 9),
            changedFeatureIndex = 1,
            expectedHex = "10 ff 04 0a 32 32 00 04 08 08 64 00 04 08 09 64 01",
            withResponse = true,
        )
        assertWrites("svakom-barney", intArrayOf(5, 0), 0, "55 03 01 00 03 05 00", false)
        assertWrites("svakom-iker", intArrayOf(5, 0), 0, "55 03 03 00 01 05", false)
        assertWrites("svakom-iker", intArrayOf(0, 0), 0, "55 07 00 00 00 00", false)
        assertWrites("svakom-v4", intArrayOf(5, 5), 1, "55 03 00 00 01 05 00", false)
        assertWrites("svakom-v6", intArrayOf(5, 0, 0), 0, "55 03 01 00 01 05 00", false)
        assertWrites("svakom-v6", intArrayOf(0, 0, 4), 2, "55 07 00 00 01 04 00", false)
        assertWrites("zalo", intArrayOf(0, 0), 0, "02 01 01", true)
        assertWrites("zalo", intArrayOf(3, 4), 1, "01 03 04", true)
        assertWrites(
            protocolId = "vibratissimo",
            speeds = intArrayOf(7),
            changedFeatureIndex = 0,
            expectedHex = "03 ff|07 00",
            withResponse = false,
            endpointRoles = listOf("txmode", "txvibrate"),
        )
        assertWrites(
            protocolId = "vibratissimo",
            speeds = intArrayOf(7, 8),
            changedFeatureIndex = 1,
            expectedHex = "03 ff|07 08",
            withResponse = false,
            endpointRoles = listOf("txmode", "txvibrate"),
        )
        assertWrites("youou", intArrayOf(9), 0, "aa 55 00 02 03 01 09 01 f7 ff 00 00 00 00 00 00 00", false)
        assertEquals(93, StatefulVibrateProtocolPlans.forProtocolId("mysteryvibe")?.keepaliveIntervalMs)
        assertEquals(93, StatefulVibrateProtocolPlans.forProtocolId("mysteryvibe-v2")?.keepaliveIntervalMs)
    }

    @Test
    fun statefulHandlerWritesThroughToyTransport() = runBlocking {
        val transport = RecordingTransport()
        val handler = requireNotNull(StatefulVibrateProtocolHandlers.forProtocolId("zalo"))
        val session = handler.createSession(match("zalo", channels = 2), transport)

        session.handle(ToyOutputCommand.Scalar(featureIndex = 0, kind = ToyOutputKind.Vibrate, value = 3))
        session.handle(ToyOutputCommand.Scalar(featureIndex = 1, kind = ToyOutputKind.Vibrate, value = 4))
        session.stop()

        assertContentEquals(hex("01 03 01"), transport.writeBytesAt(0))
        assertContentEquals(hex("01 03 04"), transport.writeBytesAt(1))
        assertContentEquals(hex("02 01 01"), transport.writeBytesAt(2))
    }

    @Test
    fun statefulHandlerRunsInitSubscriptions() = runBlocking {
        val transport = RecordingTransport()
        val handler = requireNotNull(StatefulVibrateProtocolHandlers.forProtocolId("lelo-f1s"))
        val session = handler.createSession(match("lelo-f1s", channels = 2), transport)

        session.initialize()

        assertEquals(HardwareOperation.Subscribe("rx"), transport.operations.single())
    }

    @Test
    fun statefulHandlerAdvancesPacketSequence() = runBlocking {
        val transport = RecordingTransport()
        val handler = requireNotNull(StatefulVibrateProtocolHandlers.forProtocolId("youou"))
        val session = handler.createSession(match("youou", channels = 1), transport)

        session.handle(ToyOutputCommand.Scalar(featureIndex = 0, kind = ToyOutputKind.Vibrate, value = 9))
        session.handle(ToyOutputCommand.Scalar(featureIndex = 0, kind = ToyOutputKind.Vibrate, value = 10))

        assertContentEquals(hex("aa 55 00 02 03 01 09 01 f7 ff 00 00 00 00 00 00 00"), transport.writeBytesAt(0))
        assertContentEquals(hex("aa 55 01 02 03 01 0a 01 f5 ff 00 00 00 00 00 00 00"), transport.writeBytesAt(1))
    }

    private fun assertWrites(
        protocolId: String,
        speeds: IntArray,
        changedFeatureIndex: Int,
        expectedHex: String,
        withResponse: Boolean,
        endpointRole: String = "tx",
        endpointRoles: List<String>? = null,
    ) {
        val plan = requireNotNull(StatefulVibrateProtocolPlans.forProtocolId(protocolId))
        val writes = plan.writes(speeds, changedFeatureIndex)
        val expectedWrites = expectedHex.split("|")
        assertEquals(expectedWrites.size, writes.size)
        writes.forEachIndexed { index, write ->
            assertEquals(withResponse, write.withResponse)
            assertEquals(endpointRoles?.get(index) ?: endpointRole, write.endpointRole)
            assertContentEquals(hex(expectedWrites[index]), write.bytes)
        }
        assertTrue(protocolId in StatefulVibrateProtocolPlans.protocolIds)
        assertFalse(StatefulVibrateProtocolHandlers.forProtocolId(protocolId) == null)
    }

    private fun assertInitWrite(
        protocolId: String,
        expectedHex: String,
        withResponse: Boolean,
        endpointRole: String,
    ) {
        val plan = requireNotNull(StatefulVibrateProtocolPlans.forProtocolId(protocolId))
        val write = plan.initWrites.single()
        assertEquals(endpointRole, write.endpointRole)
        assertEquals(withResponse, write.withResponse)
        assertContentEquals(hex(expectedHex), write.bytes)
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
                    characteristics = mapOf("tx" to "tx", "rx" to "rx"),
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
