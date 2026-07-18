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
                modeMax = SosexyMotor.MODE_MAX,
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
            is ToyControlAction.Pattern -> listOf(command(SosexyMotor.Suction, mode = 1, intensity = 100))
            is ToyControlAction.Intensity -> listOf(command(SosexyMotor.Suction, mode = 1, intensity = action.value))
            is ToyControlAction.Combined -> {
                val motor = motorForMode(action.mode)
                listOf(command(motor, functionModeForMode(action.mode), action.intensity))
            }
            is ToyControlAction.DualMotor -> listOf(
                command(SosexyMotor.Suction, mode = 1, intensity = action.internalIntensity),
                command(SosexyMotor.Vibration, mode = 1, intensity = action.externalIntensity),
            )
            ToyControlAction.Stop -> listOf(
                write(
                    bytes(
                        0x03,
                        0x00,
                        0x01, 0x11, 0x00,
                        0x00,
                        0x03, 0x11, 0x00,
                        0x00,
                        0x07, 0x11, 0x00,
                    )
                )
            )
        }

    private fun motorForMode(mode: Int): SosexyMotor {
        val functionCode = mode / 100
        if (functionCode > 0) {
            return SosexyMotor.entries.firstOrNull { it.functionCode == functionCode } ?: SosexyMotor.Suction
        }
        return SosexyMotor.entries.getOrNull(mode.coerceIn(1, SosexyMotor.entries.size) - 1) ?: SosexyMotor.Suction
    }

    private fun functionModeForMode(mode: Int): Int =
        if (mode / 100 > 0) {
            (mode % 100).coerceIn(1, SosexyMotor.MODE_MAX)
        } else {
            1
        }

    private fun command(motor: SosexyMotor, mode: Int, intensity: Int): BleProtocolOperation.Write =
        write(
            bytes(
                0x02,
                0x00,
                motor.intensityByte,
                0x11,
                intensity.coerceIn(0, 100),
                0x00,
                motor.modeByte,
                0x11,
                mode.coerceIn(1, SosexyMotor.MODE_MAX),
            )
        )

    private fun write(bytes: ByteArray): BleProtocolOperation.Write =
        BleProtocolOperation.Write(
            characteristicUuid = writeUuid,
            bytes = bytes,
            withResponse = true,
        )
}

private enum class SosexyMotor(
    val functionCode: Int,
    val type: String,
    val label: String,
    val intensityByte: Int,
    val modeByte: Int,
) {
    Suction(1, "constrict", "吮吸", 0x07, 0x08),
    Vibration(2, "vibrate", "震动", 0x01, 0x02),
    Electric(3, "shock", "电流", 0x03, 0x04);

    companion object {
        const val MODE_MAX = 4
    }
}

internal fun BleProtocolStatus.independentFunctionCode(feature: BleProtocolFeature): Int =
    when (id) {
        AnkniMxProtocol.status.id -> feature.index + 1
        CachitoDaxiuBroadcastProtocol.status.id -> feature.index + 1
        MesanelProtocol.status.id,
        "mesanel_coco",
        "mesanel_cocopro",
        "mesanel_super",
        "mesanel_handou",
        "mesanel_cisecat",
        "mesanel_pinkpo",
        "mesanel_bosscat",
        "mesanel_generic" -> mesanelFunctionCodeForFeature(feature)
        SosexyBoboBeiProtocol.status.id -> feature.index + 1
        SistalkPopocatProtocol.status.id -> feature.index + 1
        else -> feature.functionCode.takeIf { it > 0 } ?: svakomV2FunctionCode(feature.type)
    }

internal fun BleProtocolStatus.independentFunctionModeMax(feature: BleProtocolFeature): Int =
    when (id) {
        AnkniMxProtocol.status.id -> modeMax.coerceAtLeast(1)
        "ankni_qd1_xhtkj",
        "ankni_qd1_xhtkj_ff15" -> feature.modeMax
        CachitoDaxiuBroadcastProtocol.status.id -> 0
        "beyourlover_bx288a",
        "beyourlover_hx029a_v1",
        "beyourlover_vx357b_v1",
        "beyourlover_va422a_v1",
        "beyourlover_ct654a_v1",
        "beyourlover_vv468a" -> feature.modeMax
        MesanelProtocol.status.id,
        "mesanel_coco",
        "mesanel_cocopro",
        "mesanel_super",
        "mesanel_handou",
        "mesanel_cisecat",
        "mesanel_pinkpo",
        "mesanel_bosscat",
        "mesanel_generic" -> feature.modeMax
        SosexyBoboBeiProtocol.status.id -> feature.modeMax
        SistalkPopocatProtocol.status.id -> 1
        else -> feature.modeMax.takeIf { it > 0 } ?: svakomV2FunctionModeMax(feature.type)
    }
