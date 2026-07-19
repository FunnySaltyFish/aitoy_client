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

internal object SvakomQhSx045Protocol : BleDeviceProtocol {
    private val serviceUuid = Uuid.parse("0000ffe0-0000-1000-8000-00805f9b34fb")
    private val writeUuid = Uuid.parse("0000ffe1-0000-1000-8000-00805f9b34fb")

    override val status = BleProtocolStatus(
        id = "svakom_qh_sx045",
        displayName = "SVAKOM QH-SX045",
        controllable = true,
        intensityMax = 10,
        supportsMode = false,
        controlStyle = ToyControlStyle.IntensityOnly,
        automatic = true,
    )

    override fun matches(fingerprint: BleGattFingerprint): Boolean {
        val name = fingerprint.name.normalizedDeviceName()
        val knownName = name.contains("qh-sx") || name.contains("svakom")
        val hasSvakomGatt = fingerprint.serviceUuids.contains(serviceUuid) &&
                fingerprint.characteristicUuids.contains(writeUuid)
        return knownName && hasSvakomGatt
    }

    override fun commandsFor(action: ToyControlAction): List<BleProtocolOperation> =
        when (action) {
            is ToyControlAction.Intensity -> listOf(command(0x01, action.value.coerceIn(0, status.intensityMax)))
            is ToyControlAction.Combined -> listOf(command(0x01, action.intensity.coerceIn(0, status.intensityMax)))
            is ToyControlAction.DualMotor -> listOf(command(0x01, action.strongestIntensity(status.intensityMax)))
            is ToyControlAction.Pattern -> emptyList()
            ToyControlAction.Stop -> listOf(command(0x00, 0x00))
        }

    private fun command(mode: Int, speed: Int) = BleProtocolOperation.Write(
        characteristicUuid = writeUuid,
        bytes = byteArrayOf(
            0x55,
            0x03,
            0x00,
            0x00,
            mode.toByte(),
            speed.toByte(),
            0x00,
        ),
        withResponse = false,
    )
}

internal object SvakomV2MultiFunctionProtocol : BleDeviceProtocol {
    private val serviceUuid = Uuid.parse("0000ffe0-0000-1000-8000-00805f9b34fb")
    private val writeUuid = Uuid.parse("0000ffe1-0000-1000-8000-00805f9b34fb")
    private val notifyUuid = Uuid.parse("0000ffe2-0000-1000-8000-00805f9b34fb")

    override val status = SVAKOM_V2_STRETCH_VIBRATE.status

    override fun matches(fingerprint: BleGattFingerprint): Boolean {
        val name = fingerprint.name.normalizedDeviceName()
        val hasSvakomGatt = fingerprint.serviceUuids.contains(serviceUuid) &&
                fingerprint.characteristicUuids.contains(writeUuid) &&
                fingerprint.characteristicUuids.contains(notifyUuid)
        val productCode = fingerprint.svakomProductCode()
        val isSl278h = productCode in SL278_PLUS_PRODUCT_CODES ||
                productCode == null && SL278_PLUS_NAMES.any { name.contains(it) }
        return isSl278h && hasSvakomGatt && fingerprint.hasSvakomManufacturerData()
    }

    override fun createInstance(fingerprint: BleGattFingerprint): BleDeviceProtocol =
        if (fingerprint.svakomProductCode() == 240) {
            SvakomTl278bAppSession()
        } else {
            SvakomV2Session(fingerprint.svakomProductCode().svakomV2Profile())
        }

    override fun commandsFor(action: ToyControlAction): List<BleProtocolOperation> = emptyList()

    private val SL278_PLUS_PRODUCT_CODES = setOf(100, 101, 107, 108, 110, 111, 121, 128, 129, 145, 240, 328, 329)
    private val SL278_PLUS_NAMES = listOf("sl278b", "sl278f", "sl278h", "sl278i", "sl278j", "sl278k", "sl278u")
}

// 司康沃按官方产品码分流的通用 V2 多功能 matcher（走通用 SVAKOM V2 GATT FFE0/FFE1/FFE2）。
// QH-SX007E / Svakom Alberta：十档震动（强度可调）+ 五档吮吸。
// SX119B（波波鸟）：官方产品码 57，八档吮吸（强度 0..5）+ 十一档震动（强度 0..10）。
// SX589A（青提）：官方产品码 59，与 SX119B 同构（case 45/46 fall-through）。
// ST462A（口口甜）：官方产品码 313，舔（10 档）+ 吮吸（3 档无强度）+ 震动（10 档）三功能区。
// HS141B（啾啾雀）：官方产品码 30，吮吸 8 档 + 舌舔 8 档，两路均有强度滑杆。
internal object SvakomVibrateSuckProtocol : BleDeviceProtocol {
    private val serviceUuid = Uuid.parse("0000ffe0-0000-1000-8000-00805f9b34fb")
    private val writeUuid = Uuid.parse("0000ffe1-0000-1000-8000-00805f9b34fb")
    private val notifyUuid = Uuid.parse("0000ffe2-0000-1000-8000-00805f9b34fb")

    override val status = SVAKOM_V2_VIBRATE_SUCK.status

    override fun matches(fingerprint: BleGattFingerprint): Boolean {
        val name = fingerprint.name.normalizedDeviceName()
        val hasSvakomGatt = fingerprint.serviceUuids.contains(serviceUuid) &&
                fingerprint.characteristicUuids.contains(writeUuid) &&
                fingerprint.characteristicUuids.contains(notifyUuid)
        val isKnownModel = vibrateSuckProfile(fingerprint.svakomProductCode(), name) != null
        return isKnownModel && hasSvakomGatt && fingerprint.hasSvakomManufacturerData()
    }

    override fun createInstance(fingerprint: BleGattFingerprint): BleDeviceProtocol =
        SvakomV2Session(
            vibrateSuckProfile(
                fingerprint.svakomProductCode(),
                fingerprint.name.normalizedDeviceName(),
            ) ?: SVAKOM_V2_VIBRATE_SUCK,
        )

    override fun commandsFor(action: ToyControlAction): List<BleProtocolOperation> = emptyList()

    private fun vibrateSuckProfile(productCode: Int?, normalizedName: String): SvakomV2Profile? =
        when {
            productCode == 57 || normalizedName.contains("sx119b") -> SVAKOM_V2_SX119B
            productCode == 59 || normalizedName.contains("sx589a") -> SVAKOM_V2_SX589A
            productCode == 313 || normalizedName.contains("st462a") -> SVAKOM_V2_ST462A
            productCode == 30 || normalizedName.contains("hs141b") -> SVAKOM_V2_HS141B
            normalizedName.contains("qhsx007e") -> SVAKOM_V2_VIBRATE_SUCK
            else -> null
        }
}

// 礼物猫 / BeYourLover V1：官方 DeviceBean 按 SVA V1 广播的产品码和功能位识别，
// 部分型号还按底层设备名特化（BX288A、VX357B、VV468A、CT654A）。
internal object BeYourLoverV1Protocol : BleDeviceProtocol {
    private val serviceUuid = Uuid.parse("0000ffe0-0000-1000-8000-00805f9b34fb")
    private val writeUuid = Uuid.parse("0000ffe1-0000-1000-8000-00805f9b34fb")

    override val status = BE_YOUR_LOVER_V1_VIBRATE.status

    override fun matches(fingerprint: BleGattFingerprint): Boolean {
        val hasWriteGatt = fingerprint.serviceUuids.contains(serviceUuid) &&
                fingerprint.characteristicUuids.contains(writeUuid)
        return hasWriteGatt &&
                fingerprint.hasSvakomManufacturerData() &&
                fingerprint.beYourLoverV1Profile() != null
    }

