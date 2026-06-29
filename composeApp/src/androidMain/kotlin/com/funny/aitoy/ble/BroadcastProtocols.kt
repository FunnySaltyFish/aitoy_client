package com.funny.aitoy.ble

import com.funny.aitoy.core.prefs.DataSaverUtils
import kotlin.random.Random

internal data class BleAdvertiseOperation(
    val serviceUuid: String = "",
    val serviceDataUuid: String = "",
    val serviceData: ByteArray = byteArrayOf(),
    val manufacturerId: Int? = null,
    val manufacturerData: ByteArray = byteArrayOf(),
)

internal interface BleBroadcastProtocol {
    val status: BleProtocolStatus
    val preferGattWhenConnectable: Boolean get() = false

    fun matches(device: ScannedBleDevice): Boolean

    fun commandsFor(action: ToyControlAction): List<BleAdvertiseOperation>
}

internal object BleBroadcastProtocolRegistry {
    private val protocols = listOf(
        AnkniQd1BroadcastProtocol,
        CachitoShikong2BroadcastProtocol,
        CachitoDaxiuBroadcastProtocol,
    )

    fun resolveAll(device: ScannedBleDevice): List<BleBroadcastProtocol> =
        protocols.filter { it.matches(device) }

    fun resolveFirstName(device: ScannedBleDevice): String =
        resolveAll(device).firstOrNull()?.status?.displayName.orEmpty()
}

/**
 * ANKNI QD1 的不可连接分支通过 FEFF 广播 UUID 控制；可连接设备优先走 GATT。
 * 控制帧复用醉清风小程序的 ANKNI 滑动强度封包：makeHexStr("08", makeSlideOrder(200, value, ...))。
 */
internal object AnkniQd1BroadcastProtocol : BleBroadcastProtocol {
    private const val SERVICE_DATA_UUID = "0000feff-0000-1000-8000-00805f9b34fb"

    override val preferGattWhenConnectable = true

    override val status = BleProtocolStatus(
        id = "ankni_qd1_advertise",
        displayName = "ANKNI QD1",
        controllable = true,
        intensityMax = 100,
        supportsMode = false,
        controlStyle = ToyControlStyle.IntensityOnly,
        intensityLabel = "强度",
        automatic = true,
    )

    override fun matches(device: ScannedBleDevice): Boolean {
        val name = device.name.normalizedBroadcastDeviceName()
        val hasQd1Name = name.contains("ankniqd1") || name.contains("ankniqd01")
        val hasQd1Broadcast = device.serviceUuids.any { it.equals(SERVICE_DATA_UUID, ignoreCase = true) } ||
            device.scanRecordHex.contains("03 02 FF FE", ignoreCase = true)
        return hasQd1Name && hasQd1Broadcast
    }

    override fun commandsFor(action: ToyControlAction): List<BleAdvertiseOperation> =
        when (action) {
            is ToyControlAction.Intensity -> listOf(command(action.value))
            is ToyControlAction.Combined -> listOf(command(action.intensity))
            is ToyControlAction.DualMotor -> listOf(command(action.strongestIntensity(status.intensityMax)))
            is ToyControlAction.Pattern -> listOf(command(status.intensityMax))
            ToyControlAction.Stop -> listOf(command(0))
        }

    private fun command(intensity: Int): BleAdvertiseOperation =
        BleAdvertiseOperation(
            serviceDataUuid = SERVICE_DATA_UUID,
            serviceData = slideFrame(intensity.coerceIn(0, status.intensityMax)),
        )

    private fun slideFrame(intensity: Int): ByteArray {
        val duration = if (intensity > 0) SLIDE_DURATION else 0
        val values = intArrayOf(intensity, duration)
        return ankniFrame(0x08, values)
    }

    private fun ankniFrame(command: Int, values: IntArray): ByteArray {
        val bytes = ByteArray(values.size + 4)
        bytes[0] = 0xaa.toByte()
        bytes[1] = command.toByte()
        bytes[2] = values.size.toByte()
        values.forEachIndexed { index, value ->
            bytes[index + 3] = value.coerceIn(0, 255).toByte()
        }
        bytes[bytes.lastIndex] = (bytes.dropLast(1).sumOf { it.toInt() and 0xff } and 0xff).toByte()
        return bytes
    }

    private const val SLIDE_DURATION = 0xC8
}

/**
 * Cachito 反编译来源：
 * - com/cs/cachito/utils/ble/BleAdvertiser.smali
 * - com/cs/cachito/ext/CommandExtKt.smali
 * - com/cs/cachito/base/DeviceType.smali
 *
 * 控制方式是把 36 字符 UUID 放进 BLE legacy advertisement 的 Service UUID，
 * 不是连接 GATT 后写 characteristic。
 */
internal object CachitoShikong2BroadcastProtocol : BleBroadcastProtocol {
    private val modeTemplates = listOf(
        CachitoShikong2Mode("吸吮", CachitoShikong2Function.Suction),
        CachitoShikong2Mode("震动", CachitoShikong2Function.Vibration),
    )

