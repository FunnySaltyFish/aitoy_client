package com.funny.aitoy.buttplug

data class ScalarProtocolWrite(
    val endpointRole: String = "tx",
    val bytes: ByteArray,
    val withResponse: Boolean,
)

data class ScalarProtocolCommand(
    val featureIndex: Int,
    val kind: ToyOutputKind,
    val value: Int,
)

data class ScalarProtocolPlan(
    val protocolId: String,
    val supportedKinds: Set<ToyOutputKind>,
    val stateSize: Int,
    val initWrites: List<ScalarProtocolWrite> = emptyList(),
    val keepaliveIntervalMs: Long = 0L,
    val writes: (state: IntArray, command: ScalarProtocolCommand) -> List<ScalarProtocolWrite>,
) {
    var sequencedWrites: ((state: IntArray, command: ScalarProtocolCommand, packetId: Int) -> List<ScalarProtocolWrite>)? = null

    val usesPacketSequence: Boolean
        get() = sequencedWrites != null

    fun writesFor(state: IntArray, command: ScalarProtocolCommand, packetId: Int): List<ScalarProtocolWrite> =
        sequencedWrites?.invoke(state, command, packetId) ?: writes(state, command)
}

object ScalarProtocolPlans {
    private val plans = listOf(
        ScalarProtocolPlan(
            protocolId = "bananasome",
            supportedKinds = setOf(ToyOutputKind.Oscillate, ToyOutputKind.Vibrate),
            stateSize = 3,
            writes = { state, command ->
                state[command.featureIndex.coerceIn(0, 2)] = command.value
                listOf(write(bytes(0xA0, 0x03, state[0], state[1], state[2])))
            },
        ),
        ScalarProtocolPlan(
            protocolId = "feelingso",
            supportedKinds = setOf(ToyOutputKind.Vibrate, ToyOutputKind.Oscillate),
            stateSize = 2,
            writes = { state, command ->
                val index = if (command.kind == ToyOutputKind.Oscillate) 1 else 0
                state[index] = command.value
                listOf(write(bytes(0xAA, 0x40, 0x03, state[0], state[1], 0x14, 0x19)))
            },
        ),
        ScalarProtocolPlan(
            protocolId = "itoys",
            supportedKinds = setOf(ToyOutputKind.Vibrate, ToyOutputKind.Oscillate, ToyOutputKind.Rotate),
            stateSize = 3,
            writes = { _, command ->
                when (command.kind) {
                    ToyOutputKind.Vibrate -> listOf(write(bytes(0xA0, 0x01, 0x00, 0x00, command.value, 0xFF)))
                    ToyOutputKind.Oscillate -> listOf(
                        write(
                            bytes(
                                0xA0,
                                0x06,
                                if (command.value == 0) 0x00 else 0x01,
                                0x00,
                                if (command.value == 0) 0x00 else 0x01,
                                command.value,
                            ),
                        ),
                    )
                    ToyOutputKind.Rotate -> listOf(
                        write(
                            bytes(
                                0xA0,
                                0x08,
                                if (command.value == 0) 0x00 else 0x01,
                                0x00,
                                command.value.absoluteByte(),
                                if (command.value == 0) 0x00 else 0xFF,
                            ),
                        ),
                    )
                    else -> unsupported(command)
                }
            },
        ),
        ScalarProtocolPlan(
            protocolId = "xibao",
            supportedKinds = setOf(ToyOutputKind.Oscillate),
            stateSize = 1,
            writes = { _, command ->
                listOf(
                    write(
                        bytes(
                            0x66,
                            0x3A,
                            0x00,
                            0x06,
                            0x00,
                            0x06,
                            0x01,
                            0x02,
                            0x00,
                            0x02,
                            0x04,
                            command.value,
                            (command.value + 0xB5) and 0xFF,
                        ),
                    ),
                )
            },
        ),
        ScalarProtocolPlan(
            protocolId = "synchro",
            supportedKinds = setOf(ToyOutputKind.Rotate),
            stateSize = 1,
            writes = { _, command ->
                val value = command.value.absoluteByte() or if (command.value >= 0) 0x00 else 0x80
                listOf(write(bytes(0xA1, 0x01, value, 0x77, 0x55)))
            },
        ),
        ScalarProtocolPlan(
            protocolId = "cowgirl",
            supportedKinds = setOf(ToyOutputKind.Vibrate, ToyOutputKind.Rotate),
            stateSize = 2,
            writes = { state, command ->
                val index = if (command.kind == ToyOutputKind.Rotate) 1 else 0
                state[index] = command.value
                listOf(write(bytes(0x00, 0x01, state[0], state[1]), withResponse = true))
            },
        ),
        ScalarProtocolPlan(
            protocolId = "motorbunny",
            supportedKinds = setOf(ToyOutputKind.Vibrate, ToyOutputKind.Rotate),
            stateSize = 2,
            writes = { _, command ->
                when (command.kind) {
                    ToyOutputKind.Vibrate -> listOf(write(motorbunnyVibrate(command.value)))
                    ToyOutputKind.Rotate -> listOf(write(motorbunnyRotate(command.value)))
                    else -> unsupported(command)
                }
            },
        ),
        ScalarProtocolPlan(
            protocolId = "nexus-revo",
            supportedKinds = setOf(ToyOutputKind.Vibrate, ToyOutputKind.Rotate),
            stateSize = 2,
            writes = { _, command ->
                when (command.kind) {
                    ToyOutputKind.Vibrate -> listOf(
                        write(
                            bytes(0xAA, 0x01, 0x01, 0x00, 0x01, command.value),
                            withResponse = true,
                        ),
                    )
                    ToyOutputKind.Rotate -> listOf(
                        write(
                            bytes(
                                0xAA,
                                0x01,
                                0x02,
                                0x00,
                                command.value.absoluteByte() + if (command.value > 0) 2 else 0,
                                0x00,
                            ),
                            withResponse = true,
                        ),
                    )
                    else -> unsupported(command)
                }
            },
        ),
        ScalarProtocolPlan(
            protocolId = "sakuraneko",
            supportedKinds = setOf(ToyOutputKind.Vibrate, ToyOutputKind.Rotate),
            stateSize = 2,
            writes = { _, command ->
                when (command.kind) {
                    ToyOutputKind.Vibrate -> listOf(
                        write(bytes(0xA1, 0x08, 0x01, 0x00, 0x00, 0x00, 0x64, command.value, 0x00, 0x64, 0xDF, 0x55)),
                    )
                    ToyOutputKind.Rotate -> listOf(
                        write(bytes(0xA2, 0x08, 0x01, 0x00, 0x00, 0x00, 0x64, command.value.rawByte(), 0x00, 0x32, 0xDF, 0x55)),
                    )
                    else -> unsupported(command)
                }
            },
        ),
        ScalarProtocolPlan(
            protocolId = "tryfun-meta2",
            supportedKinds = setOf(ToyOutputKind.Oscillate, ToyOutputKind.Vibrate, ToyOutputKind.Rotate),
            stateSize = 3,
            writes = { _, command -> listOf(write(tryfunMeta2Payload(command, packetId = 0))) },
        ).apply {
            sequencedWrites = { _, command, packetId -> listOf(write(tryfunMeta2Payload(command, packetId))) }
        },
        ScalarProtocolPlan(
            protocolId = "tryfun-blackhole",
            supportedKinds = setOf(ToyOutputKind.Oscillate, ToyOutputKind.Vibrate),
            stateSize = 2,
            writes = { _, command -> listOf(write(tryfunBlackHolePayload(command, packetId = 0))) },
        ).apply {
            sequencedWrites = { _, command, packetId -> listOf(write(tryfunBlackHolePayload(command, packetId))) }
        },
        ScalarProtocolPlan(
            protocolId = "utimi",
            supportedKinds = setOf(ToyOutputKind.Vibrate, ToyOutputKind.Oscillate),
            stateSize = 5,
            writes = { state, command ->
                state[command.featureIndex.coerceIn(0, state.lastIndex)] = command.value
                listOf(write(bytes(0xA0, 0x03, state[0], state[1], state[2], state[3], state[4], 0xAA)))
            },
        ),
        ScalarProtocolPlan(
            protocolId = "sexverse-v2",
            supportedKinds = setOf(ToyOutputKind.Vibrate, ToyOutputKind.Oscillate),
            stateSize = 2,
            initWrites = listOf(write(bytes(0xAA, 0x04), withResponse = true)),
            writes = { _, command ->
                listOf(write(bytes(0xAA, 0x03, 0x01, command.featureIndex + 1, 0x64, command.value), withResponse = true))
            },
        ),
        ScalarProtocolPlan(
            protocolId = "sexverse-v3",
            supportedKinds = setOf(ToyOutputKind.Vibrate, ToyOutputKind.Rotate),
            stateSize = 2,
            keepaliveIntervalMs = 100,
            writes = { _, command ->
                listOf(write(bytes(0xA1, 0x04, command.value.rawByte(), command.featureIndex + 1), withResponse = true))
            },
        ),
        ScalarProtocolPlan(
            protocolId = "sexverse-lg389",
            supportedKinds = setOf(ToyOutputKind.Vibrate, ToyOutputKind.Oscillate),
            stateSize = 2,
            writes = { state, command ->
                val index = if (command.kind == ToyOutputKind.Oscillate) 1 else 0
                state[index] = command.value
                val osc = state[1]
                listOf(
                    write(
                        bytes(
                            0xAA,
                            0x05,
                            state[0],
                            0x14,
                            if (osc == 0) 0x00 else 0x01,
                            0x00,
                            if (osc == 0) 0x00 else 0x04,
                            0x00,
                            osc,
                            0x00,
                        ),
                        withResponse = true,
                    ),
                )
            },
        ),
        ScalarProtocolPlan(
            protocolId = "galaku-pump",
            supportedKinds = setOf(ToyOutputKind.Oscillate, ToyOutputKind.Vibrate),
            stateSize = 2,
            writes = { state, command ->
                val index = if (command.kind == ToyOutputKind.Vibrate) 1 else 0
                state[index] = command.value
                listOf(write(galakuPumpPayload(state[0], state[1]), withResponse = true))
            },
        ),
    ).associateBy { it.protocolId }

