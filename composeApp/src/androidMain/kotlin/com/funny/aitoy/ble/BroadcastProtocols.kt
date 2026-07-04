package com.funny.aitoy.ble

import com.funny.aitoy.core.prefs.DataSaverUtils
import kotlin.random.Random

internal data class BleAdvertiseOperation(
    val serviceUuid: String = "",
    val serviceDataUuid: String = "",
    val serviceData: ByteArray = byteArrayOf(),
    val manufacturerId: Int? = null,
    val manufacturerData: ByteArray = byteArrayOf(),
)

internal interface BleBroadcastProtocol {
    val status: BleProtocolStatus
    val preferGattWhenConnectable: Boolean get() = false

    fun matches(device: ScannedBleDevice): Boolean

    fun commandsFor(action: ToyControlAction): List<BleAdvertiseOperation>
}

internal object BleBroadcastProtocolRegistry {
    private val cachitoTemplateProtocols = listOf(
        CachitoTemplateBroadcastProtocol(
            CachitoTemplateBroadcastSpec(
                id = "cachito_touhuan_pro_advertise",
                displayName = "Cachito 套环 Pro",
                aliases = listOf("套环pro", "touhuanpro"),
                framePrefixes = listOf("71000C"),
                modeNames = listOf("强度"),
                commandTemplates = listOf({ progress -> cachito8200Template("71000C", progress) }),
                stopTemplates = listOf("71000C**-0F00-####-0100-3211643202"),
            )
        ),
        CachitoTemplateBroadcastProtocol(
            CachitoTemplateBroadcastSpec(
                id = "cachito_touhuan_mini_advertise",
                displayName = "Cachito 套环 Mini",
                aliases = listOf("套环mini", "touhuanmini"),
                framePrefixes = listOf("71000A"),
                modeNames = listOf("强度"),
                commandTemplates = listOf({ progress -> cachito8200Template("71000A", progress) }),
                stopTemplates = listOf("71000A**-0F00-####-0100-3211643202"),
            )
        ),
        CachitoTemplateBroadcastProtocol(
            CachitoTemplateBroadcastSpec(
                id = "cachito_dianchaobi_advertise",
                displayName = "Cachito 点潮笔",
                aliases = listOf("点潮笔", "dianchaobi"),
                framePrefixes = listOf("710009"),
                modeNames = listOf("强度"),
                commandTemplates = listOf({ progress -> cachito8200Template("710009", progress) }),
                stopTemplates = listOf("710009**-0F00-####-0100-3211643202"),
            )
        ),
        CachitoTemplateBroadcastProtocol(
            CachitoTemplateBroadcastSpec(
                id = "cachito_maozhua2_advertise",
                displayName = "Cachito 猫爪 2",
                aliases = listOf("猫爪2", "maozhua2"),
                framePrefixes = listOf("710001"),
                modeNames = listOf("强度"),
                commandTemplates = listOf({ progress -> cachitoSuctionTemplate("710001", progress, command = "050A") }),
                stopTemplates = listOf("710001**-0400-####-0601-0200000000"),
            )
        ),
        CachitoTemplateBroadcastProtocol(
            CachitoTemplateBroadcastSpec(
                id = "cachito_touhuan_xuegao_advertise",
                displayName = "Cachito 套环雪糕",
                aliases = listOf("套环雪糕", "touhuanxuegao"),
                framePrefixes = listOf("710004", "710000"),
                modeNames = listOf("吸吮", "震动"),
                commandTemplates = listOf(
                    { progress -> cachitoSuctionTemplate("710004", progress) },
                    { progress -> cachitoSuctionTemplate("710000", progress, command = "050A") },
                ),
                stopTemplates = listOf(
                    "710004**-0400-####-0302-0000000000",
                    "710000**-0400-####-0601-0000000000",
                ),
            )
        ),
        CachitoTemplateBroadcastProtocol(
            CachitoTemplateBroadcastSpec(
                id = "cachito_xuegao2_advertise",
                displayName = "Cachito 雪糕 2",
                aliases = listOf("雪糕2", "xuegao2"),
                framePrefixes = listOf("710000"),
                modeNames = listOf("吸吮", "震动"),
                commandTemplates = listOf(
                    { progress -> "710000**-0400-####-0302-" + CachitoBroadcastCodec.toHex(progress.coerceIn(0, 100)) + "00000000" },
                    { progress -> cachitoSuctionTemplate("710000", progress, command = "050A") },
                ),
                stopTemplates = listOf(
                    "710000**-0400-####-0302-0000000000",
                    "710000**-0400-####-0601-0000000000",
                ),
            )
        ),
        CachitoTemplateBroadcastProtocol(
            CachitoTemplateBroadcastSpec(
                id = "cachito_mb_mini_advertise",
                displayName = "Cachito MB Mini",
                aliases = listOf("mbmini", "mb迷你"),
                framePrefixes = listOf("710005"),
                modeNames = listOf("强度"),
                commandTemplates = listOf({ progress -> cachitoSuctionTemplate("710005", progress) }),
                stopTemplates = listOf("710005**-0400-####-0302-0000000000"),
            )
        ),
        CachitoTemplateBroadcastProtocol(
            CachitoTemplateBroadcastSpec(
                id = "cachito_mb_pro_advertise",
                displayName = "Cachito MB Pro",
                aliases = listOf("mbpro", "mb08", "漫步者pro", "漫步pro"),
                framePrefixes = listOf("710006"),
                modeNames = listOf("强度"),
                commandTemplates = listOf({ progress -> cachitoSuctionTemplate("710006", progress) }),
                stopTemplates = listOf("710006**-0400-####-0302-0000000000"),
            )
        ),
        CachitoTemplateBroadcastProtocol(
            CachitoTemplateBroadcastSpec(
                id = "cachito_touhuan_advertise",
                displayName = "Cachito 套环",
                aliases = listOf("套环", "touhuan"),
                framePrefixes = listOf("710004"),
                modeNames = listOf("强度"),
                commandTemplates = listOf({ progress -> cachitoSuctionTemplate("710004", progress) }),
                stopTemplates = listOf("710004**-0400-####-0302-0000000000"),
                excludedAliases = listOf("套环mini", "套环pro", "套环雪糕", "touhuanmini", "touhuanpro", "touhuanxuegao"),
            )
        ),
    )

