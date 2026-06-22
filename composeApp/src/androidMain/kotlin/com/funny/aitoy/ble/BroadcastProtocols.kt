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
    private val protocols = listOf(CachitoBroadcastProtocol)

    fun resolveAll(device: ScannedBleDevice): List<BleBroadcastProtocol> =
        protocols.filter { it.matches(device) }

    fun resolveFirstName(device: ScannedBleDevice): String =
        resolveAll(device).firstOrNull()?.status?.displayName.orEmpty()
}

/**
 * Cachito 反编译来源：
 * - com/cs/cachito/utils/ble/BleAdvertiser.smali
 * - com/cs/cachito/ext/CommandExtKt.smali
 *
 * 控制方式是把 36 字符 UUID 放进 BLE legacy advertisement 的 Service UUID，
 * 不是连接 GATT 后写 characteristic。
 */
internal object CachitoBroadcastProtocol : BleBroadcastProtocol {
    private val modeTemplates = listOf(
        CachitoMode("推拉深度", CachitoFunction.ThrustDepth),
        CachitoMode("伸出速度", CachitoFunction.ExtendSpeed),
        CachitoMode("缩回速度", CachitoFunction.RetractSpeed),
        CachitoMode("入体端强度", CachitoFunction.Strength),
        CachitoMode("加热温度", CachitoFunction.Temperature),
    )

    private var deviceId = newDeviceId()
    private var selectedMode = 1
    private var depth = 36
    private var extendSpeed = 8
    private var retractSpeed = 8

    override val status = BleProtocolStatus(
        id = "cachito_advertise",
        displayName = "Cachito 广播",
        controllable = true,
        intensityMax = 100,
        supportsMode = true,
        modeMax = modeTemplates.size,
        controlStyle = ToyControlStyle.CombinedPatternAndIntensity,
        modeNames = modeTemplates.map { it.name },
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
        val knownHint = text.contains("cachito", ignoreCase = true) ||
            text.contains("7100", ignoreCase = true)
        return knownHint
    }

    override fun commandsFor(action: ToyControlAction): List<BleAdvertiseOperation> =
        when (action) {
            is ToyControlAction.Pattern -> control(action.mode, 100)
            is ToyControlAction.Intensity -> control(selectedMode, action.value)
            is ToyControlAction.Combined -> control(action.mode, action.intensity)
            ToyControlAction.Stop -> stop()
        }

    private fun control(mode: Int, intensity: Int): List<BleAdvertiseOperation> {
        val selected = modeTemplates[(mode - 1).coerceIn(0, modeTemplates.lastIndex)]
        selectedMode = mode.coerceIn(1, modeTemplates.size)
        val progress = intensity.coerceIn(0, 100)
        return when (selected.function) {
            CachitoFunction.ThrustDepth -> {
                depth = scale(progress, 72)
                listOf(BleAdvertiseOperation(finalUuid(thrustTemplate(depth, extendSpeed, retractSpeed))))
            }
            CachitoFunction.ExtendSpeed -> {
                extendSpeed = scale(progress, 15)
                listOf(BleAdvertiseOperation(finalUuid(thrustTemplate(depth, extendSpeed, retractSpeed))))
            }
            CachitoFunction.RetractSpeed -> {
                retractSpeed = scale(progress, 15)
                listOf(BleAdvertiseOperation(finalUuid(thrustTemplate(depth, extendSpeed, retractSpeed))))
            }
            CachitoFunction.Strength ->
                listOf(BleAdvertiseOperation(finalUuid(strengthTemplate(progress))))
            CachitoFunction.Temperature ->
                listOf(BleAdvertiseOperation(finalUuid(temperatureTemplate(scale(progress, 60)))))
        }
    }

    private fun stop(): List<BleAdvertiseOperation> =
        listOf(
            BleAdvertiseOperation(finalUuid(thrustTemplate(0, 0, 0))),
            BleAdvertiseOperation(finalUuid(strengthTemplate(0))),
            BleAdvertiseOperation(finalUuid(temperatureTemplate(0))),
        )

    private fun finalUuid(template: String): String {
        val command = template
            .replace("####", deviceId)
            .replace("**", newCommandId())
        val checksumSource = command.replace("-", "")
        return command + checksum(checksumSource)
    }

    private fun thrustTemplate(depth: Int, extendSpeed: Int, retractSpeed: Int): String =
        "710003**-8800-####-0000-" +
            toHex(depth.coerceIn(0, 72)) +
            toHex(extendSpeed.coerceIn(0, 15)) +
            toHex(retractSpeed.coerceIn(0, 15)) +
            "0000"

    private fun strengthTemplate(strength: Int): String =
        "710003**-0400-####-050A-" + toHex(strength.coerceIn(0, 100)) + "00000000"

    private fun temperatureTemplate(temp: Int): String =
        "710003**-1F00-####-0000-" + toHex(temp.coerceIn(0, 60)) + "00000000"

    private fun checksum(hex: String): String {
        val sum = hex.chunked(2).sumOf { it.toIntOrNull(16) ?: 0 }
        return toHex(sum % 256)
    }

    private fun newCommandId(): String = toHex(Random.nextInt(100, 256))

    private fun newDeviceId(): String =
        buildString {
            repeat(4) { append("0123456789ABCDEF"[Random.nextInt(16)]) }
        }

    private fun toHex(value: Int): String =
        (value and 0xff).toString(16).uppercase().padStart(2, '0')

    private fun scale(progress: Int, max: Int): Int =
        (progress.coerceIn(0, 100) * max / 100f).toInt().coerceIn(0, max)

    private data class CachitoMode(
        val name: String,
        val function: CachitoFunction,
    )

    private enum class CachitoFunction {
        ThrustDepth,
        ExtendSpeed,
        RetractSpeed,
        Strength,
        Temperature,
    }
}