    override fun createInstance(fingerprint: BleGattFingerprint): BleDeviceProtocol =
        BeYourLoverV1Session(fingerprint.beYourLoverV1Profile() ?: BE_YOUR_LOVER_V1_VIBRATE)

    override fun commandsFor(action: ToyControlAction): List<BleProtocolOperation> = emptyList()
}

private class BeYourLoverV1Session(
    private val profile: BeYourLoverV1Profile,
) : BleDeviceProtocol {
    private val writeUuid = Uuid.parse("0000ffe1-0000-1000-8000-00805f9b34fb")
    private val states = profile.functions.associate { it.code to BeYourLoverV1FunctionState() }.toMutableMap()

    override val status: BleProtocolStatus = profile.status

    override fun matches(fingerprint: BleGattFingerprint): Boolean = false

    override fun keepaliveIntervalMs(): Long = 1_500L

    override fun commandsFor(action: ToyControlAction): List<BleProtocolOperation> =
        when (action) {
            is ToyControlAction.Pattern -> write(
                profile.defaultFunction,
                mode = action.mode,
                intensity = if (profile.defaultFunction.modeMax > 0) 1 else action.mode,
            )
            is ToyControlAction.Intensity -> write(profile.defaultFunction, mode = 0, intensity = action.value)
            is ToyControlAction.Combined -> {
                val function = profile.functionByCode(action.mode / 100)
                write(function, mode = action.mode % 100, intensity = action.intensity)
            }
            is ToyControlAction.DualMotor -> {
                val first = profile.functions.getOrNull(0)
                val second = profile.functions.getOrNull(1)
                buildList {
                    if (first != null) add(writeOperation(first, mode = 0, intensity = action.internalIntensity))
                    if (second != null) add(writeOperation(second, mode = 0, intensity = action.externalIntensity))
                }
            }
            ToyControlAction.Stop -> stopAll()
        }

    private fun write(function: BeYourLoverV1Function, mode: Int, intensity: Int): List<BleProtocolOperation> =
        buildList {
            val state = function.safeState(mode, intensity)
            if (profile.exclusiveFunctionCodes.contains(function.code) && state.isActive()) {
                profile.exclusiveFunctionCodes
                    .filter { it != function.code }
                    .mapNotNull(profile::functionByCodeOrNull)
                    .filter { other -> states[other.code]?.isActive() == true }
                    .forEach { other ->
                        states[other.code] = BeYourLoverV1FunctionState()
                        add(writeOperation(other, mode = 0, intensity = 0))
                    }
            }
            add(writeOperation(function, mode, intensity))
        }

    private fun writeOperation(function: BeYourLoverV1Function, mode: Int, intensity: Int): BleProtocolOperation.Write {
        val state = function.safeState(mode, intensity)
        states[function.code] = state
        return BleProtocolOperation.Write(
            characteristicUuid = writeUuid,
            bytes = function.frame(state),
            withResponse = false,
        )
    }

    private fun BeYourLoverV1Function.safeState(mode: Int, intensity: Int): BeYourLoverV1FunctionState =
        BeYourLoverV1FunctionState(
            mode = mode.coerceIn(0, modeMax.coerceAtLeast(0)),
            intensity = intensity.coerceIn(0, intensityMax.coerceAtLeast(1)),
        )

    private fun stopAll(): List<BleProtocolOperation> =
        profile.functions.map { function ->
            BleProtocolOperation.Write(
                characteristicUuid = writeUuid,
                bytes = function.frame(BeYourLoverV1FunctionState()),
                withResponse = false,
            )
        } + profile.extraStopFrames.map { frame ->
            BleProtocolOperation.Write(writeUuid, frame, withResponse = false)
        }
}

private data class BeYourLoverV1Function(
    val code: Int,
    val type: String,
    val label: String,
    val intensityMax: Int,
    val modeMax: Int = 0,
    val frame: (BeYourLoverV1FunctionState) -> ByteArray,
)

private data class BeYourLoverV1FunctionState(
    val mode: Int = 0,
    val intensity: Int = 0,
) {
    fun isActive(): Boolean = mode > 0 || intensity > 0
}

private data class BeYourLoverV1Profile(
    val id: String,
    val displayName: String,
    val functions: List<BeYourLoverV1Function>,
    val extraStopFrames: List<ByteArray> = emptyList(),
    val exclusiveFunctionCodes: Set<Int> = emptySet(),
) {
    val defaultFunction: BeYourLoverV1Function = functions.first()
    val status: BleProtocolStatus = svakomV2Status(
        id = id,
        displayName = displayName,
        functions = functions.map {
            SvakomV2Function(
                code = it.code,
                type = it.type,
                label = it.label,
                command = 0,
                modeMax = it.modeMax,
                intensityMax = it.intensityMax,
            )
        },
    )

    fun functionByCode(code: Int): BeYourLoverV1Function =
        functionByCodeOrNull(code) ?: defaultFunction

    fun functionByCodeOrNull(code: Int): BeYourLoverV1Function? =
        functions.firstOrNull { it.code == code }
}

private val BE_YOUR_LOVER_V1_BX288A = BeYourLoverV1Profile(
    id = "beyourlover_bx288a",
    displayName = "礼物猫 BX288A",
    functions = listOf(
        BeYourLoverV1Function(1, "constrict", "吮吸", 9) { state ->
            val value = state.intensity
            v1Bytes(0x55, 0x03, 0x03, 0x00, if (value == 0) 0x00 else 0x01, value + 1)
        },
    ),
)

private val BE_YOUR_LOVER_V1_HX029A = BeYourLoverV1Profile(
    id = "beyourlover_hx029a_v1",
    displayName = "礼物猫 HX029A",
    functions = listOf(
        BeYourLoverV1Function(1, "constrict", "吮吸", 9) { state ->
            val value = state.intensity
            v1Bytes(0x55, 0x03, 0x03, 0x00, if (value == 0) 0x00 else 0x01, value + 1)
        },
    ),
)

private val BE_YOUR_LOVER_V1_HIBA = BeYourLoverV1Profile(
    id = "beyourlover_hiba_v1",
    displayName = "礼物猫 HiBa",
    functions = listOf(
        BeYourLoverV1Function(1, "constrict", "吮吸强度", intensityMax = 5) { state ->
            val value = state.intensity
            v1Bytes(0x55, 0x03, 0x03, 0x00, if (value == 0) 0x00 else 0x01, value + 1)
        },
        BeYourLoverV1Function(2, "constrict", "吮吸模式", intensityMax = 1, modeMax = 5) { state ->
            val mode = state.mode.takeIf { state.intensity > 0 } ?: 0
            v1Bytes(0x55, 0x03, 0x03, 0x00, if (mode == 0) 0x00 else 0x01, mode + 1)
        },
        BeYourLoverV1Function(3, "heat", "加热", intensityMax = 1, modeMax = 1) { state ->
            if (state.intensity > 0) {
                v1Bytes(0x55, 0x05, 0x01, 0x37, 0x00, 0x00)
            } else {
                v1Bytes(0x55, 0x05, 0x00, 0x00, 0x00, 0x00)
            }
        },
    ),
    exclusiveFunctionCodes = setOf(1, 2),
)

private val BE_YOUR_LOVER_V1_VX357B = BeYourLoverV1Profile(
    id = "beyourlover_vx357b_v1",
    displayName = "礼物猫 VX357B",
    functions = listOf(
        BeYourLoverV1Function(1, "constrict", "吮吸", 10) { state ->
            val value = state.intensity
            v1Bytes(0x55, 0x09, 0x00, 0x00, value, 0x00)
        },
        BeYourLoverV1Function(2, "vibrate", "震动", 30) { state ->
            val value = state.intensity
            var speed = value % 10
            var intensity = if (value == 0) 0 else value / 10 + 1
            if (speed == 0 && intensity != 0) {
                speed = 10
                intensity -= 1
            }
            v1Bytes(0x55, 0x03, 0x00, 0x00, intensity, speed)
        },
    ),
    extraStopFrames = listOf(v1Bytes(0x55, 0x03, 0x00, 0x00, 0x00, 0x00)),
)

