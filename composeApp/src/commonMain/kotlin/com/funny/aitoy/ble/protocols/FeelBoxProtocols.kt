package com.funny.aitoy.ble

import kotlin.uuid.Uuid

// 感觉盒子 Flutter 官方端：
// service BAE0、写 BAE1、通知 BAE2。连接后订阅通知，并依次发送 AA01 / AA04 初始化帧。
// 星球走单吮吸；紫月、芋泥啵啵、福袋走同一组吮吸 + 震动 + 节奏控制帧。

private val FEELBOX_SERVICE_UUID = Uuid.parse("0000bae0-0000-1000-8000-00805f9b34fb")
private val FEELBOX_WRITE_UUID = Uuid.parse("0000bae1-0000-1000-8000-00805f9b34fb")
private val FEELBOX_NOTIFY_UUID = Uuid.parse("0000bae2-0000-1000-8000-00805f9b34fb")

internal val feelBoxProtocols: List<BleDeviceProtocol> = listOf(
    FeelBoxProfileProtocol(
        profile = FeelBoxProfile(
            id = "feelbox_planet",
            displayName = "感觉星球",
            nameTokens = setOf("s071", "feelplanet"),
            functions = listOf(
                FeelBoxFunction(1, "constrict", "吮吸", FeelBoxCommand.Suction),
            ),
        ),
    ),
    FeelBoxProfileProtocol(
        profile = FeelBoxProfile(
            id = "feelbox_moon",
            displayName = "紫月",
            nameTokens = setOf("feelbox02", "feelmoon"),
            functions = FeelBoxFunction.multiFunctions(),
        ),
    ),
    FeelBoxProfileProtocol(
        profile = FeelBoxProfile(
            id = "feelbox_bobo",
            displayName = "芋泥啵啵",
            nameTokens = setOf("fbbobo", "feelbobo"),
            functions = FeelBoxFunction.multiFunctions(),
        ),
    ),
    FeelBoxProfileProtocol(
        profile = FeelBoxProfile(
            id = "feelbox_lucky",
            displayName = "福袋",
            nameTokens = setOf("fblucky", "fbluckybag", "feellucky"),
            functions = FeelBoxFunction.multiFunctions(),
        ),
    ),
)

private data class FeelBoxProfile(
    val id: String,
    val displayName: String,
    val nameTokens: Set<String>,
    val functions: List<FeelBoxFunction>,
)

private data class FeelBoxFunction(
    val code: Int,
    val type: String,
    val label: String,
    val command: FeelBoxCommand,
    val modeMax: Int = 0,
) {
    companion object {
        fun multiFunctions(): List<FeelBoxFunction> = listOf(
            FeelBoxFunction(1, "constrict", "吮吸", FeelBoxCommand.Suction),
            FeelBoxFunction(2, "vibrate", "震动", FeelBoxCommand.Vibration),
            FeelBoxFunction(3, "thrust", "拍打", FeelBoxCommand.Pattern, modeMax = FeelBoxCommand.MODE_MAX),
        )
    }
}

private enum class FeelBoxCommand {
    Suction,
    Vibration,
    Pattern;

    companion object {
        const val MODE_MAX = 9
    }
}

