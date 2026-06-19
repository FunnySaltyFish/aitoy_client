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
    val controllable: Boolean get() = connectable || broadcastProtocolName.isNotBlank()
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

data class BleProtocolStatus(
    val id: String = "",
    val displayName: String = "尚未识别",
    val controllable: Boolean = false,
    val verifiedControl: Boolean = false,
    val intensityMax: Int = 0,
    val supportsMode: Boolean = false,
    val modeMax: Int = 8,
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
    return resolved.split(Regex("[\\s,]+")).map { token ->
        require(token.length in 1..2 && token.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) {
            "无法识别的字节：$token"
        }
        token.toInt(16).also { require(it in 0..255) }.toByte()
    }.toByteArray()
}

fun ByteArray.toHexString(): String = joinToString(" ") { byte ->
    (byte.toInt() and 0xff).toString(16).uppercase().padStart(2, '0')
}