    private val protocols = listOf(
        AnkniQd1BroadcastProtocol,
        CachitoShikong4BroadcastProtocol,
        CachitoShikong25BroadcastProtocol,
        CachitoShikong3BroadcastProtocol,
        CachitoShikong2BroadcastProtocol,
        CachitoDaxiuBroadcastProtocol,
    ) + cachitoTemplateProtocols

    private val shikongFallbackProtocols = listOf(
        CachitoShikong3BroadcastProtocol,
        CachitoShikong25BroadcastProtocol,
        CachitoShikong4BroadcastProtocol,
        CachitoShikong2BroadcastProtocol,
    )

    private val shikongProtocolIds = shikongFallbackProtocols.map { it.status.id }.toSet()

    fun resolveAll(device: ScannedBleDevice): List<BleBroadcastProtocol> {
        val matched = protocols.filter { it.matches(device) }
        if (matched.none { it.status.id in shikongProtocolIds }) return matched
        return (matched + shikongFallbackProtocols).distinctBy { it.status.id }
    }

    fun resolveFirstName(device: ScannedBleDevice): String =
        resolveAll(device).firstOrNull()?.status?.displayName.orEmpty()
}

/**
 * ANKNI QD1 的不可连接分支通过 FEFF 广播 UUID 控制；可连接设备优先走 GATT。
 * 控制帧复用醉清风小程序的 ANKNI 滑动强度封包：makeHexStr("08", makeSlideOrder(200, value, ...))。
 */
internal object AnkniQd1BroadcastProtocol : BleBroadcastProtocol {
    private const val SERVICE_DATA_UUID = "0000feff-0000-1000-8000-00805f9b34fb"

    override val preferGattWhenConnectable = true

    override val status = BleProtocolStatus(
        id = "ankni_qd1_advertise",
        displayName = "ANKNI QD1",
        controllable = true,
        intensityMax = 100,
        supportsMode = false,
        controlStyle = ToyControlStyle.IntensityOnly,
        intensityLabel = "强度",
        automatic = true,
    )

    override fun matches(device: ScannedBleDevice): Boolean {
        val name = device.name.normalizedBroadcastDeviceName()
        val hasQd1Name = name.contains("ankniqd1") || name.contains("ankniqd01")
        val hasQd1Broadcast = device.serviceUuids.any { it.equals(SERVICE_DATA_UUID, ignoreCase = true) } ||
            device.scanRecordHex.contains("03 02 FF FE", ignoreCase = true)
        return hasQd1Name && hasQd1Broadcast
    }

    override fun commandsFor(action: ToyControlAction): List<BleAdvertiseOperation> =
        when (action) {
            is ToyControlAction.Intensity -> listOf(command(action.value))
            is ToyControlAction.Combined -> listOf(command(action.intensity))
            is ToyControlAction.DualMotor -> listOf(command(action.strongestIntensity(status.intensityMax)))
            is ToyControlAction.Pattern -> listOf(command(status.intensityMax))
            ToyControlAction.Stop -> listOf(command(0))
        }

