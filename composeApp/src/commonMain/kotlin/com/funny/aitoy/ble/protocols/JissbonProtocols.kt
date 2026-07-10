package com.funny.aitoy.ble

import kotlin.uuid.Uuid

// 杰士邦「情趣星球」小程序（ref/杰士邦-wxapp/.../app-service.js）是其全系玩具官方控制端。
// GATT：service FFE0、写/通知特征 FFE1。控制帧固定 `55 03 <mask> <马达字节...>`。
// functionType（马达组合）决定 mask 与帧长；每个 func 号对应固定字节位；停止帧 mask 归 0。
// 强度范围 0-100（官方滑杆 setIntensity 输出 15-100，直接作为马达字节）。

// func 号 → 帧内马达字节位（对应 app-service.js sendIntensity 的 r[5]/r[3]/r[4]/r[6]/r[7]/r[8]）。
private val JISSBON_MOTOR_BYTE = mapOf(1 to 5, 2 to 3, 3 to 4, 4 to 6, 5 to 7, 6 to 8)

// mask 字节：官方按 functionType 字符串硬编码，未列出的组合保持默认 1。
private fun jissbonMask(functionType: String): Int =
    when (functionType) {
        "1" -> 1
        "2" -> 4
        "4" -> 8
        "1,2" -> 5
        "1,2,3" -> 7
        "1,4" -> 9
        "1,6" -> 17
        else -> 1
    }

// 还原 sendIntensity：按 functionType/型号拼帧并把各马达强度写到对应字节位。
internal fun jissbonIntensityFrame(
    functionType: List<Int>,
    canonicalName: String,
    isOld: Boolean,
    values: List<Int>,
): ByteArray {
    if (isOld) {
        val v = values.firstOrNull()?.coerceIn(0, 100) ?: 0
        return intArrayOf(0x55, 0x03, 0x01, 0x00, 0x00, v).toJissbonBytes()
    }
    val ftKey = functionType.joinToString(",")
    val stormGirlSe = canonicalName == "Jissbon Storm girl SE"
    val mask = if (stormGirlSe) 1 else jissbonMask(ftKey)
    var len = when {
        stormGirlSe -> 8
        canonicalName == "Jissbon Spartan Warrior Trainer" -> 12
        canonicalName == "Jissbon Abyss" -> 8
        ftKey == "1,4" -> 8
        ftKey == "1,6" -> 9
        else -> 6
    }
    val positions = functionType.map { JISSBON_MOTOR_BYTE.getValue(it) }
    len = maxOf(len, (positions.maxOrNull() ?: 2) + 1)
    val frame = IntArray(len)
    frame[0] = 0x55
    frame[1] = 0x03
    frame[2] = mask
    functionType.forEachIndexed { i, func ->
        frame[JISSBON_MOTOR_BYTE.getValue(func)] = values.getOrElse(i) { 0 }.coerceIn(0, 100)
    }
    return frame.toJissbonBytes()
}

// 还原 stopSendIntensity：functionType=="1,4" 或 Storm girl SE / Abyss 用 8 字节，否则 6 字节，全 0。
internal fun jissbonStopFrame(functionType: List<Int>, canonicalName: String): ByteArray {
    val ftKey = functionType.joinToString(",")
    val len = if (ftKey == "1,4" ||
        canonicalName == "Jissbon Storm girl SE" ||
        canonicalName == "Jissbon Abyss"
    ) 8 else 6
    return IntArray(len).also { it[0] = 0x55; it[1] = 0x03 }.toJissbonBytes()
}

private fun IntArray.toJissbonBytes(): ByteArray = map { (it and 0xff).toByte() }.toByteArray()

// 单个杰士邦产品的功能画像：官方 canonicalName + functionType(马达组合) + productType + isOld。
internal data class JissbonProfileSpec(
    val id: String,
    val displayName: String,
    val canonicalName: String,
    val functionType: List<Int>,
    val isOld: Boolean = false,
)

