package com.funny.aitoy.ble

import kotlin.uuid.Uuid

internal object SistalkMixPwmProtocol : BleDeviceProtocol {
    private val pwmUuid = Uuid.parse("00010203-0405-0607-0809-0a0b0c0d2b12")
    private val sistalkV2ServiceUuid = Uuid.parse("00009000-0000-1000-8000-00805f9b34fb")
    private val sistalkV2OpUuid = Uuid.parse("00009001-0000-1000-8000-00805f9b34fb")
    private val sistalkV2FunctionUuid = Uuid.parse("00009002-0000-1000-8000-00805f9b34fb")

    // MonsterPub V1 Hub 的电机服务/特征。这类设备同样会暴露 2b12，但向 2b12 写入会让其在 ~4 秒后掉电关机
    // （2026-06-24 23:35 白夜魔炮 Trace：写 2b12 → status=8 链路超时）。因此只要存在 6000 电机服务就禁用 Mix PWM，
    // 改走官方 6003/600a/6001 路线。
    private val sistalkMotorServiceUuid = Uuid.parse("00006000-0000-1000-8000-00805f9b34fb")
    private val sistalkMotorWriteUuids = setOf(
        Uuid.parse("0000600a-0000-1000-8000-00805f9b34fb"),
        Uuid.parse("00006003-0000-1000-8000-00805f9b34fb"),
        Uuid.parse("00006001-0000-1000-8000-00805f9b34fb"),
    )
    private val currentPwm = IntArray(2)

    override val status = BleProtocolStatus(
        id = "sistalk_mix_pwm",
        displayName = "SISTALK Mix PWM",
        controllable = true,
        intensityMax = 100,
        supportsMode = true,
        modeMax = 2,
        controlStyle = ToyControlStyle.CombinedPatternAndIntensity,
        modeLabel = "部位",
        modeNames = listOf("吮吸", "尾部震动"),
        automatic = true,
    )

    override fun matches(fingerprint: BleGattFingerprint): Boolean =
        fingerprint.characteristicUuids.contains(pwmUuid) &&
                !fingerprint.hasSistalkV2Gatt() &&
                !fingerprint.hasSistalkMotorService()

    override fun initialize(fingerprint: BleGattFingerprint): List<BleProtocolOperation> {
        currentPwm.fill(0)
        return emptyList()
    }

    override fun commandsFor(action: ToyControlAction): List<BleProtocolOperation> =
        when (action) {
            is ToyControlAction.Intensity -> listOf(updateAllChannels(action.value))
            is ToyControlAction.Combined -> listOf(updateChannel(action.mode, action.intensity))
            is ToyControlAction.DualMotor -> listOf(updateAllChannels(action.strongestIntensity(status.intensityMax)))
            is ToyControlAction.Pattern -> listOf(updateChannel(action.mode, status.intensityMax.coerceAtLeast(1)))
            ToyControlAction.Stop -> stop()?.let(::listOf).orEmpty()
        }

    private fun updateAllChannels(intensity: Int): BleProtocolOperation.Write {
        currentPwm.fill(intensity.coerceIn(0, status.intensityMax))
        return pwm(currentPwm[0], currentPwm[1])
    }

    private fun updateChannel(channel: Int, intensity: Int): BleProtocolOperation.Write {
        val index = channel.coerceIn(1, 2) - 1
        currentPwm[index] = intensity.coerceIn(0, status.intensityMax)
        return pwm(currentPwm[0], currentPwm[1])
    }

    private fun stop(): BleProtocolOperation.Write? {
        if (currentPwm.all { it == 0 }) return null
        currentPwm.fill(0)
        return pwm(0, 0)
    }

    private fun pwm(suction: Int, tailVibration: Int): BleProtocolOperation.Write =
        BleProtocolOperation.Write(
            characteristicUuid = pwmUuid,
            bytes = bytes(
                suction.coerceIn(0, status.intensityMax),
                tailVibration.coerceIn(0, status.intensityMax),
            ),
            withResponse = false,
        )

    private fun BleGattFingerprint.hasSistalkV2Gatt(): Boolean =
        serviceUuids.contains(sistalkV2ServiceUuid) &&
                characteristicUuids.contains(sistalkV2OpUuid) &&
                characteristicUuids.contains(sistalkV2FunctionUuid)

    private fun BleGattFingerprint.hasSistalkMotorService(): Boolean =
        serviceUuids.contains(sistalkMotorServiceUuid) ||
                characteristicUuids.any { it in sistalkMotorWriteUuids }

}

