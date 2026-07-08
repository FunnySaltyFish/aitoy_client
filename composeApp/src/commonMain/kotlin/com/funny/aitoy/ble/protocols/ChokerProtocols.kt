package com.funny.aitoy.ble

import kotlin.math.ceil
import kotlin.uuid.Uuid

internal object ChokerMiyinProtocol : BleDeviceProtocol {
    private val serviceUuid = Uuid.parse("0000fff0-0000-1000-8000-00805f9b34fb")
    private val writeUuid = Uuid.parse("0000fff5-0000-1000-8000-00805f9b34fb")
    private val notifyUuid = Uuid.parse("0000fff6-0000-1000-8000-00805f9b34fb")

    private val modeCommands = listOf(
        bytes(0xA5, 0x12, 0x4B, 0x01, 0x14, 0x2A, 0x14, 0x2C, 0x05, 0x00, 0x14, 0x2D, 0x14, 0x2E, 0x05, 0x00, 0xF8, 0x0A),
        bytes(0xA5, 0x12, 0x4B, 0x02, 0x14, 0x2D, 0x05, 0x00, 0x14, 0x2D, 0x05, 0x00, 0x14, 0x2D, 0x05, 0x00, 0xC2, 0x0A),
        bytes(0xA5, 0x12, 0x4B, 0x03, 0x0A, 0x2A, 0x0A, 0x2C, 0x0A, 0x2D, 0x0A, 0x2E, 0x0A, 0x30, 0x05, 0x00, 0xC5, 0x0A),
        bytes(0xA5, 0x12, 0x4B, 0x04, 0x0A, 0x2C, 0x0A, 0x2E, 0x05, 0x00, 0x0A, 0x2C, 0x0A, 0x2E, 0x05, 0x00, 0xF8, 0x0A),
        bytes(0xA5, 0x12, 0x4B, 0x05, 0x1E, 0x2A, 0x1E, 0x2C, 0x1E, 0x2D, 0x1E, 0x2A, 0x1E, 0x2C, 0x1E, 0x2D, 0xF9, 0x0A),
        bytes(0xA5, 0x12, 0x4B, 0x06, 0x05, 0x2A, 0x05, 0x2C, 0x05, 0x2A, 0x05, 0x2C, 0x05, 0x2A, 0x05, 0x2C, 0xFC, 0x0A),
        bytes(0xA5, 0x12, 0x4B, 0x07, 0x28, 0x2D, 0x14, 0x2E, 0x1E, 0x2A, 0x28, 0x2D, 0x14, 0x2E, 0x1E, 0x2A, 0xFB, 0x0A),
        bytes(0xA5, 0x12, 0x4B, 0x08, 0x0A, 0x2A, 0x1E, 0x2D, 0x0A, 0x2A, 0x1E, 0x2D, 0x0A, 0x2A, 0x1E, 0x2D, 0xE7, 0x0A),
        bytes(0xA5, 0x12, 0x4B, 0x09, 0x05, 0x2D, 0x05, 0x00, 0x05, 0x2D, 0x05, 0x00, 0x05, 0x2D, 0x05, 0x00, 0xD8, 0x0A),
        bytes(0xA5, 0x12, 0x4B, 0x0A, 0x3C, 0x2E, 0x3C, 0x2C, 0x3C, 0x2E, 0x3C, 0x2C, 0x3C, 0x2E, 0x3C, 0x2C, 0xF4, 0x0A),
    )

    override val status = BleProtocolStatus(
        id = "choker_miyin",
        displayName = "iCHOKer 谜音",
        controllable = true,
        intensityMax = 100,
        supportsMode = true,
        modeMax = modeCommands.size,
        controlStyle = ToyControlStyle.ExclusivePatternOrIntensity,
        modeLabel = "choker模式",
        modeNames = List(modeCommands.size) { index -> "模式 ${index + 1}" },
        intensityLabel = "吮吸强度",
        features = listOf(
            BleProtocolFeature(
                type = "suck",
                min = 0,
                max = 100,
                index = 0,
                label = "吮吸强度",
            ),
        ),
        automatic = true,
        repeatIntervalMs = KEEPALIVE_MS.toInt(),
    )

    override fun matches(fingerprint: BleGattFingerprint): Boolean {
        val name = fingerprint.name.normalizedDeviceName()
        val hasOfficialGatt = fingerprint.serviceUuids.contains(serviceUuid) &&
                fingerprint.characteristicUuids.contains(writeUuid) &&
                fingerprint.characteristicUuids.contains(notifyUuid)
        return hasOfficialGatt && (name.startsWith("chux") || name.startsWith("bt00"))
    }

    override fun initialize(fingerprint: BleGattFingerprint): List<BleProtocolOperation> =
        listOf(
            BleProtocolOperation.SubscribeNotify(notifyUuid),
            BleProtocolOperation.Sleep(500),
            write(KEEPALIVE_COMMAND),
        )

    override fun keepaliveIntervalMs(): Long = KEEPALIVE_MS

    override fun keepaliveCommands(lastCommands: List<BleProtocolOperation>): List<BleProtocolOperation> =
        listOf(write(KEEPALIVE_COMMAND))

    override fun commandsFor(action: ToyControlAction): List<BleProtocolOperation> =
        when (action) {
            is ToyControlAction.Pattern -> listOf(patternCommand(action.mode))
            is ToyControlAction.Intensity -> listOf(intensityCommand(action.value))
            is ToyControlAction.Combined ->
                listOf(if (action.intensity > 0) intensityCommand(action.intensity) else patternCommand(action.mode))
            is ToyControlAction.DualMotor -> listOf(intensityCommand(action.strongestIntensity(status.intensityMax)))
            ToyControlAction.Stop -> listOf(write(STOP_COMMAND))
        }

    private fun patternCommand(mode: Int): BleProtocolOperation.Write =
        write(modeCommands[mode.coerceIn(1, status.modeMax) - 1])

    private fun intensityCommand(intensity: Int): BleProtocolOperation.Write {
        val normalized = intensity.coerceIn(0, status.intensityMax)
        if (normalized <= 0) return write(STOP_COMMAND)
        val gear = (0x25 + ceil(normalized / 9.0).toInt()).coerceIn(0x26, 0x30)
        val payload = intArrayOf(0xA5, 0x07, 0x5A, 0x00, gear)
        return write((payload + bcc(payload) + 0x0A).map { it.toByte() }.toByteArray())
    }

    private fun write(bytes: ByteArray): BleProtocolOperation.Write =
        BleProtocolOperation.Write(writeUuid, bytes, withResponse = false)

    private fun bcc(bytes: IntArray): Int =
        bytes.fold(0) { acc, value -> acc xor (value and 0xFF) } and 0xFF

    private const val KEEPALIVE_MS = 60_000L
    private val KEEPALIVE_COMMAND = bytes(0xA5, 0x07, 0x69, 0x00, 0x5A, 0x91, 0x0A)
    private val STOP_COMMAND = bytes(0xA5, 0x07, 0x3C, 0x00, 0x02, 0x9C, 0x0A)
}
