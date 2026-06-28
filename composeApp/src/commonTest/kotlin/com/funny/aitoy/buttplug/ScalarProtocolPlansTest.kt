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
        assertWrites("magic-motion-1", intArrayOf(), command(0, ToyOutputKind.Vibrate, 0x32), "0b ff 04 0a 32 32 00 04 08 32 64 00")
        assertWrites("magic-motion-2", intArrayOf(0x4B, 0), command(1, ToyOutputKind.Vibrate, 0x32), "10 ff 04 0a 32 0a 00 04 08 4b 64 00 04 08 32 64 01")
        assertWrites("sakuraneko", intArrayOf(), command(0, ToyOutputKind.Vibrate, 0x32), "a1 08 01 00 00 00 64 32 00 64 df 55")
        assertWrites("sakuraneko", intArrayOf(), command(1, ToyOutputKind.Rotate, 0x32), "a2 08 01 00 00 00 64 32 00 32 df 55")
        assertWrites("tryfun-meta2", intArrayOf(), command(0, ToyOutputKind.Oscillate, 0x64), "00 02 00 05 21 05 0b 64 6b")
        assertWrites("tryfun-blackhole", intArrayOf(), command(0, ToyOutputKind.Oscillate, 0x64), "00 02 00 03 0c 64 90")
        assertWrites("tryfun", intArrayOf(), command(0, ToyOutputKind.Oscillate, 0x05), "aa 02 07 05 f4", true)
        assertWrites("tryfun", intArrayOf(), command(1, ToyOutputKind.Rotate, 0x06), "aa 02 08 06 f2", true)
        assertWrites("tryfun", intArrayOf(), command(0, ToyOutputKind.Vibrate, 0x04), "00 02 00 05 02 04 01 00 f9", true)
        assertWrites("tryfun", intArrayOf(), command(0, ToyOutputKind.Vibrate, 0), "00 02 00 05 01 02 01 01 fc", true)
        assertWrites("fredorch-rotary", intArrayOf(0, 0), command(0, ToyOutputKind.Oscillate, 3), "55 03 01 04 aa")
        assertWrites("fredorch-rotary", intArrayOf(5, 0), command(0, ToyOutputKind.Oscillate, 3), "55 03 02 05 aa")
        assertWrites("fredorch-rotary", intArrayOf(5, 0), command(0, ToyOutputKind.Oscillate, 0), "55 03 24 27 aa")
        assertWrites("utimi", intArrayOf(0, 0, 0, 0, 0), command(1, ToyOutputKind.Oscillate, 0x32), "a0 03 00 32 00 00 00 aa")
        assertWrites("luvmazer", intArrayOf(), command(0, ToyOutputKind.Vibrate, 0x32), "a0 01 00 00 64 32")
        assertWrites("luvmazer", intArrayOf(), command(2, ToyOutputKind.Vibrate, 0x32), "a0 0c 00 00 64 32")
        assertWrites("luvmazer", intArrayOf(), command(1, ToyOutputKind.Oscillate, 0x32), "a0 06 01 00 64 32")
        assertWrites("luvmazer", intArrayOf(), command(1, ToyOutputKind.Rotate, 0x32), "a0 0f 00 00 64 32")
        assertWrites("luvmazer", intArrayOf(), command(2, ToyOutputKind.Constrict, 0), "a0 0d 00 00 00 00")
        assertWrites("luvmazer", intArrayOf(), command(2, ToyOutputKind.Constrict, 0x20), "a0 0d 00 00 14 20")
        assertWrites("hismith", intArrayOf(), command(0, ToyOutputKind.Oscillate, 0x10), "aa 04 10 14")
        assertWrites("hismith", intArrayOf(), command(1, ToyOutputKind.Vibrate, 0), "aa 06 f0 f6")
        assertWrites("hismith-mini", intArrayOf(1, 0), command(0, ToyOutputKind.Vibrate, 0x10), "cc 03 10 13")
        assertWrites("hismith-mini", intArrayOf(1, 1), command(1, ToyOutputKind.Constrict, 0x10), "cc 05 10 15")
        assertWrites("hismith-mini", intArrayOf(), command(0, ToyOutputKind.Spray, 1), "cc 0b 01 0c")
        assertWrites("sexverse-v2", intArrayOf(), command(1, ToyOutputKind.Oscillate, 0x0A), "aa 03 01 02 64 0a", true)
        assertWrites("sexverse-v3", intArrayOf(), command(1, ToyOutputKind.Rotate, -1), "a1 04 ff 02", true)
        assertWrites("sexverse-lg389", intArrayOf(0x03, 0), command(1, ToyOutputKind.Oscillate, 0x08), "aa 05 03 14 01 00 04 00 08 00", true)
        assertWrites("galaku-pump", intArrayOf(0, 0), command(0, ToyOutputKind.Oscillate, 0x32), "23 81 bb ab d2 9b 44 61 3b a3 3b 44", true)
        assertWrites("svakom-v3", intArrayOf(), command(0, ToyOutputKind.Vibrate, 3), "55 03 03 00 01 03")
        assertWrites("svakom-v3", intArrayOf(), command(1, ToyOutputKind.Rotate, 1), "55 08 00 00 01 ff")
        assertWrites("svakom-avaneo", intArrayOf(), command(1, ToyOutputKind.Oscillate, 1), "55 08 00 00 01 ff")
        assertWrites("svakom-barnard", intArrayOf(), command(0, ToyOutputKind.Vibrate, 2), "55 03 00 00 02 01")
        assertWrites("svakom-barnard", intArrayOf(), command(1, ToyOutputKind.Oscillate, 2), "55 08 00 00 02 ff")
        assertWrites("svakom-jordan", intArrayOf(), command(1, ToyOutputKind.Oscillate, 2), "55 08 00 00 01 02 00")
        assertWrites("svakom-v5", intArrayOf(5, 0, 0), command(1, ToyOutputKind.Vibrate, 0), "55 03 01 00 01 05")
        assertWrites("svakom-v5", intArrayOf(), command(2, ToyOutputKind.Oscillate, 3), "55 09 00 00 03 00")
        assertWrites("svakom-dt250a", intArrayOf(), command(0, ToyOutputKind.Vibrate, 2), "55 03 00 00 02 01")
        assertWrites("svakom-dt250a", intArrayOf(), command(1, ToyOutputKind.Vibrate, 2), "55 08 00 00 02 01")
        assertWrites("svakom-dt250a", intArrayOf(), command(2, ToyOutputKind.Constrict, 2), "55 09 00 00 02 00")
        assertWrites("svakom-sam", intArrayOf(0), command(0, ToyOutputKind.Vibrate, 3), "12 01 03 00 05 03")
        assertWrites("svakom-sam", intArrayOf(1), command(0, ToyOutputKind.Vibrate, 3), "12 01 03 00 04 03")
        assertWrites("svakom-sam", intArrayOf(1), command(0, ToyOutputKind.Vibrate, 0), "12 01 03 00 00 00")
        assertWrites("svakom-sam", intArrayOf(1), command(1, ToyOutputKind.Constrict, 1), "12 06 01 01")
        assertWrites("svakom-sam2", intArrayOf(), command(0, ToyOutputKind.Vibrate, 3), "55 03 00 00 05 03 00", true)
        assertWrites("svakom-sam2", intArrayOf(), command(0, ToyOutputKind.Vibrate, 0), "55 03 00 00 00 00 00", true)
        assertWrites("svakom-sam2", intArrayOf(), command(1, ToyOutputKind.Constrict, 3), "55 09 00 00 01 03 00", true)
        assertWrites("svakom-sam2", intArrayOf(), command(1, ToyOutputKind.Constrict, 0), "55 09 00 00 00 00 00", true)

        assertEquals(100, ScalarProtocolPlans.forProtocolId("sexverse-v3")?.keepaliveIntervalMs)
        assertEquals(100, ScalarProtocolPlans.forProtocolId("svakom-v3")?.keepaliveIntervalMs)
        assertEquals(100, ScalarProtocolPlans.forProtocolId("svakom-barnard")?.keepaliveIntervalMs)
        assertEquals(100, ScalarProtocolPlans.forProtocolId("svakom-jordan")?.keepaliveIntervalMs)
        assertEquals(100, ScalarProtocolPlans.forProtocolId("svakom-v5")?.keepaliveIntervalMs)
        assertEquals(100, ScalarProtocolPlans.forProtocolId("svakom-sam")?.keepaliveIntervalMs)
        assertEquals(100, ScalarProtocolPlans.forProtocolId("svakom-sam2")?.keepaliveIntervalMs)
        assertEquals(100, ScalarProtocolPlans.forProtocolId("fredorch-rotary")?.keepaliveIntervalMs)
        assertEquals("rx", requireNotNull(ScalarProtocolPlans.forProtocolId("svakom-sam")).initSubscriptions.single())
        assertContentEquals(hex("aa 04"), requireNotNull(ScalarProtocolPlans.forProtocolId("sexverse-v2")).initWrites.single().bytes)
        assertEquals("tx", requireNotNull(ScalarProtocolPlans.forProtocolId("sensee-v2")).initReads.single().endpointRole)

        val fredorchRotary = requireNotNull(ScalarProtocolPlans.forProtocolId("fredorch-rotary"))
        assertEquals("rx", fredorchRotary.initSubscriptions.single())
        assertContentEquals(hex("55 03 99 9c aa"), fredorchRotary.initWrites[0].bytes)
        assertContentEquals(hex("55 09 21 00 00 00 00 00 00 2a aa"), fredorchRotary.initWrites[1].bytes)
        assertContentEquals(hex("55 03 1f 22 aa"), fredorchRotary.initWrites[2].bytes)
        assertContentEquals(hex("55 03 24 27 aa"), fredorchRotary.initWrites[3].bytes)
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

    @Test
    fun sexverseV1HandlerKeepsTypedMultiOutputState() = runBlocking {
        val transport = RecordingTransport()
        val handler = requireNotNull(ScalarProtocolHandlers.forProtocolId("sexverse-v1"))
        val session = handler.createSession(
            match(
                "sexverse-v1",
                listOf(
                    ButtplugOutputFeature(OUTPUT_VIBRATE, 0, 255, 0, "Vibrate"),
                    ButtplugOutputFeature(OUTPUT_CONSTRICT, 0, 255, 1, "Constrict"),
                    ButtplugOutputFeature(OUTPUT_ROTATE, 0, 255, 2, "Rotate"),
                ),
            ),
            transport,
        )

        session.handle(ToyOutputCommand.Scalar(featureIndex = 0, kind = ToyOutputKind.Vibrate, value = 0x11))
        session.handle(ToyOutputCommand.Scalar(featureIndex = 1, kind = ToyOutputKind.Constrict, value = 0x22))
        session.handle(ToyOutputCommand.Scalar(featureIndex = 2, kind = ToyOutputKind.Rotate, value = 0x33))

        assertContentEquals(hex("23 07 09 81 03 11 82 04 00 83 06 00 bd"), transport.writeBytesAt(0))
        assertContentEquals(hex("23 07 09 81 03 11 82 04 22 83 06 00 9f"), transport.writeBytesAt(1))
        assertContentEquals(hex("23 07 09 81 03 11 82 04 22 83 06 33 ac"), transport.writeBytesAt(2))
    }

    @Test
    fun senseeV2ReadsDeviceTypeAndKeepsTypedGroups() = runBlocking {
        val features = listOf(
            ButtplugOutputFeature(OUTPUT_VIBRATE, 0, 100, 0, "Vibrate"),
            ButtplugOutputFeature(OUTPUT_OSCILLATE, 0, 100, 1, "Oscillate"),
            ButtplugOutputFeature(OUTPUT_CONSTRICT, 0, 100, 2, "Constrict"),
        )
        val plan = requireNotNull(ScalarProtocolPlans.forProtocolId("sensee-v2"))
        val state = plan.createState(match("sensee-v2", features))

        val first = plan.writes(state, command(0, ToyOutputKind.Vibrate, 10)).single()
        assertContentEquals(hex("55 aa f0 02 00 11 65 f1 03 00 01 01 0a 01 01 01 00 02 01 01 00 00 00"), first.bytes)

        plan.onRead?.invoke(state, "tx", byteArrayOf(0, 0, 0, 0, 0, 0, 0x66))
        val second = plan.writes(state, command(1, ToyOutputKind.Oscillate, 20)).single()
        val third = plan.writes(state, command(2, ToyOutputKind.Constrict, 30)).single()

        assertContentEquals(hex("55 aa f0 02 00 11 66 f1 03 00 01 01 0a 01 01 01 14 02 01 01 00 00 00"), second.bytes)
        assertContentEquals(hex("55 aa f0 02 00 11 66 f1 03 00 01 01 0a 01 01 01 14 02 01 01 1e 00 00"), third.bytes)
    }

    @Test
    fun scalarHandlerRunsInitReads() = runBlocking {
        val transport = RecordingTransport(readBytes = byteArrayOf(0, 0, 0, 0, 0, 0, 0x66))
        val handler = requireNotNull(ScalarProtocolHandlers.forProtocolId("sensee-v2"))
        val session = handler.createSession(
            match(
                "sensee-v2",
                listOf(ButtplugOutputFeature(OUTPUT_VIBRATE, 0, 100, 0, "Vibrate")),
            ),
            transport,
        )

        session.initialize()
        session.handle(ToyOutputCommand.Scalar(featureIndex = 0, kind = ToyOutputKind.Vibrate, value = 10))

        assertEquals(HardwareOperation.Read("tx"), transport.operations[0])
        assertContentEquals(hex("55 aa f0 02 00 09 66 f1 01 00 01 01 0a 00 00"), transport.writeBytesAt(1))
    }

    @Test
    fun scalarHandlerRunsInitSubscriptions() = runBlocking {
        val transport = RecordingTransport()
        val handler = requireNotNull(ScalarProtocolHandlers.forProtocolId("svakom-sam"))
        val session = handler.createSession(
            match(
                "svakom-sam",
                listOf(
                    ButtplugOutputFeature(OUTPUT_VIBRATE, 0, 10, 0, "Vibrate"),
                    ButtplugOutputFeature(OUTPUT_CONSTRICT, 0, 1, 1, "Constrict"),
                ),
                mapOf("tx" to "tx", "rx" to "rx", "txmode" to "txmode"),
            ),
            transport,
        )

        session.initialize()
        session.handle(ToyOutputCommand.Scalar(featureIndex = 0, kind = ToyOutputKind.Vibrate, value = 3))

        assertEquals(HardwareOperation.Subscribe("rx"), transport.operations[0])
        assertContentEquals(hex("12 01 03 00 04 03"), transport.writeBytesAt(1))
    }

    @Test
    fun fredorchRotaryHandlerUsesDynamicKeepaliveSteps() = runBlocking {
        val transport = RecordingTransport()
        val handler = requireNotNull(ScalarProtocolHandlers.forProtocolId("fredorch-rotary"))
        val session = handler.createSession(
            match(
                "fredorch-rotary",
                listOf(ButtplugOutputFeature(OUTPUT_OSCILLATE, 0, 20, 0, "Oscillate")),
                mapOf("tx" to "tx", "rx" to "rx"),
            ),
            transport,
        )
        val plan = requireNotNull(ScalarProtocolPlans.forProtocolId("fredorch-rotary"))
        val state = plan.createState(match("fredorch-rotary"))

        session.initialize()
        session.handle(ToyOutputCommand.Scalar(featureIndex = 0, kind = ToyOutputKind.Oscillate, value = 2))
        val secondStep = requireNotNull(plan.keepaliveWritesFor(state.apply {
            this[0] = 1
            this[1] = 2
        })).single()
        val done = requireNotNull(plan.keepaliveWritesFor(state.apply {
            this[0] = 2
            this[1] = 2
        }))

        assertEquals(HardwareOperation.Subscribe("rx"), transport.operations[0])
        assertContentEquals(hex("55 03 99 9c aa"), transport.writeBytesAt(1))
        assertContentEquals(hex("55 03 01 04 aa"), transport.writeBytesAt(5))
        assertContentEquals(hex("55 03 01 04 aa"), secondStep.bytes)
        assertTrue(done.isEmpty())
    }

    @Test
    fun hismithMiniHandlerSupportsRotateAndSprayStopWithoutTriggeringSpray() = runBlocking {
        val transport = RecordingTransport()
        val handler = requireNotNull(ScalarProtocolHandlers.forProtocolId("hismith-mini"))
        val session = handler.createSession(
            match(
                "hismith-mini",
                listOf(
                    ButtplugOutputFeature(OUTPUT_ROTATE, -100, 100, 0, "Rotate"),
                    ButtplugOutputFeature(OUTPUT_SPRAY, 0, 1, 1, "Spray"),
                ),
            ),
            transport,
        )

        session.handle(ToyOutputCommand.Scalar(featureIndex = 0, kind = ToyOutputKind.Rotate, value = -7))
        session.handle(ToyOutputCommand.Scalar(featureIndex = 1, kind = ToyOutputKind.Spray, value = 1))
        session.stop()

        assertContentEquals(hex("cc 03 07 0a"), transport.writeBytesAt(0))
        assertContentEquals(hex("cc 01 c1 c2"), transport.writeBytesAt(1))
        assertContentEquals(hex("cc 0b 01 0c"), transport.writeBytesAt(2))
        assertContentEquals(hex("cc 03 00 03"), transport.writeBytesAt(3))
        assertContentEquals(hex("cc 01 c0 c1"), transport.writeBytesAt(4))
        assertEquals(5, transport.operations.size)
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
        return match(
            protocolId,
            listOf(
                ButtplugOutputFeature(OUTPUT_VIBRATE, 0, 255, 0, "Vibrate"),
                ButtplugOutputFeature(OUTPUT_OSCILLATE, 0, 255, 1, "Oscillate"),
                ButtplugOutputFeature(OUTPUT_ROTATE, -255, 255, 2, "Rotate"),
                ButtplugOutputFeature(OUTPUT_CONSTRICT, 0, 255, 3, "Constrict"),
            ),
        )
    }

    private fun match(protocolId: String, features: List<ButtplugOutputFeature>): ButtplugDeviceMatch {
        return match(protocolId, features, mapOf("tx" to "tx"))
    }

    private fun match(
        protocolId: String,
        features: List<ButtplugOutputFeature>,
        endpointCharacteristics: Map<String, String>,
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

    private class RecordingTransport(
        private val readBytes: ByteArray = byteArrayOf(),
    ) : ToyTransport {
        val operations = mutableListOf<HardwareOperation>()

        override suspend fun execute(operation: HardwareOperation): HardwareResult {
            operations += operation
            if (operation is HardwareOperation.Read) {
                return HardwareResult.ReadResult(readBytes)
            }
            return HardwareResult.Success
        }

        fun writeBytesAt(index: Int): ByteArray =
            (operations[index] as HardwareOperation.Write).bytes
    }

    private fun hex(value: String): ByteArray =
        value.split(" ").map { it.toInt(16).toByte() }.toByteArray()
}