private fun beYourLoverV1VibrateProfile(model: String) = BeYourLoverV1Profile(
    id = "beyourlover_${model.lowercase()}_v1",
    displayName = "礼物猫 $model",
    functions = listOf(
        BeYourLoverV1Function(1, "vibrate", "震动", 10) { state ->
            val value = state.intensity
            v1Bytes(0x55, 0x04, 0x00, 0x00, if (value == 0) 0x00 else 0x01, value * 25, 0xAA)
        },
    ),
    extraStopFrames = listOf(v1Bytes(0x55, 0x03, 0x00, 0x00, 0x00, 0x00)),
)

private val BE_YOUR_LOVER_V1_VIBRATE = beYourLoverV1VibrateProfile("V1")
private val BE_YOUR_LOVER_V1_VA422A = beYourLoverV1VibrateProfile("VA422A")
private val BE_YOUR_LOVER_V1_CT654A = beYourLoverV1VibrateProfile("CT654A")

private val BE_YOUR_LOVER_V1_VV468A = BeYourLoverV1Profile(
    id = "beyourlover_vv468a",
    displayName = "礼物猫 VV468A",
    functions = listOf(
        BeYourLoverV1Function(1, "vibrate", "震动", 10) { state ->
            val value = state.intensity
            v1Bytes(0x55, 0x04, 0x00, 0x00, 0x01, value * 25, 0xAA)
        },
    ),
    extraStopFrames = listOf(v1Bytes(0x55, 0x03, 0x00, 0x00, 0x00, 0x00)),
)

private fun BleGattFingerprint.beYourLoverV1Profile(): BeYourLoverV1Profile? {
    if (svakomProtocolVersion() == 0x02) return null
    val name = name.normalizedDeviceName()
    return when {
        name.contains("bx288a") -> BE_YOUR_LOVER_V1_BX288A
        name.contains("hiba") || name.contains("qhhx029ab") -> BE_YOUR_LOVER_V1_HIBA
        name.contains("hx029a") -> BE_YOUR_LOVER_V1_HX029A
        name.contains("vx357a") || name.contains("vx357b") || name.contains("vx357d") -> BE_YOUR_LOVER_V1_VX357B
        name.contains("va422a") -> BE_YOUR_LOVER_V1_VA422A
        name.contains("ct654a") -> BE_YOUR_LOVER_V1_CT654A
        name.contains("vv468a") -> BE_YOUR_LOVER_V1_VV468A
        else -> null
    }
}

private fun v1Bytes(vararg values: Int): ByteArray =
    values.map { it.coerceIn(0, 0xFF).toByte() }.toByteArray()

// 礼物猫 / BeYourLover：官方 ProductCodeMapper.BE_YOUR_LOVER_MAP 产品码表。
// 设备使用 SVA V2 广播前缀和 FFE0/FFE1 写入通道；分流只看官方产品码和功能表，不按中文商品名匹配。
internal object BeYourLoverProtocol : BleDeviceProtocol {
    private val serviceUuid = Uuid.parse("0000ffe0-0000-1000-8000-00805f9b34fb")
    private val writeUuid = Uuid.parse("0000ffe1-0000-1000-8000-00805f9b34fb")

    override val status = BE_YOUR_LOVER_VA617A_SUCK.status

    override fun matches(fingerprint: BleGattFingerprint): Boolean {
        val productCode = fingerprint.svakomProductCode()
        val hasWriteGatt = fingerprint.serviceUuids.contains(serviceUuid) &&
                fingerprint.characteristicUuids.contains(writeUuid)
        return productCode != null &&
                BeYourLoverProfiles.containsKey(productCode) &&
                hasWriteGatt &&
                fingerprint.hasSvakomManufacturerData()
    }

    override fun createInstance(fingerprint: BleGattFingerprint): BleDeviceProtocol =
        SvakomV2Session(BeYourLoverProfiles[fingerprint.svakomProductCode()] ?: BE_YOUR_LOVER_VA617A_SUCK)

    override fun commandsFor(action: ToyControlAction): List<BleProtocolOperation> = emptyList()
}

