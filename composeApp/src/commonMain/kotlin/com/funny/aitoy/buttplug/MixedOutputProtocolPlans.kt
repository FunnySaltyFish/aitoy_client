package com.funny.aitoy.buttplug

import kotlin.math.pow

data class MixedOutputProtocolWrite(
    val endpointRole: String = "tx",
    val bytes: ByteArray,
    val withResponse: Boolean,
)

data class MixedOutputProtocolPlan(
    val protocolId: String,
    val supportedKinds: Set<ToyOutputKind>,
    val stateSize: Int,
    val initialState: ((ButtplugDeviceMatch) -> IntArray)? = null,
    val scalarWrites: (state: IntArray, command: ScalarProtocolCommand) -> List<MixedOutputProtocolWrite>,
    val linearWrites: (state: IntArray, command: LinearProtocolCommand) -> List<MixedOutputProtocolWrite>,
) {
    fun createState(match: ButtplugDeviceMatch): IntArray =
        initialState?.invoke(match)
            ?: IntArray(maxOf(stateSize, match.scalarOutputs.size, match.linearOutputs.size))
}

object MixedOutputProtocolPlans {
    private val plans = listOf(
        MixedOutputProtocolPlan(
            protocolId = "kiiroo-v21",
            supportedKinds = setOf(ToyOutputKind.Vibrate, ToyOutputKind.Linear),
            stateSize = 1,
            scalarWrites = { _, command ->
                when (command.kind) {
                    ToyOutputKind.Vibrate -> listOf(write(bytes(0x01, command.value)))
                    else -> unsupportedScalar(command)
                }
            },
            linearWrites = { state, command ->
                val position = command.position.coerceIn(0, 99)
                val previousPosition = state[0]
                state[0] = position
                val speed = kiirooSpeed(previousPosition, position, command.durationMs)
                listOf(write(bytes(0x03, 0x00, speed, position)))
            },
        ),
        MixedOutputProtocolPlan(
            protocolId = "vorze-sa",
            supportedKinds = setOf(ToyOutputKind.Vibrate, ToyOutputKind.Rotate, ToyOutputKind.Linear),
            stateSize = VORZE_STATE_SIZE,
            initialState = { match -> vorzeInitialState(match) },
            scalarWrites = { state, command ->
                when (command.kind) {
                    ToyOutputKind.Vibrate -> vorzeVibrateWrites(state, command)
                    ToyOutputKind.Rotate -> vorzeRotateWrites(state, command)
                    else -> unsupportedScalar(command)
                }
            },
            linearWrites = { state, command ->
                val position = command.position.coerceIn(0, 99)
                val previousPosition = state[VORZE_PREVIOUS_POSITION_INDEX]
                state[VORZE_PREVIOUS_POSITION_INDEX] = position
                listOf(write(bytes(VORZE_DEVICE_PISTON, position, vorzePistonSpeed(previousPosition, position, command.durationMs)), withResponse = true))
            },
        ),
    ).associateBy { it.protocolId }

    val protocolIds: Set<String> = plans.keys

    fun forProtocolId(protocolId: String): MixedOutputProtocolPlan? =
        plans[protocolId]

    fun supportedKinds(protocolId: String): Set<ToyOutputKind> =
        plans[protocolId]?.supportedKinds.orEmpty()

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

    private fun vorzeInitialState(match: ButtplugDeviceMatch): IntArray {
        val name = match.device.name.lowercase()
        val deviceType = when {
            "bach" in name -> VORZE_DEVICE_BACH
            "rocket" in name -> VORZE_DEVICE_ROCKET
            "cyclone" in name || "cycsa" in name -> VORZE_DEVICE_CYCLONE
            "ufo tw" in name || "ufo-tw" in name -> VORZE_DEVICE_UFO_TW
            "ufo" in name -> VORZE_DEVICE_UFO
            "piston" in name -> VORZE_DEVICE_PISTON
            "omorfi" in name || "omor" in name -> VORZE_DEVICE_OMORFI
            else -> 0
        }
        return IntArray(VORZE_STATE_SIZE).also { it[VORZE_DEVICE_TYPE_INDEX] = deviceType }
    }

