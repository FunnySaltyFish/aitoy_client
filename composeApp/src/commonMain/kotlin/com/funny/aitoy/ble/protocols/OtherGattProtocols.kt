package com.funny.aitoy.ble

import com.funny.aitoy.buttplug.ButtplugConfigRepository
import com.funny.aitoy.buttplug.ButtplugDeviceFingerprint
import com.funny.aitoy.buttplug.ButtplugDeviceMatch
import com.funny.aitoy.buttplug.ButtplugLocalHandlerRegistry
import com.funny.aitoy.buttplug.FlufferProtocolPlans
import com.funny.aitoy.buttplug.HandyProtocolPlans
import com.funny.aitoy.buttplug.HoneyPlayBoxFrameCollector
import com.funny.aitoy.buttplug.HoneyPlayBoxProtocolPlans
import com.funny.aitoy.buttplug.LegacyStpihkalProtocolPlans
import com.funny.aitoy.buttplug.LeloProtocolPlans
import com.funny.aitoy.buttplug.LinearProtocolPlan
import com.funny.aitoy.buttplug.LinearProtocolPlans
import com.funny.aitoy.buttplug.LovenseProtocolPlans
import com.funny.aitoy.buttplug.MixedOutputProtocolPlan
import com.funny.aitoy.buttplug.MixedOutputProtocolPlans
import com.funny.aitoy.buttplug.ScalarProtocolPlan
import com.funny.aitoy.buttplug.ScalarProtocolPlans
import com.funny.aitoy.buttplug.ScalarProtocolWrite
import com.funny.aitoy.buttplug.SimpleVibrateProtocolPlan
import com.funny.aitoy.buttplug.SimpleVibrateProtocolPlans
import com.funny.aitoy.buttplug.SpecializedProtocolPlans
import com.funny.aitoy.buttplug.StatefulVibrateProtocolPlan
import com.funny.aitoy.buttplug.StatefulVibrateProtocolPlans
import com.funny.aitoy.buttplug.ToyOutputKind
import com.funny.aitoy.buttplug.VibCrafterProtocolPlans
import com.funny.aitoy.buttplug.WeVibeProtocolPlans
import com.funny.aitoy.buttplug.normalizedUuidText
import com.funny.aitoy.buttplug.toToyOutputKind
import kotlin.random.Random
import kotlin.uuid.Uuid

internal object SatisfyerProtocol : BleDeviceProtocol {
    private val controlUuid = Uuid.parse("51361501-c5e7-47c7-8a6e-47ebc99d80e8")
    private val speedUuid = Uuid.parse("51361502-c5e7-47c7-8a6e-47ebc99d80e8")

    override val status = BleProtocolStatus(
        id = "satisfyer",
        displayName = "Satisfyer",
        controllable = true,
        intensityMax = 100,
        supportsMode = false,
        controlStyle = ToyControlStyle.IntensityOnly,
        automatic = true,
    )

    override fun matches(fingerprint: BleGattFingerprint): Boolean =
        fingerprint.characteristicUuids.contains(controlUuid) &&
                fingerprint.characteristicUuids.contains(speedUuid)

    override fun commandsFor(action: ToyControlAction): List<BleProtocolOperation> =
        when (action) {
            is ToyControlAction.Intensity -> run(action.value)
            is ToyControlAction.Combined -> run(action.intensity)
            is ToyControlAction.DualMotor -> run(action.strongestIntensity(status.intensityMax))
            is ToyControlAction.Pattern -> emptyList()
            ToyControlAction.Stop -> listOf(speedCommand(0), BleProtocolOperation.Write(controlUuid, byteArrayOf(0x07), withResponse = true))
        }

    private fun run(intensity: Int): List<BleProtocolOperation> =
        listOf(
            BleProtocolOperation.Write(controlUuid, byteArrayOf(0x01), withResponse = true),
            speedCommand(intensity),
        )

    private fun speedCommand(intensity: Int) = BleProtocolOperation.Write(
        characteristicUuid = speedUuid,
        bytes = byteArrayOf(0x00, 0x00, 0x00, intensity.coerceIn(0, 100).toByte()),
        withResponse = true,
    )
}