    private var selectedMode = 1

    override val status = BleProtocolStatus(
        id = "cachito_shikong2_advertise",
        displayName = "Cachito 失控 2.0",
        controllable = true,
        intensityMax = 100,
        supportsMode = true,
        modeMax = modeTemplates.size,
        controlStyle = ToyControlStyle.CombinedPatternAndIntensity,
        modeLabel = "部位",
        modeNames = modeTemplates.map { it.name },
        intensityLabel = "强度",
        automatic = true,
    )

    override fun matches(device: ScannedBleDevice): Boolean {
        val text = buildString {
            append(device.name)
            append(' ')
            append(device.serviceUuids.joinToString())
            append(' ')
            append(device.manufacturerData)
            append(' ')
            append(device.scanRecordHex)
        }
        val knownHint = text.contains("失控", ignoreCase = true) ||
            text.contains("shikong", ignoreCase = true) ||
            text.contains("710002", ignoreCase = true)
        val shikong2TraceHint = !device.connectable &&
            device.serviceUuids.isEmpty() &&
            device.manufacturerData.contains("0x6:01 09 20 22", ignoreCase = true)
        return knownHint || shikong2TraceHint
    }

    override fun commandsFor(action: ToyControlAction): List<BleAdvertiseOperation> =
        when (action) {
            is ToyControlAction.Pattern -> control(action.mode, 100)
            is ToyControlAction.Intensity -> control(selectedMode, action.value)
            is ToyControlAction.Combined -> control(action.mode, action.intensity)
            is ToyControlAction.DualMotor -> control(action.mode, action.strongestIntensity(100))
            ToyControlAction.Stop -> stop()
        }

    private fun control(mode: Int, intensity: Int): List<BleAdvertiseOperation> {
        val selected = modeTemplates[(mode - 1).coerceIn(0, modeTemplates.lastIndex)]
        selectedMode = mode.coerceIn(1, modeTemplates.size)
        val progress = intensity.coerceIn(0, 100)
        return when (selected.function) {
            CachitoShikong2Function.Suction ->
                listOf(BleAdvertiseOperation(CachitoBroadcastCodec.finalUuid(suctionTemplate(progress))))
            CachitoShikong2Function.Vibration ->
                listOf(BleAdvertiseOperation(CachitoBroadcastCodec.finalUuid(vibrationTemplate(progress))))
        }
    }

    private fun stop(): List<BleAdvertiseOperation> =
        listOf(
            BleAdvertiseOperation(CachitoBroadcastCodec.finalUuid(suctionStopTemplate())),
            BleAdvertiseOperation(CachitoBroadcastCodec.finalUuid(vibrationStopTemplate())),
        )

    private fun suctionTemplate(progress: Int): String =
        "710002**-0400-####-0302-" + shikong2ScaledProgress(progress) + "00000000"

    private fun vibrationTemplate(progress: Int): String =
        "710002**-0400-####-050A-" + shikong2ScaledProgress(progress) + "00000000"

    private fun suctionStopTemplate(): String =
        "710002**-0400-####-0302-0000000000"

    private fun vibrationStopTemplate(): String =
        "710002**-0400-####-0601-0000000000"

    private fun shikong2ScaledProgress(progress: Int): String {
        val value = (progress.coerceIn(0, 100) * 0.75 + 25).toInt()
        return CachitoBroadcastCodec.toHex(value)
    }

    private data class CachitoShikong2Mode(
        val name: String,
        val function: CachitoShikong2Function,
    )

    private enum class CachitoShikong2Function {
        Suction,
        Vibration,
    }
}

internal object CachitoDaxiuBroadcastProtocol : BleBroadcastProtocol {
    private val modeTemplates = listOf(
        CachitoDaxiuMode("推拉深度", CachitoDaxiuFunction.Depth),
        CachitoDaxiuMode("伸出速度", CachitoDaxiuFunction.ExtendSpeed),
        CachitoDaxiuMode("缩回速度", CachitoDaxiuFunction.RetractSpeed),
        CachitoDaxiuMode("入体端强度", CachitoDaxiuFunction.EnterStrength),
        CachitoDaxiuMode("加热温度", CachitoDaxiuFunction.Temperature),
    )

    private var selectedMode = 1
    private var depth = 36
    private var extendSpeed = 8
    private var retractSpeed = 8

    override val status = BleProtocolStatus(
        id = "cachito_daxiu_advertise",
        displayName = "Cachito 大秀",
        controllable = true,
        intensityMax = 100,
        supportsMode = true,
        modeMax = modeTemplates.size,
        controlStyle = ToyControlStyle.CombinedPatternAndIntensity,
        modeLabel = "功能",
        modeNames = modeTemplates.map { it.name },
        intensityLabel = "数值",
        automatic = true,
    )