internal class SvakomV2Session(
    private val profile: SvakomV2Profile,
) : BleDeviceProtocol {
    private val writeUuid = Uuid.parse("0000ffe1-0000-1000-8000-00805f9b34fb")
    private val notifyUuid = Uuid.parse("0000ffe2-0000-1000-8000-00805f9b34fb")
    private val states = profile.functions.associate { it.code to SvakomV2FunctionState() }.toMutableMap()

    override val status: BleProtocolStatus = profile.status

    override fun matches(fingerprint: BleGattFingerprint): Boolean = false

    override fun initialize(fingerprint: BleGattFingerprint): List<BleProtocolOperation> {
        states.keys.forEach { states[it] = SvakomV2FunctionState() }
        return if (fingerprint.characteristicUuids.contains(notifyUuid)) {
            listOf(BleProtocolOperation.SubscribeNotify(notifyUuid))
        } else {
            emptyList()
        }
    }

    override fun keepaliveIntervalMs(): Long = 1_500L

    override fun commandsFor(action: ToyControlAction): List<BleProtocolOperation> =
        when (action) {
            is ToyControlAction.Pattern -> updateFunction(profile.defaultFunction.code, action.mode, currentIntensity(profile.defaultFunction.code))
            is ToyControlAction.Intensity -> updateFunction(profile.defaultFunction.code, currentMode(profile.defaultFunction.code), action.value)
            is ToyControlAction.Combined -> {
                val (functionCode, mode) = decodeFunctionMode(action.mode)
                updateFunction(functionCode, mode, action.intensity)
            }
            is ToyControlAction.DualMotor -> updateDualMotor(action)
            ToyControlAction.Stop -> stopAll()
        }

    private fun decodeFunctionMode(encodedMode: Int): Pair<Int, Int> {
        val functionCode = encodedMode / 100
        val mode = encodedMode % 100
        return if (profile.functions.any { it.code == functionCode }) {
            functionCode to mode
        } else {
            profile.defaultFunction.code to encodedMode
        }
    }

    private fun updateDualMotor(action: ToyControlAction.DualMotor): List<BleProtocolOperation> {
        profile.pairedDualControl?.let { control ->
            val internalFunction = profile.functionByType(control.internalType)
            val externalFunction = profile.functionByType(control.externalType)
            if (internalFunction != null && externalFunction != null) {
                updateState(internalFunction, action.mode, action.internalIntensity)
                updateState(externalFunction, action.mode, action.externalIntensity)
                // 这些设备的两个功能区需要分别下发；紧邻写入会让部分固件只响应第一帧。
                return listOf(
                    write(internalFunction.commandBytes(states.getValue(internalFunction.code))),
                    BleProtocolOperation.Sleep(control.interFrameDelayMs),
                    write(externalFunction.commandBytes(states.getValue(externalFunction.code))),
                )
            }
        }
        profile.functionByType("oscillate")?.let {
            updateState(it, action.mode, action.internalIntensity)
        }
        profile.functionByType("vibrate")?.let {
            updateState(it, 1, action.externalIntensity)
        }
        if (profile.functionByType("oscillate") == null && profile.functionByType("vibrate") == null) {
            updateState(profile.defaultFunction, action.mode, action.strongestIntensity(status.intensityMax))
        }
        return writeCurrentState()
    }

    private fun updateFunction(functionCode: Int, mode: Int, intensity: Int): List<BleProtocolOperation> {
        val function = profile.functions.firstOrNull { it.code == functionCode } ?: profile.defaultFunction
        updateState(function, mode, intensity)
        return when {
            profile.writeCurrentStateOnUpdate -> writeCurrentState()
            profile.replayActiveStateOnUpdate -> writeChangedAndActiveState(function)
            else -> listOf(write(function.commandBytes(states[function.code] ?: SvakomV2FunctionState())))
        }
    }

    private fun updateState(function: SvakomV2Function, mode: Int, intensity: Int) {
        states[function.code] = SvakomV2FunctionState(
            mode = mode.coerceIn(0, function.modeMax.coerceAtLeast(1)),
            intensity = intensity.coerceIn(0, function.intensityMax.coerceAtLeast(1)),
        )
    }

    private fun writeCurrentState(): List<BleProtocolOperation> =
        profile.functions.map { function ->
            write(function.commandBytes(states[function.code] ?: SvakomV2FunctionState()))
        }

    private fun writeChangedAndActiveState(changedFunction: SvakomV2Function): List<BleProtocolOperation> =
        profile.functions
            .filter { function ->
                function.code == changedFunction.code ||
                        (states[function.code]?.isActive(function) == true)
            }
            .map { function ->
                write(function.commandBytes(states[function.code] ?: SvakomV2FunctionState()))
            }

    private fun SvakomV2FunctionState.isActive(function: SvakomV2Function): Boolean =
        if (function.strengthDriven && mode > function.strengthDisabledThroughMode) {
            intensity > 0
        } else {
            mode > 0
        }

    private fun currentMode(functionCode: Int): Int =
        states[functionCode]?.mode ?: 1

    private fun currentIntensity(functionCode: Int): Int =
        states[functionCode]?.intensity ?: 0

    private fun SvakomV2Function.commandBytes(state: SvakomV2FunctionState): ByteArray {
        if (type == "heat") {
            return if (state.intensity > 0) {
                bytes(0x55, 0x05, 0x01, 0x37, 0x01, 0x00, 0x00)
            } else {
                bytes(0x55, 0x05, 0x00, 0x00, 0x00, 0x00, 0x00)
            }
        }
        // 强度型默认以强度判活；低档关闭强度滑杆的型号改为仅按模式判活，level 固定为 0。
        val usesStrength = strengthDriven && state.mode > strengthDisabledThroughMode
        val active = if (usesStrength) state.intensity > 0 else state.mode > 0
        val level = if (usesStrength && active) state.intensity else 0
        val mode = if (active) state.mode.coerceAtLeast(1) else 0
        return when (frameLayout) {
            SvakomV2FrameLayout.Standard -> bytes(0x55, command, head, 0x00, mode, level, 0x00)
            SvakomV2FrameLayout.Rotate -> bytes(0x55, command, mode, level, 0x00, 0x00, 0x00)
        }
    }

    private fun stopAll(): List<BleProtocolOperation> {
        states.keys.forEach { states[it] = SvakomV2FunctionState(mode = 0, intensity = 0) }
        return writeCurrentState() + write(bytes(0x55, 0x04, 0x00, 0x00, 0x00, 0x00, 0xaa))
    }

    private fun write(bytes: ByteArray): BleProtocolOperation.Write =
        BleProtocolOperation.Write(
            characteristicUuid = writeUuid,
            bytes = bytes,
            withResponse = false,
    )
}

internal class SvakomTl278bAppSession : BleDeviceProtocol {
    private val writeUuid = Uuid.parse("0000ffe1-0000-1000-8000-00805f9b34fb")
    private val notifyUuid = Uuid.parse("0000ffe2-0000-1000-8000-00805f9b34fb")
    private var stretchMode = 0
    private var vibrateMode = 0
    private var vibrateIntensity = 0
    private var suckMode = 0
    private var suckIntensity = 0

    override val status: BleProtocolStatus = svakomV2Status(
        id = "svakom_sl278_app_pair",
        displayName = "SVAKOM 分欣 App 版",
        functions = listOf(SvakomV2Stretch, SvakomV2Vibrate, SvakomV2Suck),
    )

    override fun matches(fingerprint: BleGattFingerprint): Boolean = false

    override fun initialize(fingerprint: BleGattFingerprint): List<BleProtocolOperation> {
        stretchMode = 0
        vibrateMode = 0
        vibrateIntensity = 0
        suckMode = 0
        suckIntensity = 0
        val operations = mutableListOf<BleProtocolOperation>()
        if (fingerprint.characteristicUuids.contains(notifyUuid)) {
            operations += BleProtocolOperation.SubscribeNotify(notifyUuid)
        }
        operations += write(bytes(0x55, 0x17))
        operations += BleProtocolOperation.Sleep(600L)
        operations += write(heatZoneCommand(zone = 1, enabled = false))
        operations += write(heatZoneCommand(zone = 2, enabled = false))
        return operations
    }

    override fun keepaliveIntervalMs(): Long = 1_500L

    override fun commandsFor(action: ToyControlAction): List<BleProtocolOperation> =
        when (action) {
            is ToyControlAction.Pattern -> updateVibrate(action.mode, vibrateIntensity.takeIf { it > 0 } ?: 1)
            is ToyControlAction.Intensity -> updateVibrate(vibrateMode.takeIf { it > 0 } ?: 1, action.value)
            is ToyControlAction.Combined -> {
                val functionCode = action.mode / 100
                val mode = action.mode % 100
                when (functionCode) {
                    SvakomV2Stretch.code -> updateStretch(mode)
                    SvakomV2Vibrate.code -> updateVibrate(mode, action.intensity)
                    SvakomV2Suck.code -> updateSuck(mode, action.intensity)
                    else -> updateVibrate(action.mode, action.intensity)
                }
            }
            is ToyControlAction.DualMotor -> {
                val mode = action.mode.coerceAtLeast(1)
                updateSuck(mode, action.internalIntensity) +
                    BleProtocolOperation.Sleep(500L) +
                    updateVibrate(mode, action.externalIntensity)
            }
            ToyControlAction.Stop -> stopAll()
        }

    private fun updateStretch(mode: Int): List<BleProtocolOperation> {
        stretchMode = mode.coerceIn(0, SvakomV2Stretch.modeMax)
        return listOf(write(stretchCommand(stretchMode)))
    }

    private fun updateVibrate(mode: Int, intensity: Int): List<BleProtocolOperation> {
        vibrateIntensity = intensity.coerceIn(0, SvakomV2Vibrate.intensityMax)
        vibrateMode = if (vibrateIntensity > 0) mode.coerceIn(1, SvakomV2Vibrate.modeMax) else 0
        return listOf(write(modeIntensityCommand(command = 0x03, mode = vibrateMode, intensity = vibrateIntensity)))
    }

    private fun updateSuck(mode: Int, intensity: Int): List<BleProtocolOperation> {
        suckIntensity = intensity.coerceIn(0, SvakomV2Suck.intensityMax)
        suckMode = if (suckIntensity > 0) mode.coerceIn(1, SvakomV2Suck.modeMax) else 0
        return listOf(write(modeIntensityCommand(command = 0x09, mode = suckMode, intensity = suckIntensity)))
    }

    private fun stopAll(): List<BleProtocolOperation> {
        stretchMode = 0
        vibrateMode = 0
        vibrateIntensity = 0
        suckMode = 0
        suckIntensity = 0
        return listOf(
            write(modeIntensityCommand(command = 0x03, mode = 0, intensity = 0)),
            write(stretchCommand(mode = 0)),
            write(modeIntensityCommand(command = 0x09, mode = 0, intensity = 0)),
            write(bytes(0x55, 0x04, 0x03, 0x00, 0x00, 0x00, 0xaa)),
            write(heatZoneCommand(zone = 1, enabled = false)),
            write(heatZoneCommand(zone = 2, enabled = false)),
        )
    }

