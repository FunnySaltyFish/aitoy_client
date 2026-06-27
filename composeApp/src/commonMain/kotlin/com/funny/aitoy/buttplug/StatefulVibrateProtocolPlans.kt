package com.funny.aitoy.buttplug

data class StatefulVibrateWrite(
    val endpointRole: String = "tx",
    val bytes: ByteArray,
    val withResponse: Boolean,
)

data class StatefulVibrateProtocolPlan(
    val protocolId: String,
    val minimumChannels: Int,
    val initSubscriptions: List<String> = emptyList(),
    val initWrites: List<StatefulVibrateWrite> = emptyList(),
    val keepaliveIntervalMs: Long = 0L,
    val writes: (speeds: IntArray, changedFeatureIndex: Int) -> List<StatefulVibrateWrite>,
) {
    var sequencedWrites: ((speeds: IntArray, changedFeatureIndex: Int, packetId: Int) -> List<StatefulVibrateWrite>)? = null

    val usesPacketSequence: Boolean
        get() = sequencedWrites != null

    fun writesFor(speeds: IntArray, changedFeatureIndex: Int, packetId: Int): List<StatefulVibrateWrite> =
        sequencedWrites?.invoke(speeds, changedFeatureIndex, packetId) ?: writes(speeds, changedFeatureIndex)
}

