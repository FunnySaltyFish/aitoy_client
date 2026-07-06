package com.funny.aitoy.ble

import com.funny.aitoy.buttplug.ButtplugConfigRepository
import com.funny.aitoy.buttplug.ButtplugDeviceFingerprint
import com.funny.aitoy.buttplug.ButtplugDeviceMatch
import com.funny.aitoy.buttplug.ButtplugLocalHandlerRegistry
import com.funny.aitoy.buttplug.FlufferProtocolPlans
import com.funny.aitoy.buttplug.HandyProtocolPlans
import com.funny.aitoy.buttplug.HoneyPlayBoxFrameCollector
import com.funny.aitoy.buttplug.HoneyPlayBoxProtocolPlans
import com.funny.aitoy.buttplug.LegacyStpihkalProtocolPlans
import com.funny.aitoy.buttplug.LeloProtocolPlans
import com.funny.aitoy.buttplug.LinearProtocolPlan
import com.funny.aitoy.buttplug.LinearProtocolPlans
import com.funny.aitoy.buttplug.LovenseProtocolPlans
import com.funny.aitoy.buttplug.MixedOutputProtocolPlan
import com.funny.aitoy.buttplug.MixedOutputProtocolPlans
import com.funny.aitoy.buttplug.ScalarProtocolPlan
import com.funny.aitoy.buttplug.ScalarProtocolPlans
import com.funny.aitoy.buttplug.ScalarProtocolWrite
import com.funny.aitoy.buttplug.SimpleVibrateProtocolPlan
import com.funny.aitoy.buttplug.SimpleVibrateProtocolPlans
import com.funny.aitoy.buttplug.SpecializedProtocolPlans
import com.funny.aitoy.buttplug.StatefulVibrateProtocolPlan
import com.funny.aitoy.buttplug.StatefulVibrateProtocolPlans
import com.funny.aitoy.buttplug.ToyOutputKind
import com.funny.aitoy.buttplug.VibCrafterProtocolPlans
import com.funny.aitoy.buttplug.WeVibeProtocolPlans
import com.funny.aitoy.buttplug.normalizedUuidText
import com.funny.aitoy.buttplug.toToyOutputKind
import kotlin.random.Random
import kotlin.uuid.Uuid