    private fun stretchCommand(mode: Int): ByteArray =
        bytes(0x55, 0x08, 0x00, 0x00, mode.coerceIn(0, SvakomV2Stretch.modeMax), if (mode > 0) 0xff else 0x00)

    private fun modeIntensityCommand(command: Int, mode: Int, intensity: Int): ByteArray =
        bytes(0x55, command, 0x00, 0x00, mode, intensity)

    private fun heatZoneCommand(zone: Int, enabled: Boolean): ByteArray =
        if (enabled) {
            bytes(0x55, 0x05, 0x01, 0x37, zone, 0x00)
        } else {
            bytes(0x55, 0x05, 0x00, 0x00, zone, 0x00)
        }

    private fun write(bytes: ByteArray): BleProtocolOperation.Write =
        BleProtocolOperation.Write(
            characteristicUuid = writeUuid,
            bytes = bytes,
            withResponse = false,
        )
}

internal sealed class SvakomV2Profile(
    val status: BleProtocolStatus,
    val functions: List<SvakomV2Function>,
    val defaultFunction: SvakomV2Function = functions.first(),
    val writeCurrentStateOnUpdate: Boolean = true,
    val replayActiveStateOnUpdate: Boolean = false,
    val pairedDualControl: SvakomV2PairedDualControl? = null,
) {
    fun functionByType(type: String): SvakomV2Function? =
        functions.firstOrNull { it.type == type }
}

/** 成对功能区使用 `dual` 时的强度归属与写入间隔。 */
internal data class SvakomV2PairedDualControl(
    val internalType: String,
    val externalType: String,
    val interFrameDelayMs: Long = 500L,
)

internal data class SvakomV2Function(
    val code: Int,
    val type: String,
    val label: String,
    val command: Int,
    val modeMax: Int,
    val intensityMax: Int = 10,
    /**
     * 该功能是否由连续强度驱动。
     * 官方 SL278K 走 isSubProduct → OrderingViewTools → AutoV2ModeView 路径：
     * 伸缩(STRETCH)、拍打(FLAP）的 FunctionBean 没有 stretch/flap_strong_max，
     * 因此 isShowStrongSlider()=false，且该路径不 setDefaultStrong，defaultStrong 保持 0，
     * getModeBytes() 里强度字节 = defaultStrong = 0，帧形为 `55 CMD 00 00 <mode> 00 00`，只靠模式驱动、模式=0 即停止；
     * 震动(VIBRATE)、吮吸(SUCK）才 setShowStrongSlider(true)，强度走 0..10 滑杆值。
     * 无强度滑杆的功能 level 字节始终为 0，激活与否由“模式是否 > 0”决定，而不是强度。
     */
    val strengthDriven: Boolean = true,
    /** 某些型号在低档模式不显示强度滑杆，命令的强度字节必须固定为 0。 */
    val strengthDisabledThroughMode: Int = 0,
    val head: Int = 0,
    val frameLayout: SvakomV2FrameLayout = SvakomV2FrameLayout.Standard,
)

internal enum class SvakomV2FrameLayout {
    Standard,
    Rotate,
}

internal data class SvakomV2FunctionState(
    // 初始 mode=0 表示未启动：强度型看 intensity>0，模式型看 mode>0，两种模型下空闲都判定为停止。
    val mode: Int = 0,
    val intensity: Int = 0,
)

// 伸缩、拍打没有强度滑杆：官方固定发 0xFF，只用模式区分动作，intensityMax=1 表示只有开/关。
internal val SvakomV2Stretch = SvakomV2Function(1, "oscillate", "伸缩", 0x08, modeMax = 7, intensityMax = 1, strengthDriven = false)
internal val SvakomV2Vibrate = SvakomV2Function(2, "vibrate", "震动", 0x03, modeMax = 10)
internal val SvakomV2Vibrate11 = SvakomV2Vibrate.copy(modeMax = 11)
internal val SvakomV2Suck = SvakomV2Function(3, "constrict", "吮吸", 0x09, modeMax = 5)
internal val SvakomV2Suck8Level5 = SvakomV2Suck.copy(modeMax = 8, intensityMax = 5)
internal val SvakomV2Suck8Level10 = SvakomV2Suck.copy(modeMax = 8, intensityMax = 10)
// ST462A 官方 case 82 的吮吸：setSuck_num(3) 且未 setSuck_strong_max，没有强度滑杆，只有 3 档模式启停。
internal val SvakomV2Suck3Mode = SvakomV2Function(3, "constrict", "吮吸", 0x09, modeMax = 3, intensityMax = 1, strengthDriven = false)
internal val SvakomV2Flap = SvakomV2Function(4, "flap", "拍打", 0x07, modeMax = 7, intensityMax = 1, strengthDriven = false)
internal val SvakomV2Heat = SvakomV2Function(5, "heat", "加热", 0x05, modeMax = 1, intensityMax = 1, strengthDriven = false)
// 舔（LICKING）：官方 AutoV2ModeView case 5 命令字 0x14(=20)，帧仍是通用 55 CMD 00 00 <档> <强> 00。
// ST462A 官方 case 82 的舔为 setLicking_num(10) + setLicking_strong_max(10)，10 档 + 0..10 强度滑杆。
internal val SvakomV2Lick = SvakomV2Function(6, "lick", "舔", 0x14, modeMax = 10)
// HS141B 官方 case 38 把第二功能挂在 VIBRATION 命令族，并将标题显示为“舌舔”。
internal val SvakomV2TongueLick8 = SvakomV2Function(6, "lick", "舌舔", 0x03, modeMax = 8)
internal val SvakomV2Rotate = SvakomV2Function(7, "rotate", "旋转", 0x0d, modeMax = 5, frameLayout = SvakomV2FrameLayout.Rotate)
internal val SvakomV2Occlusion = SvakomV2Function(8, "occlusion", "收缩", 0x15, modeMax = 10)

private val svakomV2SuckVibrateDualControl = SvakomV2PairedDualControl(
    internalType = "constrict",
    externalType = "vibrate",
)

/** 多功能协议里加热功能的固定功能码。 */
const val SvakomV2HeatFunctionCode: Int = 5

internal val svakomV2FunctionsByType: Map<String, SvakomV2Function> =
    listOf(
        SvakomV2Stretch,
        SvakomV2Vibrate,
        SvakomV2Suck,
        SvakomV2Flap,
        SvakomV2Heat,
        SvakomV2Lick,
        SvakomV2Rotate,
        SvakomV2Occlusion,
    )
        .associateBy { it.type }

/** 按功能类型返回多功能协议的功能码；未知类型回退到伸缩。 */
fun svakomV2FunctionCode(type: String): Int =
    svakomV2FunctionsByType[type]?.code ?: SvakomV2Stretch.code

/** 按功能类型返回多功能协议的模式上限；未知类型回退到伸缩。 */
fun svakomV2FunctionModeMax(type: String): Int =
    svakomV2FunctionsByType[type]?.modeMax ?: SvakomV2Stretch.modeMax

internal fun svakomV2Status(
    id: String,
    displayName: String,
    functions: List<SvakomV2Function>,
): BleProtocolStatus =
    BleProtocolStatus(
        id = id,
        displayName = displayName,
        controllable = true,
        intensityMax = 10,
        supportsMode = true,
        modeMax = functions.filter { it.type != "heat" }.maxOfOrNull { it.modeMax } ?: 1,
        controlStyle = ToyControlStyle.IndependentFunctions,
        modeLabel = "模式",
        intensityLabel = "强度",
        channelNames = functions.filter { it.type != "heat" }.map { it.label },
        features = functions.mapIndexed { index, function ->
            BleProtocolFeature(
                type = function.type,
                min = 0,
                max = function.intensityMax,
                index = index,
                label = function.label,
                modeMax = function.modeMax,
                functionCode = function.code,
            )
        },
        automatic = true,
    )

