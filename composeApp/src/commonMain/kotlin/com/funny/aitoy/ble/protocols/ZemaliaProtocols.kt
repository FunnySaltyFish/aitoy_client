package com.funny.aitoy.ble

import kotlin.uuid.Uuid

// ZEMALIA（枕木恋）是司康沃 V2 底层库换皮：同一套 FFE0/FFE1/FFE2 GATT、SVA(53 56 41) 广播前缀、
// `55 CMD 00 00 <档位> <强度> 00` 帧格式。识别按官方 manufacturer 广播里的 V2 产品码分流到各型号功能区。
// 帧命令字：震动 0x03、伸缩/旋转 0x08、拍打 0x07、吮吸 0x09、舌舔 0x14、加热 0x05、全局停止 0x04。
// 证据：ref/zemalia-apk .../connect/ble/BleManager.java、ProductBean.getCurrentProductMode()、
//       ZEMALIAWhiteList.java、auto/modes/base/BaseAutoModeLayout.java、auto/modes/auto_v2/*View.java。

// 舌舔功能：命令字 0x14(20)，仅 ZT758A(373) 使用，见 AutoV2LickingView。
internal val ZemaliaLicking =
    SvakomV2Function(6, "licking", "舌舔", 0x14, modeMax = 8, intensityMax = 1, strengthDriven = false)

// 功能派生：strength=null 表示官方无强度滑杆(isShowStrong=false)，帧强度字节恒 0、靠模式启停；
// strength=N 表示有强度滑杆，强度 0..N。code 固定保证同一设备内各功能可寻址（session 按 code 存状态）。
private fun zVib(mode: Int, strength: Int? = null, strengthDisabledThroughMode: Int = 0) =
    SvakomV2Function(
        2,
        "vibrate",
        "震动",
        0x03,
        mode,
        strength ?: 1,
        strength != null,
        strengthDisabledThroughMode,
    )

private fun zSuck(
    mode: Int,
    strength: Int? = null,
    label: String = "吮吸",
    strengthDisabledThroughMode: Int = 0,
) = SvakomV2Function(
    3,
    "constrict",
    label,
    0x09,
    mode,
    strength ?: 1,
    strength != null,
    strengthDisabledThroughMode,
)

private fun zStretch(mode: Int, strength: Int? = null, label: String = "伸缩") =
    SvakomV2Function(1, "oscillate", label, 0x08, mode, strength ?: 1, strength != null)

private fun zFlap(
    mode: Int,
    strength: Int? = null,
    label: String = "拍打",
    strengthDisabledThroughMode: Int = 0,
) = SvakomV2Function(
    4,
    "flap",
    label,
    0x07,
    mode,
    strength ?: 1,
    strength != null,
    strengthDisabledThroughMode,
)

// ZEMALIA 各型号都走「操作哪个功能就只写该功能单帧」：官方每个功能是独立 View，
// selectModeIndex 只发当前功能帧，从不连发其它功能帧（连发会让部分设备只响应首帧后卡死）。
internal class ZemaliaV2Profile(
    modelId: String,
    displayName: String,
    functionList: List<SvakomV2Function>,
    default: SvakomV2Function = functionList.first(),
) : SvakomV2Profile(
    status = svakomV2Status(modelId, displayName, functionList),
    functions = functionList,
    defaultFunction = default,
    writeCurrentStateOnUpdate = false,
)

