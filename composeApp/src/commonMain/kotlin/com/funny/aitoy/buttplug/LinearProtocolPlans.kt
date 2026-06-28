package com.funny.aitoy.buttplug

import kotlin.math.pow

data class LinearProtocolWrite(
    val endpointRole: String = "tx",
    val bytes: ByteArray,
    val withResponse: Boolean,
)

data class LinearProtocolCommand(
    val featureIndex: Int,
    val position: Int,
    val durationMs: Int,
)

data class LinearProtocolPlan(
    val protocolId: String,
    val stateSize: Int,
    val initSubscriptions: List<String> = emptyList(),
    val initWrites: List<LinearProtocolWrite> = emptyList(),
    val writes: (state: IntArray, command: LinearProtocolCommand) -> List<LinearProtocolWrite>,
)

object LinearProtocolPlans {
    private val plans = listOf(
        LinearProtocolPlan(
            protocolId = "fleshy-thrust",
            stateSize = 1,
            writes = { _, command ->
                listOf(write(bytes(command.position, command.durationMs.highByte(), command.durationMs.lowByte())))
            },
        ),
        LinearProtocolPlan(
            protocolId = "loob",
            stateSize = 1,
            initWrites = listOf(write(bytes(0x00, 0x01, 0x01, 0xF4), withResponse = true)),
            writes = { _, command ->
                val position = command.position.coerceIn(1, 1000)
                val duration = command.durationMs.coerceAtLeast(1)
                listOf(write(position.u16be() + duration.u16be()))
            },
        ),
        LinearProtocolPlan(
            protocolId = "serveu",
            stateSize = 1,
            writes = { state, command ->
                val lastPosition = state[0]
                val goalPosition = command.position.coerceIn(0, 100)
                state[0] = goalPosition
                val speed = serveUSpeed(lastPosition, goalPosition, command.durationMs)
                listOf(write(bytes(0x01, goalPosition, speed)))
            },
        ),
        LinearProtocolPlan(
            protocolId = "kiiroo-v2",
            stateSize = 1,
            initWrites = listOf(write(bytes(0x00), endpointRole = "firmware", withResponse = true)),
            writes = { state, command ->
                val position = command.position.coerceIn(0, 99)
                val previousPosition = state[0]
                state[0] = position
                val speed = kiirooSpeed(previousPosition, position, command.durationMs)
                listOf(write(bytes(position, speed)))
            },
        ),
        LinearProtocolPlan(
            protocolId = "kiiroo-v21-initialized",
            stateSize = 1,
            initWrites = listOf(
                write(bytes(0x03, 0x00, 0x64, 0x19), withResponse = true),
                write(bytes(0x03, 0x00, 0x64, 0x00), withResponse = true),
            ),
            writes = { state, command -> listOf(write(kiirooV21Payload(state, command))) },
        ),
        LinearProtocolPlan(
            protocolId = "kiiroo-v3",
            stateSize = 1,
            writes = { state, command -> listOf(write(kiirooV21Payload(state, command))) },
        ),
        LinearProtocolPlan(
            protocolId = "fredorch",
            stateSize = 1,
            initSubscriptions = listOf("rx"),
            initWrites = fredorchInitWrites(),
            writes = { state, command ->
                val previousPosition = state[0]
                val position = command.position.coerceIn(0, 15)
                val distance = kotlin.math.abs(previousPosition - position)
                state[0] = position
                val speed = fredorchSpeed(distance, command.durationMs)
                listOf(
                    write(
                        fredorchCrcPayload(
                            0x01,
                            0x10,
                            0x00,
                            0x6B,
                            0x00,
                            0x05,
                            0x0A,
                            0x00,
                            speed,
                            0x00,
                            speed,
                            0x00,
                            position * 10,
                            0x00,
                            position * 10,
                            0x00,
                            0x01,
                        ),
                    ),
                )
            },
        ),
    ).associateBy { it.protocolId }

    val protocolIds: Set<String> = plans.keys

    fun forProtocolId(protocolId: String): LinearProtocolPlan? =
        plans[protocolId]

    private fun serveUSpeed(lastPosition: Int, goalPosition: Int, durationMs: Int): Int {
        val durationSeconds = durationMs.coerceAtLeast(1) / 1000.0
        val speedThreshold = kotlin.math.ceil(kotlin.math.abs(goalPosition - lastPosition) / durationSeconds)
        return when {
            speedThreshold <= 0.00001 -> 0
            speedThreshold <= 50.0 -> kotlin.math.ceil(speedThreshold / 2.0).toInt()
            speedThreshold <= 750.0 -> kotlin.math.ceil((speedThreshold - 50.0) / 4.0).toInt() + 25
            speedThreshold <= 2000.0 -> kotlin.math.ceil((speedThreshold - 750.0) / 25.0).toInt() + 200
            else -> 0xFA
        }.coerceIn(0, 0xFF)
    }

