package com.funny.aitoy.ble

data class ScannedBleDevice(
    val name: String,
    val address: String,
    val rssi: Int,
    val connectable: Boolean,
    val serviceUuids: List<String> = emptyList(),
    val manufacturerData: String = "",
    val scanRecordHex: String = "",
    val broadcastProtocolName: String = "",
) {
    val controllable: Boolean get() = connectable || broadcastProtocolName.isNotBlank() || serviceUuids.isNotEmpty()
}

enum class BleConnectionState(val label: String) {
    Idle("未连接"),
    Connecting("正在连接"),
    Discovering("正在读取设备能力"),
    Ready("已连接，可以测试"),
    Disconnecting("正在断开"),
    Error("连接失败"),
}

data class ProtocolTemplate(
    val serviceUuid: String,
    val writeUuid: String,
    val notifyUuid: String,
    val writeWithResponse: Boolean,
    val manualControlEnabled: Boolean,
    val commandTemplate: String,
    val stopTemplate: String,
) {
    fun commandBytes(mode: Int, intensity: Int): ByteArray =
        parseHexTemplate(commandTemplate, mode, intensity)

    fun stopBytes(): ByteArray = parseHexTemplate(stopTemplate, mode = 0, intensity = 0)
}

enum class ToyControlStyle {
    IntensityOnly,
    PatternOnly,
    ExclusivePatternOrIntensity,
    CombinedPatternAndIntensity,
}

sealed interface ToyControlAction {
    data class Pattern(val mode: Int) : ToyControlAction
    data class Intensity(val value: Int) : ToyControlAction
    data class Combined(val mode: Int, val intensity: Int) : ToyControlAction
    data object Stop : ToyControlAction
}

data class BleProtocolStatus(
    val id: String = "",
    val displayName: String = "尚未识别",
    val controllable: Boolean = false,
    val verifiedControl: Boolean = false,
    val intensityMax: Int = 0,
    val supportsMode: Boolean = false,
    val modeMax: Int = 8,
    val controlStyle: ToyControlStyle = ToyControlStyle.IntensityOnly,
    val modeLabel: String = "节奏",
    val intensityLabel: String = "强度",
    val automatic: Boolean = false,
    val repeatIntervalMs: Int = 0,
)

data class ProtocolAttemptStatus(
    val active: Boolean = false,
    val success: Boolean = false,
    val title: String = "",
    val message: String = "",
    val protocolName: String = "",
    val currentIndex: Int = 0,
    val total: Int = 0,
    val failedNames: List<String> = emptyList(),
)

fun parseHexTemplate(template: String, mode: Int, intensity: Int): ByteArray {
    val resolved = template
        .replace("{mode}", mode.toString(16).padStart(2, '0'), ignoreCase = true)
        .replace("{intensity}", intensity.toString(16).padStart(2, '0'), ignoreCase = true)
        .trim()
    require(resolved.isNotEmpty()) { "指令不能为空" }
    val tokens = resolved.split(Regex("[\\s,]+"))
    val checksumIndex = tokens.indexOfFirst { it.equals("{checksum}", ignoreCase = true) }
    require(checksumIndex == -1 || checksumIndex == tokens.lastIndex) { "校验位只能放在最后" }
    val bytes = tokens.filterNot { it.equals("{checksum}", ignoreCase = true) }.map { token ->
        require(token.length in 1..2 && token.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) {
            "无法识别的字节：$token"
        }
        token.toInt(16).also { require(it in 0..255) }.toByte()
    }
    return if (checksumIndex == -1) {
        bytes.toByteArray()
    } else {
        val checksum = bytes.sumOf { it.toInt() and 0xff } and 0xff
        (bytes + checksum.toByte()).toByteArray()
    }
}

fun ByteArray.toHexString(): String = joinToString(" ") { byte ->
    (byte.toInt() and 0xff).toString(16).uppercase().padStart(2, '0')
}
