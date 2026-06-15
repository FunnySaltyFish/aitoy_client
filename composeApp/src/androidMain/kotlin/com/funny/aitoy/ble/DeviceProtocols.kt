package com.funny.aitoy.ble

import java.util.UUID

internal sealed interface BleProtocolOperation {
    data class Read(val characteristicUuid: UUID) : BleProtocolOperation

    data class Write(
        val characteristicUuid: UUID,
        val bytes: ByteArray,
        val withResponse: Boolean,
    ) : BleProtocolOperation
}

internal data class BleGattFingerprint(
    val name: String,
    val characteristicUuids: Set<UUID>,
)

internal interface BleDeviceProtocol {
    val status: BleProtocolStatus

    fun matches(fingerprint: BleGattFingerprint): Boolean

    fun initialize(fingerprint: BleGattFingerprint): List<BleProtocolOperation> = emptyList()

    fun onRead(characteristicUuid: UUID, bytes: ByteArray): List<BleProtocolOperation> = emptyList()

    fun setCommand(mode: Int, intensity: Int): BleProtocolOperation.Write

    fun stopCommand(): BleProtocolOperation.Write
}

internal object BleProtocolRegistry {
    private val protocols = listOf(AnkniProtocol)

    fun resolve(fingerprint: BleGattFingerprint): BleDeviceProtocol? =
        protocols.firstOrNull { it.matches(fingerprint) }
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
        automatic = false,
    )

    override fun matches(fingerprint: BleGattFingerprint): Boolean = false

    override fun setCommand(mode: Int, intensity: Int) = BleProtocolOperation.Write(
        characteristicUuid = writeUuid,
        bytes = template.commandBytes(mode, intensity),
        withResponse = template.writeWithResponse,
    )

    override fun stopCommand() = BleProtocolOperation.Write(
        characteristicUuid = writeUuid,
        bytes = template.stopBytes(),
        withResponse = template.writeWithResponse,
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
        intensityMax = 3,
        supportsMode = false,
        automatic = true,
    )

    override fun matches(fingerprint: BleGattFingerprint): Boolean {
        val hasWrite = writeCandidates.any(fingerprint.characteristicUuids::contains)
        return fingerprint.name.equals("DSJM", ignoreCase = true) &&
            fingerprint.characteristicUuids.contains(deviceInfoUuid) &&
            hasWrite
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

    override fun setCommand(mode: Int, intensity: Int): BleProtocolOperation.Write =
        controlCommand(intensity.coerceIn(1, status.intensityMax))

    override fun stopCommand(): BleProtocolOperation.Write = controlCommand(0)

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

private fun uuid(value: String): UUID = UUID.fromString(value)