private class FeelBoxProfileProtocol(
    private val profile: FeelBoxProfile,
) : BleDeviceProtocol {
    override val status = BleProtocolStatus(
        id = profile.id,
        displayName = profile.displayName,
        controllable = true,
        verifiedControl = false,
        intensityMax = FEELBOX_INTENSITY_MAX,
        supportsMode = profile.functions.any { it.modeMax > 0 },
        modeMax = FeelBoxCommand.MODE_MAX,
        controlStyle = if (profile.functions.size == 1) {
            ToyControlStyle.IntensityOnly
        } else {
            ToyControlStyle.IndependentFunctions
        },
        modeLabel = "节奏",
        intensityLabel = if (profile.functions.size == 1) "吮吸强度" else "强度",
        channelNames = profile.functions.map { it.label },
        features = profile.functions.mapIndexed { index, function ->
            BleProtocolFeature(
                type = function.type,
                min = 0,
                max = FEELBOX_INTENSITY_MAX,
                index = index,
                label = function.label,
                modeMax = function.modeMax,
                functionCode = function.code,
            )
        },
        automatic = true,
        repeatIntervalMs = 5000,
    )

    override fun matches(fingerprint: BleGattFingerprint): Boolean {
        val name = fingerprint.name.normalizedDeviceName()
        return profile.nameTokens.any { token -> name == token || name.startsWith(token) } && fingerprint.hasFeelBoxGatt()
    }

    override fun initialize(fingerprint: BleGattFingerprint): List<BleProtocolOperation> =
        listOf(
            BleProtocolOperation.SubscribeNotify(FEELBOX_NOTIFY_UUID),
            write(feelBoxFrame(command = 0x01)),
            write(feelBoxFrame(command = 0x04)),
        )

    override fun keepaliveIntervalMs(): Long = 5000L

    override fun keepaliveCommands(lastCommands: List<BleProtocolOperation>): List<BleProtocolOperation> =
        lastCommands.takeIf { it.isNotEmpty() } ?: listOf(write(feelBoxFrame(command = 0x04)))

    override fun commandsFor(action: ToyControlAction): List<BleProtocolOperation> =
        when (action) {
            is ToyControlAction.Pattern -> listOf(command(profile.functions.first(), action.mode, FEELBOX_INTENSITY_MAX))
            is ToyControlAction.Intensity -> listOf(command(profile.functions.first(), mode = 1, intensity = action.value))
            is ToyControlAction.Combined -> listOf(commandForCombined(action))
            is ToyControlAction.DualMotor -> dualCommands(action)
            ToyControlAction.Stop -> stopCommands()
        }

    private fun commandForCombined(action: ToyControlAction.Combined): BleProtocolOperation {
        val function = functionForMode(action.mode)
        val requestedMode = action.mode % 100
        val mode = if (function.modeMax > 0) requestedMode.coerceIn(1, function.modeMax) else 1
        return command(function, mode, action.intensity)
    }

    private fun dualCommands(action: ToyControlAction.DualMotor): List<BleProtocolOperation> {
        val first = profile.functions.getOrNull(0) ?: return stopCommands()
        val second = profile.functions.getOrNull(1)
        return listOfNotNull(
            command(first, mode = 1, intensity = action.internalIntensity),
            second?.let { command(it, mode = 1, intensity = action.externalIntensity) },
        )
    }

    private fun stopCommands(): List<BleProtocolOperation> =
        profile.functions.map { function -> command(function, mode = 1, intensity = 0) }

    private fun functionForMode(mode: Int): FeelBoxFunction {
        val code = mode / 100
        if (code > 0) {
            profile.functions.firstOrNull { it.code == code }?.let { return it }
        }
        return profile.functions.getOrNull(mode.coerceIn(1, profile.functions.size) - 1)
            ?: profile.functions.first()
    }

    private fun command(function: FeelBoxFunction, mode: Int, intensity: Int): BleProtocolOperation.Write =
        when (function.command) {
            FeelBoxCommand.Suction -> write(feelBoxSuctionFrame(intensity))
            FeelBoxCommand.Vibration -> write(feelBoxModeFrame(mode = 3, intensity = intensity))
            FeelBoxCommand.Pattern -> write(feelBoxModeFrame(mode = mode, intensity = intensity))
        }

    private fun write(bytes: ByteArray): BleProtocolOperation.Write =
        BleProtocolOperation.Write(
            characteristicUuid = FEELBOX_WRITE_UUID,
            bytes = bytes,
            withResponse = false,
        )
}

private fun BleGattFingerprint.hasFeelBoxGatt(): Boolean =
    characteristicUuids.contains(FEELBOX_WRITE_UUID) &&
            characteristicUuids.contains(FEELBOX_NOTIFY_UUID) &&
            (serviceUuids.isEmpty() || serviceUuids.contains(FEELBOX_SERVICE_UUID))

private fun feelBoxSuctionFrame(intensity: Int): ByteArray =
    bytes(0xAA, 0x03, 0x01, 0x02, 0x03, intensity.feelBoxStrength()) + ByteArray(12)

private fun feelBoxModeFrame(mode: Int, intensity: Int): ByteArray =
    bytes(0xAA, 0x05, mode.coerceIn(1, FeelBoxCommand.MODE_MAX), intensity.feelBoxStrength()) +
            bytes(mode.coerceIn(1, FeelBoxCommand.MODE_MAX), intensity.feelBoxStrength()) +
            ByteArray(12)

private fun feelBoxFrame(command: Int): ByteArray =
    bytes(0xAA, command) + ByteArray(16)

private fun Int.feelBoxStrength(): Int =
    coerceIn(0, FEELBOX_INTENSITY_MAX)

private const val FEELBOX_INTENSITY_MAX = 40
