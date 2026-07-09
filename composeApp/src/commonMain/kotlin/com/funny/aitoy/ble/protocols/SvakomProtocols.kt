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
                SL278_PLUS_NAMES.any { name.contains(it) }
        return isSl278h && hasSvakomGatt && fingerprint.hasSvakomManufacturerData()
    }

    override fun createInstance(fingerprint: BleGattFingerprint): BleDeviceProtocol =
        SvakomV2Session(fingerprint.svakomProductCode().svakomV2Profile())

    override fun commandsFor(action: ToyControlAction): List<BleProtocolOperation> = emptyList()

    private val SL278_PLUS_PRODUCT_CODES = setOf(100, 101, 107, 108, 110, 111, 121, 128, 129, 145, 328, 329)
    private val SL278_PLUS_NAMES = listOf("sl278b", "sl278f", "sl278h", "sl278i", "sl278j", "sl278k", "sl278u")
}

// 司康沃「震动 + 吮吸」双功能区设备。
// QH-SX007E / Svakom Alberta：十档震动（强度可调）+ 五档吮吸，走通用 SVAKOM V2 GATT。
// SX119B：官方产品码 57，八档吮吸（强度 0..5）+ 十一档震动（强度 0..10）。
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
            normalizedName.contains("qhsx007e") -> SVAKOM_V2_VIBRATE_SUCK
            else -> null
        }
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
        return listOf(BleProtocolOperation.SubscribeNotify(notifyUuid))
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
        // 强度型(震动/吮吸)：强度>0 才算激活，level 走 0..10 滑杆值。
        // 模式型(伸缩/拍打)：官方 level 恒为 0，激活与否由“模式>0”决定，强度字节不参与。
        val active = if (strengthDriven) state.intensity > 0 else state.mode > 0
        val level = if (strengthDriven && active) state.intensity else 0
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

internal sealed class SvakomV2Profile(
    val status: BleProtocolStatus,
    val functions: List<SvakomV2Function>,
    val defaultFunction: SvakomV2Function = functions.first(),
    val writeCurrentStateOnUpdate: Boolean = true,
) {
    fun functionByType(type: String): SvakomV2Function? =
        functions.firstOrNull { it.type == type }
}

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
     * 注意：level 字节始终为 0，激活与否由“模式是否 > 0”决定，而不是强度。
     */
    val strengthDriven: Boolean = true,
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
internal val SvakomV2Flap = SvakomV2Function(4, "flap", "拍打", 0x07, modeMax = 7, intensityMax = 1, strengthDriven = false)
internal val SvakomV2Heat = SvakomV2Function(5, "heat", "加热", 0x05, modeMax = 1, intensityMax = 1, strengthDriven = false)

/** 多功能协议里加热功能的固定功能码。 */
const val SvakomV2HeatFunctionCode: Int = 5

internal val svakomV2FunctionsByType: Map<String, SvakomV2Function> =
    listOf(SvakomV2Stretch, SvakomV2Vibrate, SvakomV2Suck, SvakomV2Flap, SvakomV2Heat)
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
// 避免每次都把震动+吮吸两帧背靠背连发导致设备只响应第一条、之后卡死。
internal object SVAKOM_V2_VIBRATE_SUCK : SvakomV2Profile(
    status = svakomV2Status(
        id = "svakom_vibrate_suck",
        displayName = "SVAKOM 震动吮吸",
        functions = listOf(SvakomV2Vibrate, SvakomV2Suck),
    ),
    functions = listOf(SvakomV2Vibrate, SvakomV2Suck),
    writeCurrentStateOnUpdate = false,
)

internal object SVAKOM_V2_SX119B : SvakomV2Profile(
    status = svakomV2Status(
        id = "svakom_sx119b",
        displayName = "SVAKOM SX119B",
        functions = listOf(SvakomV2Suck8Level5, SvakomV2Vibrate11),
    ),
    functions = listOf(SvakomV2Suck8Level5, SvakomV2Vibrate11),
    defaultFunction = SvakomV2Suck8Level5,
    writeCurrentStateOnUpdate = false,
)

internal fun Int?.svakomV2Profile(): SvakomV2Profile =
    when (this) {
        128 -> SVAKOM_V2_SL278K_PAIR
        129 -> SVAKOM_V2_SUCK
        108 -> SVAKOM_V2_SL278B_PAIR
        107 -> SVAKOM_V2_SL278B_SUCK
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