internal object AnkniProtocol : BleDeviceProtocol {
    private val deviceInfoUuid = Uuid.parse("00002a50-0000-1000-8000-00805f9b34fb")
    private val writeCandidates = listOf(
        Uuid.parse("0000fe02-0000-1000-8000-00805f9b34fb"),
        Uuid.parse("0000fe01-0000-1000-8000-00805f9b34fb"),
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
        characteristicUuid: Uuid,
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
            is ToyControlAction.DualMotor -> listOf(controlCommand(action.strongestIntensity(status.intensityMax).coerceAtLeast(1)))
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

internal object AnkniQd1GattProtocol : BleDeviceProtocol {
    private val writeUuid = Uuid.parse("0000ddd1-0000-1000-8000-00805f9b34fb")

    override val status = BleProtocolStatus(
        id = "ankni_qd1_gatt",
        displayName = "ANKNI QD1",
        controllable = true,
        intensityMax = 100,
        supportsMode = false,
        controlStyle = ToyControlStyle.IntensityOnly,
        intensityLabel = "强度",
        automatic = true,
        repeatIntervalMs = QD1_KEEP_ALIVE_MS,
    )

    override fun matches(fingerprint: BleGattFingerprint): Boolean {
        if (!fingerprint.hasAnkniDdddGatt()) return false
        return fingerprint.hasAnkniQd1Name()
    }

    override fun commandsFor(action: ToyControlAction): List<BleProtocolOperation> =
        when (action) {
            is ToyControlAction.Intensity -> listOf(write(slideFrame(action.value)))
            is ToyControlAction.Combined -> listOf(write(slideFrame(action.intensity)))
            is ToyControlAction.DualMotor -> listOf(write(slideFrame(action.strongestIntensity(status.intensityMax))))
            is ToyControlAction.Pattern -> listOf(write(slideFrame(status.intensityMax)))
            ToyControlAction.Stop -> listOf(write(slideFrame(0)))
        }

    private fun slideFrame(intensity: Int): ByteArray {
        val value = intensity.coerceIn(0, status.intensityMax)
        val duration = if (value > 0) SLIDE_DURATION else 0
        return ankniAaFrame(SLIDE_COMMAND, value, duration)
    }

    private fun write(bytes: ByteArray): BleProtocolOperation.Write =
        BleProtocolOperation.Write(
            characteristicUuid = writeUuid,
            bytes = bytes,
            withResponse = false,
        )

    private const val SLIDE_COMMAND = 0x08
    private const val SLIDE_DURATION = 0xC8
    private const val QD1_KEEP_ALIVE_MS = 200
}

internal object AnkniQd1Ff12GattProtocol : BleDeviceProtocol {
    private val serviceUuid = Uuid.parse("0000ff10-0000-1000-8000-00805f9b34fb")
    private val writeUuid = Uuid.parse("0000ff12-0000-1000-8000-00805f9b34fb")

    override val status = BleProtocolStatus(
        id = "ankni_qd1_ff12_gatt",
        displayName = "ANKNI QD1",
        controllable = true,
        intensityMax = 100,
        supportsMode = false,
        controlStyle = ToyControlStyle.IntensityOnly,
        intensityLabel = "强度",
        automatic = true,
        repeatIntervalMs = QD1_KEEP_ALIVE_MS,
    )

    override fun matches(fingerprint: BleGattFingerprint): Boolean {
        return fingerprint.hasAnkniQd1Name() &&
            fingerprint.serviceUuids.contains(serviceUuid) &&
            fingerprint.characteristicUuids.contains(writeUuid)
    }

    override fun commandsFor(action: ToyControlAction): List<BleProtocolOperation> =
        when (action) {
            is ToyControlAction.Intensity -> listOf(write(slideFrame(action.value)))
            is ToyControlAction.Combined -> listOf(write(slideFrame(action.intensity)))
            is ToyControlAction.DualMotor -> listOf(write(slideFrame(action.strongestIntensity(status.intensityMax))))
            is ToyControlAction.Pattern -> listOf(write(slideFrame(status.intensityMax)))
            ToyControlAction.Stop -> listOf(write(slideFrame(0)))
        }

    private fun slideFrame(intensity: Int): ByteArray {
        val value = intensity.coerceIn(0, status.intensityMax)
        val duration = if (value > 0) SLIDE_DURATION else 0
        return ankniAaFrame(SLIDE_COMMAND, value, duration)
    }

    private fun write(bytes: ByteArray): BleProtocolOperation.Write =
        BleProtocolOperation.Write(
            characteristicUuid = writeUuid,
            bytes = bytes,
            withResponse = true,
        )

    private const val SLIDE_COMMAND = 0x08
    private const val SLIDE_DURATION = 0xC8
    private const val QD1_KEEP_ALIVE_MS = 200
}

internal object Ankni0010Protocol : BleDeviceProtocol {
    private val writeUuid = Uuid.parse("0000ffe1-0000-1000-8000-00805f9b34fb")

    override val status = BleProtocolStatus(
        id = "ankni_0010",
        displayName = "ANKNI 0010",
        controllable = true,
        intensityMax = 100,
        supportsMode = false,
        controlStyle = ToyControlStyle.IntensityOnly,
        intensityLabel = "强度",
        automatic = true,
    )

    override fun matches(fingerprint: BleGattFingerprint): Boolean {
        if (!fingerprint.characteristicUuids.contains(writeUuid)) return false
        val name = fingerprint.name.normalizedDeviceName()
        return name.contains("安可尼0010") ||
            name.contains("ankni0010") ||
            name.contains("ankeni0010")
    }

    override fun commandsFor(action: ToyControlAction): List<BleProtocolOperation> =
        when (action) {
            is ToyControlAction.Intensity -> listOf(write(slideFrame(action.value)))
            is ToyControlAction.Combined -> listOf(write(slideFrame(action.intensity)))
            is ToyControlAction.DualMotor -> listOf(write(slideFrame(action.strongestIntensity(status.intensityMax))))
            is ToyControlAction.Pattern -> listOf(write(slideFrame(status.intensityMax)))
            ToyControlAction.Stop -> listOf(write(slideFrame(0)))
        }

    private fun slideFrame(intensity: Int): ByteArray {
        val value = intensity.coerceIn(0, status.intensityMax)
        val duration = if (value > 0) SLIDE_DURATION else 0
        return ankniAaFrame(SLIDE_COMMAND, value, duration)
    }

    private fun write(bytes: ByteArray): BleProtocolOperation.Write =
        BleProtocolOperation.Write(
            characteristicUuid = writeUuid,
            bytes = bytes,
            withResponse = false,
        )

    private const val SLIDE_COMMAND = 0x08
    private const val SLIDE_DURATION = 0xC8
}

internal val ankniDdddProfileProtocols: List<BleDeviceProtocol> = listOf(
    AnkniDdddProfileProtocol(AnkniDdddProfile.tqjD()),
    AnkniDdddProfileProtocol(AnkniDdddProfile.classic("ankni_0001", "ANKNI 0001", 0x0A, 1, 10, listOf("安可尼0001", "ankni0001", "ankeni0001", "intung", "intang"))),
    AnkniDdddProfileProtocol(AnkniDdddProfile.classic("ankni_0002", "ANKNI 0002", 0x0A, 1, 8, listOf("安可尼0002", "ankni0002", "ankeni0002"))),
    AnkniDdddProfileProtocol(AnkniDdddProfile.classic("ankni_0003", "ANKNI 0003", 0x0A, 1, 10, listOf("安可尼0003", "ankni0003", "ankeni0003", "tanghe"))),
    AnkniDdddProfileProtocol(AnkniDdddProfile.classic("ankni_0005", "ANKNI 0005", 0x0A, 1, 10, listOf("安可尼0005", "ankni0005", "ankeni0005", "xiaoshuidi"))),
    AnkniDdddProfileProtocol(AnkniDdddProfile.classic("ankni_0007", "ANKNI 0007", 0x0F, 2, 10, listOf("安可尼0007", "ankni0007", "ankeni0007", "taoxinxiong"))),
    AnkniDdddProfileProtocol(AnkniDdddProfile.classic("ankni_0009", "ANKNI 0009", 0x0F, 2, 10, listOf("安可尼0009", "ankni0009", "ankeni0009", "mitaobaicha"))),
)

internal data class AnkniDdddProfile(
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

internal class AnkniDdddProfileProtocol(
    private val profile: AnkniDdddProfile,
) : BleDeviceProtocol {
    private val writeUuid = Uuid.parse("0000ddd1-0000-1000-8000-00805f9b34fb")
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
        // 小程序对 TQJ-D 滑动控制每 200ms 重发一次；不保活时电机会在帧尾时长耗尽后自动停止。
        repeatIntervalMs = if (profile.behavior == AnkniDdddProfile.Behavior.TqjD) TQJD_KEEP_ALIVE_MS else 0,
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
            is ToyControlAction.DualMotor ->
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
            is ToyControlAction.DualMotor ->
                tqjDControl(action.mode, action.strongestIntensity(status.intensityMax))
            ToyControlAction.Stop ->
                listOf(write(ankniAaFrame(slideCommand, 0, 0, 0, 0)))
        }

    private fun tqjDControl(mode: Int, intensity: Int): List<BleProtocolOperation> {
        val channelValues = IntArray(status.modeMax)
        val channelIndex = mode.coerceIn(1, status.modeMax) - 1
        channelValues[channelIndex] = (intensity.coerceIn(0, status.intensityMax) * 10).coerceIn(0, 100)
        // 小程序 makeSlideOrder(200, ...) 把固定的 200(0xC8) 作为帧尾时长位，与强度无关。
        // 之前误把强度最大值写进时长位，导致低强度时长太短：电机只动一两秒就停、强度小于 4 直接不触发。
        return listOf(write(ankniAaFrame(slideCommand, *channelValues, TQJD_SLIDE_DURATION)))
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

    companion object {
        // 帧尾固定时长位，来源：醉清风小程序 makeSlideOrder(200, ...)
        private const val TQJD_SLIDE_DURATION = 0xC8
        // 与小程序滑动控制 setInterval(200) 保活节奏一致
        private const val TQJD_KEEP_ALIVE_MS = 200
    }
}

internal object AnkniYwtdProtocol : BleDeviceProtocol {
    private val serviceUuid = Uuid.parse("0000dddd-0000-1000-8000-00805f9b34fb")
    private val writeUuid = Uuid.parse("0000ddd1-0000-1000-8000-00805f9b34fb")
    private val notifyUuid = Uuid.parse("0000ddd2-0000-1000-8000-00805f9b34fb")
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
        repeatIntervalMs = YWTD_KEEP_ALIVE_MS,
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
            is ToyControlAction.DualMotor ->
                listOf(write(ankniAaFrame(MODE_COMMAND, action.mode.coerceIn(1, status.modeMax))))
            ToyControlAction.Stop ->
                listOf(write(ankniAaFrame(MODE_COMMAND, 0)))
        }

    private const val YWTD_KEEP_ALIVE_MS = 200

    private fun write(bytes: ByteArray): BleProtocolOperation.Write =
        BleProtocolOperation.Write(
            characteristicUuid = writeUuid,
            bytes = bytes,
            withResponse = false,
        )
}