internal object SistalkMonsterPartyProtocol : BleDeviceProtocol {
    private val serviceUuid = Uuid.parse("00009000-0000-1000-8000-00805f9b34fb")
    private val opUuid = Uuid.parse("00009001-0000-1000-8000-00805f9b34fb")
    private val functionUuid = Uuid.parse("00009002-0000-1000-8000-00805f9b34fb")

    private const val COMMAND_MOTOR = 0xA0
    private const val COMMAND_POWER_OFF = 0xA2
    private val currentPwm = IntArray(2)

    override val status = BleProtocolStatus(
        id = "sistalk_monsterparty",
        displayName = "SISTALK Monster Party",
        controllable = true,
        intensityMax = 100,
        supportsMode = true,
        modeMax = 2,
        controlStyle = ToyControlStyle.CombinedPatternAndIntensity,
        modeLabel = "部位",
        modeNames = listOf("吮吸", "尾部震动"),
        automatic = true,
    )

    override fun matches(fingerprint: BleGattFingerprint): Boolean =
        fingerprint.sistalkProtocolVersion != 1 &&
                !fingerprint.hasSvakomManufacturerData() &&
                fingerprint.serviceUuids.contains(serviceUuid) &&
                fingerprint.characteristicUuids.contains(opUuid) &&
                fingerprint.characteristicUuids.contains(functionUuid)

    override fun initialize(fingerprint: BleGattFingerprint): List<BleProtocolOperation> =
        listOf(
            BleProtocolOperation.SubscribeNotify(opUuid),
            BleProtocolOperation.SubscribeNotify(functionUuid),
        ).also {
            currentPwm.fill(0)
        }

    override fun commandsFor(action: ToyControlAction): List<BleProtocolOperation> =
        when (action) {
            is ToyControlAction.Intensity -> listOf(updateAllChannels(action.value))
            is ToyControlAction.Combined -> listOf(updateChannel(action.mode, action.intensity))
            is ToyControlAction.DualMotor -> listOf(updateAllChannels(action.strongestIntensity(status.intensityMax)))
            is ToyControlAction.Pattern -> listOf(updateChannel(action.mode, status.intensityMax.coerceAtLeast(1)))
            ToyControlAction.Stop -> stop()?.let(::listOf).orEmpty()
        }

    private fun updateAllChannels(intensity: Int): BleProtocolOperation.Write {
        currentPwm.fill(intensity.coerceIn(0, status.intensityMax))
        return motorCommand(currentPwm[0], currentPwm[1])
    }

    private fun updateChannel(channel: Int, intensity: Int): BleProtocolOperation.Write {
        val index = channel.coerceIn(1, 2) - 1
        currentPwm[index] = intensity.coerceIn(0, status.intensityMax)
        return motorCommand(currentPwm[0], currentPwm[1])
    }

    private fun stop(): BleProtocolOperation.Write? {
        if (currentPwm.all { it == 0 }) return null
        currentPwm.fill(0)
        return motorCommand(0, 0)
    }

    private fun motorCommand(suction: Int, tailVibration: Int): BleProtocolOperation.Write =
        functionCommand(
            COMMAND_MOTOR,
            0x02,
            suction.coerceIn(0, status.intensityMax),
            tailVibration.coerceIn(0, status.intensityMax),
        )

    private fun functionCommand(command: Int, vararg payload: Int): BleProtocolOperation.Write =
        BleProtocolOperation.Write(
            characteristicUuid = functionUuid,
            bytes = bytes(command, ((payload.size shl 3) or 0x80), *payload),
            withResponse = false,
        )

    @Suppress("unused")
    private fun powerOffVibrationCommand(): BleProtocolOperation.Write =
        functionCommand(COMMAND_POWER_OFF)
}

internal object SistalkPopocatProtocol : BleDeviceProtocol {
    private val serviceUuid = Uuid.parse("00009000-0000-1000-8000-00805f9b34fb")
    private val opUuid = Uuid.parse("00009001-0000-1000-8000-00805f9b34fb")
    private val functionUuid = Uuid.parse("00009002-0000-1000-8000-00805f9b34fb")

    private const val COMMAND_MOTOR = 0xA0
    private val currentLevels = IntArray(PopocatFunction.entries.size)