// app-service.js 里 localName 先经关键字改写为 canonicalName，再查 functionType/productType。
// 改写规则（子串命中即替换整名），顺序与官方一致。
internal fun jissbonCanonicalName(rawName: String): String {
    val n = rawName.trim()
    return when {
        n.contains("Crush Vibrating Egg") -> "Crush Vibrating Egg"
        n.contains("Electroshock Vibrating Egg") -> "Electroshock Vibrating Egg"
        n.contains("26-50B") -> "Jissbon Storm Girl Pro"
        n.contains("Linxi") -> "Jissbon Linxi"
        n == "26-50" -> "Jissbon Punk universe Pro"
        n == "Jissbon Dual" -> "Jissbon Dual Handle Aircraft Cup"
        n == "JSBBHXLQ" -> "Jissbon Spartan Warrior Trainer"
        n == "jissbon punk2" -> "Jissbon Punk2"
        n.contains("Tapping Vibrator") || n.contains("Knock Wave Vibrator") -> "Knock Wave Vibrator"
        n.contains("Wet Kiss Vibrator") -> "WanWan Vibrating Egg"
        else -> n
    }
}

// 官方全系产品表：canonicalName → 功能画像（switch 里逐条对应）。isOld=true 的用单值旧帧。
internal val JISSBON_PRODUCTS: Map<String, JissbonProfileSpec> = listOf(
    JissbonProfileSpec("jissbon_softoy", "杰士邦 单震动跳蛋", "Softoy Remote Vibrator", listOf(1), isOld = true),
    JissbonProfileSpec("jissbon_master_rot", "杰士邦 旋转跳蛋", "Master Rotating Vibrator", listOf(1), isOld = true),
    JissbonProfileSpec("jissbon_candy", "杰士邦 Candy 跳蛋", "Candy Remote Vibrator", listOf(1), isOld = true),
    JissbonProfileSpec("jissbon_fancy_peach", "杰士邦 蜜桃跳蛋", "Fancy Peach Vibrator", listOf(1), isOld = true),
    JissbonProfileSpec("jissbon_fancy_wear", "杰士邦 穿戴跳蛋", "Fancy Wearable Vibrating Egg", listOf(1), isOld = true),
    JissbonProfileSpec("jissbon_jsb2642", "杰士邦 单震动跳蛋", "JSB26_42", listOf(1)),
    JissbonProfileSpec("jissbon_candy_max", "杰士邦 Candy Max", "Candy max Remote Vibrator", listOf(2)),
    JissbonProfileSpec("jissbon_adopt_cat", "杰士邦 领养一只猫", "Adopt A Cat Vibrator", listOf(1, 2, 3)),
    JissbonProfileSpec("jissbon_crush_egg", "杰士邦 双头跳蛋", "Crush Vibrating Egg", listOf(1, 2)),
    JissbonProfileSpec("jissbon_fancy_remote", "杰士邦 双震动跳蛋", "Fancy Remote Vibrator", listOf(1, 2)),
    JissbonProfileSpec("jissbon_mls1557c", "杰士邦 双震动跳蛋", "MLS-15-57C", listOf(1, 2)),
    JissbonProfileSpec("jissbon_jsb_ht", "杰士邦 三震动跳蛋", "JSB_HT", listOf(1, 2, 3)),
    JissbonProfileSpec("jissbon_master_remote", "杰士邦 三震动跳蛋", "Master Remote Vibrator", listOf(1, 2, 3)),
    JissbonProfileSpec("jissbon_master_wave", "杰士邦 波浪三震动", "Master Wave Remote Vibrator", listOf(1, 2, 3)),
    JissbonProfileSpec("jissbon_joyful_lotus", "杰士邦 悦莲跳蛋", "Joyful lotus Vibrating Egg", listOf(1, 2, 3)),
    JissbonProfileSpec("jissbon_joyful_suck", "杰士邦 悦莲吮吸蛋", "Joyful Lotus Sucking Egg", listOf(1, 2)),
    JissbonProfileSpec("jissbon_storm_pro", "杰士邦 暴风女 Pro", "Jissbon Storm Girl Pro", listOf(1, 4)),
    JissbonProfileSpec("jissbon_jsbbhamq", "杰士邦 双震动飞机杯", "JSBBHAMQ", listOf(1, 4)),
    JissbonProfileSpec("jissbon_swt", "杰士邦 斯巴达训练器", "Jissbon Spartan Warrior Trainer", listOf(1)),
    JissbonProfileSpec("jissbon_electroshock", "杰士邦 电击跳蛋", "Electroshock Vibrating Egg", listOf(1, 6)),
    JissbonProfileSpec("jissbon_terminator", "杰士邦 终结者", "Jissbon Terminator", listOf(1, 4)),
    JissbonProfileSpec("jissbon_linxi", "杰士邦 邻兮", "Jissbon Linxi", listOf(1, 4)),
    JissbonProfileSpec("jissbon_ginger_cat", "杰士邦 生姜猫跳蛋", "Ginger Cat Vibrating Egg", listOf(2, 3)),
    JissbonProfileSpec("jissbon_punk_pro", "杰士邦 朋克宇宙 Pro", "Jissbon Punk universe Pro", listOf(1, 4)),
    JissbonProfileSpec("jissbon_catclaw", "杰士邦 猫爪跳蛋", "Cat-claw Vibrating Egg", listOf(1)),
    JissbonProfileSpec("jissbon_dolphin", "杰士邦 海豚", "Jissbon Dolphin", listOf(4)),
    JissbonProfileSpec("jissbon_jcandy_egg", "杰士邦 J-Candy 跳蛋", "J-Candy Vibrating Egg", listOf(1)),
    JissbonProfileSpec("jissbon_wanwan", "杰士邦 湿吻/汪汪跳蛋", "WanWan Vibrating Egg", listOf(1, 2)),
    JissbonProfileSpec("jissbon_jm_egg", "杰士邦 J-M 跳蛋", "J-M Vibrating Egg", listOf(1, 2)),
    JissbonProfileSpec("jissbon_storm_se", "杰士邦 暴风女 SE", "Jissbon Storm girl SE", listOf(4)),
    JissbonProfileSpec("jissbon_beloved", "杰士邦 挚爱穿戴蛋", "Beloved Wearable Egg", listOf(1, 2, 4)),
    JissbonProfileSpec("jissbon_abyss", "杰士邦 深渊", "Jissbon Abyss", listOf(4)),
    JissbonProfileSpec("jissbon_punk2", "杰士邦 朋克 2", "Jissbon Punk2", listOf(1, 2, 4)),
    JissbonProfileSpec("jissbon_dual_cup", "杰士邦 双手柄飞机杯", "Jissbon Dual Handle Aircraft Cup", listOf(1, 4)),
    JissbonProfileSpec("jissbon_knock_wave", "杰士邦 敲击波跳蛋", "Knock Wave Vibrator", listOf(1, 2, 3)),
).associateBy { it.canonicalName }