internal object OhMiBodEsca2Protocol : BleDeviceProtocol {
    private val writeUuid = Uuid.parse("a0d70002-4c16-4ba7-977a-d394920e13a3")

    override val status = BleProtocolStatus(
        id = "ohmibod_esca2",
        displayName = "OhMiBod Esca 2",
        controllable = true,
        intensityMax = 100,
        supportsMode = false,
        controlStyle = ToyControlStyle.IntensityOnly,
        automatic = true,
    )

    override fun matches(fingerprint: BleGattFingerprint): Boolean {
        val name = fingerprint.name.normalizedDeviceName()
        val knownName = name.contains("ohmibod") || name.contains("esca")
        return knownName &&
            fingerprint.characteristicUuids.contains(writeUuid)
    }

    override fun commandsFor(action: ToyControlAction): List<BleProtocolOperation> =
        when (action) {
            is ToyControlAction.Intensity -> listOf(command(action.value))
            is ToyControlAction.Combined -> listOf(command(action.intensity))
            is ToyControlAction.DualMotor -> listOf(command(action.strongestIntensity(status.intensityMax)))
            is ToyControlAction.Pattern -> emptyList()
            ToyControlAction.Stop -> listOf(command(0))
        }

    private fun command(intensity: Int) = BleProtocolOperation.Write(
        characteristicUuid = writeUuid,
        bytes = byteArrayOf(0x01, intensity.coerceIn(0, 100).toByte()),
        withResponse = false,
    )
}

internal object PinkPunchProtocol : BleDeviceProtocol {
    private val writeUuid = Uuid.parse("0000ffe1-0000-1000-8000-00805f9b34fb")
    private val notifyUuid = Uuid.parse("0000ffe2-0000-1000-8000-00805f9b34fb")

    override val status = BleProtocolStatus(
        id = "pink_punch",
        displayName = "Pink Punch",
        controllable = true,
        intensityMax = 100,
        supportsMode = false,
        controlStyle = ToyControlStyle.IntensityOnly,
        automatic = true,
    )

    override fun matches(fingerprint: BleGattFingerprint): Boolean =
        fingerprint.name.normalizedDeviceName().contains("pinkpunch") &&
                fingerprint.characteristicUuids.contains(writeUuid) &&
                fingerprint.characteristicUuids.contains(notifyUuid)

    override fun commandsFor(action: ToyControlAction): List<BleProtocolOperation> =
        when (action) {
            is ToyControlAction.Intensity -> listOf(command(0x09, action.value))
            is ToyControlAction.Combined -> listOf(command(0x09, action.intensity))
            is ToyControlAction.DualMotor -> listOf(command(0x09, action.strongestIntensity(status.intensityMax)))
            is ToyControlAction.Pattern -> emptyList()
            ToyControlAction.Stop -> listOf(command(0x09, 0))
        }

    private fun command(mode: Int, intensity: Int) = BleProtocolOperation.Write(
        characteristicUuid = writeUuid,
        bytes = byteArrayOf(mode.toByte(), intensity.coerceIn(0, 100).toByte()),
        withResponse = false,
    )
}

internal object LovenutsProtocol : BleDeviceProtocol {
    private val writeUuid = Uuid.parse("0000fff1-0000-1000-8000-00805f9b34fb")

    override val status = BleProtocolStatus(
        id = "lovenuts",
        displayName = "Lovenuts",
        controllable = true,
        intensityMax = 15,
        supportsMode = false,
        controlStyle = ToyControlStyle.IntensityOnly,
        automatic = true,
    )

    override fun matches(fingerprint: BleGattFingerprint): Boolean =
        fingerprint.name.normalizedDeviceName().let { it.contains("lovenuts") || it.contains("love-nuts") } &&
                fingerprint.characteristicUuids.contains(writeUuid)

    override fun commandsFor(action: ToyControlAction): List<BleProtocolOperation> =
        when (action) {
            is ToyControlAction.Intensity -> listOf(patternCommand(action.value.coerceIn(0, status.intensityMax)))
            is ToyControlAction.Combined -> listOf(patternCommand(action.intensity.coerceIn(0, status.intensityMax)))
            is ToyControlAction.DualMotor -> listOf(patternCommand(action.strongestIntensity(status.intensityMax)))
            is ToyControlAction.Pattern -> emptyList()
            ToyControlAction.Stop -> listOf(patternCommand(speed = 0))
        }