object StatefulVibrateProtocolPlans {
    private val plans = listOf(
        StatefulVibrateProtocolPlan(
            protocolId = "kiiroo-powershot",
            minimumChannels = 2,
            writes = { speeds, _ ->
                listOf(write(bytes(0x01, 0x00, 0x00, speeds[0], speeds[1], 0x00), withResponse = true))
            },
        ),
        StatefulVibrateProtocolPlan(
            protocolId = "kiiroo-v2-vibrator",
            minimumChannels = 3,
            writes = { speeds, _ ->
                listOf(write(bytes(speeds[0], speeds[1], speeds[2])))
            },
        ),
        StatefulVibrateProtocolPlan(
            protocolId = "lelo-f1s",
            minimumChannels = 2,
            initSubscriptions = listOf("rx"),
            writes = { speeds, _ ->
                listOf(write(bytes(0x01, speeds[0], speeds[1])))
            },
        ),
        StatefulVibrateProtocolPlan(
            protocolId = "htk_bm",
            minimumChannels = 2,
            writes = { speeds, _ ->
                val left = speeds[0]
                val right = speeds[1]
                val data = when {
                    left != 0 && right != 0 -> 11
                    left != 0 -> 12
                    right != 0 -> 13
                    else -> 15
                }
                listOf(write(bytes(data)))
            },
        ),
        StatefulVibrateProtocolPlan(
            protocolId = "libo-elle",
            minimumChannels = 2,
            writes = { speeds, changedFeatureIndex ->
                val speed = speeds[changedFeatureIndex.coerceIn(0, 1)]
                if (changedFeatureIndex == 1) {
                    val data = when {
                        speed in 1..7 -> ((speed - 1) shl 4) or 0x01
                        speed > 7 -> ((speed - 8) shl 4) or 0x04
                        else -> 0
                    }
                    listOf(write(bytes(data)))
                } else {
                    listOf(write(bytes(speed), endpointRole = "txmode"))
                }
            },
        ),
        StatefulVibrateProtocolPlan(
            protocolId = "libo-shark",
            minimumChannels = 2,
            writes = { speeds, _ ->
                listOf(write(bytes((speeds[0] shl 4) or speeds[1])))
            },
        ),
        StatefulVibrateProtocolPlan(
            protocolId = "libo-vibes",
            minimumChannels = 2,
            writes = { speeds, changedFeatureIndex ->
                val index = changedFeatureIndex.coerceIn(0, 1)
                if (index == 0) {
                    buildList {
                        add(write(bytes(speeds[0])))
                        if (speeds[0] == 0) {
                            add(write(bytes(0), endpointRole = "txmode"))
                        }
                    }
                } else {
                    listOf(write(bytes(speeds[1]), endpointRole = "txmode"))
                }
            },
        ),
        StatefulVibrateProtocolPlan(
            protocolId = "lovehoney-desire",
            minimumChannels = 1,
            writes = { speeds, _ ->
                if (speeds.size == 1) {
                    listOf(write(bytes(0xF3, 0x00, speeds[0]), withResponse = true))
                } else {
                    val speed0 = speeds[0]
                    val speed1 = speeds[1]
                    if (speed0 == speed1) {
                        listOf(write(bytes(0xF3, 0x00, speed0), withResponse = true))
                    } else {
                        listOf(
                            write(bytes(0xF3, 0x01, speed0), withResponse = true),
                            write(bytes(0xF3, 0x02, speed1), withResponse = true),
                        )
                    }
                }
            },
        ),
        StatefulVibrateProtocolPlan(
            protocolId = "magic-motion-4",
            minimumChannels = 1,
            writes = { speeds, _ ->
                val speed0 = speeds[0]
                val speed1 = speeds.getOrElse(1) { speed0 }
                listOf(
                    write(
                        bytes(
                            0x10,
                            0xFF,
                            0x04,
                            0x0A,
                            0x32,
                            0x32,
                            0x00,
                            0x04,
                            0x08,
                            speed0,
                            0x64,
                            0x00,
                            0x04,
                            0x08,
                            speed1,
                            0x64,
                            0x01,
                        ),
                        withResponse = true,
                    ),
                )
            },
        ),
        StatefulVibrateProtocolPlan(
            protocolId = "patoo",
            minimumChannels = 2,
            writes = { speeds, _ ->
                var mode = 0x04
                var speed = speeds[0]
                if (speed == 0) {
                    mode = 0x00
                    if (speeds[1] != 0) {
                        speed = speeds[1]
                        mode = mode or 0x80
                    }
                }
                listOf(
                    write(bytes(speed), withResponse = true),
                    write(bytes(mode), endpointRole = "txmode", withResponse = true),
                )
            },
        ),
        StatefulVibrateProtocolPlan(
            protocolId = "mysteryvibe",
            minimumChannels = 1,
            initWrites = listOf(write(bytes(0x43, 0x02, 0x00), endpointRole = "txmode", withResponse = true)),
            keepaliveIntervalMs = 93,
            writes = { speeds, _ ->
                listOf(write(bytes(*speeds), endpointRole = "txvibrate"))
            },
        ),
        StatefulVibrateProtocolPlan(
            protocolId = "mysteryvibe-v2",
            minimumChannels = 1,
            initWrites = listOf(write(bytes(0x03, 0x02, 0x40), endpointRole = "txmode", withResponse = true)),
            keepaliveIntervalMs = 93,
            writes = { speeds, _ ->
                listOf(write(bytes(*speeds), endpointRole = "txvibrate"))
            },
        ),
        StatefulVibrateProtocolPlan(
            protocolId = "svakom-barney",
            minimumChannels = 2,
            writes = { speeds, _ ->
                listOf(svakomDualMotorWrite(speeds, activeMarker = 0x03))
            },
        ),
        StatefulVibrateProtocolPlan(
            protocolId = "svakom-iker",
            minimumChannels = 2,
            writes = { speeds, _ ->
                val speed0 = speeds[0]
                val speed1 = speeds[1]
                if (speed0 == 0 && speed1 == 0) {
                    listOf(write(bytes(0x55, 0x07, 0x00, 0x00, 0x00, 0x00)))
                } else {
                    buildList {
                        add(write(bytes(0x55, 0x03, 0x03, 0x00, 0x01, speed0)))
                        if (speed1 > 0) {
                            add(write(bytes(0x55, 0x07, 0x00, 0x00, speed1, 0x00)))
                        }
                    }
                }
            },
        ),
        StatefulVibrateProtocolPlan(
            protocolId = "svakom-v4",
            minimumChannels = 2,
            writes = { speeds, _ ->
                listOf(svakomDualMotorWrite(speeds, activeMarker = 0x01))
            },
        ),
        StatefulVibrateProtocolPlan(
            protocolId = "svakom-v6",
            minimumChannels = 3,
            writes = { speeds, changedFeatureIndex ->
                if (changedFeatureIndex < 2) {
                    listOf(svakomDualMotorWrite(speeds, activeMarker = 0x01))
                } else {
                    val speed2 = speeds[2]
                    listOf(write(bytes(0x55, 0x07, 0x00, 0x00, if (speed2 == 0) 0x00 else 0x01, speed2, 0x00)))
                }
            },
        ),
        StatefulVibrateProtocolPlan(
            protocolId = "zalo",
            minimumChannels = 2,
            writes = { speeds, _ ->
                val speed0 = speeds[0]
                val speed1 = speeds[1]
                listOf(
                    write(
                        bytes(
                            if (speed0 == 0 && speed1 == 0) 0x02 else 0x01,
                            if (speed0 == 0) 0x01 else speed0,
                            if (speed1 == 0) 0x01 else speed1,
                        ),
                        withResponse = true,
                    ),
                )
            },
        ),
        StatefulVibrateProtocolPlan(
            protocolId = "vibratissimo",
            minimumChannels = 1,
            writes = { speeds, _ ->
                val data = if (speeds.size == 1) {
                    bytes(speeds[0], 0x00)
                } else {
                    bytes(*speeds)
                }
                listOf(
                    write(bytes(0x03, 0xFF), endpointRole = "txmode"),
                    write(data, endpointRole = "txvibrate"),
                )
            },
        ),
        StatefulVibrateProtocolPlan(
            protocolId = "youou",
            minimumChannels = 1,
            writes = { speeds, _ -> listOf(write(yououPayload(packetId = 0, speed = speeds[0]))) },
        ).apply {
            sequencedWrites = { speeds, _, packetId -> listOf(write(yououPayload(packetId, speeds[0]))) }
        },
    ).associateBy { it.protocolId }

    val protocolIds: Set<String> = plans.keys