    private fun vorzeVibrateWrites(state: IntArray, command: ScalarProtocolCommand): List<MixedOutputProtocolWrite> {
        val deviceType = state[VORZE_DEVICE_TYPE_INDEX]
        return when (deviceType) {
            VORZE_DEVICE_BACH,
            VORZE_DEVICE_ROCKET,
            -> listOf(write(bytes(deviceType, VORZE_ACTION_VIBRATE, command.value), withResponse = true))
            VORZE_DEVICE_OMORFI -> {
                state[VORZE_SCALAR_0_INDEX + command.featureIndex.coerceIn(0, 1)] = command.value
                listOf(write(bytes(VORZE_DEVICE_OMORFI, state[VORZE_SCALAR_0_INDEX], state[VORZE_SCALAR_1_INDEX]), withResponse = true))
            }
            else -> unsupportedScalar(command)
        }
    }

    private fun vorzeRotateWrites(state: IntArray, command: ScalarProtocolCommand): List<MixedOutputProtocolWrite> {
        val deviceType = state[VORZE_DEVICE_TYPE_INDEX]
        val encoded = vorzeRotation(command.value)
        return when (deviceType) {
            VORZE_DEVICE_CYCLONE,
            VORZE_DEVICE_UFO,
            -> listOf(write(bytes(deviceType, VORZE_ACTION_ROTATE, encoded), withResponse = true))
            VORZE_DEVICE_UFO_TW -> {
                state[VORZE_SCALAR_0_INDEX + command.featureIndex.coerceIn(0, 1)] = encoded
                listOf(write(bytes(VORZE_DEVICE_UFO_TW, state[VORZE_SCALAR_0_INDEX], state[VORZE_SCALAR_1_INDEX]), withResponse = true))
            }
            else -> unsupportedScalar(command)
        }
    }

    private fun vorzeRotation(speed: Int): Int {
        val clockwise = if (speed >= 0) 1 else 0
        return (clockwise shl 7) or kotlin.math.abs(speed).coerceIn(0, 0x7F)
    }

    private fun vorzePistonSpeed(previousPosition: Int, position: Int, durationMs: Int): Int {
        val distance = kotlin.math.abs(previousPosition - position).toDouble()
        if (distance <= 0.0) return 100
        val scaledDistance = distance.coerceAtMost(200.0)
        val duration = 200.0 * durationMs.coerceAtLeast(0).toDouble() / scaledDistance
        return (duration / 6658.0).pow(-1.21).toInt().coerceIn(0, 100)
    }

    private fun write(
        bytes: ByteArray,
        endpointRole: String = "tx",
        withResponse: Boolean = false,
    ): MixedOutputProtocolWrite =
        MixedOutputProtocolWrite(endpointRole = endpointRole, bytes = bytes, withResponse = withResponse)

    private fun unsupportedScalar(command: ScalarProtocolCommand): Nothing =
        error("${command.kind} is not supported by this mixed protocol")

    private fun bytes(vararg values: Int): ByteArray =
        ByteArray(values.size) { index -> values[index].coerceIn(0, 0xFF).toByte() }

    private const val VORZE_DEVICE_TYPE_INDEX = 0
    private const val VORZE_SCALAR_0_INDEX = 1
    private const val VORZE_SCALAR_1_INDEX = 2
    private const val VORZE_PREVIOUS_POSITION_INDEX = 3
    private const val VORZE_STATE_SIZE = 4
    private const val VORZE_DEVICE_BACH = 6
    private const val VORZE_DEVICE_PISTON = 3
    private const val VORZE_DEVICE_CYCLONE = 1
    private const val VORZE_DEVICE_ROCKET = 7
    private const val VORZE_DEVICE_OMORFI = 9
    private const val VORZE_DEVICE_UFO = 2
    private const val VORZE_DEVICE_UFO_TW = 5
    private const val VORZE_ACTION_ROTATE = 1
    private const val VORZE_ACTION_VIBRATE = 3
}