    val protocolIds: Set<String> = plans.keys

    fun forProtocolId(protocolId: String): ScalarProtocolPlan? =
        plans[protocolId]

    fun supportedKinds(protocolId: String): Set<ToyOutputKind> =
        plans[protocolId]?.supportedKinds.orEmpty()

    private fun motorbunnyVibrate(speed: Int): ByteArray {
        if (speed == 0) return bytes(0xF0, 0x00, 0x00, 0x00, 0x00, 0xEC)
        val body = mutableListOf<Int>()
        repeat(7) {
            body += speed
            body += 0x14
        }
        val crc = body.fold(0) { acc, value -> (acc + value) and 0xFF }
        return bytes(0xFF, *body.toIntArray(), crc, 0xEC)
    }

    private fun motorbunnyRotate(speed: Int): ByteArray {
        if (speed == 0) return bytes(0xA0, 0x00, 0x00, 0x00, 0x00, 0xEC)
        val body = mutableListOf<Int>()
        repeat(7) {
            body += if (speed >= 0) 0x2A else 0x29
            body += speed.absoluteByte()
        }
        val crc = body.fold(0) { acc, value -> (acc + value) and 0xFF }
        return bytes(0xAF, *body.toIntArray(), crc, 0xEC)
    }

    private fun tryfunMeta2Payload(command: ScalarProtocolCommand, packetId: Int): ByteArray {
        val commandId = when (command.kind) {
            ToyOutputKind.Oscillate -> 0x0B
            ToyOutputKind.Vibrate -> 0x08
            ToyOutputKind.Rotate -> 0x0E
            else -> unsupported(command)
        }
        val value = if (command.kind == ToyOutputKind.Rotate) {
            tryfunRotateValue(command.value)
        } else {
            command.value
        }
        return tryfunPayload(packetId, 0x05, 0x21, 0x05, commandId, value)
    }