    override val status = BleProtocolStatus(
        id = "sistalk_popocat",
        displayName = "popocat 猫猫头",
        controllable = true,
        intensityMax = 100,
        supportsMode = true,
        modeMax = PopocatFunction.entries.size,
        controlStyle = ToyControlStyle.IndependentFunctions,
        modeLabel = "功能",
        intensityLabel = "强度",
        channelNames = PopocatFunction.entries.map { it.label },
        features = PopocatFunction.entries.mapIndexed { index, function ->
            BleProtocolFeature(
                type = function.type,
                min = 0,
                max = 100,
                index = index,
                label = function.label,
            )
        },
        automatic = true,
    )

    override fun matches(fingerprint: BleGattFingerprint): Boolean =
        fingerprint.hasPopocatIdentity() &&
                fingerprint.sistalkProtocolVersion != 1 &&
                fingerprint.serviceUuids.contains(serviceUuid) &&
                fingerprint.characteristicUuids.contains(opUuid) &&
                fingerprint.characteristicUuids.contains(functionUuid)

    override fun initialize(fingerprint: BleGattFingerprint): List<BleProtocolOperation> =
        listOf(
            BleProtocolOperation.SubscribeNotify(opUuid),
            BleProtocolOperation.SubscribeNotify(functionUuid),
        ).also {
            currentLevels.fill(0)
        }

    override fun commandsFor(action: ToyControlAction): List<BleProtocolOperation> =
        when (action) {
            is ToyControlAction.Pattern -> listOf(updateAllChannels(status.intensityMax.coerceAtLeast(1)))
            is ToyControlAction.Intensity -> listOf(updateAllChannels(action.value))
            is ToyControlAction.Combined -> listOf(updateFunction(functionForMode(action.mode), action.intensity))
            is ToyControlAction.DualMotor -> listOf(updateTargets(action.internalIntensity, action.externalIntensity))
            ToyControlAction.Stop -> stop()?.let(::listOf).orEmpty()
        }

    private fun updateAllChannels(intensity: Int): BleProtocolOperation.Write {
        val safeIntensity = intensity.coerceIn(0, status.intensityMax)
        currentLevels.fill(safeIntensity)
        return motorCommand()
    }

    private fun updateFunction(function: PopocatFunction, intensity: Int): BleProtocolOperation.Write {
        currentLevels[function.index] = intensity.coerceIn(0, status.intensityMax)
        return motorCommand()
    }

    private fun updateTargets(suction: Int, catHeadVibration: Int): BleProtocolOperation.Write {
        currentLevels[PopocatFunction.Suction.index] = suction.coerceIn(0, status.intensityMax)
        currentLevels[PopocatFunction.CatHeadVibration.index] = catHeadVibration.coerceIn(0, status.intensityMax)
        return motorCommand()
    }

    private fun functionForMode(mode: Int): PopocatFunction {
        val functionCode = mode / 100
        if (functionCode > 0) {
            return PopocatFunction.entries.firstOrNull { it.functionCode == functionCode } ?: PopocatFunction.Suction
        }
        return PopocatFunction.entries.getOrNull(mode.coerceIn(1, PopocatFunction.entries.size) - 1) ?: PopocatFunction.Suction
    }

    private fun stop(): BleProtocolOperation.Write? {
        if (currentLevels.all { it == 0 }) return null
        currentLevels.fill(0)
        return motorCommand()
    }

    private fun motorCommand(): BleProtocolOperation.Write =
        functionCommand(
            currentLevels[PopocatFunction.CatHeadVibration.index],
            currentLevels[PopocatFunction.Suction.index],
            currentLevels[PopocatFunction.ExpansionVibration.index],
        )

    private fun functionCommand(vararg motorLevels: Int): BleProtocolOperation.Write =
        BleProtocolOperation.Write(
            characteristicUuid = functionUuid,
            bytes = bytes(COMMAND_MOTOR, ((motorLevels.size + 1) shl 3) or 0x80, motorLevels.size, *motorLevels),
            withResponse = false,
        )

    private fun BleGattFingerprint.hasPopocatIdentity(): Boolean {
        val manufacturerPayload = manufacturerData.toManufacturerDataMap()[0x2512] ?: return false
        return manufacturerPayload.isPopocatManufacturerPayload()
    }

    private fun ByteArray.isPopocatManufacturerPayload(): Boolean {
        if (size < 4 || unsignedByteAt(0) != 0x02) return false
        val productId = unsignedByteAt(2)
        val variantId = unsignedByteAt(3)
        return productId == 0x27 && variantId == 0x00
    }

