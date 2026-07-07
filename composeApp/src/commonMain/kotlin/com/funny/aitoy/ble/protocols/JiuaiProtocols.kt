package com.funny.aitoy.ble

import kotlin.uuid.Uuid

internal object JiuaiJellyfishProtocol : BleDeviceProtocol {
    private val serviceUuid = Uuid.parse("0000ae3a-0000-1000-8000-00805f9b34fb")
    private val writeUuid = Uuid.parse("0000ae3b-0000-1000-8000-00805f9b34fb")
    private val notifyUuid = Uuid.parse("0000ae3c-0000-1000-8000-00805f9b34fb")
    private val modeCommands = intArrayOf(0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x10, 0x11)
    private val modeNames = listOf("微风", "心跳", "冲击", "拍打", "深度", "宇宙", "海啸", "瀑布", "火山", "神风")

    override val status = BleProtocolStatus(
        id = "jiuai_jellyfish_stroker",
        displayName = "久爱水母跳蛋",
        controllable = true,
        intensityMax = 100,
        supportsMode = true,
        modeMax = modeCommands.size,
        controlStyle = ToyControlStyle.ExclusivePatternOrIntensity,
        modeLabel = "伸缩模式",
        modeNames = modeNames,
        intensityLabel = "伸缩速度",
        automatic = true,
    )

    override fun matches(fingerprint: BleGattFingerprint): Boolean {
        val name = fingerprint.name.normalizedDeviceName()
        return name == "ycmbl001" &&
            fingerprint.serviceUuids.contains(serviceUuid) &&
            fingerprint.characteristicUuids.contains(writeUuid)
    }

    override fun initialize(fingerprint: BleGattFingerprint): List<BleProtocolOperation> =
        buildList {
            if (fingerprint.characteristicUuids.contains(notifyUuid)) {
                add(BleProtocolOperation.SubscribeNotify(notifyUuid))
            }
            add(write(bytes(0xaa, 0xaa, 0xaa, 0xaa, 0xaa)))
        }

    override fun commandsFor(action: ToyControlAction): List<BleProtocolOperation> =
        when (action) {
            is ToyControlAction.Pattern -> listOf(modeCommand(action.mode))
            is ToyControlAction.Intensity -> listOf(speedCommand(action.value))
            is ToyControlAction.Combined ->
                listOf(if (action.intensity > 0) speedCommand(action.intensity) else modeCommand(action.mode))
            is ToyControlAction.DualMotor -> listOf(speedCommand(action.strongestIntensity(status.intensityMax)))
            ToyControlAction.Stop -> listOf(pauseCommand())
        }

    private fun modeCommand(mode: Int): BleProtocolOperation.Write {
        val command = modeCommands[(mode.coerceIn(1, status.modeMax) - 1)]
        return write(command(0x01, command))
    }

    private fun speedCommand(intensity: Int): BleProtocolOperation.Write {
        val normalized = intensity.coerceIn(0, status.intensityMax)
        if (normalized <= 0) return pauseCommand()
        val speed = 35 + ((normalized - 1) * (100 - 35) / (status.intensityMax - 1).coerceAtLeast(1))
        return write(command(0x02, 0x12, speed))
    }

    private fun pauseCommand(): BleProtocolOperation.Write =
        write(command(0x01, 0x00))

    private fun command(type: Int, command: Int, value: Int? = null): ByteArray {
        val payload = if (value == null) {
            intArrayOf(0xaa, type, command)
        } else {
            intArrayOf(0xaa, type, command, value)
        }
        val checksum = payload.sum() and 0xff
        return (payload + checksum).map { it.toByte() }.toByteArray()
    }

    private fun write(bytes: ByteArray): BleProtocolOperation.Write =
        BleProtocolOperation.Write(
            characteristicUuid = writeUuid,
            bytes = bytes,
            withResponse = false,
        )
}
