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

internal fun bytes(vararg values: Int): ByteArray =
    values.map { (it and 0xff).toByte() }.toByteArray()

internal fun BleGattFingerprint.toButtplugFingerprint(): ButtplugDeviceFingerprint =
    ButtplugDeviceFingerprint(
        name = name,
        serviceUuids = serviceUuids.map { it.toString().normalizedUuidText() }.toSet(),
        characteristicUuids = characteristicUuids.map { it.toString().normalizedUuidText() }.toSet(),
        manufacturerData = manufacturerData.toManufacturerDataMap(),
    )

internal fun ButtplugDeviceMatch.toBleStatus(
    id: String,
    controllable: Boolean,
    supportsMode: Boolean,
    controlStyle: ToyControlStyle,
    modeMax: Int = 1,
    modeNames: List<String> = emptyList(),
): BleProtocolStatus {
    val vibrateFeatures = vibrateOutputs.sortedBy { it.featureIndex }
    val maxIntensity = vibrateFeatures.maxOfOrNull { it.max }
        ?: device.features.maxOfOrNull { it.max }
        ?: 0
    val channelNames = when {
        vibrateFeatures.size >= 2 -> listOf("内侧", "外侧")
        else -> emptyList()
    }
    return BleProtocolStatus(
        id = id,
        displayName = device.name.ifBlank { protocol.displayName },
        controllable = controllable,
        intensityMax = maxIntensity,
        supportsMode = supportsMode,
        modeMax = modeMax,
        controlStyle = controlStyle,
        modeLabel = "预设节奏",
        modeNames = modeNames,
        intensityLabel = "强度",
        channelNames = channelNames,
        features = device.features.map {
            BleProtocolFeature(
                type = it.type,
                min = it.min,
                max = it.max,
                index = it.featureIndex,
                label = it.description,
            )
        },
        automatic = true,
    )
}

internal fun ankniAaFrame(command: Int, vararg payload: Int): ByteArray {
    val body = intArrayOf(0xAA, command, payload.size) + payload.map { it.coerceIn(0, 0xFF) }
    val checksum = body.sum() and 0xFF
    return (body + checksum).map { it.toByte() }.toByteArray()
}

internal fun String.normalizedDeviceName(): String =
    lowercase().replace("_", "").replace("-", "").replace(" ", "")

internal fun BleGattFingerprint.hasAnkniQd1Name(): Boolean {
    val name = name.normalizedDeviceName()
    return name.contains("ankniqd1") || name.contains("ankniqd01") || name.contains("ankeniqd1")
}

internal fun BleGattFingerprint.hasSvakomManufacturerData(): Boolean =
    svakomManufacturerPayload() != null

/**
 * 司康沃设备每次开机会更换 MAC，但设备名 + 产品码在同一实体上稳定。
 * 记忆设备按 MAC 重连会连到已失效的旧地址（表现为 manufacturer=<none>、连不上或连上后无法识别）。
 * 该函数给出跨 MAC 稳定的重连身份：`svakom:<产品码>`；非司康沃或无产品码时返回 null，调用方回退到 MAC。
 * 产品码本身已能区分同名的伸缩/吮吸/拍打各侧（V=0x80、S=0x81 等），无需再带设备名。
 */
fun svakomStableIdentity(manufacturerData: String): String? {
    val fingerprint = BleGattFingerprint(
        name = "",
        address = "",
        manufacturerData = manufacturerData,
        scanRecordHex = "",
        serviceUuids = emptySet(),
        characteristicUuids = emptySet(),
    )
    val code = fingerprint.svakomProductCode() ?: return null
    return "svakom:$code"
}

fun svakomSl278PairIdentity(manufacturerData: String): String? {
    val fingerprint = BleGattFingerprint(
        name = "",
        address = "",
        manufacturerData = manufacturerData,
        scanRecordHex = "",
        serviceUuids = emptySet(),
        characteristicUuids = emptySet(),
    )
    return when (fingerprint.svakomProductCode()) {
        128, 129 -> "svakom_sl278_pair:sl278k"
        else -> null
    }
}