    private fun ByteArray.unsignedByteAt(index: Int): Int =
        this[index].unsignedByte()
}

private enum class PopocatFunction(
    val functionCode: Int,
    val type: String,
    val label: String,
) {
    Suction(1, "constrict", "吮吸"),
    CatHeadVibration(2, "vibrate", "猫头震动"),
    ExpansionVibration(3, "vibrate", "拓展震动");

    val index: Int = functionCode - 1
}

private fun ByteArray.unsignedByteAt(index: Int): Int =
    this[index].unsignedByte()

internal object SistalkMonsterPartyMp2Protocol : SistalkMonsterPartyDualVibrationProtocolBase(
    id = "sistalk_monsterparty_mp2",
    displayName = "小怪兽二代",
    channelNames = listOf("内侧震动", "外侧震动"),
) {
    override fun hasDeviceIdentity(fingerprint: BleGattFingerprint): Boolean {
        val manufacturerPayload = fingerprint.manufacturerData.toManufacturerDataMap()[0x2512] ?: return false
        return manufacturerPayload.isMonsterPartyMp2ManufacturerPayload()
    }

    private fun ByteArray.isMonsterPartyMp2ManufacturerPayload(): Boolean {
        if (size < 4 || unsignedByteAt(0) != 0x02) return false
        val productId = unsignedByteAt(2)
        val variantId = unsignedByteAt(3)
        return productId == 0x18 && variantId == 0x00
    }
}

internal object SistalkWeightless808Protocol : SistalkMonsterPartyDualVibrationProtocolBase(
    id = "sistalk_weightless_808",
    displayName = "失重808",
    channelNames = listOf("头震", "尾震"),
) {
    override fun hasDeviceIdentity(fingerprint: BleGattFingerprint): Boolean {
        if (fingerprint.name.normalizedDeviceName().contains("失重808")) return true
        val manufacturerPayload = fingerprint.manufacturerData.toManufacturerDataMap()[0x2512] ?: return false
        return manufacturerPayload.isWeightless808ManufacturerPayload()
    }

    private fun ByteArray.isWeightless808ManufacturerPayload(): Boolean {
        if (size < 4 || unsignedByteAt(0) != 0x02) return false
        val productId = unsignedByteAt(2)
        val variantId = unsignedByteAt(3)
        val hardwareId = getOrNull(4)?.unsignedByte() ?: 0
        return when (productId) {
            0x07 -> variantId == 0x00 && hardwareId == 0x0D
            0x1C -> variantId == 0x00
            else -> false
        }
    }
}