    private fun command(intensity: Int): BleAdvertiseOperation =
        BleAdvertiseOperation(
            serviceDataUuid = SERVICE_DATA_UUID,
            serviceData = slideFrame(intensity.coerceIn(0, status.intensityMax)),
        )

    private fun slideFrame(intensity: Int): ByteArray {
        val duration = if (intensity > 0) SLIDE_DURATION else 0
        val values = intArrayOf(intensity, duration)
        return ankniFrame(0x08, values)
    }

    private fun ankniFrame(command: Int, values: IntArray): ByteArray {
        val bytes = ByteArray(values.size + 4)
        bytes[0] = 0xaa.toByte()
        bytes[1] = command.toByte()
        bytes[2] = values.size.toByte()
        values.forEachIndexed { index, value ->
            bytes[index + 3] = value.coerceIn(0, 255).toByte()
        }
        bytes[bytes.lastIndex] = (bytes.dropLast(1).sumOf { it.toInt() and 0xff } and 0xff).toByte()
        return bytes
    }

    private const val SLIDE_DURATION = 0xC8
}

/**
 * Cachito 反编译来源：
 * - com/cs/cachito/utils/ble/BleAdvertiser.smali
 * - com/cs/cachito/ext/CommandExtKt.smali
 * - com/cs/cachito/base/DeviceType.smali
 *
 * 控制方式是把 36 字符 UUID 放进 BLE legacy advertisement 的 Service UUID，
 * 不是连接 GATT 后写 characteristic。
 */
internal object CachitoShikong2BroadcastProtocol : BleBroadcastProtocol {
    private val modeTemplates = listOf(
        CachitoShikong2Mode("吸吮", CachitoShikong2Function.Suction),
        CachitoShikong2Mode("震动", CachitoShikong2Function.Vibration),
    )

    private var selectedMode = 1

    override val status = BleProtocolStatus(
        id = "cachito_shikong2_advertise",
        displayName = "Cachito 失控 2.0",
        controllable = true,
        intensityMax = 100,
        supportsMode = true,
        modeMax = modeTemplates.size,
        controlStyle = ToyControlStyle.CombinedPatternAndIntensity,
        modeLabel = "部位",
        modeNames = modeTemplates.map { it.name },
        intensityLabel = "强度",
        automatic = true,
    )

    override fun matches(device: ScannedBleDevice): Boolean {
        val text = buildString {
            append(device.name)
            append(' ')
            append(device.broadcastProtocolName)
            append(' ')
            append(device.serviceUuids.joinToString())
            append(' ')
            append(device.manufacturerData)
            append(' ')
            append(device.scanRecordHex)
        }
        val compactText = text.lowercase().replace(" ", "").replace(".", "")
        val hasShikong2Name = (compactText.contains("失控2") && !compactText.contains("失控25")) ||
            (compactText.contains("shikong2") && !compactText.contains("shikong25"))
        return hasShikong2Name ||
            text.contains("710002", ignoreCase = true)
    }

    override fun commandsFor(action: ToyControlAction): List<BleAdvertiseOperation> =
        when (action) {
            is ToyControlAction.Pattern -> control(action.mode, 100)
            is ToyControlAction.Intensity -> control(selectedMode, action.value)
            is ToyControlAction.Combined -> control(action.mode, action.intensity)
            is ToyControlAction.DualMotor -> control(action.mode, action.strongestIntensity(100))
            ToyControlAction.Stop -> stop()
        }

    private fun control(mode: Int, intensity: Int): List<BleAdvertiseOperation> {
        val selected = modeTemplates[(mode - 1).coerceIn(0, modeTemplates.lastIndex)]
        selectedMode = mode.coerceIn(1, modeTemplates.size)
        val progress = intensity.coerceIn(0, 100)
        return when (selected.function) {
            CachitoShikong2Function.Suction ->
                listOf(BleAdvertiseOperation(CachitoBroadcastCodec.finalUuid(suctionTemplate(progress))))
            CachitoShikong2Function.Vibration ->
                listOf(BleAdvertiseOperation(CachitoBroadcastCodec.finalUuid(vibrationTemplate(progress))))
        }
    }

    private fun stop(): List<BleAdvertiseOperation> =
        listOf(
            BleAdvertiseOperation(CachitoBroadcastCodec.finalUuid(suctionStopTemplate())),
            BleAdvertiseOperation(CachitoBroadcastCodec.finalUuid(vibrationStopTemplate())),
        )

    private fun suctionTemplate(progress: Int): String =
        "710002**-0400-####-0302-" + shikong2ScaledProgress(progress) + "00000000"

    private fun vibrationTemplate(progress: Int): String =
        "710002**-0400-####-050A-" + shikong2ScaledProgress(progress) + "00000000"