    private fun kiirooV21Payload(state: IntArray, command: LinearProtocolCommand): ByteArray {
        val position = command.position.coerceIn(0, 99)
        val previousPosition = state[0]
        state[0] = position
        val speed = kiirooSpeed(previousPosition, position, command.durationMs)
        return bytes(0x03, 0x00, speed, position)
    }

    private fun kiirooSpeed(previousPosition: Int, position: Int, durationMs: Int): Int {
        val distance = (kotlin.math.abs(previousPosition - position) / 99.0).coerceIn(0.0, 1.0)
        if (distance <= 0.0) return 0
        val scalar = ((durationMs.coerceAtLeast(0) * 90.0) / (distance * 100.0)).pow(-1.05)
        val speed = 250.0 * scalar * 99.0
        return when {
            speed.isNaN() -> 0
            speed == Double.POSITIVE_INFINITY -> 0xFF
            else -> speed.toInt().coerceIn(0, 0xFF)
        }
    }

    private fun fredorchInitWrites(): List<LinearProtocolWrite> =
        listOf(
            fredorchCrcPayload(0x01, 0x06, 0x00, 0x64, 0x00, 0x01),
            fredorchCrcPayload(0x01, 0x06, 0x00, 0x69, 0x00, 0x00),
            fredorchCrcPayload(
                0x01,
                0x10,
                0x00,
                0x6B,
                0x00,
                0x05,
                0x0A,
                0x00,
                0x05,
                0x00,
                0x05,
                0x00,
                0x00,
                0x00,
                0x00,
                0x00,
                0x01,
            ),
            fredorchCrcPayload(0x01, 0x06, 0x00, 0x69, 0x00, 0x01),
            fredorchCrcPayload(0x01, 0x06, 0x00, 0x6A, 0x00, 0x01),
        ).map { write(it) }

    private fun fredorchSpeed(distance: Int, durationMs: Int): Int {
        val boundedDistance = distance.coerceIn(0, 15)
        if (boundedDistance == 0) return 0
        var speed = 1
        while (speed < 20) {
            if (FREDORCH_SPEED_MATRIX[boundedDistance - 1][speed - 1] < durationMs.coerceAtLeast(0)) {
                return speed
            }
            speed += 1
        }
        return speed
    }

    private fun fredorchCrcPayload(vararg values: Int): ByteArray {
        val data = bytes(*values)
        val crc = fredorchCrc16(data)
        return data + byteArrayOf(((crc ushr 8) and 0xFF).toByte(), (crc and 0xFF).toByte())
    }

    private fun fredorchCrc16(data: ByteArray): Int {
        var crc = 0xFFFF
        for (byte in data) {
            crc = crc xor (byte.toInt() and 0xFF)
            repeat(8) {
                crc = if ((crc and 1) != 0) {
                    (crc ushr 1) xor 0xA001
                } else {
                    crc ushr 1
                }
            }
        }
        return crc and 0xFFFF
    }

    private fun write(
        bytes: ByteArray,
        endpointRole: String = "tx",
        withResponse: Boolean = false,
    ): LinearProtocolWrite =
        LinearProtocolWrite(endpointRole = endpointRole, bytes = bytes, withResponse = withResponse)

    private fun Int.highByte(): Int = (this ushr 8) and 0xFF

    private fun Int.lowByte(): Int = this and 0xFF

    private fun Int.u16be(): ByteArray = bytes(highByte(), lowByte())

    private fun bytes(vararg values: Int): ByteArray =
        ByteArray(values.size) { index -> values[index].coerceIn(0, 0xFF).toByte() }