private val JISSBON_SERVICE_UUID = Uuid.parse("0000ffe0-0000-1000-8000-00805f9b34fb")
private val JISSBON_WRITE_UUID = Uuid.parse("0000ffe1-0000-1000-8000-00805f9b34fb")

// 由一个产品画像生成的可控协议实例：马达数 → 控制风格与通道，命令走还原后的 0x55 帧。
internal class JissbonProfileProtocol(private val spec: JissbonProfileSpec) : BleDeviceProtocol {
    private val motorCount = spec.functionType.size

    private fun channelLabels(): List<String> =
        if (motorCount >= 2) List(motorCount) { "震动 ${('A' + it)}" } else emptyList()

    override val status = BleProtocolStatus(
        id = spec.id,
        displayName = spec.displayName,
        controllable = true,
        intensityMax = 100,
        controlStyle = if (motorCount >= 2) ToyControlStyle.DualIntensityOnly else ToyControlStyle.IntensityOnly,
        intensityLabel = "强度",
        channelNames = channelLabels().take(2),
        features = spec.functionType.mapIndexed { i, _ ->
            BleProtocolFeature(
                type = "vibrate",
                min = 0,
                max = 100,
                index = i,
                label = if (motorCount >= 2) "震动 ${('A' + i)}" else "震动",
            )
        },
    )

    override fun matches(fingerprint: BleGattFingerprint): Boolean = false

    // 把控制动作展开成每个马达的强度（长度对齐 functionType）。
    private fun motorValues(action: ToyControlAction): List<Int> =
        when (action) {
            is ToyControlAction.Intensity -> List(motorCount) { action.value }
            is ToyControlAction.Combined -> List(motorCount) { action.intensity }
            is ToyControlAction.Pattern -> List(motorCount) { 100 }
            is ToyControlAction.DualMotor -> List(motorCount) { i ->
                if (i == 0) action.internalIntensity else action.externalIntensity
            }
            ToyControlAction.Stop -> List(motorCount) { 0 }
        }

