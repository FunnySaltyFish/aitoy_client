package com.funny.aitoy.ble

import kotlin.uuid.Uuid

// 享要小程序（ref/享要-wxapp/wx3a9d61e08ec6acf0_unpacked）官方控制端。
// GATT：service FF60、写 FF61、通知 FF62。控制帧为 4 字节明文 + 1 字节累加和，
// 再用固定 key 对 5 字节逐位异或；控制 `35 62 <功能位> <档位>`，停止 `35 60 <功能位> 00`。

private val MESANEL_SERVICE_UUID = Uuid.parse("0000ff60-0000-1000-8000-00805f9b34fb")
private val MESANEL_WRITE_UUID = Uuid.parse("0000ff61-0000-1000-8000-00805f9b34fb")
private val MESANEL_NOTIFY_UUID = Uuid.parse("0000ff62-0000-1000-8000-00805f9b34fb")
private val MESANEL_XOR_KEY = intArrayOf(0xF6, 0x1E, 0x25, 0x62)

internal data class MesanelFunction(
    val code: Int,
    val type: String,
    val label: String,
    val commandMax: Int,
    val modeMax: Int = 0,
    val intensityMax: Int = commandMax,
)

internal data class MesanelProfile(
    val id: String,
    val displayName: String,
    val nameTokens: Set<String>,
    val functions: List<MesanelFunction>,
)

internal val MESANEL_PROFILES: List<MesanelProfile> = listOf(
    MesanelProfile(
        id = "mesanel_coco",
        displayName = "享要 口口舱",
        nameTokens = setOf("coco"),
        functions = listOf(
            MesanelFunction(0x01, "licking", "舔动", commandMax = 10, modeMax = 10, intensityMax = 1),
            MesanelFunction(0x02, "constrict", "吮吸", commandMax = 3, modeMax = 3, intensityMax = 1),
        ),
    ),
    MesanelProfile(
        id = "mesanel_cocopro",
        displayName = "享要 口口舱 Pro",
        nameTokens = setOf("cocopro"),
        functions = listOf(
            MesanelFunction(0x01, "licking", "舔动", commandMax = 10, modeMax = 10, intensityMax = 1),
            MesanelFunction(0x02, "constrict", "吮吸", commandMax = 3, modeMax = 3, intensityMax = 1),
            MesanelFunction(0x04, "vibrate", "震动棒", 10),
        ),
    ),
    MesanelProfile(
        id = "mesanel_super",
        displayName = "享要 速泡",
        nameTokens = setOf("super"),
        functions = listOf(
            MesanelFunction(0x01, "oscillate", "伸缩", 20),
            MesanelFunction(0x02, "vibrate", "震动", 20),
            MesanelFunction(0x04, "vibrate", "震动棒", 20),
        ),
    ),
    MesanelProfile(
        id = "mesanel_handou",
        displayName = "享要 含豆",
        nameTokens = setOf("handou"),
        functions = listOf(
            MesanelFunction(0x01, "constrict", "吮吸", 20),
            MesanelFunction(0x02, "vibrate", "震动", 20),
        ),
    ),
    MesanelProfile(
        id = "mesanel_cisecat",
        displayName = "享要 海狸",
        nameTokens = setOf("cisecat"),
        functions = listOf(
            MesanelFunction(0x01, "vibrate", "头部", 20),
            MesanelFunction(0x02, "vibrate", "尾部", 20),
        ),
    ),
    MesanelProfile(
        id = "mesanel_pinkpo",
        displayName = "享要 粉噗",
        nameTokens = setOf("pinkpo"),
        functions = listOf(
            MesanelFunction(0x01, "constrict", "吮吸", 20),
            MesanelFunction(0x02, "vibrate", "拍震", 20),
        ),
    ),
    MesanelProfile(
        id = "mesanel_bosscat",
        displayName = "享要 啵吸猫",
        nameTokens = setOf("bosscat"),
        functions = listOf(
            MesanelFunction(0x01, "vibrate", "震动", 20),
            MesanelFunction(0x02, "constrict", "吮吸", 20),
        ),
    ),
)

private val MESANEL_GENERIC_PROFILE = MesanelProfile(
    id = "mesanel_generic",
    displayName = "享要玩具",
    nameTokens = emptySet(),
    functions = listOf(
        MesanelFunction(0x01, "vibrate", "功能 1", 20),
        MesanelFunction(0x02, "constrict", "功能 2", 20),
        MesanelFunction(0x04, "vibrate", "功能 3", 20),
    ),
)

internal fun mesanelEncryptedFrame(command: Int, functionCode: Int, value: Int): ByteArray {
    val plain = intArrayOf(
        0x35,
        command.coerceIn(0, 0xff),
        functionCode.coerceIn(0, 0xff),
        value.coerceIn(0, 0xff),
    )
    val checksum = plain.sum() and 0xff
    return (plain + checksum)
        .mapIndexed { index, byte -> ((byte xor MESANEL_XOR_KEY[index % MESANEL_XOR_KEY.size]) and 0xff).toByte() }
        .toByteArray()
}

