package com.funny.aitoy.ble

import java.util.UUID

internal sealed interface BleProtocolOperation {
    data class Read(val characteristicUuid: UUID) : BleProtocolOperation

    data class SubscribeNotify(val characteristicUuid: UUID) : BleProtocolOperation

    data class Write(
        val characteristicUuid: UUID,
        val bytes: ByteArray,
        val withResponse: Boolean,
    ) : BleProtocolOperation {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Write

            if (withResponse != other.withResponse) return false
            if (characteristicUuid != other.characteristicUuid) return false
            if (!bytes.contentEquals(other.bytes)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = withResponse.hashCode()
            result = 31 * result + characteristicUuid.hashCode()
            result = 31 * result + bytes.contentHashCode()
            return result
        }
    }
}

internal data class BleGattFingerprint(
    val name: String,
    val manufacturerData: String,
    val scanRecordHex: String,
    val serviceUuids: Set<UUID>,
    val characteristicUuids: Set<UUID>,
) {
    val sistalkProtocolVersion: Int
        get() = manufacturerData.firstManufacturerByte() ?: 0
}

internal interface BleDeviceProtocol {
    val status: BleProtocolStatus

    fun matches(fingerprint: BleGattFingerprint): Boolean

    fun initialize(fingerprint: BleGattFingerprint): List<BleProtocolOperation> = emptyList()

    fun onRead(characteristicUuid: UUID, bytes: ByteArray): List<BleProtocolOperation> = emptyList()

    fun commandsFor(action: ToyControlAction): List<BleProtocolOperation>
}

internal object BleProtocolRegistry {
    private val protocols =
        listOf(AnkniProtocol) +
                ankniDdddProfileProtocols +
                listOf(
                    AnkniYwtdProtocol,
                    KissToyProtocol,
                    SistalkMonsterPartyProtocol,
                    SistalkMixPwmProtocol,
                    SistalkMonsterPubMultiMotorProtocol,
                    SistalkMonsterPubDualMotorProtocol,
                    SistalkMonsterPubSingleMotorProtocol,
                    SvakomQhSx045Protocol,
                    MizzzeeXhtkjProtocol,
                    SenseeCcpa10S2Protocol,
                    LovenseProtocol,
                    SatisfyerProtocol,
                    OhMiBodEsca2Protocol,
                    PinkPunchProtocol,
                    LovenutsProtocol,
                    JeJoueNuoProtocol,
                )

    fun resolve(fingerprint: BleGattFingerprint): BleDeviceProtocol? =
        protocols.firstOrNull { it.matches(fingerprint) }

    fun resolveAll(fingerprint: BleGattFingerprint): List<BleDeviceProtocol> =
        protocols.filter { it.matches(fingerprint) }
}

private object SistalkMixPwmProtocol : BleDeviceProtocol {
    private val pwmUuid = uuid("00010203-0405-0607-0809-0a0b0c0d2b12")
    private val sistalkV2ServiceUuid = uuid("00009000-0000-1000-8000-00805f9b34fb")
    private val sistalkV2OpUuid = uuid("00009001-0000-1000-8000-00805f9b34fb")
    private val sistalkV2FunctionUuid = uuid("00009002-0000-1000-8000-00805f9b34fb")
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
                !fingerprint.hasSistalkV2Gatt()

    override fun initialize(fingerprint: BleGattFingerprint): List<BleProtocolOperation> {
        currentPwm.fill(0)
        return emptyList()
    }

    override fun commandsFor(action: ToyControlAction): List<BleProtocolOperation> =
        when (action) {
            is ToyControlAction.Intensity -> listOf(updateAllChannels(action.value))
            is ToyControlAction.Combined -> listOf(updateChannel(action.mode, action.intensity))
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

}

private object SistalkMonsterPartyProtocol : BleDeviceProtocol {
    private val serviceUuid = uuid("00009000-0000-1000-8000-00805f9b34fb")
    private val opUuid = uuid("00009001-0000-1000-8000-00805f9b34fb")
    private val functionUuid = uuid("00009002-0000-1000-8000-00805f9b34fb")

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

private object SistalkMonsterPubMultiMotorProtocol : SistalkMonsterPubProtocolBase(
    id = "sistalk_monsterpub_multi",
    displayName = "SISTALK MonsterPub",
    writeUuid = uuid("0000600a-0000-1000-8000-00805f9b34fb"),
    channelCount = 10,
    intensityMax = 100,
)

private object SistalkMonsterPubDualMotorProtocol : SistalkMonsterPubProtocolBase(
    id = "sistalk_monsterpub_dual",
    displayName = "SISTALK MonsterPub",
    writeUuid = uuid("00006003-0000-1000-8000-00805f9b34fb"),
    channelCount = 2,
    intensityMax = 100,
)

private object SistalkMonsterPubSingleMotorProtocol : SistalkMonsterPubProtocolBase(
    id = "sistalk_monsterpub",
    displayName = "SISTALK MonsterPub",
    writeUuid = uuid("00006001-0000-1000-8000-00805f9b34fb"),
    channelCount = 1,
    intensityMax = 20,
    useLegacyLevelMap = true,
)

private sealed class SistalkMonsterPubProtocolBase(
    id: String,
    displayName: String,
    private val writeUuid: UUID,
    private val channelCount: Int,
    private val intensityMax: Int,
    private val useLegacyLevelMap: Boolean = false,
) : BleDeviceProtocol {
    private val motorServiceUuid = uuid("00006000-0000-1000-8000-00805f9b34fb")
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
        modeNames = (1..channelCount).map { "部位 $it" },
        automatic = true,
    )

