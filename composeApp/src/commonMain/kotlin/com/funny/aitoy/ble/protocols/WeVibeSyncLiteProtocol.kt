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

internal object WeVibeSyncLiteProtocol : BleDeviceProtocol {
    private val serviceUuid = Uuid.parse("f000bb03-0451-4000-b000-000000000000")
    private val txUuid = Uuid.parse("f000c000-0451-4000-b000-000000000000")
    private val rxUuid = Uuid.parse("f000b000-0451-4000-b000-000000000000")
    private var currentPattern = 1
    private var currentInternalIntensity = 0
    private var currentExternalIntensity = 0

    override val status = BleProtocolStatus(
        id = "wevibe_sync_lite",
        displayName = "We-Vibe Sync Lite",
        controllable = true,
        intensityMax = 15,
        supportsMode = true,
        modeMax = WeVibeProtocolPlans.packedPatternCodes.size,
        controlStyle = ToyControlStyle.PatternAndDualIntensity,
        modeLabel = "预设节奏",
        modeNames = WeVibeProtocolPlans.packedPatternNames,
        intensityLabel = "整体强度",
        channelNames = listOf("内侧", "外侧"),
        automatic = true,
    )

    override fun matches(fingerprint: BleGattFingerprint): Boolean {
        val isSyncLite = fingerprint.name.normalizedDeviceName() == "synclite"
        return isSyncLite &&
                fingerprint.serviceUuids.contains(serviceUuid) &&
                fingerprint.characteristicUuids.contains(txUuid) &&
                fingerprint.characteristicUuids.contains(rxUuid)
    }

    override fun initialize(fingerprint: BleGattFingerprint): List<BleProtocolOperation> {
        currentPattern = 1
        currentInternalIntensity = 0
        currentExternalIntensity = 0
        return emptyList()
    }

    override fun commandsFor(action: ToyControlAction): List<BleProtocolOperation> =
        when (action) {
            is ToyControlAction.Intensity -> listOf(command(currentPattern, action.value, action.value))
            is ToyControlAction.Combined -> listOf(command(action.mode, action.intensity, action.intensity))
            is ToyControlAction.DualMotor -> listOf(
                command(action.mode, action.internalIntensity, action.externalIntensity),
            )
            is ToyControlAction.Pattern -> listOf(
                command(action.mode, currentInternalIntensity, currentExternalIntensity),
            )
            ToyControlAction.Stop -> listOf(stopCommand())
        }

    private fun command(mode: Int, internalIntensity: Int, externalIntensity: Int): BleProtocolOperation.Write {
        currentPattern = mode.coerceIn(1, WeVibeProtocolPlans.packedPatternCodes.size)
        currentInternalIntensity = internalIntensity.coerceIn(0, status.intensityMax)
        currentExternalIntensity = externalIntensity.coerceIn(0, status.intensityMax)
        return BleProtocolOperation.Write(
            characteristicUuid = txUuid,
            bytes = WeVibeProtocolPlans.packedPatternPayload(
                currentPattern,
                currentInternalIntensity,
                currentExternalIntensity,
            ),
            withResponse = true,
        )
    }

    private fun stopCommand(): BleProtocolOperation.Write {
        currentInternalIntensity = 0
        currentExternalIntensity = 0
        return BleProtocolOperation.Write(
            characteristicUuid = txUuid,
            bytes = WeVibeProtocolPlans.stopPayload(),
            withResponse = true,
        )
    }
}