    private fun tryfunBlackHolePayload(command: ScalarProtocolCommand, packetId: Int): ByteArray {
        val commandId = when (command.kind) {
            ToyOutputKind.Oscillate -> 0x0C
            ToyOutputKind.Vibrate -> 0x09
            else -> unsupported(command)
        }
        return tryfunPayload(packetId, 0x03, commandId, command.value)
    }

    private fun tryfunPayload(packetId: Int, vararg body: Int): ByteArray {
        val data = mutableListOf(packetId and 0xFF, 0x02, 0x00)
        body.forEach(data::add)
        var sum = 0xFF
        var count = 1
        for (item in data.drop(1)) {
            sum = (sum - (item and 0xFF)) and 0xFF
            count += 1
        }
        data += (sum + count) and 0xFF
        return bytes(*data.toIntArray())
    }

    private fun tryfunRotateValue(value: Int): Int {
        var speed = value.coerceIn(-127, 127)
        if (speed >= 0) speed += 1
        return (-speed) and 0xFF
    }

    private fun galakuPumpPayload(oscillate: Int, vibrate: Int): ByteArray {
        val data = mutableListOf(0x23, 0x5A, 0x00, 0x00, 0x01, 0x60, 0x03, oscillate, vibrate, 0x00, 0x00)
        data += data.fold(0) { acc, value -> (acc + value) and 0xFF }
        val encoded = mutableListOf(0x23)
        for (index in 1 until data.size) {
            val key = GALAKU_PUMP_KEY_TABLE[encoded[index - 1] and 0x03][index]
            encoded += ((((key xor 0x23) xor data[index]) + key) and 0xFF)
        }
        return bytes(*encoded.toIntArray())
    }