internal object SVAKOM_V2_STRETCH_VIBRATE : SvakomV2Profile(
    status = svakomV2Status(
        id = "svakom_sl278_plus_v",
        displayName = "SVAKOM 分欣 Plus",
        functions = listOf(SvakomV2Stretch, SvakomV2Vibrate, SvakomV2Heat),
    ),
    functions = listOf(SvakomV2Stretch, SvakomV2Vibrate, SvakomV2Heat),
)

internal object SVAKOM_V2_SUCK : SvakomV2Profile(
    status = svakomV2Status(
        id = "svakom_sl278_plus_s",
        displayName = "SVAKOM 分欣 Plus",
        functions = listOf(SvakomV2Suck, SvakomV2Heat),
    ),
    functions = listOf(SvakomV2Suck, SvakomV2Heat),
)

internal object SVAKOM_V2_SL278B_SUCK : SvakomV2Profile(
    status = svakomV2Status(
        id = "svakom_sl278_ai_s",
        displayName = "SVAKOM 分欣(AI 版)",
        functions = listOf(SvakomV2Suck, SvakomV2Heat),
    ),
    functions = listOf(SvakomV2Suck, SvakomV2Heat),
)

internal object SVAKOM_V2_SL278B_PAIR : SvakomV2Profile(
    status = svakomV2Status(
        id = "svakom_sl278_ai_pair",
        displayName = "SVAKOM 分欣(AI 版)",
        functions = listOf(SvakomV2Stretch, SvakomV2Vibrate, SvakomV2Suck, SvakomV2Heat),
    ),
    functions = listOf(SvakomV2Stretch, SvakomV2Vibrate, SvakomV2Suck, SvakomV2Heat),
    writeCurrentStateOnUpdate = false,
)

internal object SVAKOM_V2_SL278_APP_PAIR : SvakomV2Profile(
    status = svakomV2Status(
        id = "svakom_sl278_app_pair",
        displayName = "SVAKOM 分欣 App 版",
        functions = listOf(SvakomV2Stretch, SvakomV2Vibrate, SvakomV2Suck, SvakomV2Heat),
    ),
    functions = listOf(SvakomV2Stretch, SvakomV2Vibrate, SvakomV2Suck, SvakomV2Heat),
    writeCurrentStateOnUpdate = false,
)

internal object SVAKOM_V2_SL278K_PAIR : SvakomV2Profile(
    status = svakomV2Status(
        id = "svakom_sl278_plus_pair",
        displayName = "SVAKOM 分欣 Plus",
        functions = listOf(SvakomV2Stretch, SvakomV2Vibrate, SvakomV2Suck, SvakomV2Heat),
    ),
    functions = listOf(SvakomV2Stretch, SvakomV2Vibrate, SvakomV2Suck, SvakomV2Heat),
)

internal object SVAKOM_V2_FLAP : SvakomV2Profile(
    status = svakomV2Status(
        id = "svakom_sl278_plus_f",
        displayName = "SVAKOM 分欣 Plus",
        functions = listOf(SvakomV2Flap, SvakomV2Heat),
    ),
    functions = listOf(SvakomV2Flap, SvakomV2Heat),
)

// 震动 + 吮吸双功能区通用 profile：震动 10 档、吮吸 5 档，各自独立命名和强度滑杆，不绑定单一型号。
// QH-SX007E（Svakom Alberta）首个接入，后续同类司康沃设备可复用此 profile。
// 官方 AutoV2ModeView 每个功能是独立 View，selectModeIndex 只发当前功能单帧；
// 因此这里 writeCurrentStateOnUpdate=false：操作哪个功能就只写该功能帧，
// 避免每次都把震动+吮吸两帧背靠背连发导致设备只响应第一条、之后卡死；`dual` 另行插入间隔。
internal object SVAKOM_V2_VIBRATE_SUCK : SvakomV2Profile(
    status = svakomV2Status(
        id = "svakom_vibrate_suck",
        displayName = "SVAKOM 震动吮吸",
        functions = listOf(SvakomV2Vibrate, SvakomV2Suck),
    ),
    functions = listOf(SvakomV2Vibrate, SvakomV2Suck),
    writeCurrentStateOnUpdate = false,
    pairedDualControl = svakomV2SuckVibrateDualControl,
)

// SX119B（波波鸟）：官方 case 45，八档吮吸（强度 0..5）+ 十一档震动（强度 0..10）。
internal object SVAKOM_V2_SX119B : SvakomV2Profile(
    status = svakomV2Status(
        id = "svakom_sx119b",
        displayName = "SVAKOM SX119B",
        functions = listOf(SvakomV2Suck8Level5, SvakomV2Vibrate11),
    ),
    functions = listOf(SvakomV2Suck8Level5, SvakomV2Vibrate11),
    defaultFunction = SvakomV2Suck8Level5,
    writeCurrentStateOnUpdate = false,
    pairedDualControl = svakomV2SuckVibrateDualControl,
)

// SX589A（青提）：官方 case 46 与 case 45（SX119B）fall-through 共用同一功能表，
// 八档吮吸（强度 0..5）+ 十一档震动（强度 0..10）；产品码 59，独立 id 便于溯源与状态区分。
internal object SVAKOM_V2_SX589A : SvakomV2Profile(
    status = svakomV2Status(
        id = "svakom_sx589a",
        displayName = "SVAKOM SX589A",
        functions = listOf(SvakomV2Suck8Level5, SvakomV2Vibrate11),
    ),
    functions = listOf(SvakomV2Suck8Level5, SvakomV2Vibrate11),
    defaultFunction = SvakomV2Suck8Level5,
    writeCurrentStateOnUpdate = false,
    pairedDualControl = svakomV2SuckVibrateDualControl,
)

// ST462A（口口甜）：官方 case 82，三功能区——舔（10 档 + 强度 0..10）、吮吸（3 档无强度）、震动（10 档 + 强度 0..10）。
// 官方每个功能是独立 View、单帧下发，故 writeCurrentStateOnUpdate=false：操作哪个功能就只写该功能帧。
internal object SVAKOM_V2_ST462A : SvakomV2Profile(
    status = svakomV2Status(
        id = "svakom_st462a",
        displayName = "SVAKOM ST462A",
        functions = listOf(SvakomV2Lick, SvakomV2Suck3Mode, SvakomV2Vibrate),
    ),
    functions = listOf(SvakomV2Lick, SvakomV2Suck3Mode, SvakomV2Vibrate),
    defaultFunction = SvakomV2Lick,
    writeCurrentStateOnUpdate = false,
)

// HS141B（啾啾雀）：官方 ProductCodeMapper 产品码 30，AllSupportFunctionBeanTools case 38：
// 吮吸 8 档强度 0..10；“舌舔”标题复用 VIBRATION 命令族，8 档强度 0..10，帧命令字仍为 0x03。
internal object SVAKOM_V2_HS141B : SvakomV2Profile(
    status = svakomV2Status(
        id = "svakom_hs141b",
        displayName = "SVAKOM HS141B",
        functions = listOf(SvakomV2Suck8Level10, SvakomV2TongueLick8),
    ),
    functions = listOf(SvakomV2Suck8Level10, SvakomV2TongueLick8),
    defaultFunction = SvakomV2Suck8Level10,
    writeCurrentStateOnUpdate = false,
)