internal abstract class SistalkMonsterPartyDualVibrationProtocolBase(
    id: String,
    displayName: String,
    private val channelNames: List<String>,
) : BleDeviceProtocol {
    private val serviceUuid = Uuid.parse("00009000-0000-1000-8000-00805f9b34fb")
    private val opUuid = Uuid.parse("00009001-0000-1000-8000-00805f9b34fb")
    private val functionUuid = Uuid.parse("00009002-0000-1000-8000-00805f9b34fb")

    private val commandMotor = 0xA0
    private val startupPwm = 70
    private val startupSettleMs = 120L
    private val currentPwm = IntArray(2)

    final override val status = BleProtocolStatus(
        id = id,
        displayName = displayName,
        controllable = true,
        intensityMax = 100,
        supportsMode = false,
        controlStyle = ToyControlStyle.DualIntensityOnly,
        intensityLabel = "强度",
        channelNames = channelNames,
        features = listOf(
            BleProtocolFeature(type = "vibrate", min = 0, max = 100, index = 0, label = channelNames[0]),
            BleProtocolFeature(type = "vibrate", min = 0, max = 100, index = 1, label = channelNames[1]),
        ),
        automatic = true,
    )

    final override fun matches(fingerprint: BleGattFingerprint): Boolean =
        hasDeviceIdentity(fingerprint) &&
                fingerprint.sistalkProtocolVersion != 1 &&
                fingerprint.serviceUuids.contains(serviceUuid) &&
                fingerprint.characteristicUuids.contains(opUuid) &&
                fingerprint.characteristicUuids.contains(functionUuid)

    protected abstract fun hasDeviceIdentity(fingerprint: BleGattFingerprint): Boolean

    final override fun initialize(fingerprint: BleGattFingerprint): List<BleProtocolOperation> =
        listOf(
            BleProtocolOperation.SubscribeNotify(opUuid),
            BleProtocolOperation.SubscribeNotify(functionUuid),
        ).also {
            currentPwm.fill(0)
        }

    final override fun commandsFor(action: ToyControlAction): List<BleProtocolOperation> =
        when (action) {
            is ToyControlAction.Intensity -> updateAllChannels(action.value)
            is ToyControlAction.Combined -> updateChannel(action.mode, action.intensity)
            is ToyControlAction.DualMotor -> updateDualChannels(action.internalIntensity, action.externalIntensity)
            is ToyControlAction.Pattern -> updateAllChannels(status.intensityMax.coerceAtLeast(1))
            ToyControlAction.Stop -> stop()?.let(::listOf).orEmpty()
        }

    private fun updateAllChannels(intensity: Int): List<BleProtocolOperation> {
        val target = intensity.coerceIn(0, status.intensityMax)
        return updateTargets(target, target)
    }

    private fun updateDualChannels(headVibration: Int, tailVibration: Int): List<BleProtocolOperation> =
        updateTargets(
            headVibration.coerceIn(0, status.intensityMax),
            tailVibration.coerceIn(0, status.intensityMax),
        )

    private fun updateChannel(channel: Int, intensity: Int): List<BleProtocolOperation> {
        val target = currentPwm.copyOf()
        val index = channel.coerceIn(1, 2) - 1
        target[index] = intensity.coerceIn(0, status.intensityMax)
        return updateTargets(target[0], target[1])
    }

    private fun updateTargets(headVibration: Int, tailVibration: Int): List<BleProtocolOperation> {
        val startupHead = currentPwm[0] == 0 && headVibration in 1 until startupPwm
        val startupTail = currentPwm[1] == 0 && tailVibration in 1 until startupPwm
        val operations = buildList {
            if (startupHead || startupTail) {
                add(
                    motorCommand(
                        if (startupHead) startupPwm else headVibration,
                        if (startupTail) startupPwm else tailVibration,
                    )
                )
                add(BleProtocolOperation.Sleep(startupSettleMs))
            }
            add(motorCommand(headVibration, tailVibration))
        }
        currentPwm[0] = headVibration
        currentPwm[1] = tailVibration
        return operations
    }

    private fun stop(): BleProtocolOperation.Write? {
        if (currentPwm.all { it == 0 }) return null
        currentPwm.fill(0)
        return motorCommand(0, 0)
    }

    private fun motorCommand(headVibration: Int, tailVibration: Int): BleProtocolOperation.Write =
        BleProtocolOperation.Write(
            characteristicUuid = functionUuid,
            bytes = bytes(
                commandMotor,
                (3 shl 3) or 0x80,
                0x02,
                headVibration.coerceIn(0, status.intensityMax),
                tailVibration.coerceIn(0, status.intensityMax),
            ),
            withResponse = false,
        )

}

internal object SistalkMonsterPubMultiMotorProtocol : SistalkMonsterPubProtocolBase(
    id = "sistalk_monsterpub_multi",
    displayName = "SISTALK MonsterPub",
    writeUuid = Uuid.parse("0000600a-0000-1000-8000-00805f9b34fb"),
    channelCount = 10,
    intensityMax = 100,
)

internal object SistalkMonsterPubDualMotorProtocol : SistalkMonsterPubProtocolBase(
    id = "sistalk_monsterpub_dual",
    displayName = "SISTALK MonsterPub",
    writeUuid = Uuid.parse("00006003-0000-1000-8000-00805f9b34fb"),
    channelCount = 2,
    intensityMax = 100,
    // 白夜魔炮等双电机 Hub：吮吸 + 震动两个模式，各有档位。
    // 通道顺序（字节0=吮吸 / 字节1=震动）以真机反馈为准，如反了把这里两项对调即可。
    channelNames = listOf("吮吸", "震动"),
)

internal object SistalkMonsterPubSingleMotorProtocol : SistalkMonsterPubProtocolBase(
    id = "sistalk_monsterpub",
    displayName = "SISTALK MonsterPub",
    writeUuid = Uuid.parse("00006001-0000-1000-8000-00805f9b34fb"),
    channelCount = 1,
    intensityMax = 20,
    useLegacyLevelMap = true,
)

