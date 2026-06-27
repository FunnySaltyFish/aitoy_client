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

    private companion object {
        const val DEFAULT_LINEAR_DURATION_MS = 1000
    }
}
