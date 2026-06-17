package com.funny.aitoy.ble

import kotlin.random.Random

internal data class BleAdvertiseOperation(
    val serviceUuid: String,
)

internal interface BleBroadcastProtocol {
    val status: BleProtocolStatus

    fun matches(device: ScannedBleDevice): Boolean

    fun setCommands(mode: Int, intensity: Int): List<BleAdvertiseOperation>

    fun stopCommands(mode: Int): List<BleAdvertiseOperation>
}

internal object BleBroadcastProtocolRegistry {
    private val protocols = listOf(CachitoBroadcastProtocol)

    fun resolveAll(device: ScannedBleDevice): List<BleBroadcastProtocol> =
        protocols.filter { it.matches(device) }
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
        CachitoMode("通用吸吮", "710000**-0400-####-0302-{sx075}00000000", "710000**-0400-####-0302-0000000000"),
        CachitoMode("RTD", "710003**-8800-####-0000-{rtd}", "710003**-0200-####-0000-0000000000"),
        CachitoMode("通用震动", "710000**-0400-####-050A-{sx075}00000000", "710000**-0400-####-0601-0000000000"),
        CachitoMode("SK4", "710017**-5100-####-0100-640000{sx050}02", "710017**-5100-####-0100-6400000002"),
        CachitoMode("设备 02 吸吮", "710002**-0400-####-0302-{sx075}00000000", "710002**-0400-####-0601-0000000000"),
        CachitoMode("设备 04 吸吮", "710004**-0400-####-0302-{sx075}00000000", "710004**-0400-####-0302-0000000000"),
        CachitoMode("设备 05 吸吮", "710005**-0400-####-0302-{sx075}00000000", "710004**-0400-####-0302-0000000000"),
        CachitoMode("设备 06 吸吮", "710006**-0400-####-0302-{sx075}00000000", "710004**-0400-####-0302-0000000000"),
        CachitoMode("设备 07 吸吮", "710007**-8200-####-0100-640000{sx050}02", "710007**-0F00-####-0100-0000000000"),
        CachitoMode("设备 09 吸吮", "710009**-8200-####-0100-640000{sx050}02", "710004**-0400-####-0302-0000000000"),
        CachitoMode("设备 0A 吸吮", "71000A**-8200-####-0100-640000{sx050}02", "710004**-0400-####-0302-0000000000"),
        CachitoMode("设备 0B 吸吮", "71000B**-8200-####-0100-640000{sx050}02", "71000B**-0F00-####-0100-0000000000"),
        CachitoMode("设备 0C 吸吮", "71000C**-8200-####-0100-640000{sx050}02", "71000C**-0F00-####-0100-0000000000"),
        CachitoMode("设备 02 震动", "710002**-0400-####-050A-{sx075}00000000", "710002**-0400-####-0601-0000000000"),
        CachitoMode("设备 07 震动", "710007**-8100-####-0100-0A{pj057}1C0002", "710007**-0100-####-0100-6400000002"),
        CachitoMode("设备 0B 震动", "71000B**-8100-####-0100-0A{pj057}1C0002", "71000B**-0100-####-0100-6400000002"),
    )

    private var deviceId = newDeviceId()

    override val status = BleProtocolStatus(
        id = "cachito_advertise",
        displayName = "Cachito 广播",
        controllable = true,
        intensityMax = 100,
        supportsMode = true,
        modeMax = modeTemplates.size,
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
        return knownHint || !device.connectable
    }

    override fun setCommands(mode: Int, intensity: Int): List<BleAdvertiseOperation> {
        val selected = modeTemplates[(mode - 1).coerceIn(0, modeTemplates.lastIndex)]
        return listOf(BleAdvertiseOperation(finalUuid(selected.command, intensity.coerceIn(0, 100))))
    }

    override fun stopCommands(mode: Int): List<BleAdvertiseOperation> {
        val selected = modeTemplates[(mode - 1).coerceIn(0, modeTemplates.lastIndex)]
        return listOf(BleAdvertiseOperation(finalUuid(selected.stop, 0)))
    }

    internal fun modeNames(): List<String> = modeTemplates.map { it.name }

    private fun finalUuid(template: String, progress: Int): String {
        val command = template
            .replace("{sx075}", toHex((progress * 0.75 + 25).toInt()))
            .replace("{sx050}", toHex((progress * 0.5 + 50).toInt()))
            .replace("{pj057}", toHex((60 - progress * 0.57f).toInt()))
            .replace("{rtd}", rtdPayload(progress))
            .replace("####", deviceId)
            .replace("**", newCommandId())
        val checksumSource = command.replace("-", "")
        return command + checksum(checksumSource)
    }

    private fun rtdPayload(progress: Int): String {
        if (progress <= 0) return "0000000000"
        val depth = ((progress - 1) * 58 / 99f + 14).toInt().coerceAtLeast(0)
        val speed = (progress * 0.15f).toInt().coerceAtLeast(2)
        val delay = (progress * 0.15f).toInt().coerceAtLeast(2)
        return toHex(depth) + toHex(speed) + toHex(delay) + "0000"
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

    private fun toHex(value: Int): String =
        (value and 0xff).toString(16).uppercase().padStart(2, '0')

    private data class CachitoMode(
        val name: String,
        val command: String,
        val stop: String,
    )
}