internal sealed class SistalkMonsterPubProtocolBase(
    id: String,
    displayName: String,
    private val writeUuid: Uuid,
    private val channelCount: Int,
    private val intensityMax: Int,
    private val useLegacyLevelMap: Boolean = false,
    channelNames: List<String>? = null,
) : BleDeviceProtocol {
    private val motorServiceUuid = Uuid.parse("00006000-0000-1000-8000-00805f9b34fb")
    // 官方 BleDeviceV1._fetchInfo 在连接后会订阅心跳(600b)与读取电量(6051)等通知通道，
    // 让设备认定为合法 App 会话。缺少该握手是历史「6000 路线无反应」的主要原因。
    private val heartbeatUuid = Uuid.parse("0000600b-0000-1000-8000-00805f9b34fb")
    private val batteryNotifyUuid = Uuid.parse("00006051-0000-1000-8000-00805f9b34fb")
    private val resolvedChannelNames = channelNames
    private val currentPwm = IntArray(channelCount)
    private val levelMap = intArrayOf(
        0,
        10,
        14,
        18,
        22,
        26,
        30,
        34,
        38,
        42,
        46,
        50,
        54,
        58,
        62,
        66,
        70,
        74,
        78,
        82,
        86,
    )

    override val status = BleProtocolStatus(
        id = id,
        displayName = displayName,
        controllable = true,
        intensityMax = intensityMax,
        supportsMode = channelCount > 1,
        modeMax = channelCount,
        controlStyle = if (channelCount > 1) {
            ToyControlStyle.CombinedPatternAndIntensity
        } else {
            ToyControlStyle.IntensityOnly
        },
        modeLabel = "部位",
        modeNames = resolvedChannelNames ?: (1..channelCount).map { "部位 $it" },
        automatic = true,
    )

    override fun matches(fingerprint: BleGattFingerprint): Boolean {
        return fingerprint.serviceUuids.contains(motorServiceUuid) &&
                fingerprint.characteristicUuids.contains(writeUuid) &&
                fingerprint.sistalkProtocolVersion <= 1
    }

    override fun initialize(fingerprint: BleGattFingerprint): List<BleProtocolOperation> {
        currentPwm.fill(0)
        // 还原官方 V1 连接握手：订阅设备实际存在的心跳/电量通知通道，再开始电机写入。
        return buildList {
            if (fingerprint.characteristicUuids.contains(heartbeatUuid)) {
                add(BleProtocolOperation.SubscribeNotify(heartbeatUuid))
            }
            if (fingerprint.characteristicUuids.contains(batteryNotifyUuid)) {
                add(BleProtocolOperation.SubscribeNotify(batteryNotifyUuid))
            }
        }
    }

    override fun commandsFor(action: ToyControlAction): List<BleProtocolOperation> =
        when (action) {
            is ToyControlAction.Intensity -> listOf(updateAllChannels(action.value))
            is ToyControlAction.Combined -> listOf(updateChannel(action.mode, action.intensity))
            is ToyControlAction.DualMotor -> listOf(updateAllChannels(action.strongestIntensity(status.intensityMax)))
            is ToyControlAction.Pattern -> listOf(updateChannel(action.mode, status.intensityMax.coerceAtLeast(1)))
            ToyControlAction.Stop -> stop()?.let(::listOf).orEmpty()
        }

    private fun updateAllChannels(intensity: Int): BleProtocolOperation.Write {
        currentPwm.fill(toLevel(intensity))
        return motorWrite(currentPwm)
    }

    private fun updateChannel(channel: Int, intensity: Int): BleProtocolOperation.Write {
        val index = channel.coerceIn(1, channelCount) - 1
        currentPwm[index] = toLevel(intensity)
        return motorWrite(currentPwm)
    }

    private fun toLevel(intensity: Int): Int =
        if (useLegacyLevelMap) {
            levelMap[intensity.coerceIn(0, status.intensityMax)]
        } else {
            intensity.coerceIn(0, status.intensityMax)
        }

    private fun stop(): BleProtocolOperation.Write? {
        if (currentPwm.all { it == 0 }) return null
        currentPwm.fill(0)
        return motorWrite(currentPwm)
    }

    private fun motorWrite(levels: IntArray): BleProtocolOperation.Write =
        BleProtocolOperation.Write(
            characteristicUuid = writeUuid,
            bytes = levels.toByteArrayForSistalkV1(),
            withResponse = false,
        )
}

private val BleGattFingerprint.sistalkProtocolVersion: Int
    get() = manufacturerData.firstManufacturerByte() ?: 0