object MixedOutputProtocolHandlers {
    fun forProtocolId(protocolId: String): ToyProtocolHandler? =
        MixedOutputProtocolPlans.forProtocolId(protocolId)?.let(::MixedOutputProtocolHandler)
}

private class MixedOutputProtocolHandler(
    private val plan: MixedOutputProtocolPlan,
) : ToyProtocolHandler {
    override val protocolId: String = plan.protocolId
    override val supportedOutputKinds: Set<ToyOutputKind> = plan.supportedKinds

    override fun createSession(match: ButtplugDeviceMatch, transport: ToyTransport): ToyProtocolSession =
        MixedOutputProtocolSession(match, transport, plan)
}

private class MixedOutputProtocolSession(
    match: ButtplugDeviceMatch,
    private val transport: ToyTransport,
    private val plan: MixedOutputProtocolPlan,
) : ToyProtocolSession {
    private val state = plan.createState(match)
    private val endpointByRole = match.endpoint.characteristics
    private val scalarFeatures = match.scalarOutputs.associateBy { it.featureIndex }
    private val linearFeatures = match.linearOutputs.associateBy { it.featureIndex }

    override val supportEntry: ButtplugSupportEntry =
        ButtplugLocalHandlerRegistry.supportEntryFor(match.protocol)

    override suspend fun initialize() = Unit

    override suspend fun handle(command: ToyOutputCommand) {
        when (command) {
            is ToyOutputCommand.Scalar -> writeScalar(command.featureIndex, command.kind, command.value)
            is ToyOutputCommand.Linear -> writeLinear(command.featureIndex, command.position, command.durationMs)
            is ToyOutputCommand.Pattern -> {
                for ((featureIndex, value) in command.values) {
                    scalarFeatures[featureIndex]?.let { writeScalar(featureIndex, it.type.toToyOutputKind(), value) }
                    linearFeatures[featureIndex]?.let { writeLinear(featureIndex, value, DEFAULT_LINEAR_DURATION_MS) }
                }
            }
            ToyOutputCommand.Stop -> stop()
        }
    }

    override suspend fun stop() {
        for (feature in scalarFeatures.values.sortedBy { it.featureIndex }) {
            writeScalar(feature.featureIndex, feature.type.toToyOutputKind(), 0)
        }
        for (feature in linearFeatures.values.sortedBy { it.featureIndex }) {
            writeLinear(feature.featureIndex, feature.min, DEFAULT_LINEAR_DURATION_MS)
        }
    }

    private suspend fun writeScalar(featureIndex: Int, kind: ToyOutputKind, value: Int) {
        require(kind in plan.supportedKinds) { "${plan.protocolId} does not support $kind" }
        val feature = scalarFeatures[featureIndex]
        val coerced = value.coerceIn(feature?.min ?: 0, feature?.max ?: 0xFF)
        executeWrites(plan.scalarWrites(state, ScalarProtocolCommand(featureIndex, kind, coerced)))
    }

    private suspend fun writeLinear(featureIndex: Int, position: Int, durationMs: Int) {
        require(ToyOutputKind.Linear in plan.supportedKinds) { "${plan.protocolId} does not support linear output" }
        val feature = linearFeatures[featureIndex]
        val coerced = position.coerceIn(feature?.min ?: 0, feature?.max ?: 0xFF)
        executeWrites(plan.linearWrites(state, LinearProtocolCommand(featureIndex, coerced, durationMs)))
    }

    private suspend fun executeWrites(writes: List<MixedOutputProtocolWrite>) {
        for (write in writes) {
            val endpoint = requireNotNull(endpointByRole[write.endpointRole]) {
                "${plan.protocolId} endpoint ${write.endpointRole} is missing"
            }
            when (val result = transport.execute(HardwareOperation.Write(endpoint, write.bytes, write.withResponse))) {
                HardwareResult.Success,
                is HardwareResult.ReadResult,
                -> Unit
                is HardwareResult.Failure -> error(result.message)
            }
        }
    }

    private companion object {
        const val DEFAULT_LINEAR_DURATION_MS = 1000
    }
}