internal fun BleGattFingerprint.svakomProductCode(): Int? {
    val payload = svakomManufacturerPayload() ?: return null
    return when {
        payload.size >= 16 && payload[3].unsignedByte() == 0x02 ->
            (payload[13].unsignedByte() shl 16) or
                    (payload[14].unsignedByte() shl 8) or
                    payload[15].unsignedByte()
        payload.size >= 5 -> payload[4].unsignedByte()
        else -> null
    }
}

internal fun BleGattFingerprint.svakomManufacturerPayload(): ByteArray? {
    val mappedPayload = manufacturerData.toManufacturerDataMap()
        .values
        .firstOrNull { it.startsWithSvakomPrefix() }
    if (mappedPayload != null) return mappedPayload
    return manufacturerData.substringAfter(':', missingDelimiterValue = manufacturerData)
        .trim()
        .hexByteArray()
        .takeIf { it.startsWithSvakomPrefix() }
}

internal fun ByteArray.startsWithSvakomPrefix(): Boolean =
    size >= 3 &&
            this[0].unsignedByte() == 0x53 &&
            this[1].unsignedByte() == 0x56 &&
            this[2].unsignedByte() == 0x41

internal fun Byte.unsignedByte(): Int = toInt() and 0xff

internal fun BleGattFingerprint.hasAnkniDdddGatt(): Boolean =
    serviceUuids.contains(Uuid.parse("0000dddd-0000-1000-8000-00805f9b34fb")) &&
            characteristicUuids.contains(Uuid.parse("0000ddd1-0000-1000-8000-00805f9b34fb")) &&
            characteristicUuids.contains(Uuid.parse("0000ddd2-0000-1000-8000-00805f9b34fb"))

internal fun String.xiuxiudaPacket(command: IntArray): ByteArray {
    require(command.size == 4) { "羞羞哒指令必须是 4 字节" }
    val prefix = requireNotNull(xiuxiudaAddressPrefix()) { "羞羞哒设备地址无效" }
    val payload = prefix + command.map { it.coerceIn(0, 0xFF) }
    val checksum = payload.fold(0) { acc, value -> acc xor value }
    return (payload + checksum).map { it.toByte() }.toByteArray()
}

internal fun String.xiuxiudaAddressPrefix(): IntArray? {
    val addressBytes = split(':')
        .takeIf { it.size == 6 }
        ?.map { part -> part.toIntOrNull(16) ?: return null }
        ?: return null
    val key = 0x04 xor addressBytes.fold(0) { acc, value -> acc xor value }
    val high = (key ushr 4) and 0x0F
    val low = key and 0x0F
    val z1 = 0x80 or high
    val z2 = 0x80 or low
    val z3 = key
    val z4 = z1 xor z2 xor z3
    return intArrayOf(z1, z2, z3, z4)
}

internal fun String.firstManufacturerByte(): Int? {
    val payload = substringAfter(':', missingDelimiterValue = this)
    return payload
        .trim()
        .split(Regex("\\s+"))
        .firstOrNull()
        ?.takeIf { it.length in 1..2 && it.all { ch -> ch.isDigit() || ch.lowercaseChar() in 'a'..'f' } }
        ?.toIntOrNull(16)
}

internal fun String.toManufacturerDataMap(): Map<Int, ByteArray> =
    split(",")
        .mapNotNull { entry ->
            val idText = entry.substringBefore(":", missingDelimiterValue = "").trim()
            val dataText = entry.substringAfter(":", missingDelimiterValue = "").trim()
            val id = idText.removePrefix("0x").removePrefix("0X").toIntOrNull(16) ?: return@mapNotNull null
            id to dataText.hexByteArray()
        }
        .toMap()

internal fun String.hexByteArray(): ByteArray =
    trim()
        .takeIf { it.isNotEmpty() }
        ?.split(Regex("\\s+"))
        ?.mapNotNull { token ->
            token.takeIf { it.length in 1..2 && it.all { ch -> ch.isDigit() || ch.lowercaseChar() in 'a'..'f' } }
                ?.toIntOrNull(16)
                ?.toByte()
        }
        ?.toByteArray()
        ?: byteArrayOf()

internal fun IntArray.toByteArrayForSistalkV1(): ByteArray =
    if (size > 2) {
        byteArrayOf(size.toByte()) + map { it.coerceIn(0, 0xff).toByte() }
    } else {
        map { it.coerceIn(0, 0xff).toByte() }.toByteArray()
    }