// 按官方 manufacturer 广播 V2 产品码分流。产品码 → ProductMode → 功能区取自：
// ProductBean.getCurrentProductMode()（码→型号）、BaseAutoModeLayout / OrderingV2Layout /
// OrderingV2_278_Layout（型号→功能区、档数、强度、命令字）。
// 套装型号（含另一侧 subConnect 的）直接暴露两侧全部功能：只写用户实际操作的功能帧，
// 另一侧未在线时该帧被主设备忽略，不影响；这样两侧任一在线都能控制，最大化可用。
internal fun zemaliaV2Profile(productCode: Int): ZemaliaV2Profile? =
    when (productCode) {
        // 震动(9档/强度10) + 吮吸(8档/无强度) 套装
        35 -> ZemaliaV2Profile("zemalia_zx258a", "ZEMALIA 震动吮吸", listOf(zVib(9, 10), zSuck(8)))
        37 -> ZemaliaV2Profile("zemalia_zt461a", "ZEMALIA 震动吮吸", listOf(zVib(9, 10), zSuck(8)))
        99 -> ZemaliaV2Profile("zemalia_zt729a_v", "ZEMALIA 震动吮吸", listOf(zVib(9, 10), zSuck(8)))
        36 -> ZemaliaV2Profile("zemalia_zx258a_s", "ZEMALIA 吮吸震动", listOf(zSuck(8), zVib(9, 10)))
        // 吮吸(8档/强度10) + 震动(9档/强度10)
        98 -> ZemaliaV2Profile("zemalia_zt729a_s", "ZEMALIA 吮吸震动", listOf(zSuck(8, 10), zVib(9, 10)))
        85 -> ZemaliaV2Profile("zemalia_zx571a", "ZEMALIA 吮吸震动", listOf(zSuck(8, 10), zVib(9, 10)))
        // 吮吸/震动 各 5 档强度5 套装
        66 -> ZemaliaV2Profile("zemalia_zx140a_v", "ZEMALIA 震动吮吸", listOf(zVib(5, 5), zSuck(5, 5)))
        67 -> ZemaliaV2Profile("zemalia_zx140a_s", "ZEMALIA 吮吸震动", listOf(zSuck(5, 5), zVib(5, 5)))
        // 吮吸/震动 各 8 档无强度 + 加热 套装
        80 -> ZemaliaV2Profile("zemalia_sxm07a_s", "ZEMALIA 吮吸震动加热", listOf(zSuck(8), zVib(8), SvakomV2Heat))
        81 -> ZemaliaV2Profile("zemalia_sxm07a_v", "ZEMALIA 震动吮吸加热", listOf(zVib(8), zSuck(8), SvakomV2Heat))
        // 震动/吮吸 各 6 档强度10 套装（ZY382）
        112 -> ZemaliaV2Profile("zemalia_zy382_v", "ZEMALIA 震动吮吸", listOf(zVib(6, 10), zSuck(6, 10)))
        113 -> ZemaliaV2Profile("zemalia_zy382_s", "ZEMALIA 吮吸震动", listOf(zSuck(6, 10), zVib(6, 10)))
        // 伸缩 + 震动
        114 -> ZemaliaV2Profile("zemalia_zt370a", "ZEMALIA 伸缩震动", listOf(zStretch(6, 10), zVib(6, 10)))
        358 -> ZemaliaV2Profile("zemalia_zt784a_1", "ZEMALIA 伸缩震动", listOf(zStretch(8), zVib(9, 10)))
        56 -> ZemaliaV2Profile("zemalia_vx634a", "ZEMALIA 伸缩吮吸震动", listOf(zStretch(8, 5), zSuck(8, 5), zVib(9, 10)))
        // 吮吸 + 灯光(不可控) / 加热
        50 -> ZemaliaV2Profile("zemalia_va617a_s", "ZEMALIA 吮吸", listOf(zSuck(8, 10)))
        51 -> ZemaliaV2Profile("zemalia_va617a_v", "ZEMALIA 震动", listOf(zVib(9, 10)))
        52 -> ZemaliaV2Profile("zemalia_zx531a", "ZEMALIA 吮吸加热", listOf(zSuck(8), SvakomV2Heat))
        // 舌舔标签的吮吸 + 震动（ZT433A）
        55 -> ZemaliaV2Profile("zemalia_zt433a", "ZEMALIA 舌舔震动", listOf(zSuck(8, 5, "舌舔"), zVib(9, 10)))
        // ZX650A 的三个功能在 1..3 档都关闭强度滑杆，官方帧强度字节必须固定为 0。
        87 -> ZemaliaV2Profile(
            "zemalia_zx650a",
            "ZEMALIA 吮吸拍打震动",
            listOf(
                zSuck(8, 10, strengthDisabledThroughMode = 3),
                zFlap(8, 10, strengthDisabledThroughMode = 3),
                zVib(9, 10, strengthDisabledThroughMode = 3),
            ),
        )
        // 拍打 + 吮吸 / 舌舔 + 吮吸
        117 -> ZemaliaV2Profile("zemalia_zt158e", "ZEMALIA 舌舔吮吸", listOf(zFlap(8, label = "舌舔"), zSuck(8)))
        364 -> ZemaliaV2Profile("zemalia_zt784a_2", "ZEMALIA 拍打吮吸", listOf(zFlap(8), zSuck(8)))
        // 单吮吸
        352 -> ZemaliaV2Profile("zemalia_zt799a", "ZEMALIA 吮吸", listOf(zSuck(8)))
        375 -> ZemaliaV2Profile("zemalia_zt742b", "ZEMALIA 吮吸", listOf(zSuck(8)))
        // 单震动（6档/强度8）
        365 -> ZemaliaV2Profile("zemalia_za801a", "ZEMALIA 震动", listOf(zVib(6, 8)))
        // 舌舔 + 吮吸 + 震动（ZT758A）
        373 -> ZemaliaV2Profile("zemalia_zt758a", "ZEMALIA 舌舔吮吸震动", listOf(ZemaliaLicking, zSuck(8), zVib(9)))
        else -> null
    }

