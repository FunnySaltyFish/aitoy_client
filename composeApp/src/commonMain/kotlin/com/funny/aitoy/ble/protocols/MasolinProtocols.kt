package com.funny.aitoy.ble

import kotlin.uuid.Uuid

internal object MasolinWaiwaimaFfa0Protocol : BleDeviceProtocol {
    private val serviceUuid = Uuid.parse("0000ffa0-0000-1000-8000-00805f9b34fb")
    private val writeUuid = Uuid.parse("0000ffa1-0000-1000-8000-00805f9b34fb")
    private const val continuousMotor = 0x00
    private const val vibrationMotor = 0x02
    private const val stopMotor = 0x14

    override val status = BleProtocolStatus(
        id = "masolin_waiwaima_ffa0",
        displayName = "MASOLIN 小纤蛋",
        controllable = true,
        intensityMax = 100,
        supportsMode = true,
        modeMax = 8,
        controlStyle = ToyControlStyle.CombinedPatternAndIntensity,
        modeLabel = "节奏",
        modeNames = List(8) { index -> "节奏 ${index + 1}" },
        intensityLabel = "强度",
        automatic = true,
        repeatIntervalMs = 200,
    )

    override fun matches(fingerprint: BleGattFingerprint): Boolean {
        if (!fingerprint.serviceUuids.contains(serviceUuid)) return false
        if (!fingerprint.characteristicUuids.contains(writeUuid)) return false
        return fingerprint.hasMasolinManufacturer() || fingerprint.hasOfficialWaiwaimaBleName()
    }

    override fun commandsFor(action: ToyControlAction): List<BleProtocolOperation> =
        when (action) {
            is ToyControlAction.Intensity -> listOf(controlFrame(motor = continuousMotor, mode = 0, intensity = action.value))
            is ToyControlAction.Combined -> {
                if (action.intensity <= 0) {
                    listOf(stopFrame())
                } else {
                    listOf(controlFrame(motor = vibrationMotor, mode = action.mode, intensity = action.intensity))
                }
            }
            is ToyControlAction.DualMotor -> {
                val intensity = action.strongestIntensity(status.intensityMax)
                if (intensity <= 0) {
                    listOf(stopFrame())
                } else {
                    listOf(controlFrame(motor = vibrationMotor, mode = action.mode, intensity = intensity))
                }
            }
            is ToyControlAction.Pattern -> listOf(controlFrame(motor = vibrationMotor, mode = action.mode, intensity = 50))
            ToyControlAction.Stop -> listOf(stopFrame())
        }

    private fun controlFrame(motor: Int, mode: Int, intensity: Int): BleProtocolOperation.Write =
        write(
            byteArrayOf(
                0xaa.toByte(),
                0x0b,
                motor.coerceIn(0, 0xff).toByte(),
                mode.coerceIn(0, status.modeMax).toByte(),
                intensity.toMasolinStrengthByte(),
            ),
        )

    private fun stopFrame(): BleProtocolOperation.Write =
        write(byteArrayOf(0xaa.toByte(), 0x0b, stopMotor.toByte(), 0x00, 0x00))

    private fun write(bytes: ByteArray): BleProtocolOperation.Write =
        BleProtocolOperation.Write(writeUuid, bytes, withResponse = true)

    private fun Int.toMasolinStrengthByte(): Byte =
        if (this <= 0) {
            0
        } else {
            ((coerceIn(1, status.intensityMax) + 9) / 10).coerceIn(1, 10)
        }.toByte()

    private fun BleGattFingerprint.hasMasolinManufacturer(): Boolean {
        val compactManufacturer = manufacturerData.compactHexLower()
        val compactRecord = scanRecordHex.compactHexLower()
        return compactManufacturer.startsWith("0x642") ||
            compactManufacturer.startsWith("0x0642") ||
            compactManufacturer.startsWith("0642") ||
            compactManufacturer.startsWith("642") ||
            compactRecord.contains("ff4206")
    }

    private fun BleGattFingerprint.hasOfficialWaiwaimaBleName(): Boolean {
        val normalized = name.trim().lowercase()
        return normalized.startsWith("my_") || normalized == "rsq-app01"
    }

    private fun String.compactHexLower(): String =
        lowercase().filter { it.isDigit() || it in 'a'..'f' || it == 'x' }
}