    private fun suctionStopTemplate(): String =
        "710002**-0400-####-0302-0000000000"

    private fun vibrationStopTemplate(): String =
        "710002**-0400-####-0601-0000000000"

    private fun shikong2ScaledProgress(progress: Int): String {
        val value = (progress.coerceIn(0, 100) * 0.75 + 25).toInt()
        return CachitoBroadcastCodec.toHex(value)
    }

    private data class CachitoShikong2Mode(
        val name: String,
        val function: CachitoShikong2Function,
    )

    private enum class CachitoShikong2Function {
        Suction,
        Vibration,
    }
}

/**
 * 失控 3.0 对应官方 App `TYPE_SHIKONG3 = 0x23`。
 * 该机型仍使用 Service UUID 广播控制，但命令前缀是 `71000B`，不能复用 2.0 的 `710002` 帧。
 */
internal object CachitoShikong3BroadcastProtocol : BleBroadcastProtocol {
    private val modeTemplates = listOf(
        CachitoShikong3Mode("吸吮", CachitoShikong3Function.Suction),
        CachitoShikong3Mode("震动", CachitoShikong3Function.Vibration),
    )

    private var selectedMode = 1

    override val status = BleProtocolStatus(
        id = "cachito_shikong3_advertise",
        displayName = "Cachito 失控 3.0",
        controllable = true,
        intensityMax = 100,
        supportsMode = true,
        modeMax = modeTemplates.size,
        controlStyle = ToyControlStyle.CombinedPatternAndIntensity,
        modeLabel = "部位",
        modeNames = modeTemplates.map { it.name },
        intensityLabel = "强度",
        automatic = true,
    )

    override fun matches(device: ScannedBleDevice): Boolean {
        val text = buildString {
            append(device.name)
            append(' ')
            append(device.broadcastProtocolName)
            append(' ')
            append(device.serviceUuids.joinToString())
            append(' ')
            append(device.manufacturerData)
            append(' ')
            append(device.scanRecordHex)
        }
        val compactText = text.lowercase().replace(" ", "").replace(".", "")
        val knownHint = compactText.contains("失控3") ||
            compactText.contains("shikong3") ||
            text.contains("71000B", ignoreCase = true)
        val shikong3TraceHint = !device.connectable &&
            device.serviceUuids.isEmpty() &&
            device.manufacturerData.contains("0x6:01 09 20 22", ignoreCase = true)
        return knownHint || shikong3TraceHint
    }

    override fun commandsFor(action: ToyControlAction): List<BleAdvertiseOperation> =
        when (action) {
            is ToyControlAction.Pattern -> control(action.mode, 100)
            is ToyControlAction.Intensity -> control(selectedMode, action.value)
            is ToyControlAction.Combined -> control(action.mode, action.intensity)
            is ToyControlAction.DualMotor -> control(action.mode, action.strongestIntensity(100))
            ToyControlAction.Stop -> stop()
        }

    private fun control(mode: Int, intensity: Int): List<BleAdvertiseOperation> {
        val selected = modeTemplates[(mode - 1).coerceIn(0, modeTemplates.lastIndex)]
        selectedMode = mode.coerceIn(1, modeTemplates.size)
        val progress = intensity.coerceIn(0, 100)
        return when (selected.function) {
            CachitoShikong3Function.Suction ->
                listOf(BleAdvertiseOperation(CachitoBroadcastCodec.finalUuid(suctionTemplate(progress))))
            CachitoShikong3Function.Vibration ->
                listOf(BleAdvertiseOperation(CachitoBroadcastCodec.finalUuid(vibrationTemplate(progress))))
        }
    }

    private fun stop(): List<BleAdvertiseOperation> =
        listOf(
            BleAdvertiseOperation(CachitoBroadcastCodec.finalUuid(suctionStopTemplate())),
            BleAdvertiseOperation(CachitoBroadcastCodec.finalUuid(vibrationStopTemplate())),
            BleAdvertiseOperation(CachitoBroadcastCodec.finalUuid(stopAllTemplate())),
        )

    private fun suctionTemplate(progress: Int): String =
        "71000B**-8200-####-0100-640000" + shikong3SuctionProgress(progress) + "02"

    private fun vibrationTemplate(progress: Int): String =
        "71000B**-8100-####-0100-0A" + shikong3VibrationProgress(progress) + "1c0002"

    private fun suctionStopTemplate(): String =
        "71000B**-0200-####-0100-6400000002"

    private fun vibrationStopTemplate(): String =
        "71000B**-0100-####-0100-6400000002"

    private fun stopAllTemplate(): String =
        "71000B**-0F00-####-0100-6400000002"