private val BeYourLoverVa617aSuck =
    SvakomV2Suck.copy(modeMax = 8, intensityMax = 10)

private val BeYourLoverVa617aVibrate =
    SvakomV2Vibrate.copy(modeMax = 9, intensityMax = 10)

internal object BE_YOUR_LOVER_VA617A_SUCK : SvakomV2Profile(
    status = svakomV2Status(
        id = "beyourlover_va617a_suck",
        displayName = "礼物猫大人铃",
        functions = listOf(BeYourLoverVa617aSuck),
    ),
    functions = listOf(BeYourLoverVa617aSuck),
    defaultFunction = BeYourLoverVa617aSuck,
    writeCurrentStateOnUpdate = false,
)

internal object BE_YOUR_LOVER_VA617A_VIBRATE : SvakomV2Profile(
    status = svakomV2Status(
        id = "beyourlover_va617a_vibrate",
        displayName = "礼物猫大人铃",
        functions = listOf(BeYourLoverVa617aVibrate),
    ),
    functions = listOf(BeYourLoverVa617aVibrate),
    defaultFunction = BeYourLoverVa617aVibrate,
    writeCurrentStateOnUpdate = false,
)

private class StaticSvakomV2Profile(
    status: BleProtocolStatus,
    functions: List<SvakomV2Function>,
    defaultFunction: SvakomV2Function = functions.first(),
    writeCurrentStateOnUpdate: Boolean = false,
    replayActiveStateOnUpdate: Boolean = false,
    pairedDualControl: SvakomV2PairedDualControl? = null,
) : SvakomV2Profile(
    status = status,
    functions = functions,
    defaultFunction = defaultFunction,
    writeCurrentStateOnUpdate = writeCurrentStateOnUpdate,
    replayActiveStateOnUpdate = replayActiveStateOnUpdate,
    pairedDualControl = pairedDualControl,
)

private data class BeYourLoverFunctionSpec(
    val type: String,
    val label: String,
    val command: Int,
    val modeMax: Int,
    val intensityMax: Int?,
    val head: Int = 0,
    val frameLayout: SvakomV2FrameLayout = SvakomV2FrameLayout.Standard,
)

private fun BeYourLoverFunctionSpec.toFunction(code: Int, labelOverride: String? = null, headOverride: Int? = null): SvakomV2Function =
    SvakomV2Function(
        code = code,
        type = type,
        label = labelOverride ?: label,
        command = command,
        modeMax = modeMax,
        intensityMax = intensityMax ?: 1,
        head = headOverride ?: head,
        frameLayout = frameLayout,
        strengthDriven = intensityMax != null,
    )

private fun bylSuck(mode: Int, strength: Int?) =
    BeYourLoverFunctionSpec("constrict", "吮吸", 0x09, mode, strength)

private fun bylVibrate(mode: Int, strength: Int?, head: Int = 0, label: String = "震动") =
    BeYourLoverFunctionSpec("vibrate", label, 0x03, mode, strength, head = head)

private fun bylHeat() =
    BeYourLoverFunctionSpec("heat", "加热", 0x05, modeMax = 1, intensityMax = null)

private fun bylDoubleVibrate(mode: Int, strength: Int?) =
    listOf(
        bylVibrate(mode, strength, head = 1, label = "震动 1"),
        bylVibrate(mode, strength, head = 2, label = "震动 2"),
    )

private fun bylStretch(mode: Int, strength: Int?) =
    BeYourLoverFunctionSpec("oscillate", "伸缩", 0x08, mode, strength)

private fun bylStretchWithVibrateCommand(mode: Int, strength: Int?) =
    BeYourLoverFunctionSpec("oscillate", "伸缩", 0x03, mode, strength)

private fun bylFlap(mode: Int, strength: Int?) =
    BeYourLoverFunctionSpec("flap", "拍打", 0x07, mode, strength)

private fun bylRotate(mode: Int, strength: Int?) =
    BeYourLoverFunctionSpec("rotate", "旋转", 0x0d, mode, strength, frameLayout = SvakomV2FrameLayout.Rotate)

private fun bylLick(mode: Int, strength: Int?) =
    BeYourLoverFunctionSpec("lick", "舔", 0x14, mode, strength)

private fun bylOcclusion(mode: Int, strength: Int?) =
    BeYourLoverFunctionSpec("occlusion", "收缩", 0x15, mode, strength)

private fun beYourLoverProfile(
    model: String,
    vararg specs: BeYourLoverFunctionSpec,
    replayActiveStateOnUpdate: Boolean = false,
): SvakomV2Profile {
    var vibrateOrdinal = 0
    val vibrateCount = specs.count { it.type == "vibrate" }
    val functions = specs.mapIndexed { index, spec ->
        val headOverride: Int?
        val labelOverride: String?
        if (spec.type == "vibrate" && vibrateCount > 1) {
            vibrateOrdinal += 1
            headOverride = spec.head.takeIf { it > 0 } ?: vibrateOrdinal
            labelOverride = spec.label.takeUnless { it == "震动" } ?: "震动 $vibrateOrdinal"
        } else {
            headOverride = null
            labelOverride = null
        }
        spec.toFunction(code = index + 1, labelOverride = labelOverride, headOverride = headOverride)
    }
    return StaticSvakomV2Profile(
        status = svakomV2Status(
            id = "beyourlover_${model.lowercase()}",
            displayName = "礼物猫 $model",
            functions = functions,
        ),
        functions = functions,
        defaultFunction = functions.first(),
        replayActiveStateOnUpdate = replayActiveStateOnUpdate,
        pairedDualControl = if (functions.any { it.type == "constrict" } && functions.any { it.type == "vibrate" }) {
            svakomV2SuckVibrateDualControl
        } else {
            null
        },
    )
}

private fun beYourLoverProfile(model: String, specs: List<BeYourLoverFunctionSpec>): SvakomV2Profile =
    beYourLoverProfile(model, *specs.toTypedArray())

private fun beYourLoverDoubleVibrateProfile(model: String, mode: Int, strength: Int?): SvakomV2Profile =
    beYourLoverProfile(
        model,
        *bylDoubleVibrate(mode, strength).toTypedArray(),
        replayActiveStateOnUpdate = true,
    )

