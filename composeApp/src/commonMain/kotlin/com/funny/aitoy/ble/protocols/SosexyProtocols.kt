package com.funny.aitoy.ble

import kotlin.uuid.Uuid

internal object SosexyBoboBeiProtocol : BleDeviceProtocol {
    private val writeUuid = Uuid.parse("0000ee03-0000-1000-8000-00805f9b34fb")
    private val notifyUuid = Uuid.parse("0000ee02-0000-1000-8000-00805f9b34fb")

    override val status = BleProtocolStatus(
        id = "sosexy_bobo_bei",
        displayName = "Funf 啵啵贝",
        controllable = true,
        intensityMax = 100,
        supportsMode = true,
        modeMax = SosexyMotor.entries.size,
        controlStyle = ToyControlStyle.IndependentFunctions,
        modeLabel = "功能",
        intensityLabel = "强度",
        channelNames = SosexyMotor.entries.map { it.label },
        features = SosexyMotor.entries.mapIndexed { index, motor ->
            BleProtocolFeature(
                type = motor.type,
                min = 0,
                max = 100,
                index = index,
                label = motor.label,
            )
        },
        automatic = true,
    )

    override fun matches(fingerprint: BleGattFingerprint): Boolean {
        val name = fingerprint.name.normalizedDeviceName()
        val hasGatt = fingerprint.characteristicUuids.contains(writeUuid) &&
                fingerprint.characteristicUuids.contains(notifyUuid)
        return name == "sosexy" && hasGatt
    }

    override fun commandsFor(action: ToyControlAction): List<BleProtocolOperation> =
        when (action) {
            is ToyControlAction.Pattern -> listOf(command(SosexyMotor.Suction, 100))
            is ToyControlAction.Intensity -> listOf(command(SosexyMotor.Suction, action.value))
            is ToyControlAction.Combined -> listOf(command(motorForMode(action.mode), action.intensity))
            is ToyControlAction.DualMotor -> listOf(
                command(SosexyMotor.Suction, action.internalIntensity),
                command(SosexyMotor.Vibration, action.externalIntensity),
            )
            ToyControlAction.Stop -> SosexyMotor.entries.map { motor -> command(motor, 0) }
        }

    private fun motorForMode(mode: Int): SosexyMotor {
        val functionCode = mode / 100
        if (functionCode > 0) {
            return SosexyMotor.entries.firstOrNull { it.functionCode == functionCode } ?: SosexyMotor.Suction
        }
        return SosexyMotor.entries.getOrNull(mode.coerceIn(1, SosexyMotor.entries.size) - 1) ?: SosexyMotor.Suction
    }

    private fun command(motor: SosexyMotor, intensity: Int): BleProtocolOperation.Write =
        BleProtocolOperation.Write(
            characteristicUuid = writeUuid,
            bytes = bytes(
                0x01,
                0x01,
                0x00,
                0x02,
                0x00,
                motor.modeByte1,
                0x11,
                intensity.coerceIn(0, 100),
                0x00,
                motor.modeByte2,
                0x11,
                0x01,
            ),
            withResponse = true,
        )
}

private enum class SosexyMotor(
    val functionCode: Int,
    val type: String,
    val label: String,
    val modeByte1: Int,
    val modeByte2: Int,
) {
    Suction(1, "constrict", "吮吸", 0x07, 0x08),
    Vibration(2, "vibrate", "震动", 0x01, 0x02),
    Electric(3, "shock", "电流", 0x03, 0x04),
}

internal fun BleProtocolStatus.independentFunctionCode(feature: BleProtocolFeature): Int =
    when (id) {
        AnkniMxProtocol.status.id -> feature.index + 1
        SosexyBoboBeiProtocol.status.id -> feature.index + 1
        SistalkPopocatProtocol.status.id -> feature.index + 1
        else -> svakomV2FunctionCode(feature.type)
    }

internal fun BleProtocolStatus.independentFunctionModeMax(feature: BleProtocolFeature): Int =
    when (id) {
        AnkniMxProtocol.status.id -> modeMax.coerceAtLeast(1)
        SosexyBoboBeiProtocol.status.id -> 1
        SistalkPopocatProtocol.status.id -> 1
        else -> svakomV2FunctionModeMax(feature.type)
    }