    private fun shikong3SuctionProgress(progress: Int): String {
        val value = (progress.coerceIn(0, 100) * 0.5 + 50).toInt()
        return CachitoBroadcastCodec.toHex(value)
    }

    private fun shikong3VibrationProgress(progress: Int): String {
        val value = (60 - progress.coerceIn(0, 100) * 0.57f).toInt()
        return CachitoBroadcastCodec.toHex(value)
    }

    private data class CachitoShikong3Mode(
        val name: String,
        val function: CachitoShikong3Function,
    )

    private enum class CachitoShikong3Function {
        Suction,
        Vibration,
    }
}

/**
 * 失控 2.5 对应官方 App `TYPE_SHIKONG25 = 0x22`，与 3.0 控制结构一致但命令前缀是 `710007`。
 */
internal object CachitoShikong25BroadcastProtocol : BleBroadcastProtocol {
    private val modeTemplates = listOf(
        CachitoShikong25Mode("吸吮", CachitoShikong25Function.Suction),
        CachitoShikong25Mode("震动", CachitoShikong25Function.Vibration),
    )

    private var selectedMode = 1

    override val status = BleProtocolStatus(
        id = "cachito_shikong25_advertise",
        displayName = "Cachito 失控 2.5",
        controllable = true,
        intensityMax = 100,
        supportsMode = true,
        modeMax = modeTemplates.size,
        controlStyle = ToyControlStyle.CombinedPatternAndIntensity,
        modeLabel = "部位",
        modeNames = modeTemplates.map { it.name },
        intensityLabel = "强度",
        automatic = true,
    )

    override fun matches(device: ScannedBleDevice): Boolean {
        val text = buildString {
            append(device.name)
            append(' ')
            append(device.serviceUuids.joinToString())
            append(' ')
            append(device.manufacturerData)
            append(' ')
            append(device.scanRecordHex)
        }
        val compactText = text.lowercase().replace(" ", "").replace(".", "")
        return compactText.contains("失控25") ||
            compactText.contains("shikong25") ||
            text.contains("710007", ignoreCase = true)
    }

    override fun commandsFor(action: ToyControlAction): List<BleAdvertiseOperation> =
        when (action) {
            is ToyControlAction.Pattern -> control(action.mode, 100)
            is ToyControlAction.Intensity -> control(selectedMode, action.value)
            is ToyControlAction.Combined -> control(action.mode, action.intensity)
            is ToyControlAction.DualMotor -> control(action.mode, action.strongestIntensity(100))
            ToyControlAction.Stop -> stop()
        }

    private fun control(mode: Int, intensity: Int): List<BleAdvertiseOperation> {
        val selected = modeTemplates[(mode - 1).coerceIn(0, modeTemplates.lastIndex)]
        selectedMode = mode.coerceIn(1, modeTemplates.size)
        val progress = intensity.coerceIn(0, 100)
        return when (selected.function) {
            CachitoShikong25Function.Suction ->
                listOf(BleAdvertiseOperation(CachitoBroadcastCodec.finalUuid(suctionTemplate(progress))))
            CachitoShikong25Function.Vibration ->
                listOf(BleAdvertiseOperation(CachitoBroadcastCodec.finalUuid(vibrationTemplate(progress))))
        }
    }

    private fun stop(): List<BleAdvertiseOperation> =
        listOf(
            BleAdvertiseOperation(CachitoBroadcastCodec.finalUuid(suctionStopTemplate())),
            BleAdvertiseOperation(CachitoBroadcastCodec.finalUuid(vibrationStopTemplate())),
            BleAdvertiseOperation(CachitoBroadcastCodec.finalUuid(stopAllTemplate())),
        )

    private fun suctionTemplate(progress: Int): String =
        "710007**-8200-####-0100-640000" + shikong25SuctionProgress(progress) + "02"

    private fun vibrationTemplate(progress: Int): String =
        "710007**-8100-####-0100-0A" + shikong25VibrationProgress(progress) + "1c0002"

    private fun suctionStopTemplate(): String =
        "710007**-0200-####-0100-6400000002"

    private fun vibrationStopTemplate(): String =
        "710007**-0100-####-0100-6400000002"

    private fun stopAllTemplate(): String =
        "710007**-0F00-####-0100-6400000002"

    private fun shikong25SuctionProgress(progress: Int): String {
        val value = (progress.coerceIn(0, 100) * 0.5 + 50).toInt()
        return CachitoBroadcastCodec.toHex(value)
    }

    private fun shikong25VibrationProgress(progress: Int): String {
        val value = (60 - progress.coerceIn(0, 100) * 0.57f).toInt()
        return CachitoBroadcastCodec.toHex(value)
    }

    private data class CachitoShikong25Mode(
        val name: String,
        val function: CachitoShikong25Function,
    )

