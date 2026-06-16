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

    fun setCommands(mode: Int, intensity: Int): List<BleProtocolOperation> =
        listOf(setCommand(mode, intensity))

    fun stopCommands(): List<BleProtocolOperation> = listOf(stopCommand())
}

internal object BleProtocolRegistry {
    private val protocols = listOf(
        AnkniProtocol,
        LovenseProtocol,
        SatisfyerProtocol,
        OhMiBodEsca2Protocol,
        PinkPunchProtocol,
        LovenutsProtocol,
        JeJoueNuoProtocol,
    )

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

private object SatisfyerProtocol : BleDeviceProtocol {
    private val controlUuid = uuid("51361501-c5e7-47c7-8a6e-47ebc99d80e8")
    private val speedUuid = uuid("51361502-c5e7-47c7-8a6e-47ebc99d80e8")

    override val status = BleProtocolStatus(
        id = "satisfyer",
        displayName = "Satisfyer",
        controllable = true,
        intensityMax = 100,
        supportsMode = false,
        automatic = true,
    )

    override fun matches(fingerprint: BleGattFingerprint): Boolean =
        fingerprint.characteristicUuids.contains(controlUuid) &&
                fingerprint.characteristicUuids.contains(speedUuid)

    override fun setCommand(mode: Int, intensity: Int): BleProtocolOperation.Write =
        speedCommand(intensity)

    override fun setCommands(mode: Int, intensity: Int): List<BleProtocolOperation> =
        listOf(
            BleProtocolOperation.Write(controlUuid, byteArrayOf(0x01), withResponse = true),
            speedCommand(intensity),
        )

    override fun stopCommand(): BleProtocolOperation.Write =
        BleProtocolOperation.Write(controlUuid, byteArrayOf(0x07), withResponse = true)

    override fun stopCommands(): List<BleProtocolOperation> =
        listOf(speedCommand(0), stopCommand())

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
        automatic = true,
    )

    override fun matches(fingerprint: BleGattFingerprint): Boolean =
        fingerprint.name.equals("OhMiBod 4.0", ignoreCase = true) &&
                fingerprint.characteristicUuids.contains(writeUuid)

    override fun setCommand(mode: Int, intensity: Int): BleProtocolOperation.Write =
        command(intensity)

    override fun stopCommand(): BleProtocolOperation.Write = command(0)

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
        automatic = true,
    )

    override fun matches(fingerprint: BleGattFingerprint): Boolean =
        fingerprint.name.equals("Pink_Punch", ignoreCase = true) &&
                fingerprint.characteristicUuids.contains(writeUuid) &&
                fingerprint.characteristicUuids.contains(notifyUuid)

    override fun setCommand(mode: Int, intensity: Int): BleProtocolOperation.Write =
        command(0x09, intensity)

    override fun stopCommand(): BleProtocolOperation.Write = command(0x09, 0)

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
        automatic = true,
    )

    override fun matches(fingerprint: BleGattFingerprint): Boolean =
        fingerprint.name.equals("Love_Nuts", ignoreCase = true) &&
                fingerprint.characteristicUuids.contains(writeUuid)

    override fun setCommand(mode: Int, intensity: Int): BleProtocolOperation.Write =
        patternCommand(intensity.coerceIn(0, status.intensityMax), intervalMs = 250)

    override fun stopCommand(): BleProtocolOperation.Write =
        patternCommand(speed = 0, intervalMs = 0)

    private fun patternCommand(speed: Int, intervalMs: Int): BleProtocolOperation.Write {
        val packed = ((speed and 0x0f) shl 4) or (speed and 0x0f)
        val bytes = ByteArray(17)
        bytes[0] = 0x45
        bytes[1] = 0x56
        bytes[2] = 0x04
        bytes[3] = 0x0f
        bytes[4] = 0x4c
        for (index in 5..14) bytes[index] = packed.toByte()
        bytes[15] = ((intervalMs ushr 8) and 0xff).toByte()
        bytes[16] = (intervalMs and 0xff).toByte()
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
        automatic = true,
    )

    override fun matches(fingerprint: BleGattFingerprint): Boolean {
        val knownName = fingerprint.name.contains("Je Joue", ignoreCase = true) ||
                fingerprint.name.contains("Nuo", ignoreCase = true)
        return knownName && fingerprint.characteristicUuids.contains(writeUuid)
    }

    override fun setCommand(mode: Int, intensity: Int): BleProtocolOperation.Write =
        command("${mode.coerceIn(1, 8)}${intensity.coerceIn(0, status.intensityMax)}")

    override fun stopCommand(): BleProtocolOperation.Write = command("80")

    private fun command(value: String) = BleProtocolOperation.Write(
        characteristicUuid = writeUuid,
        bytes = value.encodeToByteArray(),
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

    override fun setCommand(mode: Int, intensity: Int): BleProtocolOperation.Write =
        command("Vibrate:${intensity.coerceIn(0, status.intensityMax)};")

    override fun stopCommand(): BleProtocolOperation.Write = command("Vibrate:0;")

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