    private fun patternCommand(speed: Int): BleProtocolOperation.Write {
        val packed = ((speed and 0x0f) shl 4) or (speed and 0x0f)
        val bytes = ByteArray(16)
        bytes[0] = 0x45
        bytes[1] = 0x56
        bytes[2] = 0x4f
        bytes[3] = 0x4c
        for (index in 4..13) bytes[index] = packed.toByte()
        bytes[14] = 0x00
        bytes[15] = 0xff.toByte()
        return BleProtocolOperation.Write(writeUuid, bytes, withResponse = false)
    }
}

internal object JeJoueNuoProtocol : BleDeviceProtocol {
    private val writeUuid = Uuid.parse("0000fff1-0000-1000-8000-00805f9b34fb")

    override val status = BleProtocolStatus(
        id = "je_joue_nuo",
        displayName = "Je Joue Nuo",
        controllable = true,
        intensityMax = 5,
        supportsMode = true,
        modeMax = 3,
        controlStyle = ToyControlStyle.CombinedPatternAndIntensity,
        automatic = true,
    )

    override fun matches(fingerprint: BleGattFingerprint): Boolean {
        val knownName = fingerprint.name.contains("Je Joue", ignoreCase = true) ||
                fingerprint.name.contains("Nuo", ignoreCase = true)
        return knownName && fingerprint.characteristicUuids.contains(writeUuid)
    }

    override fun commandsFor(action: ToyControlAction): List<BleProtocolOperation> =
        when (action) {
            is ToyControlAction.Combined -> listOf(command(action.mode.coerceIn(1, status.modeMax), action.intensity.coerceIn(0, status.intensityMax)))
            is ToyControlAction.Intensity -> listOf(command(pattern = 1, speed = action.value.coerceIn(0, status.intensityMax)))
            is ToyControlAction.DualMotor -> listOf(command(action.mode.coerceIn(1, status.modeMax), action.strongestIntensity(status.intensityMax)))
            is ToyControlAction.Pattern -> listOf(command(pattern = action.mode.coerceIn(1, status.modeMax), speed = status.intensityMax.coerceAtLeast(1)))
            ToyControlAction.Stop -> listOf(command(pattern = 1, speed = 0))
        }

    private fun command(pattern: Int, speed: Int) = BleProtocolOperation.Write(
        characteristicUuid = writeUuid,
        bytes = byteArrayOf(pattern.toByte(), speed.toByte()),
        withResponse = false,
    )
}

/**
 * Lovense BLE 字符串协议。
 * 公开资料显示 Lovense 设备名通常以 LVS- 或 LOVE- 开头，振动控制使用 Vibrate:n;。
 */
internal object LovenseProtocol : BleDeviceProtocol {
    private val writeCandidates = listOf(
        Uuid.parse("50300012-0023-4bd4-bbd5-a6920e4c5653"),
        Uuid.parse("0000fff2-0000-1000-8000-00805f9b34fb"),
        Uuid.parse("0000ffe1-0000-1000-8000-00805f9b34fb"),
    )
    private var writeUuid = writeCandidates.first()

    override val status = BleProtocolStatus(
        id = "lovense",
        displayName = "Lovense",
        controllable = true,
        intensityMax = 20,
        supportsMode = false,
        controlStyle = ToyControlStyle.IntensityOnly,
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

    override fun commandsFor(action: ToyControlAction): List<BleProtocolOperation> =
        when (action) {
            is ToyControlAction.Intensity -> listOf(command("Vibrate:${action.value.coerceIn(0, status.intensityMax)};"))
            is ToyControlAction.Combined -> listOf(command("Vibrate:${action.intensity.coerceIn(0, status.intensityMax)};"))
            is ToyControlAction.DualMotor -> listOf(command("Vibrate:${action.strongestIntensity(status.intensityMax)};"))
            is ToyControlAction.Pattern -> emptyList()
            ToyControlAction.Stop -> listOf(command("Vibrate:0;"))
        }

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