    override fun matches(device: ScannedBleDevice): Boolean {
        val text = buildString {
            append(device.name)
            append(' ')
            append(device.serviceUuids.joinToString())
            append(' ')
            append(device.manufacturerData)
            append(' ')
            append(device.scanRecordHex)
        }
        return text.contains("大秀", ignoreCase = true) ||
            text.contains("daxiu", ignoreCase = true) ||
            text.contains("710003", ignoreCase = true)
    }

    override fun commandsFor(action: ToyControlAction): List<BleAdvertiseOperation> =
        when (action) {
            is ToyControlAction.Pattern -> control(action.mode, 100)
            is ToyControlAction.Intensity -> control(selectedMode, action.value)
            is ToyControlAction.Combined -> control(action.mode, action.intensity)
            is ToyControlAction.DualMotor -> control(action.mode, action.strongestIntensity(100))
            ToyControlAction.Stop -> stop()
        }

    private fun control(mode: Int, intensity: Int): List<BleAdvertiseOperation> {
        val selected = modeTemplates[(mode - 1).coerceIn(0, modeTemplates.lastIndex)]
        selectedMode = mode.coerceIn(1, modeTemplates.size)
        val progress = intensity.coerceIn(0, 100)
        val template = when (selected.function) {
            CachitoDaxiuFunction.Depth -> {
                depth = progress.coerceIn(0, 72)
                thrustTemplate()
            }
            CachitoDaxiuFunction.ExtendSpeed -> {
                extendSpeed = progress.coerceIn(0, 15)
                thrustTemplate()
            }
            CachitoDaxiuFunction.RetractSpeed -> {
                retractSpeed = progress.coerceIn(0, 15)
                thrustTemplate()
            }
            CachitoDaxiuFunction.EnterStrength -> strengthTemplate(progress)
            CachitoDaxiuFunction.Temperature -> temperatureTemplate(progress)
        }
        return listOf(BleAdvertiseOperation(CachitoBroadcastCodec.finalUuid(template)))
    }

    private fun stop(): List<BleAdvertiseOperation> =
        listOf(
            BleAdvertiseOperation(CachitoBroadcastCodec.finalUuid(thrustStopTemplate())),
            BleAdvertiseOperation(CachitoBroadcastCodec.finalUuid(strengthTemplate(0))),
            BleAdvertiseOperation(CachitoBroadcastCodec.finalUuid(temperatureStopTemplate())),
        )

    private fun thrustTemplate(): String =
        "710003**-8800-####-0000-" +
            CachitoBroadcastCodec.toHex(depth.coerceIn(0, 72)) +
            CachitoBroadcastCodec.toHex(extendSpeed.coerceIn(0, 15)) +
            CachitoBroadcastCodec.toHex(retractSpeed.coerceIn(0, 15)) +
            "0000"

    private fun thrustStopTemplate(): String =
        "710003**-8800-####-0000-0000000000"

    private fun strengthTemplate(strength: Int): String =
        "710003**-0400-####-050A-" + CachitoBroadcastCodec.toHex(strength.coerceIn(0, 100)) + "00000000"

    private fun temperatureTemplate(progress: Int): String =
        "710003**-1F00-####-0000-" + CachitoBroadcastCodec.toHex(progress.coerceIn(0, 60)) + "00000000"

    private fun temperatureStopTemplate(): String =
        "710003**-1F00-####-0000-0000000000"

    private data class CachitoDaxiuMode(
        val name: String,
        val function: CachitoDaxiuFunction,
    )

    private enum class CachitoDaxiuFunction {
        Depth,
        ExtendSpeed,
        RetractSpeed,
        EnterStrength,
        Temperature,
    }
}

private object CachitoBroadcastCodec {
    private const val CONTROL_DEVICE_ID_KEY = "CACHITO_CONTROL_DEVICE_ID"
    private var deviceId = loadDeviceId()

    fun finalUuid(template: String): String {
        val command = template
            .replace("####", deviceId)
            .replace("**", newCommandId())
        val checksumSource = command.replace("-", "")
        return command + checksum(checksumSource)
    }

    private fun checksum(hex: String): String {
        val sum = hex.chunked(2).sumOf { it.toIntOrNull(16) ?: 0 }
        return toHex(sum % 256)
    }

    private fun newCommandId(): String = toHex(Random.nextInt(100, 256))

    private fun loadDeviceId(): String {
        val saved = DataSaverUtils.readData(CONTROL_DEVICE_ID_KEY, "")
            .uppercase()
            .filter { it in '0'..'9' || it in 'A'..'F' }
        if (saved.length == 4) return saved
        return newDeviceId().also { DataSaverUtils.saveData(CONTROL_DEVICE_ID_KEY, it) }
    }

    private fun newDeviceId(): String =
        buildString {
            repeat(4) { append("0123456789ABCDEF"[Random.nextInt(16)]) }
        }

    fun toHex(value: Int): String =
        (value and 0xff).toString(16).uppercase().padStart(2, '0')
}

private fun String.normalizedBroadcastDeviceName(): String =
    lowercase().filter { it.isLetterOrDigit() }