    private enum class CachitoShikong25Function {
        Suction,
        Vibration,
    }
}

/**
 * 失控 4.0 对应官方 App `TYPE_SHIKONG4 = 0x26`，使用独立 `710017` 帧和脉冲类型。
 */
internal object CachitoShikong4BroadcastProtocol : BleBroadcastProtocol {
    private val modeTemplates = listOf(
        CachitoShikong4Mode("小号脉冲", 0x34),
        CachitoShikong4Mode("震动", 0x35),
        CachitoShikong4Mode("拍打", 0x36),
        CachitoShikong4Mode("抠动", 0x37),
        CachitoShikong4Mode("蠕动", 0x38),
        CachitoShikong4Mode("大号脉冲", 0x39),
    )

    private var selectedMode = 1

    override val status = BleProtocolStatus(
        id = "cachito_shikong4_advertise",
        displayName = "Cachito 失控 4.0",
        controllable = true,
        intensityMax = 100,
        supportsMode = true,
        modeMax = modeTemplates.size,
        controlStyle = ToyControlStyle.CombinedPatternAndIntensity,
        modeLabel = "模式",
        modeNames = modeTemplates.map { it.name },
        intensityLabel = "强度",
        automatic = true,
    )

    override fun matches(device: ScannedBleDevice): Boolean {
        val text = buildString {
            append(device.name)
            append(' ')
            append(device.serviceUuids.joinToString())
            append(' ')
            append(device.manufacturerData)
            append(' ')
            append(device.scanRecordHex)
        }
        val compactText = text.lowercase().replace(" ", "").replace(".", "")
        return compactText.contains("失控4") ||
            compactText.contains("shikong4") ||
            text.contains("710017", ignoreCase = true)
    }

    override fun commandsFor(action: ToyControlAction): List<BleAdvertiseOperation> =
        when (action) {
            is ToyControlAction.Pattern -> control(action.mode, 100)
            is ToyControlAction.Intensity -> control(selectedMode, action.value)
            is ToyControlAction.Combined -> control(action.mode, action.intensity)
            is ToyControlAction.DualMotor -> control(action.mode, action.strongestIntensity(100))
            ToyControlAction.Stop -> stop()
        }

    private fun control(mode: Int, intensity: Int): List<BleAdvertiseOperation> {
        selectedMode = mode.coerceIn(1, modeTemplates.size)
        val selected = modeTemplates[selectedMode - 1]
        return listOf(
            BleAdvertiseOperation(
                CachitoBroadcastCodec.finalUuid(commandTemplate(selected.pulseType, intensity.coerceIn(0, 100)))
            )
        )
    }

    private fun stop(): List<BleAdvertiseOperation> =
        listOf(
            BleAdvertiseOperation(CachitoBroadcastCodec.finalUuid("710017**-0F00-####-0100-6400000002")),
        )

    private fun commandTemplate(pulseType: Int, progress: Int): String {
        if (progress <= 0) {
            return "710017**-${pulseType}00-####-0100-6400000002"
        }
        return when (pulseType) {
            0x34, 0x39 -> {
                val value = CachitoBroadcastCodec.toHex(0x66 - progress.coerceIn(0, 100))
                "710017**-${pulseType}00-####-0100-2d${value}150002"
            }
            0x35 -> strengthTemplate(pulseType, progress, 0.8, 20)
            0x36 -> strengthTemplate(pulseType, progress, 0.65, 25)
            0x38 -> strengthTemplate(pulseType, progress, 0.7, 30)
            else -> {
                val value = CachitoBroadcastCodec.toHex(progress.coerceIn(0, 100))
                "710017**-${pulseType}00-####-0100-640000${value}02"
            }
        }
    }

    private fun strengthTemplate(pulseType: Int, progress: Int, multiplier: Double, offset: Int): String {
        val value = CachitoBroadcastCodec.toHex((progress.coerceIn(0, 100) * multiplier + offset).toInt())
        return "710017**-${pulseType}00-####-0100-640000${value}02"
    }

    private data class CachitoShikong4Mode(
        val name: String,
        val pulseType: Int,
    )
}

private data class CachitoTemplateBroadcastSpec(
    val id: String,
    val displayName: String,
    val aliases: List<String>,
    val framePrefixes: List<String>,
    val modeNames: List<String>,
    val commandTemplates: List<(Int) -> String>,
    val stopTemplates: List<String>,
    val excludedAliases: List<String> = emptyList(),
)