    private val FREDORCH_SPEED_MATRIX = arrayOf(
        intArrayOf(1000, 800, 400, 235, 200, 172, 155, 92, 60, 45, 38, 34, 32, 28, 27, 26, 25, 24, 23, 22),
        intArrayOf(1500, 1000, 800, 680, 600, 515, 425, 265, 165, 115, 80, 70, 50, 48, 45, 35, 34, 33, 32, 30),
        intArrayOf(2500, 2310, 1135, 925, 792, 695, 565, 380, 218, 155, 105, 82, 70, 68, 65, 60, 48, 45, 43, 40),
        intArrayOf(3000, 2800, 1500, 1155, 965, 810, 690, 465, 260, 195, 140, 110, 85, 75, 74, 73, 70, 65, 60, 55),
        intArrayOf(3400, 3232, 2305, 1380, 1200, 1165, 972, 565, 328, 235, 162, 132, 98, 78, 75, 74, 73, 72, 71, 70),
        intArrayOf(3500, 3350, 2500, 1640, 1250, 1210, 1010, 645, 385, 275, 175, 160, 115, 95, 91, 90, 85, 80, 77, 75),
        intArrayOf(3600, 3472, 2980, 2060, 1560, 1275, 1132, 738, 430, 310, 230, 170, 128, 122, 110, 108, 105, 103, 101, 100),
        intArrayOf(3800, 3500, 3055, 2105, 1740, 1370, 1290, 830, 490, 355, 235, 195, 150, 140, 135, 132, 130, 125, 120, 119),
        intArrayOf(3900, 3518, 3190, 2315, 2045, 1510, 1442, 1045, 552, 392, 280, 225, 172, 145, 140, 138, 135, 134, 132, 130),
        intArrayOf(6000, 5755, 3240, 2530, 2135, 1605, 1500, 1200, 595, 425, 285, 245, 175, 170, 160, 155, 150, 145, 142, 140),
        intArrayOf(6428, 5872, 3335, 2780, 2270, 1782, 1590, 1310, 648, 470, 315, 255, 182, 180, 175, 172, 170, 162, 160, 155),
        intArrayOf(6730, 5950, 3490, 2995, 2395, 1890, 1650, 1350, 700, 500, 350, 290, 220, 190, 185, 180, 175, 170, 165, 160),
        intArrayOf(6962, 6122, 3880, 3205, 2465, 1900, 1700, 1400, 835, 545, 375, 310, 228, 195, 190, 185, 182, 181, 180, 175),
        intArrayOf(7945, 6365, 4130, 3470, 2505, 1910, 1755, 1510, 855, 580, 400, 330, 235, 210, 205, 200, 195, 190, 185, 180),
        intArrayOf(8048, 7068, 4442, 3708, 2668, 1930, 1800, 1520, 878, 618, 428, 365, 260, 255, 250, 240, 230, 220, 210, 200),
    )
}

object LinearProtocolHandlers {
    fun forProtocolId(protocolId: String): ToyProtocolHandler? =
        LinearProtocolPlans.forProtocolId(protocolId)?.let(::LinearProtocolHandler)
}

private class LinearProtocolHandler(
    private val plan: LinearProtocolPlan,
) : ToyProtocolHandler {
    override val protocolId: String = plan.protocolId
    override val supportedOutputKinds: Set<ToyOutputKind> = setOf(ToyOutputKind.Linear)

    override fun createSession(match: ButtplugDeviceMatch, transport: ToyTransport): ToyProtocolSession =
        LinearProtocolSession(match, transport, plan)
}

private class LinearProtocolSession(
    match: ButtplugDeviceMatch,
    private val transport: ToyTransport,
    private val plan: LinearProtocolPlan,
) : ToyProtocolSession {
    private val state = IntArray(maxOf(plan.stateSize, match.linearOutputs.size))
    private val endpointByRole = match.endpoint.characteristics
    private val features = match.linearOutputs.associateBy { it.featureIndex }

    override val supportEntry: ButtplugSupportEntry =
        ButtplugLocalHandlerRegistry.supportEntryFor(match.protocol)

    override suspend fun initialize() {
        executeSubscriptions(plan.initSubscriptions)
        executeWrites(plan.initWrites)
    }

    override suspend fun handle(command: ToyOutputCommand) {
        when (command) {
            is ToyOutputCommand.Linear -> write(command.featureIndex, command.position, command.durationMs)
            is ToyOutputCommand.Scalar -> {
                require(command.kind == ToyOutputKind.Linear || command.kind == ToyOutputKind.Position) {
                    "${plan.protocolId} only supports linear output"
                }
                write(command.featureIndex, command.value, DEFAULT_LINEAR_DURATION_MS)
            }
            is ToyOutputCommand.Pattern -> error("${plan.protocolId} does not support pattern output")
            ToyOutputCommand.Stop -> stop()
        }
    }

    override suspend fun stop() {
        for (feature in features.values.sortedBy { it.featureIndex }) {
            write(feature.featureIndex, feature.min, DEFAULT_LINEAR_DURATION_MS)
        }
    }

    private suspend fun write(featureIndex: Int, position: Int, durationMs: Int) {
        val feature = features[featureIndex]
        val boundedPosition = position.coerceIn(feature?.min ?: 0, feature?.max ?: 0xFF)
        executeWrites(plan.writes(state, LinearProtocolCommand(featureIndex, boundedPosition, durationMs)))
    }

    private suspend fun executeWrites(writes: List<LinearProtocolWrite>) {
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

    private suspend fun executeSubscriptions(endpointRoles: List<String>) {
        for (endpointRole in endpointRoles) {
            val endpoint = requireNotNull(endpointByRole[endpointRole]) {
                "${plan.protocolId} endpoint $endpointRole is missing"
            }
            when (val result = transport.execute(HardwareOperation.Subscribe(endpoint))) {
                HardwareResult.Success,
                is HardwareResult.ReadResult -> Unit
                is HardwareResult.Failure -> error(result.message)
            }
        }
    }

    private companion object {
        const val DEFAULT_LINEAR_DURATION_MS = 1000
    }
}
