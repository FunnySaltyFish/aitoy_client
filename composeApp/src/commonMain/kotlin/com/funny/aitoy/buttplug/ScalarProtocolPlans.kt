package com.funny.aitoy.buttplug

data class ScalarProtocolWrite(
    val endpointRole: String = "tx",
    val bytes: ByteArray,
    val withResponse: Boolean,
    val delayBeforeMs: Long = 0L,
)

data class ScalarProtocolRead(
    val endpointRole: String = "tx",
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
    val initSubscriptions: List<String> = emptyList(),
    val initWrites: List<ScalarProtocolWrite> = emptyList(),
    val initReads: List<ScalarProtocolRead> = emptyList(),
    val keepaliveIntervalMs: Long = 0L,
    val initialState: ((ButtplugDeviceMatch) -> IntArray)? = null,
    val onRead: ((state: IntArray, endpointRole: String, bytes: ByteArray) -> Unit)? = null,
    val keepaliveWrites: ((state: IntArray) -> List<ScalarProtocolWrite>)? = null,
    val writes: (state: IntArray, command: ScalarProtocolCommand) -> List<ScalarProtocolWrite>,
) {
    var sequencedWrites: ((state: IntArray, command: ScalarProtocolCommand, packetId: Int) -> List<ScalarProtocolWrite>)? = null

    val usesPacketSequence: Boolean
        get() = sequencedWrites != null

    fun writesFor(state: IntArray, command: ScalarProtocolCommand, packetId: Int): List<ScalarProtocolWrite> =
        sequencedWrites?.invoke(state, command, packetId) ?: writes(state, command)

    fun keepaliveWritesFor(state: IntArray): List<ScalarProtocolWrite>? =
        keepaliveWrites?.invoke(state)

    fun createState(match: ButtplugDeviceMatch): IntArray =
        initialState?.invoke(match) ?: IntArray(maxOf(stateSize, match.scalarOutputs.size))
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
            protocolId = "fredorch-rotary",
            supportedKinds = setOf(ToyOutputKind.Oscillate),
            stateSize = FREDORCH_ROTARY_STATE_SIZE,
            initSubscriptions = listOf("rx"),
            initWrites = listOf(
                write(bytes(0x55, 0x03, 0x99, 0x9C, 0xAA)),
                write(bytes(0x55, 0x09, 0x21, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x2A, 0xAA)),
                write(bytes(0x55, 0x03, 0x1F, 0x22, 0xAA)),
                write(bytes(0x55, 0x03, 0x24, 0x27, 0xAA)),
            ),
            keepaliveIntervalMs = 100,
            keepaliveWrites = { state -> fredorchRotaryNextStep(state) },
            writes = { state, command ->
                require(command.kind == ToyOutputKind.Oscillate) { "${command.kind} is not supported by fredorch-rotary" }
                state[FREDORCH_ROTARY_TARGET_SPEED_INDEX] = command.value.coerceIn(0, 20)
                if (command.value == 0) {
                    state[FREDORCH_ROTARY_CURRENT_SPEED_INDEX] = 0
                    listOf(write(fredorchRotaryStopPayload()))
                } else {
                    fredorchRotaryNextStep(state)
                }
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
            protocolId = "magic-motion-1",
            supportedKinds = setOf(ToyOutputKind.Vibrate, ToyOutputKind.Oscillate),
            stateSize = 1,
            writes = { _, command -> listOf(write(magicMotionV1Payload(command.value))) },
        ),
        ScalarProtocolPlan(
            protocolId = "magic-motion-2",
            supportedKinds = setOf(ToyOutputKind.Vibrate, ToyOutputKind.Oscillate),
            stateSize = 2,
            writes = { state, command ->
                state[command.featureIndex.coerceIn(0, 1)] = command.value
                listOf(write(magicMotionV2Payload(state[0], state[1])))
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
            protocolId = "tryfun",
            supportedKinds = setOf(ToyOutputKind.Oscillate, ToyOutputKind.Rotate, ToyOutputKind.Vibrate),
            stateSize = 3,
            writes = { _, command ->
                when (command.kind) {
                    ToyOutputKind.Oscillate -> listOf(write(tryfunYuanChecksumPayload(0xAA, 0x02, 0x07, command.value), withResponse = true))
                    ToyOutputKind.Rotate -> listOf(write(tryfunYuanChecksumPayload(0xAA, 0x02, 0x08, command.value), withResponse = true))
                    ToyOutputKind.Vibrate -> listOf(write(tryfunYuanVibratePayload(command.value), withResponse = true))
                    else -> unsupported(command)
                }
            },
        ),
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
            protocolId = "joyhub",
            supportedKinds = setOf(
                ToyOutputKind.Vibrate,
                ToyOutputKind.Oscillate,
                ToyOutputKind.Rotate,
                ToyOutputKind.Constrict,
                ToyOutputKind.Spray,
                ToyOutputKind.Led,
                ToyOutputKind.Temperature,
            ),
            stateSize = JOYHUB_STATE_SIZE,
            writes = { state, command ->
                when (command.kind) {
                    ToyOutputKind.Vibrate,
                    ToyOutputKind.Oscillate,
                    ToyOutputKind.Rotate,
                    -> listOf(write(joyhubMultiSpeedPayload(state, command.featureIndex, kotlin.math.abs(command.value))))
                    ToyOutputKind.Constrict -> listOf(write(joyhubConstrictPayload(command.featureIndex, command.value)))
                    ToyOutputKind.Spray -> joyhubSprayWrites(command.value)
                    ToyOutputKind.Led -> listOf(write(joyhubSwitchPayload(0x14, command.value)))
                    ToyOutputKind.Temperature -> listOf(write(joyhubSwitchPayload(0x04, command.value)))
                    else -> unsupported(command)
                }
            },
        ),
        ScalarProtocolPlan(
            protocolId = "luvmazer",
            supportedKinds = setOf(ToyOutputKind.Vibrate, ToyOutputKind.Oscillate, ToyOutputKind.Rotate, ToyOutputKind.Constrict),
            stateSize = 3,
            writes = { _, command ->
                when (command.kind) {
                    ToyOutputKind.Vibrate -> {
                        if (command.featureIndex == 2) {
                            listOf(write(bytes(0xA0, 0x0C, 0x00, 0x00, 0x64, command.value)))
                        } else {
                            listOf(write(bytes(0xA0, 0x01, 0x00, command.featureIndex, 0x64, command.value)))
                        }
                    }
                    ToyOutputKind.Oscillate -> listOf(write(bytes(0xA0, 0x06, 0x01, 0x00, 0x64, command.value)))
                    ToyOutputKind.Rotate -> listOf(write(bytes(0xA0, 0x0F, 0x00, 0x00, 0x64, command.value.rawByte())))
                    ToyOutputKind.Constrict -> listOf(
                        write(bytes(0xA0, 0x0D, 0x00, 0x00, if (command.value == 0) 0x00 else 0x14, command.value)),
                    )
                    else -> unsupported(command)
                }
            },
        ),
        ScalarProtocolPlan(
            protocolId = "sexverse-v1",
            supportedKinds = setOf(ToyOutputKind.Vibrate, ToyOutputKind.Oscillate, ToyOutputKind.Rotate, ToyOutputKind.Constrict),
            stateSize = SEXVERSE_V1_TYPE_OFFSET * 2,
            initialState = { match -> sexverseV1InitialState(match) },
            writes = { state, command ->
                state[SEXVERSE_V1_SPEED_OFFSET + command.featureIndex] = command.value
                listOf(write(sexverseV1Payload(state)))
            },
        ),
        ScalarProtocolPlan(
            protocolId = "sensee-v2",
            supportedKinds = setOf(ToyOutputKind.Vibrate, ToyOutputKind.Oscillate, ToyOutputKind.Constrict),
            stateSize = SENSEE_V2_STATE_SIZE,
            initReads = listOf(read("tx")),
            initialState = { match -> senseeV2InitialState(match) },
            onRead = { state, endpointRole, bytes ->
                if (endpointRole == "tx") {
                    state[SENSEE_V2_DEVICE_TYPE_INDEX] = if (bytes.size > 6 && bytes[6].toInt() != 0) {
                        bytes[6].toInt() and 0xFF
                    } else {
                        SENSEE_V2_DEFAULT_DEVICE_TYPE
                    }
                }
            },
            writes = { state, command ->
                senseeV2SetValue(state, command)
                listOf(write(senseeV2Payload(state)))
            },
        ),
        ScalarProtocolPlan(
            protocolId = "hismith",
            supportedKinds = setOf(ToyOutputKind.Oscillate, ToyOutputKind.Vibrate),
            stateSize = 2,
            writes = { _, command ->
                val index = when (command.kind) {
                    ToyOutputKind.Oscillate -> 0x04
                    ToyOutputKind.Vibrate -> if (command.featureIndex == 0) 0x04 else 0x06
                    else -> unsupported(command)
                }
                val value = if (command.kind == ToyOutputKind.Vibrate && command.featureIndex != 0 && command.value == 0) {
                    0xF0
                } else {
                    command.value
                }
                listOf(write(bytes(0xAA, index, value, value + index)))
            },
        ),
        ScalarProtocolPlan(
            protocolId = "hismith-mini",
            supportedKinds = setOf(
                ToyOutputKind.Oscillate,
                ToyOutputKind.Vibrate,
                ToyOutputKind.Constrict,
                ToyOutputKind.Rotate,
                ToyOutputKind.Spray,
            ),
            stateSize = HISMITH_MINI_STATE_SIZE,
            initialState = { match -> hismithMiniInitialState(match) },
            writes = { state, command ->
                when (command.kind) {
                    ToyOutputKind.Oscillate -> listOf(write(hismithMiniPacket(0x03, command.value)))
                    ToyOutputKind.Vibrate -> {
                        val index = if (state[HISMITH_MINI_DUAL_VIBE_INDEX] == 0 || command.featureIndex == 1) 0x05 else 0x03
                        listOf(write(hismithMiniPacket(index, command.value)))
                    }
                    ToyOutputKind.Constrict -> {
                        val index = if (state[HISMITH_MINI_SECOND_CONSTRICT_INDEX] != 0) 0x05 else 0x03
                        listOf(write(hismithMiniPacket(index, command.value)))
                    }
                    ToyOutputKind.Rotate -> listOf(
                        write(hismithMiniPacket(0x03, kotlin.math.abs(command.value))),
                        write(
                            bytes(
                                0xCC,
                                0x01,
                                if (command.value >= 0) 0xC0 else 0xC1,
                                if (command.value >= 0) 0xC1 else 0xC2,
                            ),
                        ),
                    )
                    ToyOutputKind.Spray -> if (command.value == 0) {
                        emptyList()
                    } else {
                        listOf(write(bytes(0xCC, 0x0B, 0x01, 0x0C)))
                    }
                    else -> unsupported(command)
                }
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
        ScalarProtocolPlan(
            protocolId = "svakom-v3",
            supportedKinds = setOf(ToyOutputKind.Vibrate, ToyOutputKind.Rotate),
            stateSize = 2,
            keepaliveIntervalMs = 100,
            writes = { _, command ->
                when (command.kind) {
                    ToyOutputKind.Vibrate -> listOf(write(svakomV3VibratePayload(command.featureIndex, command.value)))
                    ToyOutputKind.Rotate -> listOf(write(bytes(0x55, 0x08, 0x00, 0x00, command.value.rawByte(), 0xFF)))
                    else -> unsupported(command)
                }
            },
        ),
        ScalarProtocolPlan(
            protocolId = "svakom-avaneo",
            supportedKinds = setOf(ToyOutputKind.Vibrate, ToyOutputKind.Oscillate),
            stateSize = 2,
            writes = { _, command ->
                when {
                    command.kind == ToyOutputKind.Vibrate && command.featureIndex == 0 ->
                        listOf(write(bytes(0x55, 0x03, 0x00, 0x00, if (command.value == 0) 0x00 else 0x01, command.value)))
                    command.kind == ToyOutputKind.Vibrate ->
                        listOf(write(bytes(0x55, 0x09, 0x00, 0x00, command.value, 0xFF)))
                    command.kind == ToyOutputKind.Oscillate && command.featureIndex == 0 ->
                        listOf(write(bytes(0x55, 0x03, 0x00, 0x00, if (command.value == 0) 0x00 else 0x01, command.value)))
                    command.kind == ToyOutputKind.Oscillate ->
                        listOf(write(bytes(0x55, 0x08, 0x00, 0x00, command.value, 0xFF)))
                    else -> unsupported(command)
                }
            },
        ),
        ScalarProtocolPlan(
            protocolId = "svakom-barnard",
            supportedKinds = setOf(ToyOutputKind.Vibrate, ToyOutputKind.Oscillate),
            stateSize = 2,
            keepaliveIntervalMs = 100,
            writes = { _, command ->
                when (command.kind) {
                    ToyOutputKind.Vibrate -> listOf(write(bytes(0x55, 0x03, 0x00, 0x00, command.value, if (command.value == 0) 0x00 else 0x01)))
                    ToyOutputKind.Oscillate -> listOf(write(bytes(0x55, 0x08, 0x00, 0x00, command.value, if (command.value == 0) 0x00 else 0xFF)))
                    else -> unsupported(command)
                }
            },
        ),
        ScalarProtocolPlan(
            protocolId = "svakom-jordan",
            supportedKinds = setOf(ToyOutputKind.Vibrate, ToyOutputKind.Oscillate),
            stateSize = 2,
            keepaliveIntervalMs = 100,
            writes = { _, command ->
                when (command.kind) {
                    ToyOutputKind.Vibrate -> listOf(write(bytes(0x55, 0x03, 0x00, 0x00, if (command.value == 0) 0x00 else 0x01, command.value, 0x00)))
                    ToyOutputKind.Oscillate -> listOf(write(bytes(0x55, 0x08, 0x00, 0x00, if (command.value == 0) 0x00 else 0x01, command.value, 0x00)))
                    else -> unsupported(command)
                }
            },
        ),
        ScalarProtocolPlan(
            protocolId = "svakom-v5",
            supportedKinds = setOf(ToyOutputKind.Vibrate, ToyOutputKind.Oscillate),
            stateSize = 3,
            keepaliveIntervalMs = 100,
            writes = { state, command ->
                when (command.kind) {
                    ToyOutputKind.Vibrate -> {
                        state[command.featureIndex.coerceIn(0, 1)] = command.value
                        listOf(write(svakomV5VibratePayload(state[0], state[1])))
                    }
                    ToyOutputKind.Oscillate -> listOf(write(bytes(0x55, 0x09, 0x00, 0x00, command.value, 0x00)))
                    else -> unsupported(command)
                }
            },
        ),
        ScalarProtocolPlan(
            protocolId = "svakom-dt250a",
            supportedKinds = setOf(ToyOutputKind.Vibrate, ToyOutputKind.Constrict),
            stateSize = 3,
            writes = { _, command ->
                val mode = when {
                    command.kind == ToyOutputKind.Constrict -> 0x09
                    command.featureIndex == 0 -> 0x03
                    else -> 0x08
                }
                listOf(write(bytes(0x55, mode, 0x00, 0x00, command.value, if (command.value == 0 || mode == 0x09) 0x00 else 0x01)))
            },
        ),
        ScalarProtocolPlan(
            protocolId = "svakom-sam",
            supportedKinds = setOf(ToyOutputKind.Vibrate, ToyOutputKind.Constrict),
            stateSize = 1,
            initSubscriptions = listOf("rx"),
            keepaliveIntervalMs = 100,
            initialState = { match ->
                intArrayOf(
                    if ("txmode" in match.endpoint.characteristics || "firmware" in match.endpoint.characteristics) 1 else 0,
                )
            },
            writes = { state, command ->
                when (command.kind) {
                    ToyOutputKind.Vibrate -> {
                        val body = if (state[0] == 1) {
                            bytes(0x12, 0x01, 0x03, 0x00, if (command.value == 0) 0x00 else 0x04, command.value)
                        } else {
                            bytes(0x12, 0x01, 0x03, 0x00, 0x05, command.value)
                        }
                        listOf(write(body))
                    }
                    ToyOutputKind.Constrict -> listOf(write(bytes(0x12, 0x06, 0x01, command.value)))
                    else -> unsupported(command)
                }
            },
        ),
        ScalarProtocolPlan(
            protocolId = "svakom-sam2",
            supportedKinds = setOf(ToyOutputKind.Vibrate, ToyOutputKind.Constrict),
            stateSize = 2,
            keepaliveIntervalMs = 100,
            writes = { _, command ->
                when (command.kind) {
                    ToyOutputKind.Vibrate -> listOf(
                        write(
                            bytes(0x55, 0x03, 0x00, 0x00, if (command.value == 0) 0x00 else 0x05, command.value, 0x00),
                            withResponse = true,
                        ),
                    )
                    ToyOutputKind.Constrict -> listOf(
                        write(
                            bytes(0x55, 0x09, 0x00, 0x00, if (command.value == 0) 0x00 else 0x01, command.value, 0x00),
                            withResponse = true,
                        ),
                    )
                    else -> unsupported(command)
                }
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

    private fun magicMotionV1Payload(speed: Int): ByteArray =
        bytes(0x0B, 0xFF, 0x04, 0x0A, 0x32, 0x32, 0x00, 0x04, 0x08, speed, 0x64, 0x00)

    private fun magicMotionV2Payload(speed0: Int, speed1: Int): ByteArray =
        bytes(
            0x10,
            0xFF,
            0x04,
            0x0A,
            0x32,
            0x0A,
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
        )

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

    private fun tryfunYuanChecksumPayload(vararg body: Int): ByteArray {
        var sum = 0xFF
        var count = 0
        for (item in body.drop(1)) {
            sum = (sum - (item and 0xFF)) and 0xFF
            count += 1
        }
        return bytes(*body, (sum + count) and 0xFF)
    }

    private fun tryfunYuanVibratePayload(speed: Int): ByteArray =
        bytes(
            0x00,
            0x02,
            0x00,
            0x05,
            if (speed == 0) 0x01 else 0x02,
            if (speed == 0) 0x02 else speed,
            0x01,
            if (speed == 0) 0x01 else 0x00,
            0xFD - speed.coerceAtLeast(1),
        )

    private fun fredorchRotaryNextStep(state: IntArray): List<ScalarProtocolWrite> {
        val current = state[FREDORCH_ROTARY_CURRENT_SPEED_INDEX]
        val target = state[FREDORCH_ROTARY_TARGET_SPEED_INDEX]
        if (current == target) return emptyList()
        val command = if (target > current) 0x01 else 0x02
        state[FREDORCH_ROTARY_CURRENT_SPEED_INDEX] = if (target > current) current + 1 else current - 1
        return listOf(write(bytes(0x55, 0x03, command, command + 3, 0xAA)))
    }

    private fun fredorchRotaryStopPayload(): ByteArray =
        bytes(0x55, 0x03, 0x24, 0x27, 0xAA)

    private fun joyhubMultiSpeedPayload(state: IntArray, featureIndex: Int, speed: Int): ByteArray {
        state[featureIndex.coerceIn(0, JOYHUB_STATE_SIZE - 1)] = speed.coerceIn(0, 0xFF)
        return bytes(0xA0, 0x03, state[0], state[1], state[2], state[3], 0xAA)
    }

    private fun joyhubConstrictPayload(featureIndex: Int, level: Int): ByteArray =
        if (featureIndex == 4) {
            bytes(0xA0, 0x07, if (level == 0) 0x00 else 0x01, 0x00, level, 0xFF)
        } else {
            bytes(0xA0, 0x0D, 0x00, 0x00, level, 0xFF)
        }

    private fun joyhubSwitchPayload(command: Int, level: Int): ByteArray =
        if (level == 0) {
            bytes(0xA0, command, 0x00, 0x00, 0x00, 0x00)
        } else {
            bytes(0xA0, command, 0x01, 0x00, 0x01, 0xFF)
        }

    private fun joyhubSprayWrites(level: Int): List<ScalarProtocolWrite> {
        val stop = write(bytes(0xA0, 0x24, 0x00, 0x00, 0x00, 0x00), delayBeforeMs = if (level == 0) 0L else 1000L)
        return if (level == 0) {
            listOf(stop)
        } else {
            listOf(write(bytes(0xA0, 0x24, 0x01, 0x00, 0x01, 0xFF)), stop)
        }
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

    private fun sexverseV1InitialState(match: ButtplugDeviceMatch): IntArray {
        val features = match.scalarOutputs.sortedBy { it.featureIndex }
        val state = IntArray(maxOf(SEXVERSE_V1_TYPE_OFFSET * 2, features.size + SEXVERSE_V1_TYPE_OFFSET))
        state[SEXVERSE_V1_COUNT_INDEX] = features.size
        for (feature in features) {
            state[SEXVERSE_V1_TYPE_OFFSET + feature.featureIndex] = sexverseV1MotorType(feature.type.toToyOutputKind())
        }
        return state
    }

    private fun sexverseV1Payload(state: IntArray): ByteArray {
        val count = state[SEXVERSE_V1_COUNT_INDEX].coerceAtLeast(1)
        val data = mutableListOf(0x23, 0x07, count * 3)
        repeat(count) { index ->
            data += 0x80 or (index + 1)
            data += state[SEXVERSE_V1_TYPE_OFFSET + index].takeIf { it != 0 } ?: 0x03
            data += state[SEXVERSE_V1_SPEED_OFFSET + index]
        }
        data += data.fold(0) { acc, value -> acc xor (value and 0xFF) }
        return bytes(*data.toIntArray())
    }

    private fun sexverseV1MotorType(kind: ToyOutputKind): Int =
        when (kind) {
            ToyOutputKind.Rotate -> 0x06
            ToyOutputKind.Constrict,
            ToyOutputKind.Oscillate,
            -> 0x04
            else -> 0x03
        }

    private fun senseeV2InitialState(match: ButtplugDeviceMatch): IntArray {
        val state = IntArray(SENSEE_V2_STATE_SIZE)
        state[SENSEE_V2_DEVICE_TYPE_INDEX] = SENSEE_V2_DEFAULT_DEVICE_TYPE
        for (feature in match.scalarOutputs) {
            val presentOffset = senseeV2PresentOffset(feature.type.toToyOutputKind()) ?: continue
            state[presentOffset + feature.featureIndex] = 1
        }
        return state
    }

    private fun senseeV2SetValue(state: IntArray, command: ScalarProtocolCommand) {
        val speedOffset = senseeV2SpeedOffset(command.kind) ?: unsupported(command)
        state[speedOffset + command.featureIndex] = command.value
    }

    private fun senseeV2Payload(state: IntArray): ByteArray {
        val groups = listOf(
            SenseeV2Group(0, SENSEE_V2_VIBE_PRESENT_OFFSET, SENSEE_V2_VIBE_SPEED_OFFSET),
            SenseeV2Group(1, SENSEE_V2_THRUST_PRESENT_OFFSET, SENSEE_V2_THRUST_SPEED_OFFSET),
            SenseeV2Group(2, SENSEE_V2_SUCK_PRESENT_OFFSET, SENSEE_V2_SUCK_SPEED_OFFSET),
        ).mapNotNull { group ->
            val values = (0 until SENSEE_V2_MAX_FEATURE_INDEX)
                .filter { state[group.presentOffset + it] != 0 }
                .map { state[group.speedOffset + it] }
            if (values.isEmpty()) null else group.id to values
        }
        val data = mutableListOf(groups.size)
        for ((groupId, values) in groups) {
            data += groupId
            data += values.size
            values.forEachIndexed { index, value ->
                data += index + 1
                data += value
            }
        }
        return senseeV2Command(state[SENSEE_V2_DEVICE_TYPE_INDEX], 0xF1, data)
    }

    private fun senseeV2Command(deviceType: Int, function: Int, body: List<Int>): ByteArray =
        bytes(
            0x55,
            0xAA,
            0xF0,
            0x02,
            0x00,
            0x04 + body.size,
            deviceType,
            function,
            *body.toIntArray(),
            0x00,
            0x00,
        )

    private fun senseeV2PresentOffset(kind: ToyOutputKind): Int? =
        when (kind) {
            ToyOutputKind.Vibrate -> SENSEE_V2_VIBE_PRESENT_OFFSET
            ToyOutputKind.Oscillate -> SENSEE_V2_THRUST_PRESENT_OFFSET
            ToyOutputKind.Constrict -> SENSEE_V2_SUCK_PRESENT_OFFSET
            else -> null
        }

    private fun senseeV2SpeedOffset(kind: ToyOutputKind): Int? =
        when (kind) {
            ToyOutputKind.Vibrate -> SENSEE_V2_VIBE_SPEED_OFFSET
            ToyOutputKind.Oscillate -> SENSEE_V2_THRUST_SPEED_OFFSET
            ToyOutputKind.Constrict -> SENSEE_V2_SUCK_SPEED_OFFSET
            else -> null
        }

    private fun hismithMiniInitialState(match: ButtplugDeviceMatch): IntArray {
        val features = match.scalarOutputs.sortedBy { it.featureIndex }
        return intArrayOf(
            if (features.count { it.type.toToyOutputKind() == ToyOutputKind.Vibrate } >= 2) 1 else 0,
            if (features.indexOfFirst { it.type.toToyOutputKind() == ToyOutputKind.Constrict } == 1) 1 else 0,
        )
    }

    private fun hismithMiniPacket(index: Int, value: Int): ByteArray =
        bytes(0xCC, index, value, value + index)

    private data class SenseeV2Group(
        val id: Int,
        val presentOffset: Int,
        val speedOffset: Int,
    )

    private fun svakomV3VibratePayload(featureIndex: Int, speed: Int): ByteArray =
        bytes(
            0x55,
            if (featureIndex == 0) 0x03 else 0x09,
            if (featureIndex == 0) 0x03 else 0x00,
            0x00,
            if (speed == 0) 0x00 else 0x01,
            speed,
        )

    private fun svakomV5VibratePayload(vibe0: Int, vibe1: Int): ByteArray =
        bytes(
            0x55,
            0x03,
            if ((vibe0 > 0 && vibe1 > 0) || vibe0 == vibe1) {
                0x00
            } else if (vibe0 > 0) {
                0x01
            } else {
                0x02
            },
            0x00,
            if (vibe0 == vibe1 && vibe0 == 0) 0x00 else 0x01,
            maxOf(vibe0, vibe1),
        )

    private fun unsupported(command: ScalarProtocolCommand): Nothing =
        error("${command.kind} is not supported by this scalar protocol")

    private fun write(
        bytes: ByteArray,
        endpointRole: String = "tx",
        withResponse: Boolean = false,
        delayBeforeMs: Long = 0L,
    ): ScalarProtocolWrite =
        ScalarProtocolWrite(endpointRole = endpointRole, bytes = bytes, withResponse = withResponse, delayBeforeMs = delayBeforeMs)

    private fun read(endpointRole: String): ScalarProtocolRead =
        ScalarProtocolRead(endpointRole = endpointRole)

    private fun Int.absoluteByte(): Int =
        kotlin.math.abs(this).coerceIn(0, 0xFF)

    private fun Int.rawByte(): Int =
        this and 0xFF

    private fun bytes(vararg values: Int): ByteArray =
        ByteArray(values.size) { index -> values[index].coerceIn(0, 0xFF).toByte() }

    private const val SEXVERSE_V1_COUNT_INDEX = 0
    private const val SEXVERSE_V1_SPEED_OFFSET = 1
    private const val SEXVERSE_V1_TYPE_OFFSET = 16
    private const val SENSEE_V2_DEVICE_TYPE_INDEX = 0
    private const val SENSEE_V2_DEFAULT_DEVICE_TYPE = 0x65
    private const val SENSEE_V2_MAX_FEATURE_INDEX = 16
    private const val SENSEE_V2_VIBE_PRESENT_OFFSET = 1
    private const val SENSEE_V2_THRUST_PRESENT_OFFSET = 17
    private const val SENSEE_V2_SUCK_PRESENT_OFFSET = 33
    private const val SENSEE_V2_VIBE_SPEED_OFFSET = 49
    private const val SENSEE_V2_THRUST_SPEED_OFFSET = 65
    private const val SENSEE_V2_SUCK_SPEED_OFFSET = 81
    private const val SENSEE_V2_STATE_SIZE = 97
    private const val HISMITH_MINI_DUAL_VIBE_INDEX = 0
    private const val HISMITH_MINI_SECOND_CONSTRICT_INDEX = 1
    private const val HISMITH_MINI_STATE_SIZE = 2
    private const val FREDORCH_ROTARY_CURRENT_SPEED_INDEX = 0
    private const val FREDORCH_ROTARY_TARGET_SPEED_INDEX = 1
    private const val FREDORCH_ROTARY_STATE_SIZE = 2
    private const val JOYHUB_STATE_SIZE = 4

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
    private val state = plan.createState(match)
    private var packetId = 0
    private val endpointByRole = match.endpoint.characteristics
    private val features = match.scalarOutputs.associateBy { it.featureIndex }

    override val supportEntry: ButtplugSupportEntry =
        ButtplugLocalHandlerRegistry.supportEntryFor(match.protocol)

    override suspend fun initialize() {
        executeSubscriptions(plan.initSubscriptions)
        executeWrites(plan.initWrites)
        executeReads(plan.initReads)
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
            if (write.delayBeforeMs > 0) {
                when (val result = transport.execute(HardwareOperation.Sleep(write.delayBeforeMs))) {
                    HardwareResult.Success,
                    is HardwareResult.ReadResult -> Unit
                    is HardwareResult.Failure -> error(result.message)
                }
            }
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
                is HardwareResult.ReadResult,
                -> Unit
                is HardwareResult.Failure -> error(result.message)
            }
        }
    }

    private suspend fun executeReads(reads: List<ScalarProtocolRead>) {
        for (read in reads) {
            val endpoint = requireNotNull(endpointByRole[read.endpointRole]) {
                "${plan.protocolId} endpoint ${read.endpointRole} is missing"
            }
            when (val result = transport.execute(HardwareOperation.Read(endpoint))) {
                is HardwareResult.ReadResult -> plan.onRead?.invoke(state, read.endpointRole, result.bytes)
                HardwareResult.Success -> Unit
                is HardwareResult.Failure -> error(result.message)
            }
        }
    }
}