    private fun unsupported(command: ScalarProtocolCommand): Nothing =
        error("${command.kind} is not supported by this scalar protocol")

    private fun write(
        bytes: ByteArray,
        endpointRole: String = "tx",
        withResponse: Boolean = false,
    ): ScalarProtocolWrite =
        ScalarProtocolWrite(endpointRole = endpointRole, bytes = bytes, withResponse = withResponse)

    private fun Int.absoluteByte(): Int =
        kotlin.math.abs(this).coerceIn(0, 0xFF)

    private fun Int.rawByte(): Int =
        this and 0xFF

    private fun bytes(vararg values: Int): ByteArray =
        ByteArray(values.size) { index -> values[index].coerceIn(0, 0xFF).toByte() }

    private val GALAKU_PUMP_KEY_TABLE = arrayOf(
        intArrayOf(0, 24, 0x98, 0xF7, 0xA5, 61, 13, 41, 37, 80, 68, 70),
        intArrayOf(0, 69, 110, 106, 111, 120, 32, 83, 45, 49, 46, 55),
        intArrayOf(0, 101, 120, 32, 84, 111, 121, 115, 10, 0x8E, 0x9D, 0xA3),
        intArrayOf(0, 0xC5, 0xD6, 0xE7, 0xF8, 10, 50, 32, 111, 98, 13, 10),
    )
}

object ScalarProtocolHandlers {
    fun forProtocolId(protocolId: String): ToyProtocolHandler? =
        ScalarProtocolPlans.forProtocolId(protocolId)?.let(::ScalarProtocolHandler)
}

private class ScalarProtocolHandler(
    private val plan: ScalarProtocolPlan,
) : ToyProtocolHandler {
    override val protocolId: String = plan.protocolId
    override val supportedOutputKinds: Set<ToyOutputKind> = plan.supportedKinds

    override fun createSession(match: ButtplugDeviceMatch, transport: ToyTransport): ToyProtocolSession =
        ScalarProtocolSession(match, transport, plan)
}

private class ScalarProtocolSession(
    match: ButtplugDeviceMatch,
    private val transport: ToyTransport,
    private val plan: ScalarProtocolPlan,
) : ToyProtocolSession {
    private val state = IntArray(maxOf(plan.stateSize, match.scalarOutputs.size))
    private var packetId = 0
    private val endpointByRole = match.endpoint.characteristics
    private val features = match.scalarOutputs.associateBy { it.featureIndex }

    override val supportEntry: ButtplugSupportEntry =
        ButtplugLocalHandlerRegistry.supportEntryFor(match.protocol)

    override suspend fun initialize() {
        executeWrites(plan.initWrites)
    }

    override suspend fun handle(command: ToyOutputCommand) {
        when (command) {
            is ToyOutputCommand.Scalar -> write(command.featureIndex, command.kind, command.value)
            is ToyOutputCommand.Pattern -> {
                for ((featureIndex, value) in command.values) {
                    val feature = features[featureIndex] ?: continue
                    write(featureIndex, feature.type.toToyOutputKind(), value)
                }
            }
            is ToyOutputCommand.Linear -> error("${plan.protocolId} does not support linear output")
            ToyOutputCommand.Stop -> stop()
        }
    }

    override suspend fun stop() {
        for (feature in features.values.sortedBy { it.featureIndex }) {
            val kind = feature.type.toToyOutputKind()
            if (kind in plan.supportedKinds) {
                write(feature.featureIndex, kind, 0)
            }
        }
    }

    private suspend fun write(featureIndex: Int, kind: ToyOutputKind, value: Int) {
        require(kind in plan.supportedKinds) { "${plan.protocolId} does not support $kind" }
        val feature = features[featureIndex]
        val coerced = value.coerceIn(feature?.min ?: 0, feature?.max ?: 0xFF)
        executeWrites(plan.writesFor(state, ScalarProtocolCommand(featureIndex, kind, coerced), nextPacketId()))
    }

    private fun nextPacketId(): Int {
        if (!plan.usesPacketSequence) return 0
        val current = packetId
        packetId = (packetId + 1) and 0xFF
        return current
    }

    private suspend fun executeWrites(writes: List<ScalarProtocolWrite>) {
        for (write in writes) {
            val endpoint = requireNotNull(endpointByRole[write.endpointRole]) {
                "${plan.protocolId} endpoint ${write.endpointRole} is missing"
            }
            when (val result = transport.execute(HardwareOperation.Write(endpoint, write.bytes, write.withResponse))) {
                HardwareResult.Success,
                is HardwareResult.ReadResult -> Unit
                is HardwareResult.Failure -> error(result.message)
            }
        }
    }
}
