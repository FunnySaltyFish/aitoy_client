package com.funny.aitoy.ble

import kotlin.random.Random

internal data class BleAdvertiseOperation(
    val serviceUuid: String = "",
    val manufacturerId: Int? = null,
    val manufacturerData: ByteArray = byteArrayOf(),
)

internal interface BleBroadcastProtocol {
    val status: BleProtocolStatus

    fun matches(device: ScannedBleDevice): Boolean

    fun commandsFor(action: ToyControlAction): List<BleAdvertiseOperation>
}

internal object BleBroadcastProtocolRegistry {
    private val protocols = listOf(
        CachitoShikong2BroadcastProtocol,
        CachitoDaxiuBroadcastProtocol,
    )

    fun resolveAll(device: ScannedBleDevice): List<BleBroadcastProtocol> =
        protocols.filter { it.matches(device) }

    fun resolveFirstName(device: ScannedBleDevice): String =
        resolveAll(device).firstOrNull()?.status?.displayName.orEmpty()
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
    private var deviceId = newDeviceId()

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

    private fun newDeviceId(): String =
        buildString {
            repeat(4) { append("0123456789ABCDEF"[Random.nextInt(16)]) }
        }

    fun toHex(value: Int): String =
        (value and 0xff).toString(16).uppercase().padStart(2, '0')
}