internal fun mesanelFunctionCodeForFeature(feature: BleProtocolFeature): Int =
    when (feature.index) {
        0 -> 0x01
        1 -> 0x02
        2 -> 0x04
        else -> feature.index + 1
    }

private fun BleGattFingerprint.hasMesanelGatt(): Boolean =
    serviceUuids.contains(MESANEL_SERVICE_UUID) &&
            characteristicUuids.contains(MESANEL_WRITE_UUID)

private fun BleGattFingerprint.mesanelProfile(): MesanelProfile {
    val normalizedName = name.normalizedDeviceName()
    return MESANEL_PROFILES.sortedByDescending { profile ->
        profile.nameTokens.maxOfOrNull { it.length } ?: 0
    }.firstOrNull { profile ->
        profile.nameTokens.any { token -> normalizedName.contains(token) }
    } ?: MESANEL_GENERIC_PROFILE
}

internal object MesanelProtocol : BleDeviceProtocol {
    override val status = MesanelProfileProtocol(MESANEL_GENERIC_PROFILE).status

    override fun matches(fingerprint: BleGattFingerprint): Boolean =
        fingerprint.hasMesanelGatt()

    override fun createInstance(fingerprint: BleGattFingerprint): BleDeviceProtocol =
        MesanelProfileProtocol(fingerprint.mesanelProfile())

    override fun commandsFor(action: ToyControlAction): List<BleProtocolOperation> =
        MesanelProfileProtocol(MESANEL_GENERIC_PROFILE).commandsFor(action)
}

internal class MesanelProfileProtocol(
    private val profile: MesanelProfile,
) : BleDeviceProtocol {
    override val status = BleProtocolStatus(
        id = profile.id,
        displayName = profile.displayName,
        controllable = true,
        intensityMax = profile.functions.maxOfOrNull { it.intensityMax } ?: 20,
        supportsMode = false,
        controlStyle = ToyControlStyle.IndependentFunctions,
        intensityLabel = "档位",
        channelNames = profile.functions.map { it.label },
        features = profile.functions.mapIndexed { index, function ->
            BleProtocolFeature(
                type = function.type,
                min = 0,
                max = function.intensityMax,
                index = index,
                label = function.label,
                modeMax = function.modeMax,
            )
        },
        automatic = true,
        repeatIntervalMs = 6000,
    )

    override fun matches(fingerprint: BleGattFingerprint): Boolean = false

    override fun initialize(fingerprint: BleGattFingerprint): List<BleProtocolOperation> =
        listOf(
            BleProtocolOperation.SubscribeNotify(MESANEL_NOTIFY_UUID),
            write(mesanelEncryptedFrame(0x50, 0x00, 0x00)),
        )

    override fun keepaliveIntervalMs(): Long = 6000L

    override fun keepaliveCommands(lastCommands: List<BleProtocolOperation>): List<BleProtocolOperation> =
        listOf(write(mesanelEncryptedFrame(0x50, 0x00, 0x00)))

    override fun commandsFor(action: ToyControlAction): List<BleProtocolOperation> =
        when (action) {
            is ToyControlAction.Pattern -> listOf(run(profile.functions.first(), action.mode))
            is ToyControlAction.Intensity -> listOf(run(profile.functions.first(), action.value))
            is ToyControlAction.Combined -> listOf(commandForCombined(action))
            is ToyControlAction.DualMotor -> profile.functions.take(2).mapIndexed { index, function ->
                run(function, if (index == 0) action.internalIntensity else action.externalIntensity)
            }
            ToyControlAction.Stop -> listOf(stop(functionCode = 0x00))
        }

    private fun commandForCombined(action: ToyControlAction.Combined): BleProtocolOperation {
        val function = functionForMode(action.mode)
        val modeValue = action.mode % 100
        val value = if (function.modeMax > 0) {
            modeValue.coerceIn(1, function.modeMax)
        } else {
            action.intensity
        }
        return if (action.intensity <= 0) stop(function.code) else run(function, value)
    }

    private fun functionForMode(mode: Int): MesanelFunction {
        val code = mode / 100
        if (code > 0) {
            profile.functions.firstOrNull { it.code == code }?.let { return it }
        }
        return profile.functions.getOrNull(mode.coerceAtLeast(1) - 1) ?: profile.functions.first()
    }

    private fun run(function: MesanelFunction, value: Int): BleProtocolOperation.Write =
        write(mesanelEncryptedFrame(0x62, function.code, value.coerceIn(0, function.commandMax)))

    private fun stop(functionCode: Int): BleProtocolOperation.Write =
        write(mesanelEncryptedFrame(0x60, functionCode, 0x00))

    private fun write(bytes: ByteArray): BleProtocolOperation.Write =
        BleProtocolOperation.Write(
            characteristicUuid = MESANEL_WRITE_UUID,
            bytes = bytes,
            withResponse = false,
        )
}