    override fun commandsFor(action: ToyControlAction): List<BleProtocolOperation> {
        val values = motorValues(action)
        val frame = if (values.all { it <= 0 }) {
            jissbonStopFrame(spec.functionType, spec.canonicalName)
        } else {
            jissbonIntensityFrame(spec.functionType, spec.canonicalName, spec.isOld, values)
        }
        return listOf(BleProtocolOperation.Write(JISSBON_WRITE_UUID, frame, withResponse = false))
    }
}

// FFE0/FFE1 是通用透传模组，很多品牌共用，识别必须再叠加杰士邦设备名 token，避免误抢其它品牌。
private fun BleGattFingerprint.hasJissbonGatt(): Boolean =
    serviceUuids.contains(JISSBON_SERVICE_UUID) && characteristicUuids.contains(JISSBON_WRITE_UUID)

// 官方扫描 localName 关键字（getDeviceList filter）：只保留能唯一指向杰士邦的强 token，
// 避免 "Vibrator"/"Egg" 这类泛词误命中其它品牌。表内 canonicalName 直接命中优先。
private fun BleGattFingerprint.jissbonProfileSpec(): JissbonProfileSpec? {
    val canonical = jissbonCanonicalName(name)
    JISSBON_PRODUCTS[canonical]?.let { return it }
    val lower = name.trim().lowercase()
    val isJissbon = lower.contains("jissbon") ||
            lower.contains("jsbbh") ||
            name.contains("JSB26_42") ||
            lower.startsWith("26-5")
    return if (isJissbon) JISSBON_FALLBACK_DUAL else null
}

// 未在产品表内、但确属杰士邦的设备回退双震动通用画像（双头跳蛋等最常见形态）。
private val JISSBON_FALLBACK_DUAL =
    JissbonProfileSpec("jissbon_generic_dual", "杰士邦 双震动玩具", "Jissbon Generic Dual", listOf(1, 2))

// 家族入口：命中杰士邦 GATT + 设备名后，按官方表精确识别型号；识别不到则回退双震动。
internal object JissbonProtocol : BleDeviceProtocol {
    override val status = JissbonProfileProtocol(JISSBON_FALLBACK_DUAL).status

    override fun matches(fingerprint: BleGattFingerprint): Boolean =
        fingerprint.hasJissbonGatt() && fingerprint.jissbonProfileSpec() != null

    override fun createInstance(fingerprint: BleGattFingerprint): BleDeviceProtocol =
        JissbonProfileProtocol(fingerprint.jissbonProfileSpec() ?: JISSBON_FALLBACK_DUAL)

    override fun commandsFor(action: ToyControlAction): List<BleProtocolOperation> =
        JissbonProfileProtocol(JISSBON_FALLBACK_DUAL).commandsFor(action)
}

// 命令形态候选：同一杰士邦设备，若自动识别帧「没反应」，按官方各 functionType 形态逐一重试。
// 命中条件与家族入口一致（GATT + 设备名），排在 JissbonProtocol 之后即可作为下一个候选被切换。
internal class JissbonVariantProtocol(private val spec: JissbonProfileSpec) : BleDeviceProtocol {
    private val delegate = JissbonProfileProtocol(spec)
    override val status = delegate.status
    override fun matches(fingerprint: BleGattFingerprint): Boolean =
        fingerprint.hasJissbonGatt() && fingerprint.jissbonProfileSpec() != null
    override fun commandsFor(action: ToyControlAction) = delegate.commandsFor(action)
}

internal val jissbonVariantProtocols: List<JissbonVariantProtocol> = listOf(
    JissbonProfileSpec("jissbon_variant_single", "杰士邦 单震动（备选）", "Jissbon Variant Single", listOf(1)),
    JissbonProfileSpec("jissbon_variant_triple", "杰士邦 三震动（备选）", "Jissbon Variant Triple", listOf(1, 2, 3)),
    JissbonProfileSpec("jissbon_variant_14", "杰士邦 双区 1+4（备选）", "Jissbon Variant 14", listOf(1, 4)),
    JissbonProfileSpec("jissbon_variant_16", "杰士邦 双区 1+6（备选）", "Jissbon Variant 16", listOf(1, 6)),
).map { JissbonVariantProtocol(it) }