internal val BeYourLoverProfiles: Map<Int, SvakomV2Profile> = mapOf(
    26 to beYourLoverProfile("CX492B", bylSuck(5, 10), bylVibrate(5, 5)),
    50 to beYourLoverProfile("SA251B_S", bylSuck(8, 10)),
    51 to beYourLoverProfile("SA251B_V", bylVibrate(9, 10)),
    58 to beYourLoverProfile("CA594B", bylFlap(5, 10), bylVibrate(11, 10)),
    62 to BE_YOUR_LOVER_VA617A_VIBRATE,
    63 to BE_YOUR_LOVER_VA617A_SUCK,
    65 to beYourLoverProfile("MW35", bylSuck(5, null), bylVibrate(5, 10)),
    72 to beYourLoverProfile("TX640A", bylSuck(6, null), bylVibrate(10, null), bylFlap(5, null), bylHeat()),
    78 to beYourLoverProfile("TX752B", bylSuck(10, 5), bylVibrate(10, null)),
    83 to beYourLoverProfile("ST629B", bylRotate(5, 10), bylVibrate(11, 10)),
    86 to beYourLoverDoubleVibrateProfile("VX737B", 12, 10),
    320 to beYourLoverProfile("VX737B", *(bylDoubleVibrate(12, 10) + bylHeat()).toTypedArray(), replayActiveStateOnUpdate = true),
    91 to beYourLoverProfile("CL279B_V", bylStretch(10, null), bylVibrate(10, 10), bylHeat()),
    92 to beYourLoverProfile("CL279B_S", bylSuck(10, 10), bylHeat()),
    95 to beYourLoverProfile("MW36B", bylStretch(8, 5), bylVibrate(11, 10)),
    96 to beYourLoverProfile("MW36A", bylSuck(8, null), bylVibrate(11, 10)),
    97 to beYourLoverProfile("VP753A", bylStretch(5, null), bylVibrate(8, null), bylHeat()),
    103 to beYourLoverProfile("SF030C", bylRotate(5, 10), bylSuck(10, null)),
    105 to beYourLoverProfile("QL169C_V", bylStretch(7, null), bylVibrate(10, 10), bylHeat()),
    106 to beYourLoverProfile("QL169C_F", bylFlap(7, null), bylHeat()),
    118 to beYourLoverProfile("VX736A", listOf(bylOcclusion(10, 10), bylSuck(3, 10)) + bylDoubleVibrate(10, 10)),
    266 to beYourLoverProfile("VX586A", bylSuck(5, 5)),
    267 to beYourLoverProfile("VX688A_S", bylSuck(5, 5), bylHeat()),
    268 to beYourLoverProfile("VX586D", bylSuck(8, null), bylHeat()),
    274 to beYourLoverProfile("TL278B_V", bylStretch(7, null), bylVibrate(10, 10), bylHeat()),
    275 to beYourLoverProfile("TL278B_S", bylSuck(5, 10), bylHeat()),
    309 to beYourLoverProfile("TL278J", bylSuck(5, 10), bylHeat()),
    314 to beYourLoverProfile("VX719A", bylSuck(10, 10), bylStretch(6, 10), bylVibrate(10, 10), bylHeat()),
    316 to beYourLoverProfile("CA755B_V", bylVibrate(8, 10), bylVibrate(8, 10), bylHeat()),
    317 to beYourLoverProfile("CA755B_V_2", bylVibrate(8, 10)),
    323 to beYourLoverProfile("S106C", bylSuck(6, null), bylFlap(6, null), bylStretch(6, null)),
    335 to beYourLoverProfile("VX723A", bylLick(10, 10), bylSuck(3, null), bylVibrate(10, 10), bylHeat()),
    337 to beYourLoverProfile("VP757A", bylStretch(6, null), bylVibrate(8, null), bylHeat()),
    338 to beYourLoverProfile("MW655A", bylSuck(8, null), bylVibrate(11, 10)),
    339 to beYourLoverProfile("MW520A", bylSuck(8, null), bylVibrate(11, 10)),
    340 to beYourLoverProfile("MW820A", bylSuck(8, null), bylVibrate(11, 10)),
    341 to beYourLoverProfile("MW655B", bylStretch(8, 5), bylVibrate(11, 10)),
    342 to beYourLoverProfile("MW520B", bylStretch(8, 5), bylVibrate(11, 10)),
    343 to beYourLoverProfile("MW820B", bylStretch(8, 5), bylVibrate(11, 10)),
    356 to beYourLoverProfile("VX688A_V", bylVibrate(5, 5)),
    361 to beYourLoverProfile("HX029A", bylSuck(10, 10), bylHeat()),
    362 to beYourLoverProfile("TX218A", bylSuck(7, 10), bylVibrate(6, 10), bylHeat()),
    363 to beYourLoverProfile("VF721A", bylSuck(5, null), bylStretch(5, null), bylVibrate(8, null)),
    367 to beYourLoverProfile("SG02A", bylVibrate(10, 10)),
    374 to beYourLoverProfile("VX219A", bylVibrate(10, 10)),
    376 to beYourLoverProfile("VX586C", bylSuck(8, null), bylHeat()),
    378 to beYourLoverProfile("VX760A_V", bylVibrate(10, null)),
    379 to beYourLoverProfile("VX760A_S", bylSuck(10, null)),
    380 to beYourLoverProfile("VA733B", bylStretch(10, 10), bylVibrate(10, 10)),
    381 to beYourLoverProfile("VP625B", bylStretch(5, 10), bylVibrate(10, 10), bylHeat()),
    382 to beYourLoverProfile("VX737C", *(bylDoubleVibrate(12, 10) + bylHeat()).toTypedArray(), replayActiveStateOnUpdate = true),
    383 to beYourLoverProfile("S137P", bylVibrate(10, 10), bylVibrate(10, 10)),
    392 to beYourLoverProfile("VX607P", bylSuck(10, 10), bylStretchWithVibrateCommand(10, 10)),
)

internal fun Int?.svakomV2Profile(): SvakomV2Profile =
    when (this) {
        128 -> SVAKOM_V2_SL278K_PAIR
        129 -> SVAKOM_V2_SUCK
        108 -> SVAKOM_V2_SL278B_PAIR
        107 -> SVAKOM_V2_SL278B_SUCK
        240 -> SVAKOM_V2_SL278_APP_PAIR
        101, 111, 145, 328 -> SVAKOM_V2_FLAP
        121 -> SVAKOM_V2_SUCK
        else -> SVAKOM_V2_STRETCH_VIBRATE
    }

internal object SvakomSt419Protocol : BleDeviceProtocol {
    private val serviceUuid = Uuid.parse("0000ffe0-0000-1000-8000-00805f9b34fb")
    private val writeUuid = Uuid.parse("0000ffe1-0000-1000-8000-00805f9b34fb")
    private val notifyUuid = Uuid.parse("0000ffe2-0000-1000-8000-00805f9b34fb")

    override val status = BleProtocolStatus(
        id = "svakom_st419",
        displayName = "SVAKOM ST419",
        controllable = true,
        intensityMax = 10,
        supportsMode = true,
        modeMax = 11,
        controlStyle = ToyControlStyle.CombinedPatternAndIntensity,
        modeLabel = "节奏",
        intensityLabel = "强度",
        features = listOf(
            BleProtocolFeature(
                type = "vibrate",
                min = 0,
                max = 10,
                index = 0,
                label = "强度",
            ),
        ),
        automatic = true,
    )

    override fun matches(fingerprint: BleGattFingerprint): Boolean {
        val name = fingerprint.name.normalizedDeviceName()
        val hasSvakomGatt = fingerprint.serviceUuids.contains(serviceUuid) &&
                fingerprint.characteristicUuids.contains(writeUuid) &&
                fingerprint.characteristicUuids.contains(notifyUuid)
        val isSt419 = name == "st419" || name.contains("st419")
        return isSt419 && hasSvakomGatt && fingerprint.hasSvakomManufacturerData()
    }

    override fun initialize(fingerprint: BleGattFingerprint): List<BleProtocolOperation> =
        listOf(
            BleProtocolOperation.SubscribeNotify(notifyUuid),
            BleProtocolOperation.Sleep(240),
            command(mode = 0, intensity = 0),
        )

    override fun commandsFor(action: ToyControlAction): List<BleProtocolOperation> =
        when (action) {
            is ToyControlAction.Pattern -> listOf(command(action.mode, status.intensityMax))
            is ToyControlAction.Intensity -> listOf(command(mode = 1, intensity = action.value))
            is ToyControlAction.Combined -> listOf(command(action.mode, action.intensity))
            is ToyControlAction.DualMotor -> listOf(command(action.mode, action.strongestIntensity(status.intensityMax)))
            ToyControlAction.Stop -> listOf(command(mode = 0, intensity = 0))
        }

    private fun command(mode: Int, intensity: Int) = BleProtocolOperation.Write(
        characteristicUuid = writeUuid,
        bytes = bytes(
            0x55,
            0x03,
            0x00,
            0x00,
            mode.coerceIn(0, status.modeMax),
            intensity.coerceIn(0, status.intensityMax),
        ),
        withResponse = false,
    )
}