// ZEMALIA / 枕木恋：司康沃 V2 换皮 app，按官方白名单产品码识别并分流到各型号功能区。
// 白名单未覆盖或未知的 SVA V2 设备回退到「震动 + 吮吸」通用面板（SVAKOM_V2_VIBRATE_SUCK），
// 保证识别到就能试控，用户反馈「没反应」时再按真机产品码精确补录。
internal object SvakomZemaliaProtocol : BleDeviceProtocol {
    private val serviceUuid = Uuid.parse("0000ffe0-0000-1000-8000-00805f9b34fb")
    private val writeUuid = Uuid.parse("0000ffe1-0000-1000-8000-00805f9b34fb")
    private val notifyUuid = Uuid.parse("0000ffe2-0000-1000-8000-00805f9b34fb")

    // 官方 ZEMALIAWhiteList.isZEMALIAWhiteList 收录的 V2 产品码，逐一有对应功能表。
    private val whitelist = setOf(
        35, 36, 37, 50, 51, 52, 55, 56, 66, 67, 80, 81, 85, 87, 98, 99,
        112, 113, 114, 117, 352, 358, 364, 365, 373, 375,
    )

    override val status = SVAKOM_V2_VIBRATE_SUCK.status

    override fun matches(fingerprint: BleGattFingerprint): Boolean {
        val hasSvakomGatt = fingerprint.serviceUuids.contains(serviceUuid) &&
                fingerprint.characteristicUuids.contains(writeUuid) &&
                fingerprint.characteristicUuids.contains(notifyUuid)
        val code = fingerprint.svakomProductCode()
        return code != null && code in whitelist &&
                hasSvakomGatt && fingerprint.hasSvakomManufacturerData()
    }

    override fun createInstance(fingerprint: BleGattFingerprint): BleDeviceProtocol {
        val code = fingerprint.svakomProductCode()
        val profile = code?.let { zemaliaV2Profile(it) } ?: SVAKOM_V2_VIBRATE_SUCK
        return SvakomV2Session(profile)
    }

    override fun commandsFor(action: ToyControlAction): List<BleProtocolOperation> = emptyList()
}