private class CachitoTemplateBroadcastProtocol(
    private val spec: CachitoTemplateBroadcastSpec,
) : BleBroadcastProtocol {
    private var selectedMode = 1

    override val status = BleProtocolStatus(
        id = spec.id,
        displayName = spec.displayName,
        controllable = true,
        intensityMax = 100,
        supportsMode = spec.commandTemplates.size > 1,
        modeMax = spec.commandTemplates.size,
        controlStyle = if (spec.commandTemplates.size > 1) {
            ToyControlStyle.CombinedPatternAndIntensity
        } else {
            ToyControlStyle.IntensityOnly
        },
        modeLabel = if (spec.commandTemplates.size > 1) "功能" else "",
        modeNames = spec.modeNames,
        intensityLabel = "强度",
        automatic = true,
    )

    override fun matches(device: ScannedBleDevice): Boolean {
        val text = buildString {
            append(device.name)
            append(' ')
            append(device.serviceUuids.joinToString())
            append(' ')
            append(device.manufacturerData)
            append(' ')
            append(device.scanRecordHex)
        }
        val compactText = text.lowercase().replace(" ", "").replace(".", "")
        if (spec.excludedAliases.any { compactText.contains(it.lowercase().replace(" ", "").replace(".", "")) }) {
            return false
        }
        return spec.aliases.any { compactText.contains(it.lowercase().replace(" ", "").replace(".", "")) } ||
            spec.framePrefixes.any { text.contains(it, ignoreCase = true) }
    }

    override fun commandsFor(action: ToyControlAction): List<BleAdvertiseOperation> =
        when (action) {
            is ToyControlAction.Pattern -> control(action.mode, 100)
            is ToyControlAction.Intensity -> control(selectedMode, action.value)
            is ToyControlAction.Combined -> control(action.mode, action.intensity)
            is ToyControlAction.DualMotor -> control(action.mode, action.strongestIntensity(100))
            ToyControlAction.Stop -> stop()
        }

    private fun control(mode: Int, intensity: Int): List<BleAdvertiseOperation> {
        selectedMode = mode.coerceIn(1, spec.commandTemplates.size)
        val template = spec.commandTemplates[selectedMode - 1](intensity.coerceIn(0, 100))
        return listOf(BleAdvertiseOperation(CachitoBroadcastCodec.finalUuid(template)))
    }

    private fun stop(): List<BleAdvertiseOperation> =
        spec.stopTemplates.map { template ->
            BleAdvertiseOperation(CachitoBroadcastCodec.finalUuid(template))
        }
}

private fun cachitoSuctionTemplate(prefix: String, progress: Int, command: String = "0302"): String =
    "$prefix**-0400-####-$command-" + cachitoScaled75(progress) + "00000000"

private fun cachito8200Template(prefix: String, progress: Int): String =
    "$prefix**-8200-####-0100-640000" + cachitoScaled50(progress) + "02"

private fun cachitoScaled75(progress: Int): String {
    val value = (progress.coerceIn(0, 100) * 0.75 + 25).toInt()
    return CachitoBroadcastCodec.toHex(value)
}

private fun cachitoScaled50(progress: Int): String {
    val value = (progress.coerceIn(0, 100) * 0.5 + 50).toInt()
    return CachitoBroadcastCodec.toHex(value)
}

internal object CachitoDaxiuBroadcastProtocol : BleBroadcastProtocol {
    private val modeTemplates = listOf(
        CachitoDaxiuMode("推拉深度", CachitoDaxiuFunction.Depth),
        CachitoDaxiuMode("伸出速度", CachitoDaxiuFunction.ExtendSpeed),
        CachitoDaxiuMode("缩回速度", CachitoDaxiuFunction.RetractSpeed),
        CachitoDaxiuMode("入体端强度", CachitoDaxiuFunction.EnterStrength),
        CachitoDaxiuMode("加热温度", CachitoDaxiuFunction.Temperature),
    )

    private var selectedMode = 1
    private var depth = 36
    private var extendSpeed = 8
    private var retractSpeed = 8

    override val status = BleProtocolStatus(
        id = "cachito_daxiu_advertise",
        displayName = "Cachito 大秀",
        controllable = true,
        intensityMax = 100,
        supportsMode = true,
        modeMax = modeTemplates.size,
        controlStyle = ToyControlStyle.CombinedPatternAndIntensity,
        modeLabel = "功能",
        modeNames = modeTemplates.map { it.name },
        intensityLabel = "数值",
        automatic = true,
    )

    override fun matches(device: ScannedBleDevice): Boolean {
        val text = buildString {
            append(device.name)
            append(' ')
            append(device.serviceUuids.joinToString())
            append(' ')
            append(device.manufacturerData)
            append(' ')
            append(device.scanRecordHex)
        }
        return text.contains("大秀", ignoreCase = true) ||
            text.contains("daxiu", ignoreCase = true) ||
            text.contains("710003", ignoreCase = true)
    }

