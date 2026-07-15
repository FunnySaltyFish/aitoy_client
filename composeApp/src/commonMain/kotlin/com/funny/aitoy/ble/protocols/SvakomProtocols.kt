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
            normalizedName.contains("qhsx007e") -> SVAKOM_V2_VIBRATE_SUCK
            else -> null
        }
}

// 礼物猫 / BeYourLover 大人铃：官方产品码 62=VA617A_V（震动端）、63=VA617A_S（吮吸端）。
// 这两个端点使用 SVA V2 广播前缀和 FFE0/FFE1 写入通道，但名称可能在 VA617A-* / 大人铃之间变化。
internal object BeYourLoverVa617aProtocol : BleDeviceProtocol {
    private val serviceUuid = Uuid.parse("0000ffe0-0000-1000-8000-00805f9b34fb")
    private val writeUuid = Uuid.parse("0000ffe1-0000-1000-8000-00805f9b34fb")

    override val status = BE_YOUR_LOVER_VA617A_SUCK.status

    override fun matches(fingerprint: BleGattFingerprint): Boolean {
        val productCode = fingerprint.svakomProductCode()
        val hasWriteGatt = fingerprint.serviceUuids.contains(serviceUuid) &&
                fingerprint.characteristicUuids.contains(writeUuid)
        return productCode in setOf(62, 63) &&
                hasWriteGatt &&
                fingerprint.hasSvakomManufacturerData()
    }

    override fun createInstance(fingerprint: BleGattFingerprint): BleDeviceProtocol =
        SvakomV2Session(
            when (fingerprint.svakomProductCode()) {
                62 -> BE_YOUR_LOVER_VA617A_VIBRATE
                else -> BE_YOUR_LOVER_VA617A_SUCK
            }
        )

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
        return if (profile.writeCurrentStateOnUpdate) writeCurrentState() else listOf(write(function.commandBytes(states[function.code] ?: SvakomV2FunctionState())))
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
        return bytes(
            0x55,
            command,
            0x00,
            0x00,
            if (active) state.mode.coerceAtLeast(1) else 0,
            level,
            0x00,
        )
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
)

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
// ST462A 官方 case 82 的吮吸：setSuck_num(3) 且未 setSuck_strong_max，没有强度滑杆，只有 3 档模式启停。
internal val SvakomV2Suck3Mode = SvakomV2Function(3, "constrict", "吮吸", 0x09, modeMax = 3, intensityMax = 1, strengthDriven = false)
internal val SvakomV2Flap = SvakomV2Function(4, "flap", "拍打", 0x07, modeMax = 7, intensityMax = 1, strengthDriven = false)
internal val SvakomV2Heat = SvakomV2Function(5, "heat", "加热", 0x05, modeMax = 1, intensityMax = 1, strengthDriven = false)
// 舔（LICKING）：官方 AutoV2ModeView case 5 命令字 0x14(=20)，帧仍是通用 55 CMD 00 00 <档> <强> 00。
// ST462A 官方 case 82 的舔为 setLicking_num(10) + setLicking_strong_max(10)，10 档 + 0..10 强度滑杆。
internal val SvakomV2Lick = SvakomV2Function(6, "lick", "舔", 0x14, modeMax = 10)

private val svakomV2SuckVibrateDualControl = SvakomV2PairedDualControl(
    internalType = "constrict",
    externalType = "vibrate",
)

/** 多功能协议里加热功能的固定功能码。 */
const val SvakomV2HeatFunctionCode: Int = 5

internal val svakomV2FunctionsByType: Map<String, SvakomV2Function> =
    listOf(SvakomV2Stretch, SvakomV2Vibrate, SvakomV2Suck, SvakomV2Flap, SvakomV2Heat, SvakomV2Lick)
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