    override fun matches(fingerprint: BleGattFingerprint): Boolean {
        return fingerprint.serviceUuids.contains(motorServiceUuid) &&
                fingerprint.characteristicUuids.contains(writeUuid) &&
                fingerprint.sistalkProtocolVersion <= 1
    }

    override fun initialize(fingerprint: BleGattFingerprint): List<BleProtocolOperation> {
        currentPwm.fill(0)
        return emptyList()
    }

    override fun commandsFor(action: ToyControlAction): List<BleProtocolOperation> =
        when (action) {
            is ToyControlAction.Intensity -> listOf(updateAllChannels(action.value))
            is ToyControlAction.Combined -> listOf(updateChannel(action.mode, action.intensity))
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

private object SvakomQhSx045Protocol : BleDeviceProtocol {
    private val serviceUuid = uuid("0000ffe0-0000-1000-8000-00805f9b34fb")
    private val writeUuid = uuid("0000ffe1-0000-1000-8000-00805f9b34fb")

    override val status = BleProtocolStatus(
        id = "svakom_qh_sx045",
        displayName = "SVAKOM QH-SX045",
        controllable = true,
        intensityMax = 10,
        supportsMode = false,
        controlStyle = ToyControlStyle.IntensityOnly,
        automatic = true,
    )

    override fun matches(fingerprint: BleGattFingerprint): Boolean {
        val name = fingerprint.name.normalizedDeviceName()
        val knownName = name.contains("qh-sx") || name.contains("svakom")
        val hasSvakomGatt = fingerprint.serviceUuids.contains(serviceUuid) &&
                fingerprint.characteristicUuids.contains(writeUuid)
        return knownName && hasSvakomGatt
    }

    override fun commandsFor(action: ToyControlAction): List<BleProtocolOperation> =
        when (action) {
            is ToyControlAction.Intensity -> listOf(command(0x01, action.value.coerceIn(0, status.intensityMax)))
            is ToyControlAction.Combined -> listOf(command(0x01, action.intensity.coerceIn(0, status.intensityMax)))
            is ToyControlAction.Pattern -> emptyList()
            ToyControlAction.Stop -> listOf(command(0x00, 0x00))
        }

    private fun command(mode: Int, speed: Int) = BleProtocolOperation.Write(
        characteristicUuid = writeUuid,
        bytes = byteArrayOf(
            0x55,
            0x03,
            0x00,
            0x00,
            mode.toByte(),
            speed.toByte(),
            0x00,
        ),
        withResponse = false,
    )
}

private object MizzzeeXhtkjProtocol : BleDeviceProtocol {
    private val serviceUuid = uuid("0000ff10-0000-1000-8000-00805f9b34fb")
    private val writeUuid = uuid("0000ff12-0000-1000-8000-00805f9b34fb")

    override val status = BleProtocolStatus(
        id = "mizzzee_xhtkj",
        displayName = "Mizzzee XHTKJ",
        controllable = true,
        intensityMax = 100,
        supportsMode = false,
        controlStyle = ToyControlStyle.IntensityOnly,
        automatic = true,
        repeatIntervalMs = 200,
    )

    override fun matches(fingerprint: BleGattFingerprint): Boolean {
        return fingerprint.serviceUuids.contains(serviceUuid) &&
                fingerprint.characteristicUuids.contains(writeUuid)
    }

    override fun commandsFor(action: ToyControlAction): List<BleProtocolOperation> =
        when (action) {
            is ToyControlAction.Intensity -> listOf(command(action.value.coerceIn(0, status.intensityMax)))
            is ToyControlAction.Combined -> listOf(command(action.intensity.coerceIn(0, status.intensityMax)))
            is ToyControlAction.Pattern -> emptyList()
            ToyControlAction.Stop -> listOf(command(0))
        }

    private fun command(intensity: Int): BleProtocolOperation.Write {
        val encoded = encodeStrength(intensity)
        val low = (encoded and 0xff).toByte()
        val high = ((encoded ushr 8) and 0xff).toByte()
        return BleProtocolOperation.Write(
            characteristicUuid = writeUuid,
            bytes = byteArrayOf(
                0x03,
                0x12,
                0xf3.toByte(),
                0x00,
                0xfc.toByte(),
                0x00,
                0xfe.toByte(),
                0x40,
                0x01,
                low,
                high,
                0x00,
                0xfc.toByte(),
                0x00,
                0xfe.toByte(),
                0x40,
                0x01,
                low,
                high,
                0x00,
            ),
            withResponse = false,
        )
    }

    private fun encodeStrength(intensity: Int): Int {
        if (intensity <= 0) return 0x003c
        val scaled = (intensity / 100.0 * 0.7) + 0.3
        return (((scaled * 1023).toInt() shl 6) or 0x3c).coerceIn(0, 0xffff)
    }
}

private object SenseeCcpa10S2Protocol : BleDeviceProtocol {
    private val serviceUuid = uuid("0000fff0-0000-1000-8000-00805f9b34fb")
    private val writeUuid = uuid("0000fff5-0000-1000-8000-00805f9b34fb")
    private val suctionCommands = arrayOf(
        bytes(0x55, 0xaa, 0xf0, 0x01, 0x00, 0x11, 0x66, 0xf2, 0xf1, 0x00, 0x00),
        bytes(0x55, 0xaa, 0xf0, 0x01, 0x00, 0x11, 0x66, 0xf2, 0xf2, 0x00, 0x00),
        bytes(0x55, 0xaa, 0xf0, 0x01, 0x00, 0x11, 0x66, 0xf2, 0xf3, 0x00, 0x00),
    )
    private val vibrationCommands = arrayOf(
        bytes(0x55, 0xaa, 0xf0, 0x01, 0x0d, 0x12, 0x66, 0xf9, 0xf1),
        bytes(0x55, 0xaa, 0xf0, 0x01, 0x0e, 0x12, 0x66, 0xf9, 0xf2),
        bytes(0x55, 0xaa, 0xf0, 0x01, 0x0f, 0x12, 0x66, 0xf9, 0xf3),
        bytes(0x55, 0xaa, 0xf0, 0x01, 0x11, 0x12, 0x66, 0xf9, 0xfa),
    )
    private val suctionOff = bytes(0x55, 0xaa, 0xf0, 0x01, 0x00, 0x11, 0x66, 0xf2, 0xf0, 0x00, 0x00)
    private val vibrationOff = bytes(0x55, 0xaa, 0xf0, 0x01, 0x10, 0x12, 0x66, 0xf9, 0xf0)

    override val status = BleProtocolStatus(
        id = "sensee_ccpa10s2",
        displayName = "Sensee CCPA10S2",
        controllable = true,
        intensityMax = 3,
        supportsMode = true,
        modeMax = 2,
        controlStyle = ToyControlStyle.CombinedPatternAndIntensity,
        modeLabel = "功能",
        modeNames = listOf("吸力", "震动"),
        automatic = true,
    )

    override fun matches(fingerprint: BleGattFingerprint): Boolean {
        val name = fingerprint.name.normalizedDeviceName()
        val knownName = name.contains("ccpa") || name.contains("sensee")
        val hasSenseeGatt = fingerprint.serviceUuids.contains(serviceUuid) &&
                fingerprint.characteristicUuids.contains(writeUuid)
        return knownName && hasSenseeGatt
    }

    override fun commandsFor(action: ToyControlAction): List<BleProtocolOperation> =
        when (action) {
            is ToyControlAction.Combined -> listOf(command(action.mode, action.intensity))
            is ToyControlAction.Intensity -> listOf(command(2, action.value))
            is ToyControlAction.Pattern -> listOf(command(action.mode, status.intensityMax.coerceAtLeast(1)))
            ToyControlAction.Stop -> listOf(write(suctionOff), write(vibrationOff))
        }

    private fun command(mode: Int, intensity: Int): BleProtocolOperation.Write {
        val normalizedMode = mode.coerceIn(1, status.modeMax)
        val level = intensity.coerceIn(0, status.intensityMax)
        val payload = when {
            level == 0 && normalizedMode == 1 -> suctionOff
            level == 0 -> vibrationOff
            normalizedMode == 1 -> suctionCommands[level - 1]
            else -> vibrationCommands[level - 1]
        }
        return write(payload)
    }

    private fun write(payload: ByteArray) = BleProtocolOperation.Write(
        characteristicUuid = writeUuid,
        bytes = payload,
        withResponse = false,
    )
}

private object KissToyProtocol : BleDeviceProtocol {
    private val serviceUuid = uuid("00001000-0000-1000-8000-00805f9b34fb")
    private val writeUuid = uuid("00001001-0000-1000-8000-00805f9b34fb")
    private val notifyUuid = uuid("00001002-0000-1000-8000-00805f9b34fb")
    private val knownNames = setOf(
        "JY11",
        "SYQ1",
        "PJ01",
        "KX01",
        "KX02",
        "PLY1",
        "QCKH",
        "PLMX",
        "QCPJ",
        "QCPW",
        "QCSW",
        "QCVW",
        "QCTT",
        "HMML",
        "Dual-SPP",
    )

    override val status = BleProtocolStatus(
        id = "kisstoy_gatt",
        displayName = "KissToy",
        controllable = true,
        intensityMax = 100,
        supportsMode = true,
        modeMax = 4,
        controlStyle = ToyControlStyle.CombinedPatternAndIntensity,
        modeNames = listOf("主电机", "第二电机", "抽插", "双通道"),
        automatic = true,
    )

    override fun matches(fingerprint: BleGattFingerprint): Boolean {
        val hasKissToyGatt = fingerprint.serviceUuids.contains(serviceUuid) &&
                fingerprint.characteristicUuids.contains(writeUuid)
        if (!hasKissToyGatt) return false
        val knownName = knownNames.any { it.equals(fingerprint.name, ignoreCase = true) }
        val hasNotify = fingerprint.characteristicUuids.contains(notifyUuid)
        return knownName || hasNotify
    }

    override fun commandsFor(action: ToyControlAction): List<BleProtocolOperation> =
        when (action) {
            is ToyControlAction.Combined -> run(action.mode, action.intensity)
            is ToyControlAction.Intensity -> run(1, action.value)
            is ToyControlAction.Pattern -> run(action.mode, 30)
            ToyControlAction.Stop -> stop()
        }

    private fun run(mode: Int, intensity: Int): List<BleProtocolOperation> {
        val normalizedMode = mode.coerceIn(1, status.modeMax)
        val normalizedIntensity = intensity.coerceIn(0, 100)
        val clearCommands = when (normalizedMode) {
            1 -> listOf(command(kissToyPayload(0x32, 0)), command(kissToyPayload(0x71, 0)))
            2 -> listOf(command(kissToyPayload(0x31, 0)), command(kissToyPayload(0x71, 0)))
            3 -> listOf(command(kissToyPayload(0x31, 0)), command(kissToyPayload(0x32, 0)))
            else -> listOf(command(kissToyPayload(0x71, 0)))
        }
        val runCommands = if (normalizedMode == 4) {
            listOf(
                command(kissToyPayload(0x32, normalizedIntensity)),
                command(kissToyPayload(0x31, normalizedIntensity)),
            )
        } else {
            listOf(command(commandForMode(normalizedMode, normalizedIntensity)))
        }
        return clearCommands + runCommands
    }

    private fun stop(): List<BleProtocolOperation> =
        listOf(
            command(kissToyPayload(0x40, 0, 0)),
            command(kissToyPayload(0x31, 0)),
            command(kissToyPayload(0x32, 0)),
            command(kissToyPayload(0x71, 0)),
        )

    private fun command(payload: ByteArray) = BleProtocolOperation.Write(
        characteristicUuid = writeUuid,
        bytes = kissToyEncrypt(payload),
        withResponse = true,
    )

    private fun commandForMode(mode: Int, intensity: Int): ByteArray =
        when (mode) {
            1 -> kissToyPayload(0x31, intensity)
            2 -> kissToyPayload(0x32, intensity)
            3 -> kissToyPayload(0x71, intensity)
            else -> kissToyPayload(0x31, intensity)
        }

    private fun kissToyPayload(command: Int, first: Int, second: Int = 0): ByteArray =
        byteArrayOf(
            0x5A,
            0x00,
            0x00,
            0x01,
            command.toByte(),
            if (command == 0x40) 0x03 else first.toByte(),
            if (command == 0x40) first.toByte() else second.toByte(),
            if (command == 0x40) second.toByte() else 0x00,
            0x00,
            0x00,
        )

    private fun kissToyEncrypt(payload: ByteArray): ByteArray {
        val plain = ByteArray(12)
        plain[0] = 0x23
        payload.copyInto(
            plain,
            destinationOffset = 1,
            startIndex = 0,
            endIndex = payload.size.coerceAtMost(10)
        )
        plain[11] = plain.take(11).sumOf { it.toInt() }.toByte()
        val encrypted = ByteArray(12)
        val seed = plain[0]
        encrypted[0] = seed
        for (index in 1 until encrypted.size) {
            val key = kissToyKey(encrypted[index - 1], index)
            encrypted[index] =
                (((key.toInt() xor seed.toInt()) xor plain[index].toInt()) + key).toByte()
        }
        return encrypted
    }

    private fun kissToyKey(previous: Byte, index: Int): Byte =
        keyTab[previous.toInt() and 0x03][index]

    private val keyTab = arrayOf(
        byteArrayOf(0, 24, -104, -9, -91, 61, 13, 41, 37, 80, 68, 70),
        byteArrayOf(0, 69, 110, 106, 111, 120, 32, 83, 45, 49, 46, 55),
        byteArrayOf(0, 101, 120, 32, 84, 111, 121, 115, 10, -114, -99, -93),
        byteArrayOf(0, -59, -42, -25, -8, 10, 50, 32, 111, 98, 13, 10),
    )
}

internal class ManualBleProtocol(
    private val writeUuid: UUID,
    private val template: ProtocolTemplate,
) : BleDeviceProtocol {
    override val status = BleProtocolStatus(
        id = "manual",
        displayName = "高级手动模板",
        controllable = true,
        intensityMax = 5,
        supportsMode = true,
        controlStyle = ToyControlStyle.CombinedPatternAndIntensity,
        automatic = false,
    )

    override fun matches(fingerprint: BleGattFingerprint): Boolean = false

    override fun commandsFor(action: ToyControlAction): List<BleProtocolOperation> {
        val bytes = when (action) {
            is ToyControlAction.Pattern -> template.commandBytes(action.mode, 1)
            is ToyControlAction.Intensity -> template.commandBytes(1, action.value)
            is ToyControlAction.Combined -> template.commandBytes(action.mode, action.intensity)
            ToyControlAction.Stop -> template.stopBytes()
        }
        return listOf(BleProtocolOperation.Write(writeUuid, bytes, template.writeWithResponse))
    }
}

private object SatisfyerProtocol : BleDeviceProtocol {
    private val controlUuid = uuid("51361501-c5e7-47c7-8a6e-47ebc99d80e8")
    private val speedUuid = uuid("51361502-c5e7-47c7-8a6e-47ebc99d80e8")

    override val status = BleProtocolStatus(
        id = "satisfyer",
        displayName = "Satisfyer",
        controllable = true,
        intensityMax = 100,
        supportsMode = false,
        controlStyle = ToyControlStyle.IntensityOnly,
        automatic = true,
    )

    override fun matches(fingerprint: BleGattFingerprint): Boolean =
        fingerprint.characteristicUuids.contains(controlUuid) &&
                fingerprint.characteristicUuids.contains(speedUuid)

    override fun commandsFor(action: ToyControlAction): List<BleProtocolOperation> =
        when (action) {
            is ToyControlAction.Intensity -> run(action.value)
            is ToyControlAction.Combined -> run(action.intensity)
            is ToyControlAction.Pattern -> emptyList()
            ToyControlAction.Stop -> listOf(speedCommand(0), BleProtocolOperation.Write(controlUuid, byteArrayOf(0x07), withResponse = true))
        }

    private fun run(intensity: Int): List<BleProtocolOperation> =
        listOf(
            BleProtocolOperation.Write(controlUuid, byteArrayOf(0x01), withResponse = true),
            speedCommand(intensity),
        )

    private fun speedCommand(intensity: Int) = BleProtocolOperation.Write(
        characteristicUuid = speedUuid,
        bytes = byteArrayOf(0x00, 0x00, 0x00, intensity.coerceIn(0, 100).toByte()),
        withResponse = true,
    )
}

private object OhMiBodEsca2Protocol : BleDeviceProtocol {
    private val writeUuid = uuid("a0d70002-4c16-4ba7-977a-d394920e13a3")

    override val status = BleProtocolStatus(
        id = "ohmibod_esca2",
        displayName = "OhMiBod Esca 2",
        controllable = true,
        intensityMax = 100,
        supportsMode = false,
        controlStyle = ToyControlStyle.IntensityOnly,
        automatic = true,
    )

    override fun matches(fingerprint: BleGattFingerprint): Boolean =
        fingerprint.characteristicUuids.contains(writeUuid)

    override fun commandsFor(action: ToyControlAction): List<BleProtocolOperation> =
        when (action) {
            is ToyControlAction.Intensity -> listOf(command(action.value))
            is ToyControlAction.Combined -> listOf(command(action.intensity))
            is ToyControlAction.Pattern -> emptyList()
            ToyControlAction.Stop -> listOf(command(0))
        }

    private fun command(intensity: Int) = BleProtocolOperation.Write(
        characteristicUuid = writeUuid,
        bytes = byteArrayOf(0x01, intensity.coerceIn(0, 100).toByte()),
        withResponse = false,
    )
}

private object PinkPunchProtocol : BleDeviceProtocol {
    private val writeUuid = uuid("0000ffe1-0000-1000-8000-00805f9b34fb")
    private val notifyUuid = uuid("0000ffe2-0000-1000-8000-00805f9b34fb")

    override val status = BleProtocolStatus(
        id = "pink_punch",
        displayName = "Pink Punch",
        controllable = true,
        intensityMax = 100,
        supportsMode = false,
        controlStyle = ToyControlStyle.IntensityOnly,
        automatic = true,
    )

    override fun matches(fingerprint: BleGattFingerprint): Boolean =
        fingerprint.name.normalizedDeviceName().contains("pinkpunch") &&
                fingerprint.characteristicUuids.contains(writeUuid) &&
                fingerprint.characteristicUuids.contains(notifyUuid)

    override fun commandsFor(action: ToyControlAction): List<BleProtocolOperation> =
        when (action) {
            is ToyControlAction.Intensity -> listOf(command(0x09, action.value))
            is ToyControlAction.Combined -> listOf(command(0x09, action.intensity))
            is ToyControlAction.Pattern -> emptyList()
            ToyControlAction.Stop -> listOf(command(0x09, 0))
        }

    private fun command(mode: Int, intensity: Int) = BleProtocolOperation.Write(
        characteristicUuid = writeUuid,
        bytes = byteArrayOf(mode.toByte(), intensity.coerceIn(0, 100).toByte()),
        withResponse = false,
    )
}

private object LovenutsProtocol : BleDeviceProtocol {
    private val writeUuid = uuid("0000fff1-0000-1000-8000-00805f9b34fb")

    override val status = BleProtocolStatus(
        id = "lovenuts",
        displayName = "Lovenuts",
        controllable = true,
        intensityMax = 15,
        supportsMode = false,
        controlStyle = ToyControlStyle.IntensityOnly,
        automatic = true,
    )

    override fun matches(fingerprint: BleGattFingerprint): Boolean =
        fingerprint.name.normalizedDeviceName().let { it.contains("lovenuts") || it.contains("love-nuts") } &&
                fingerprint.characteristicUuids.contains(writeUuid)

    override fun commandsFor(action: ToyControlAction): List<BleProtocolOperation> =
        when (action) {
            is ToyControlAction.Intensity -> listOf(patternCommand(action.value.coerceIn(0, status.intensityMax)))
            is ToyControlAction.Combined -> listOf(patternCommand(action.intensity.coerceIn(0, status.intensityMax)))
            is ToyControlAction.Pattern -> emptyList()
            ToyControlAction.Stop -> listOf(patternCommand(speed = 0))
        }

    private fun patternCommand(speed: Int): BleProtocolOperation.Write {
        val packed = ((speed and 0x0f) shl 4) or (speed and 0x0f)
        val bytes = ByteArray(16)
        bytes[0] = 0x45
        bytes[1] = 0x56
        bytes[2] = 0x4f
        bytes[3] = 0x4c
        for (index in 4..13) bytes[index] = packed.toByte()
        bytes[14] = 0x00
        bytes[15] = 0xff.toByte()
        return BleProtocolOperation.Write(writeUuid, bytes, withResponse = false)
    }
}

private object JeJoueNuoProtocol : BleDeviceProtocol {
    private val writeUuid = uuid("0000fff1-0000-1000-8000-00805f9b34fb")

    override val status = BleProtocolStatus(
        id = "je_joue_nuo",
        displayName = "Je Joue Nuo",
        controllable = true,
        intensityMax = 5,
        supportsMode = true,
        modeMax = 3,
        controlStyle = ToyControlStyle.CombinedPatternAndIntensity,
        automatic = true,
    )

    override fun matches(fingerprint: BleGattFingerprint): Boolean {
        val knownName = fingerprint.name.contains("Je Joue", ignoreCase = true) ||
                fingerprint.name.contains("Nuo", ignoreCase = true)
        return knownName && fingerprint.characteristicUuids.contains(writeUuid)
    }

    override fun commandsFor(action: ToyControlAction): List<BleProtocolOperation> =
        when (action) {
            is ToyControlAction.Combined -> listOf(command(action.mode.coerceIn(1, status.modeMax), action.intensity.coerceIn(0, status.intensityMax)))
            is ToyControlAction.Intensity -> listOf(command(pattern = 1, speed = action.value.coerceIn(0, status.intensityMax)))
            is ToyControlAction.Pattern -> listOf(command(pattern = action.mode.coerceIn(1, status.modeMax), speed = status.intensityMax.coerceAtLeast(1)))
            ToyControlAction.Stop -> listOf(command(pattern = 1, speed = 0))
        }

    private fun command(pattern: Int, speed: Int) = BleProtocolOperation.Write(
        characteristicUuid = writeUuid,
        bytes = byteArrayOf(pattern.toByte(), speed.toByte()),
        withResponse = false,
    )
}

/**
 * Lovense BLE 字符串协议。
 * 公开资料显示 Lovense 设备名通常以 LVS- 或 LOVE- 开头，振动控制使用 Vibrate:n;。
 */
private object LovenseProtocol : BleDeviceProtocol {
    private val writeCandidates = listOf(
        uuid("50300012-0023-4bd4-bbd5-a6920e4c5653"),
        uuid("0000fff2-0000-1000-8000-00805f9b34fb"),
        uuid("0000ffe1-0000-1000-8000-00805f9b34fb"),
    )
    private var writeUuid = writeCandidates.first()

    override val status = BleProtocolStatus(
        id = "lovense",
        displayName = "Lovense",
        controllable = true,
        intensityMax = 20,
        supportsMode = false,
        controlStyle = ToyControlStyle.IntensityOnly,
        automatic = true,
    )

    override fun matches(fingerprint: BleGattFingerprint): Boolean {
        val nameMatched = fingerprint.name.startsWith("LVS-", ignoreCase = true) ||
                fingerprint.name.startsWith("LOVE-", ignoreCase = true)
        val writeMatched = writeCandidates.any(fingerprint.characteristicUuids::contains)
        return nameMatched && writeMatched
    }

    override fun initialize(fingerprint: BleGattFingerprint): List<BleProtocolOperation> {
        writeUuid = writeCandidates.first(fingerprint.characteristicUuids::contains)
        return emptyList()
    }

    override fun commandsFor(action: ToyControlAction): List<BleProtocolOperation> =
        when (action) {
            is ToyControlAction.Intensity -> listOf(command("Vibrate:${action.value.coerceIn(0, status.intensityMax)};"))
            is ToyControlAction.Combined -> listOf(command("Vibrate:${action.intensity.coerceIn(0, status.intensityMax)};"))
            is ToyControlAction.Pattern -> emptyList()
            ToyControlAction.Stop -> listOf(command("Vibrate:0;"))
        }

    private fun command(value: String) = BleProtocolOperation.Write(
        characteristicUuid = writeUuid,
        bytes = value.encodeToByteArray(),
        withResponse = false,
    )
}

/**
 * Buttplug ankni 协议的 Android 实现。
 * 上游配置将 DSJM 识别为 Roselex Device，速度范围为 0..3。
 */
private object AnkniProtocol : BleDeviceProtocol {
    private val deviceInfoUuid = uuid("00002a50-0000-1000-8000-00805f9b34fb")
    private val writeCandidates = listOf(
        uuid("0000fe02-0000-1000-8000-00805f9b34fb"),
        uuid("0000fe01-0000-1000-8000-00805f9b34fb"),
    )
    private var writeUuid = writeCandidates.first()

    override val status = BleProtocolStatus(
        id = "ankni",
        displayName = "Roselex / DSJM",
        controllable = true,
        verifiedControl = true,
        intensityMax = 3,
        supportsMode = false,
        controlStyle = ToyControlStyle.IntensityOnly,
        automatic = true,
    )

    override fun matches(fingerprint: BleGattFingerprint): Boolean {
        val hasWrite = writeCandidates.any(fingerprint.characteristicUuids::contains)
        return fingerprint.characteristicUuids.contains(deviceInfoUuid) && hasWrite
    }

    override fun initialize(fingerprint: BleGattFingerprint): List<BleProtocolOperation> {
        writeUuid = writeCandidates.first(fingerprint.characteristicUuids::contains)
        return listOf(BleProtocolOperation.Read(deviceInfoUuid))
    }

    override fun onRead(
        characteristicUuid: UUID,
        bytes: ByteArray,
    ): List<BleProtocolOperation> {
        if (characteristicUuid != deviceInfoUuid || bytes.size > 6) return emptyList()
        require(bytes.isNotEmpty()) { "设备身份信息为空，无法完成握手" }
        val checksum = (crc16(byteArrayOf(0x01) + bytes) ushr 8).toByte()
        return listOf(
            BleProtocolOperation.Write(
                characteristicUuid = writeUuid,
                bytes = ByteArray(20) { 0x01 },
                withResponse = true,
            ),
            BleProtocolOperation.Write(
                characteristicUuid = writeUuid,
                bytes = ByteArray(20).also {
                    it[0] = 0x01
                    it[1] = 0x02
                    for (index in 2..17) it[index] = checksum
                },
                withResponse = true,
            ),
        )
    }

    override fun commandsFor(action: ToyControlAction): List<BleProtocolOperation> =
        when (action) {
            is ToyControlAction.Intensity -> listOf(controlCommand(action.value.coerceIn(1, status.intensityMax)))
            is ToyControlAction.Combined -> listOf(controlCommand(action.intensity.coerceIn(1, status.intensityMax)))
            is ToyControlAction.Pattern -> emptyList()
            ToyControlAction.Stop -> listOf(controlCommand(0))
        }

    private fun controlCommand(speed: Int) = BleProtocolOperation.Write(
        characteristicUuid = writeUuid,
        bytes = ByteArray(20).also {
            it[0] = 0x03
            it[1] = 0x12
            it[2] = speed.toByte()
        },
        withResponse = true,
    )

    private fun crc16(bytes: ByteArray): Int {
        var remain = 0
        bytes.forEach { byte ->
            remain = remain xor ((byte.toInt() and 0xff) shl 8)
            repeat(8) {
                remain = if (remain and 0x8000 != 0) {
                    ((remain shl 1) xor 0x1021) and 0xffff
                } else {
                    (remain shl 1) and 0xffff
                }
            }
        }
        return remain
    }
}

private val ankniDdddProfileProtocols: List<BleDeviceProtocol> = listOf(
    AnkniDdddProfileProtocol(AnkniDdddProfile.tqjD()),
    AnkniDdddProfileProtocol(AnkniDdddProfile.classic("ankni_0001", "ANKNI 0001", 0x0A, 1, 10, listOf("安可尼0001", "ankni0001", "ankeni0001", "intung", "intang"))),
    AnkniDdddProfileProtocol(AnkniDdddProfile.classic("ankni_0002", "ANKNI 0002", 0x0A, 1, 8, listOf("安可尼0002", "ankni0002", "ankeni0002"))),
    AnkniDdddProfileProtocol(AnkniDdddProfile.classic("ankni_0003", "ANKNI 0003", 0x0A, 1, 10, listOf("安可尼0003", "ankni0003", "ankeni0003", "tanghe"))),
    AnkniDdddProfileProtocol(AnkniDdddProfile.classic("ankni_0005", "ANKNI 0005", 0x0A, 1, 10, listOf("安可尼0005", "ankni0005", "ankeni0005", "xiaoshuidi"))),
    AnkniDdddProfileProtocol(AnkniDdddProfile.classic("ankni_0007", "ANKNI 0007", 0x0F, 2, 10, listOf("安可尼0007", "ankni0007", "ankeni0007", "taoxinxiong"))),
    AnkniDdddProfileProtocol(AnkniDdddProfile.classic("ankni_0009", "ANKNI 0009", 0x0F, 2, 10, listOf("安可尼0009", "ankni0009", "ankeni0009", "mitaobaicha"))),
)

private data class AnkniDdddProfile(
    val id: String,
    val displayName: String,
    val aliases: List<String>,
    val modeMax: Int,
    val intensityMax: Int,
    val controlStyle: ToyControlStyle,
    val modeLabel: String,
    val modeNames: List<String>,
    val intensityLabel: String,
    val behavior: Behavior,
    val modeCommand: Int = 0,
    val motorNum: Int = 0,
) {
    enum class Behavior {
        Classic,
        TqjD,
    }

    companion object {
        fun classic(
            id: String,
            displayName: String,
            modeCommand: Int,
            motorNum: Int,
            modeMax: Int,
            aliases: List<String>,
        ) = AnkniDdddProfile(
            id = id,
            displayName = displayName,
            aliases = aliases,
            modeMax = modeMax,
            intensityMax = 100,
            controlStyle = ToyControlStyle.ExclusivePatternOrIntensity,
            modeLabel = "预设节奏",
            modeNames = emptyList(),
            intensityLabel = "滑动强度",
            behavior = Behavior.Classic,
            modeCommand = modeCommand,
            motorNum = motorNum,
        )

        fun tqjD() = AnkniDdddProfile(
            id = "ankni_tqj_d",
            displayName = "ANKNI TQJ-D",
            aliases = listOf("tqjd", "anknitqjd", "ankeni_tqjd"),
            modeMax = 3,
            intensityMax = 10,
            controlStyle = ToyControlStyle.CombinedPatternAndIntensity,
            modeLabel = "功能",
            modeNames = listOf("吮吸", "震动", "电击"),
            intensityLabel = "强度",
            behavior = Behavior.TqjD,
        )
    }
}

private class AnkniDdddProfileProtocol(
    private val profile: AnkniDdddProfile,
) : BleDeviceProtocol {
    private val writeUuid = uuid("0000ddd1-0000-1000-8000-00805f9b34fb")
    private val slideCommand = 0x08

    override val status = BleProtocolStatus(
        id = profile.id,
        displayName = profile.displayName,
        controllable = true,
        intensityMax = profile.intensityMax,
        supportsMode = true,
        modeMax = profile.modeMax,
        controlStyle = profile.controlStyle,
        modeLabel = profile.modeLabel,
        modeNames = profile.modeNames,
        intensityLabel = profile.intensityLabel,
        automatic = true,
    )

    override fun matches(fingerprint: BleGattFingerprint): Boolean {
        if (!fingerprint.hasAnkniDdddGatt()) return false
        val name = fingerprint.name.normalizedDeviceName()
        return profile.aliases.any { alias -> name.contains(alias.normalizedDeviceName()) }
    }

    override fun commandsFor(action: ToyControlAction): List<BleProtocolOperation> =
        when (profile.behavior) {
            AnkniDdddProfile.Behavior.Classic -> classicCommandsFor(action)
            AnkniDdddProfile.Behavior.TqjD -> tqjDCommandsFor(action)
        }

    private fun classicCommandsFor(action: ToyControlAction): List<BleProtocolOperation> {
        return when (action) {
            is ToyControlAction.Pattern ->
                listOf(write(ankniAaFrame(profile.modeCommand, *classicModeValues(action.mode, profile.motorNum))))
            is ToyControlAction.Intensity ->
                listOf(write(ankniAaFrame(slideCommand, *classicSlideValues(action.value, profile.motorNum))))
            is ToyControlAction.Combined ->
                listOf(write(ankniAaFrame(profile.modeCommand, *classicModeValues(action.mode, profile.motorNum))))
            ToyControlAction.Stop ->
                listOf(
                    write(ankniAaFrame(profile.modeCommand, *IntArray(profile.motorNum))),
                    write(ankniAaFrame(slideCommand, *IntArray(profile.motorNum + 1))),
                )
        }
    }

    private fun tqjDCommandsFor(action: ToyControlAction): List<BleProtocolOperation> =
        when (action) {
            is ToyControlAction.Pattern ->
                tqjDControl(action.mode, status.intensityMax)
            is ToyControlAction.Intensity ->
                tqjDControl(1, action.value)
            is ToyControlAction.Combined ->
                tqjDControl(action.mode, action.intensity)
            ToyControlAction.Stop ->
                listOf(write(ankniAaFrame(slideCommand, 0, 0, 0, 0)))
        }

    private fun tqjDControl(mode: Int, intensity: Int): List<BleProtocolOperation> {
        val channelValues = IntArray(status.modeMax)
        val channelIndex = mode.coerceIn(1, status.modeMax) - 1
        channelValues[channelIndex] = (intensity.coerceIn(0, status.intensityMax) * 10).coerceIn(0, 100)
        val duration = channelValues.maxOrNull() ?: 0
        return listOf(write(ankniAaFrame(slideCommand, *channelValues, duration)))
    }

    private fun classicModeValues(mode: Int, motorNum: Int): IntArray {
        val value = mode.coerceIn(1, status.modeMax)
        return IntArray(motorNum) { value }
    }

    private fun classicSlideValues(intensity: Int, motorNum: Int): IntArray {
        val value = intensity.coerceIn(0, status.intensityMax)
        return IntArray(motorNum) { value } + 0xC8
    }

    private fun write(bytes: ByteArray): BleProtocolOperation.Write =
        BleProtocolOperation.Write(
            characteristicUuid = writeUuid,
            bytes = bytes,
            withResponse = true,
        )
}

private object AnkniYwtdProtocol : BleDeviceProtocol {
    private val serviceUuid = uuid("0000dddd-0000-1000-8000-00805f9b34fb")
    private val writeUuid = uuid("0000ddd1-0000-1000-8000-00805f9b34fb")
    private val notifyUuid = uuid("0000ddd2-0000-1000-8000-00805f9b34fb")
    private const val MODE_COMMAND = 0x0F
    private const val SLIDE_COMMAND = 0x08
    private const val SLIDE_DURATION = 0xC8

    override val status = BleProtocolStatus(
        id = "ankni_ywtd",
        displayName = "ANKNI / Mizzzee DDDD",
        controllable = true,
        intensityMax = 100,
        supportsMode = true,
        modeMax = 10,
        controlStyle = ToyControlStyle.ExclusivePatternOrIntensity,
        modeLabel = "预设节奏",
        intensityLabel = "滑动强度",
        automatic = true,
    )

    override fun matches(fingerprint: BleGattFingerprint): Boolean =
        fingerprint.serviceUuids.contains(serviceUuid) &&
                fingerprint.characteristicUuids.contains(writeUuid) &&
                fingerprint.characteristicUuids.contains(notifyUuid)

    override fun commandsFor(action: ToyControlAction): List<BleProtocolOperation> =
        when (action) {
            is ToyControlAction.Pattern ->
                listOf(write(ankniAaFrame(MODE_COMMAND, action.mode.coerceIn(1, status.modeMax))))
            is ToyControlAction.Intensity ->
                listOf(write(ankniAaFrame(SLIDE_COMMAND, action.value.coerceIn(0, status.intensityMax), SLIDE_DURATION)))
            is ToyControlAction.Combined ->
                listOf(write(ankniAaFrame(MODE_COMMAND, action.mode.coerceIn(1, status.modeMax))))
            ToyControlAction.Stop ->
                listOf(write(ankniAaFrame(MODE_COMMAND, 0)))
        }

    private fun write(bytes: ByteArray): BleProtocolOperation.Write =
        BleProtocolOperation.Write(
            characteristicUuid = writeUuid,
            bytes = bytes,
            withResponse = false,
        )
}

private fun uuid(value: String): UUID = UUID.fromString(value)

private fun bytes(vararg values: Int): ByteArray =
    values.map { (it and 0xff).toByte() }.toByteArray()

private fun ankniAaFrame(command: Int, vararg payload: Int): ByteArray {
    val body = intArrayOf(0xAA, command, payload.size) + payload.map { it.coerceIn(0, 0xFF) }
    val checksum = body.sum() and 0xFF
    return (body + checksum).map { it.toByte() }.toByteArray()
}

private fun String.normalizedDeviceName(): String =
    lowercase().replace("_", "").replace("-", "").replace(" ", "")

private fun BleGattFingerprint.hasAnkniDdddGatt(): Boolean =
    serviceUuids.contains(uuid("0000dddd-0000-1000-8000-00805f9b34fb")) &&
            characteristicUuids.contains(uuid("0000ddd1-0000-1000-8000-00805f9b34fb")) &&
            characteristicUuids.contains(uuid("0000ddd2-0000-1000-8000-00805f9b34fb"))

private fun String.firstManufacturerByte(): Int? {
    val payload = substringAfter(':', missingDelimiterValue = this)
    return payload
        .trim()
        .split(Regex("\\s+"))
        .firstOrNull()
        ?.takeIf { it.length in 1..2 && it.all { ch -> ch.isDigit() || ch.lowercaseChar() in 'a'..'f' } }
        ?.toIntOrNull(16)
}

private fun IntArray.toByteArrayForSistalkV1(): ByteArray =
    if (size > 2) {
        byteArrayOf(size.toByte()) + map { it.coerceIn(0, 0xff).toByte() }
    } else {
        map { it.coerceIn(0, 0xff).toByte() }.toByteArray()
    }