    override fun commandsFor(action: ToyControlAction): List<BleAdvertiseOperation> =
        when (action) {
            is ToyControlAction.Pattern -> control(action.mode, 100)
            is ToyControlAction.Intensity -> control(selectedMode, action.value)
            is ToyControlAction.Combined -> control(action.mode, action.intensity)
            is ToyControlAction.DualMotor -> control(action.mode, action.strongestIntensity(100))
            ToyControlAction.Stop -> stop()
        }

    private fun control(mode: Int, intensity: Int): List<BleAdvertiseOperation> {
        val selected = modeTemplates[(mode - 1).coerceIn(0, modeTemplates.lastIndex)]
        selectedMode = mode.coerceIn(1, modeTemplates.size)
        val progress = intensity.coerceIn(0, 100)
        val template = when (selected.function) {
            CachitoDaxiuFunction.Depth -> {
                depth = progress.coerceIn(0, 72)
                thrustTemplate()
            }
            CachitoDaxiuFunction.ExtendSpeed -> {
                extendSpeed = progress.coerceIn(0, 15)
                thrustTemplate()
            }
            CachitoDaxiuFunction.RetractSpeed -> {
                retractSpeed = progress.coerceIn(0, 15)
                thrustTemplate()
            }
            CachitoDaxiuFunction.EnterStrength -> strengthTemplate(progress)
            CachitoDaxiuFunction.Temperature -> temperatureTemplate(progress)
        }
        return listOf(BleAdvertiseOperation(CachitoBroadcastCodec.finalUuid(template)))
    }

    private fun stop(): List<BleAdvertiseOperation> =
        listOf(
            BleAdvertiseOperation(CachitoBroadcastCodec.finalUuid(thrustStopTemplate())),
            BleAdvertiseOperation(CachitoBroadcastCodec.finalUuid(strengthTemplate(0))),
            BleAdvertiseOperation(CachitoBroadcastCodec.finalUuid(temperatureStopTemplate())),
        )

    private fun thrustTemplate(): String =
        "710003**-8800-####-0000-" +
            CachitoBroadcastCodec.toHex(depth.coerceIn(0, 72)) +
            CachitoBroadcastCodec.toHex(extendSpeed.coerceIn(0, 15)) +
            CachitoBroadcastCodec.toHex(retractSpeed.coerceIn(0, 15)) +
            "0000"

    private fun thrustStopTemplate(): String =
        "710003**-8800-####-0000-0000000000"

    private fun strengthTemplate(strength: Int): String =
        "710003**-0400-####-050A-" + CachitoBroadcastCodec.toHex(strength.coerceIn(0, 100)) + "00000000"

    private fun temperatureTemplate(progress: Int): String =
        "710003**-1F00-####-0000-" + CachitoBroadcastCodec.toHex(progress.coerceIn(0, 60)) + "00000000"

    private fun temperatureStopTemplate(): String =
        "710003**-1F00-####-0000-0000000000"

    private data class CachitoDaxiuMode(
        val name: String,
        val function: CachitoDaxiuFunction,
    )

    private enum class CachitoDaxiuFunction {
        Depth,
        ExtendSpeed,
        RetractSpeed,
        EnterStrength,
        Temperature,
    }
}

private object CachitoBroadcastCodec {
    private const val CONTROL_DEVICE_ID_KEY = "CACHITO_CONTROL_DEVICE_ID"
    private var deviceId = loadDeviceId()

    fun finalUuid(template: String): String {
        val command = template
            .replace("####", deviceId)
            .replace("**", newCommandId())
        val checksumSource = command.replace("-", "")
        return command + checksum(checksumSource)
    }

    private fun checksum(hex: String): String {
        val sum = hex.chunked(2).sumOf { it.toIntOrNull(16) ?: 0 }
        return toHex(sum % 256)
    }

    private fun newCommandId(): String = toHex(Random.nextInt(100, 256))

    private fun loadDeviceId(): String {
        val saved = DataSaverUtils.readData(CONTROL_DEVICE_ID_KEY, "")
            .uppercase()
            .filter { it in '0'..'9' || it in 'A'..'F' }
        if (saved.length == 4) return saved
        return newDeviceId().also { DataSaverUtils.saveData(CONTROL_DEVICE_ID_KEY, it) }
    }

    private fun newDeviceId(): String =
        buildString {
            repeat(4) { append("0123456789ABCDEF"[Random.nextInt(16)]) }
        }

    fun toHex(value: Int): String =
        (value and 0xff).toString(16).uppercase().padStart(2, '0')
}

private fun String.normalizedBroadcastDeviceName(): String =
    lowercase().filter { it.isLetterOrDigit() }
