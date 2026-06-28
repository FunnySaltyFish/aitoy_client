package com.funny.aitoy.buttplug

enum class ToyOutputKind {
    Vibrate,
    Oscillate,
    Rotate,
    Constrict,
    Inflate,
    Linear,
    Position,
    Heater,
    Led,
    Spray,
    Temperature,
    Unknown,
}

data class ToyFeature(
    val index: Int,
    val kind: ToyOutputKind,
    val label: String,
    val min: Int,
    val max: Int,
)

sealed interface ToyOutputCommand {
    data class Scalar(
        val featureIndex: Int,
        val kind: ToyOutputKind,
        val value: Int,
    ) : ToyOutputCommand

    data class Linear(
        val featureIndex: Int,
        val position: Int,
        val durationMs: Int,
    ) : ToyOutputCommand

    data class Pattern(
        val patternIndex: Int,
        val values: Map<Int, Int>,
    ) : ToyOutputCommand

    data object Stop : ToyOutputCommand
}

sealed interface HardwareOperation {
    data class Write(
        val endpoint: String,
        val bytes: ByteArray,
        val withResponse: Boolean,
    ) : HardwareOperation

    data class Read(val endpoint: String) : HardwareOperation
    data class Subscribe(val endpoint: String) : HardwareOperation
    data class Advertise(val payload: BroadcastPayload) : HardwareOperation
    data class Sleep(val millis: Long) : HardwareOperation
}

data class BroadcastPayload(
    val serviceUuid: String = "",
    val manufacturerId: Int? = null,
    val manufacturerData: ByteArray = byteArrayOf(),
)

sealed interface HardwareResult {
    data object Success : HardwareResult
    data class ReadResult(val bytes: ByteArray) : HardwareResult
    data class Failure(val message: String) : HardwareResult
}

interface ToyTransport {
    suspend fun execute(operation: HardwareOperation): HardwareResult
}

interface ToyProtocolHandler {
    val protocolId: String
    val supportedOutputKinds: Set<ToyOutputKind>
    fun createSession(match: ButtplugDeviceMatch, transport: ToyTransport): ToyProtocolSession
}

interface ToyProtocolSession {
    val supportEntry: ButtplugSupportEntry
    suspend fun initialize()
    suspend fun handle(command: ToyOutputCommand)
    suspend fun stop()
}

fun ButtplugOutputFeature.toToyFeature(): ToyFeature =
    ToyFeature(
        index = featureIndex,
        kind = type.toToyOutputKind(),
        label = description,
        min = min,
        max = max,
    )

fun String.toToyOutputKind(): ToyOutputKind =
    when (lowercase()) {
        OUTPUT_VIBRATE -> ToyOutputKind.Vibrate
        OUTPUT_OSCILLATE -> ToyOutputKind.Oscillate
        OUTPUT_ROTATE -> ToyOutputKind.Rotate
        OUTPUT_CONSTRICT -> ToyOutputKind.Constrict
        OUTPUT_INFLATE -> ToyOutputKind.Inflate
        OUTPUT_POSITION -> ToyOutputKind.Position
        OUTPUT_HW_POSITION_WITH_DURATION -> ToyOutputKind.Linear
        OUTPUT_HEATER -> ToyOutputKind.Heater
        OUTPUT_LED -> ToyOutputKind.Led
        OUTPUT_SPRAY -> ToyOutputKind.Spray
        OUTPUT_TEMPERATURE -> ToyOutputKind.Temperature
        else -> ToyOutputKind.Unknown
    }

fun ButtplugDeviceDefinition.toToyFeatures(): List<ToyFeature> =
    features.map(ButtplugOutputFeature::toToyFeature)
