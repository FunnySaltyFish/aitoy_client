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
    val scalarWrites: (state: IntArray, command: ScalarProtocolCommand) -> List<MixedOutputProtocolWrite>,
    val linearWrites: (state: IntArray, command: LinearProtocolCommand) -> List<MixedOutputProtocolWrite>,
) {
    fun createState(match: ButtplugDeviceMatch): IntArray =
        IntArray(maxOf(stateSize, match.scalarOutputs.size, match.linearOutputs.size))
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