    fun forProtocolId(protocolId: String): StatefulVibrateProtocolPlan? =
        plans[protocolId]

    private fun svakomDualMotorWrite(speeds: IntArray, activeMarker: Int): StatefulVibrateWrite {
        val speed0 = speeds[0]
        val speed1 = speeds.getOrElse(1) { speed0 }
        return write(
            bytes(
                0x55,
                0x03,
                if (speeds.size == 1 || (speed0 > 0 && speed1 > 0) || speed0 == speed1) {
                    0x00
                } else if (speed0 > 0) {
                    0x01
                } else {
                    0x02
                },
                0x00,
                if (speed0 == speed1 && speed0 == 0) 0x00 else activeMarker,
                maxOf(speed0, speed1),
                0x00,
            ),
        )
    }

    private fun write(
        bytes: ByteArray,
        endpointRole: String = "tx",
        withResponse: Boolean = false,
    ): StatefulVibrateWrite =
        StatefulVibrateWrite(endpointRole = endpointRole, bytes = bytes, withResponse = withResponse)

    private fun bytes(vararg values: Int): ByteArray =
        ByteArray(values.size) { index -> values[index].coerceIn(0, 0xFF).toByte() }

    private fun yououPayload(packetId: Int, speed: Int): ByteArray {
        val state = if (speed > 0) 1 else 0
        val prefix = bytes(0xAA, 0x55, packetId, 0x02, 0x03, 0x01, speed, state)
        val crc = prefix.fold(0) { acc, byte -> acc xor (byte.toInt() and 0xFF) }
        return prefix + bytes(crc, 0xFF, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00)
    }
}

object StatefulVibrateProtocolHandlers {
    fun forProtocolId(protocolId: String): ToyProtocolHandler? =
        StatefulVibrateProtocolPlans.forProtocolId(protocolId)?.let(::StatefulVibrateHandler)
}

private class StatefulVibrateHandler(
    private val plan: StatefulVibrateProtocolPlan,
) : ToyProtocolHandler {
    override val protocolId: String = plan.protocolId
    override val supportedOutputKinds: Set<ToyOutputKind> = setOf(ToyOutputKind.Vibrate)

    override fun createSession(match: ButtplugDeviceMatch, transport: ToyTransport): ToyProtocolSession =
        StatefulVibrateSession(match, transport, plan)
}

private class StatefulVibrateSession(
    match: ButtplugDeviceMatch,
    private val transport: ToyTransport,
    private val plan: StatefulVibrateProtocolPlan,
) : ToyProtocolSession {
    private val channelCount = maxOf(match.vibrateOutputs.size, plan.minimumChannels)
    private val speeds = IntArray(channelCount)
    private var packetId = 0
    private val maxByFeature = match.vibrateOutputs.associate { it.featureIndex to it.max.coerceAtLeast(1) }
    private val endpointByRole = match.endpoint.characteristics

    override val supportEntry: ButtplugSupportEntry =
        ButtplugLocalHandlerRegistry.supportEntryFor(match.protocol)

    override suspend fun initialize() {
        for (endpointRole in plan.initSubscriptions) {
            val endpoint = requireNotNull(endpointByRole[endpointRole]) {
                "${plan.protocolId} endpoint $endpointRole is missing"
            }
            when (val result = transport.execute(HardwareOperation.Subscribe(endpoint))) {
                HardwareResult.Success,
                is HardwareResult.ReadResult -> Unit
                is HardwareResult.Failure -> error(result.message)
            }
        }
        executeWrites(plan.initWrites)
    }

    override suspend fun handle(command: ToyOutputCommand) {
        when (command) {
            is ToyOutputCommand.Scalar -> {
                require(command.kind == ToyOutputKind.Vibrate) { "${plan.protocolId} only supports vibrate scalar output" }
                write(command.featureIndex, command.value)
            }
            is ToyOutputCommand.Pattern -> {
                val speed = command.values.values.maxOrNull() ?: maxByFeature.values.maxOrNull() ?: 1
                repeat(channelCount) { index -> write(index, speed) }
            }
            is ToyOutputCommand.Linear -> error("${plan.protocolId} does not support linear output")
            ToyOutputCommand.Stop -> stop()
        }
    }

    override suspend fun stop() {
        repeat(channelCount) { speeds[it] = 0 }
        repeat(channelCount) { index -> executeWrites(plan.writesFor(speeds, index, nextPacketId())) }
    }

    private suspend fun write(featureIndex: Int, speed: Int) {
        val max = maxByFeature[featureIndex] ?: maxByFeature.values.firstOrNull() ?: 1
        speeds[featureIndex.coerceIn(0, speeds.lastIndex)] = speed.coerceIn(0, max)
        executeWrites(plan.writesFor(speeds, featureIndex, nextPacketId()))
    }

    private fun nextPacketId(): Int {
        if (!plan.usesPacketSequence) return 0
        val current = packetId
        packetId = (packetId + 1) and 0xFF
        return current
    }

    private suspend fun executeWrites(writes: List<StatefulVibrateWrite>) {
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
