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

internal data class KissToyTutuIIRoute(
    val id: String,
    val displayName: String,
    val serviceUuid: Uuid,
    val writeUuid: Uuid,
    val notifyUuid: Uuid,
    val requireRouteForMatch: Boolean = true,
)

internal val KissToyTutuIIProtocol = KissToyTutuIIProtocolVariant(
    KissToyTutuIIRoute(
        id = "kisstoy_tutu2",
        displayName = "KissToy 突突二代",
        serviceUuid = Uuid.parse("0000ae3a-0000-1000-8000-00805f9b34fb"),
        writeUuid = Uuid.parse("0000ae3b-0000-1000-8000-00805f9b34fb"),
        notifyUuid = Uuid.parse("0000ae3c-0000-1000-8000-00805f9b34fb"),
    )
)

internal data class KissToyOfficialV2Motor(
    val type: Int,
    val label: String,
    val startUp: Double = 0.2,
)

internal data class KissToyOfficialV2Profile(
    val code: String,
    val displayName: String,
    val motors: List<KissToyOfficialV2Motor>,
    val toyType: Int,
    val channelNames: List<String> = motors.take(2).map { it.label },
    val intensityMax: Int = 100,
    val intensityLabel: String = "强度",
    val bleHeader: Int = 0x58,
    // QCTT 突突二代由独立 handler `KissToyTutuIIProtocol` 承接（保留 kisstoy_tutu2 id、专属通道名与保活，
    // 并按真机 btsnoop 做 challenge-response 认证与 15 31 b9 控制帧），与通用官方 V2 handler 互斥。
    val usesDedicatedTutuRoute: Boolean = false,
)

internal data class KissToyOfficialV2Route(
    val serviceUuid: Uuid?,
    val writeUuid: Uuid,
    val notifyUuid: Uuid?,
    val subscribeNotifyOnInit: Boolean = false,
)

internal object KissToyOfficialV2Protocol : BleDeviceProtocol {
    override val status = BleProtocolStatus(
        id = KISS_TOY_OFFICIAL_V2_PROTOCOL_ID,
        displayName = "KissToy",
        controllable = true,
        intensityMax = 100,
        supportsMode = false,
        controlStyle = ToyControlStyle.DualIntensityOnly,
        automatic = true,
    )

    override fun matches(fingerprint: BleGattFingerprint): Boolean {
        val profile = fingerprint.kissToyOfficialV2Profile() ?: return false
        return !profile.usesDedicatedTutuRoute && fingerprint.kissToyOfficialV2Route() != null
    }

    override fun createInstance(fingerprint: BleGattFingerprint): BleDeviceProtocol {
        val profile = fingerprint.kissToyOfficialV2Profile() ?: return this
        if (profile.usesDedicatedTutuRoute) return this
        val route = fingerprint.kissToyOfficialV2Route() ?: return this
        return Session(route, profile)
    }

    override fun commandsFor(action: ToyControlAction): List<BleProtocolOperation> = emptyList()

    private class Session(
        private val route: KissToyOfficialV2Route,
        private val profile: KissToyOfficialV2Profile,
    ) : BleDeviceProtocol {
        override val status = BleProtocolStatus(
            id = KISS_TOY_OFFICIAL_V2_PROTOCOL_ID,
            displayName = "KissToy ${profile.displayName}",
            controllable = true,
            intensityMax = profile.intensityMax,
            supportsMode = false,
            controlStyle = if (profile.motors.size >= 2) ToyControlStyle.DualIntensityOnly else ToyControlStyle.IntensityOnly,
            intensityLabel = profile.intensityLabel,
            channelNames = profile.channelNames,
            automatic = true,
        )

        override fun matches(fingerprint: BleGattFingerprint): Boolean =
            fingerprint.kissToyOfficialV2Route() == route &&
                fingerprint.kissToyOfficialV2Profile()?.code == profile.code

        override fun initialize(fingerprint: BleGattFingerprint): List<BleProtocolOperation> =
            route.notifyUuid
                ?.takeIf { route.subscribeNotifyOnInit }
                ?.let { listOf(BleProtocolOperation.SubscribeNotify(it)) }
                ?: emptyList()

        override fun commandsFor(action: ToyControlAction): List<BleProtocolOperation> =
            profile.commandsForOfficialV2(
                action = action,
                intensityMax = status.intensityMax,
                write = ::write,
            )

        private fun write(bytes: ByteArray): BleProtocolOperation.Write =
            BleProtocolOperation.Write(
                characteristicUuid = route.writeUuid,
                bytes = bytes,
                withResponse = false,
            )
    }
}

internal class KissToyTutuIIProtocolVariant(
    private val route: KissToyTutuIIRoute,
) : BleDeviceProtocol {
    override val status = BleProtocolStatus(
        id = route.id,
        displayName = route.displayName,
        controllable = true,
        intensityMax = 100,
        supportsMode = false,
        controlStyle = ToyControlStyle.DualIntensityOnly,
        intensityLabel = "强度",
        channelNames = listOf("底座伸缩", "头部伸缩"),
        automatic = true,
    )

    override fun matches(fingerprint: BleGattFingerprint): Boolean {
        val profile = fingerprint.kissToyOfficialV2Profile() ?: return false
        return profile.usesDedicatedTutuRoute && fingerprint.hasRoute(route)
    }

    override fun createInstance(fingerprint: BleGattFingerprint): BleDeviceProtocol =
        KissToyTutuIIProtocolSession(route, status)

    override fun commandsFor(action: ToyControlAction): List<BleProtocolOperation> = emptyList()

    private class KissToyTutuIIProtocolSession(
        private val route: KissToyTutuIIRoute,
        override val status: BleProtocolStatus,
    ) : BleDeviceProtocol {
        private var authenticated = false

        override fun matches(fingerprint: BleGattFingerprint): Boolean =
            fingerprint.kissToyOfficialV2Profile()?.usesDedicatedTutuRoute == true && fingerprint.hasRoute(route)

        // 突突二代帧格式与认证按真机 btsnoop 抓包（OWALabuy/ai-love-machines）：
        // 订阅 AE3C 后设备下发 13 字节挑战 `58 c0..c11`，客户端回 `58 + XOR(challenge[1:13], K_FIXED)` 完成认证，
        // 之后才接受电机控制帧。认证前必须等待挑战通知，因此设 ready gate。
        override fun initialize(fingerprint: BleGattFingerprint): List<BleProtocolOperation> =
            listOf(
                BleProtocolOperation.SubscribeNotify(route.notifyUuid),
                BleProtocolOperation.WaitForProtocolReady(KISS_TOY_TUTU_AUTH_TIMEOUT_MS),
            )

        override fun onNotify(characteristicUuid: Uuid, bytes: ByteArray): List<BleProtocolOperation> {
            if (characteristicUuid != route.notifyUuid || authenticated) return emptyList()
            if (bytes.size < KISS_TOY_TUTU_PACKET_SIZE || bytes[0] != KISS_TOY_TUTU_PACKET_PREFIX) return emptyList()
            authenticated = true
            return listOf(
                write(kissToyTutuAuthResponse(bytes)),
                BleProtocolOperation.Sleep(KISS_TOY_TUTU_AUTH_SETTLE_MS),
            )
        }

        override fun isProtocolReady(): Boolean = authenticated

        override fun keepaliveIntervalMs(): Long = KISS_TOY_TUTU_REPEAT_MS.toLong()

        override fun commandsFor(action: ToyControlAction): List<BleProtocolOperation> {
            val (motor1, motor2) = tutuMotorSpeeds(action)
            return listOf(write(kissToyTutuMotorPacket(motor1, motor2)))
        }

        // 通道语义按官方设备表 QCTT motors=[type1, type4]：底座伸缩=type1、头部伸缩=type4。
        // 帧内 byte4=Motor1(头部/thrusting)、byte5=Motor2(底座/vibration)，映射到界面通道时对应回填。
        private fun tutuMotorSpeeds(action: ToyControlAction): Pair<Int, Int> =
            when (action) {
                is ToyControlAction.DualMotor ->
                    action.externalIntensity.scaleTutuSpeed() to action.internalIntensity.scaleTutuSpeed()
                is ToyControlAction.Intensity -> action.value.scaleTutuSpeed().let { it to it }
                is ToyControlAction.Combined -> action.intensity.scaleTutuSpeed().let { it to it }
                is ToyControlAction.Pattern -> 50.scaleTutuSpeed().let { it to it }
                ToyControlAction.Stop -> 0 to 0
            }

        private fun Int.scaleTutuSpeed(): Int =
            (coerceIn(0, status.intensityMax) * 255 / status.intensityMax.coerceAtLeast(1)).coerceIn(0, 255)

        private fun write(bytes: ByteArray): BleProtocolOperation.Write =
            BleProtocolOperation.Write(
                characteristicUuid = route.writeUuid,
                bytes = bytes,
                withResponse = false,
            )
    }

}

// 认证响应：`58 + XOR(challenge[1:13], K_FIXED)`，K_FIXED 为会话无关的固定 12 字节共享密钥。
internal fun kissToyTutuAuthResponse(challenge: ByteArray): ByteArray =
    byteArrayOf(KISS_TOY_TUTU_PACKET_PREFIX) +
        challenge.copyOfRange(1, KISS_TOY_TUTU_PACKET_SIZE).xorKissToyTutuFixedKey()

// 电机控制明文 `15 31 b9 <seq> <M1> <M2> <seq> d0 <seq> 16 16 69`，再整包异或 K_FIXED 加 `0x58` 头。
// seq 位设备不校验，固定 0；M1=byte4（头部/thrusting）、M2=byte5（底座/vibration）。
internal fun kissToyTutuMotorPacket(motor1: Int, motor2: Int): ByteArray {
    val plaintext = intArrayOf(
        0x15, 0x31, 0xb9, 0x00,
        motor1 and 0xff, motor2 and 0xff,
        0x00, 0xd0, 0x00, 0x16, 0x16, 0x69,
    )
    return byteArrayOf(KISS_TOY_TUTU_PACKET_PREFIX) +
        ByteArray(plaintext.size) { index ->
            (plaintext[index] xor KISS_TOY_TUTU_FIXED_KEY[index].toInt()).toByte()
        }
}

internal fun ByteArray.xorKissToyTutuFixedKey(): ByteArray =
    ByteArray(size.coerceAtMost(KISS_TOY_TUTU_FIXED_KEY.size)) { index ->
        (this[index].toInt() xor KISS_TOY_TUTU_FIXED_KEY[index].toInt()).toByte()
    }

internal fun BleGattFingerprint.hasRoute(route: KissToyTutuIIRoute): Boolean =
    serviceUuids.contains(route.serviceUuid) &&
        characteristicUuids.contains(route.writeUuid) &&
        characteristicUuids.contains(route.notifyUuid)

internal fun BleGattFingerprint.kissToyOfficialV2Route(): KissToyOfficialV2Route? {
    KISS_TOY_OFFICIAL_V2_DISCOVERY_ROUTES.firstOrNull {
        it.serviceUuid == KISS_TOY_OFFICIAL_V2_AE3A_SERVICE_UUID && hasKissToyOfficialV2Route(it)
    }?.let { return it }

    preferredWriteUuid?.let { writeUuid ->
        val serviceUuid = preferredServiceUuid
        val notifyUuid = preferredNotifyUuid
        if (
            (serviceUuid == null || serviceUuids.contains(serviceUuid)) &&
            characteristicUuids.contains(writeUuid) &&
            (notifyUuid == null || characteristicUuids.contains(notifyUuid)) &&
            !isKissToyHistoricalGattRoute(serviceUuid, writeUuid)
        ) {
            return KissToyOfficialV2Route(
                serviceUuid = serviceUuid,
                writeUuid = writeUuid,
                notifyUuid = notifyUuid,
                subscribeNotifyOnInit = isKissToyOfficialV2Ae3aRoute(serviceUuid, writeUuid, notifyUuid),
            )
        }
    }
    return KISS_TOY_OFFICIAL_V2_DISCOVERY_ROUTES.firstOrNull { hasKissToyOfficialV2Route(it) }
}

internal fun BleGattFingerprint.hasKissToyOfficialV2Route(route: KissToyOfficialV2Route): Boolean {
    val serviceUuid = route.serviceUuid
    val writeUuid = route.writeUuid
    val notifyUuid = route.notifyUuid
    if (serviceUuid != null && !serviceUuids.contains(serviceUuid)) return false
    if (!characteristicUuids.contains(writeUuid)) return false
    if (notifyUuid != null && !characteristicUuids.contains(notifyUuid)) return false
    if (isKissToyHistoricalGattRoute(serviceUuid, writeUuid)) return false
    return true
}

internal fun isKissToyHistoricalGattRoute(serviceUuid: Uuid?, writeUuid: Uuid): Boolean =
    serviceUuid == KISS_TOY_HISTORICAL_GATT_SERVICE_UUID ||
        writeUuid == KISS_TOY_HISTORICAL_GATT_WRITE_UUID

internal fun isKissToyOfficialV2Ae3aRoute(serviceUuid: Uuid?, writeUuid: Uuid, notifyUuid: Uuid?): Boolean =
    serviceUuid == KISS_TOY_OFFICIAL_V2_AE3A_SERVICE_UUID &&
        writeUuid == KISS_TOY_OFFICIAL_V2_AE3A_WRITE_UUID &&
        notifyUuid == KISS_TOY_OFFICIAL_V2_AE3A_NOTIFY_UUID

internal fun BleGattFingerprint.kissToyOfficialV2Profile(): KissToyOfficialV2Profile? {
    val normalizedName = name.normalizedDeviceName()
    return KISS_TOY_OFFICIAL_V2_PROFILES[normalizedName]
}

internal fun KissToyOfficialV2Profile.commandsForOfficialV2(
    action: ToyControlAction,
    intensityMax: Int,
    write: (ByteArray) -> BleProtocolOperation.Write,
): List<BleProtocolOperation> =
    when (action) {
        is ToyControlAction.DualMotor -> runOfficialV2(dualIntensities(action.internalIntensity, action.externalIntensity), intensityMax, write)
        is ToyControlAction.Intensity -> runOfficialV2(sameIntensities(action.value), intensityMax, write)
        is ToyControlAction.Combined -> runOfficialV2(sameIntensities(action.intensity), intensityMax, write)
        is ToyControlAction.Pattern -> runOfficialV2(sameIntensities(50), intensityMax, write)
        ToyControlAction.Stop -> stopOfficialV2(write)
    }

internal fun KissToyOfficialV2Profile.runOfficialV2(
    intensities: List<Int>,
    intensityMax: Int,
    write: (ByteArray) -> BleProtocolOperation.Write,
): List<BleProtocolOperation> =
    listOf(write(kissToyOfficialV2BasicControlPacket(intensities, intensityMax)))

internal fun KissToyOfficialV2Profile.stopOfficialV2(
    write: (ByteArray) -> BleProtocolOperation.Write,
): List<BleProtocolOperation> =
    listOf(write(kissToyOfficialV2BasicControlPacket(List(motors.size) { 0 }, intensityMax = 100)))

internal fun KissToyOfficialV2Profile.sameIntensities(value: Int): List<Int> =
    List(motors.size) { value }

internal fun KissToyOfficialV2Profile.dualIntensities(first: Int, second: Int): List<Int> =
    motors.mapIndexed { index, _ ->
        when (index) {
            0 -> first
            1 -> second
            else -> first.coerceAtLeast(second)
        }
    }

internal fun KissToyOfficialV2Profile.kissToyOfficialV2BasicControlPacket(
    intensities: List<Int>,
    intensityMax: Int,
): ByteArray {
    val content = IntArray(KISS_TOY_BASIC_CONTROL_CONTENT_SIZE)
    motors.zip(intensities).forEach { (motor, intensity) ->
        val value = intensity.scaleKissToyOfficialV2BasicControlValue(intensityMax, motor.startUp)
        when (motor.type) {
            1 -> content[0] = value
            2 -> if (code == "QCVW") content[2] = value else content[1] = value
            3, 6 -> content[2] = value
            4 -> content[3] = value
            5 -> content[4] = value
        }
    }
    val data = IntArray(KISS_TOY_BASIC_CONTROL_PACKET_DATA_SIZE)
    data[0] = bleHeader and 0xff
    data[1] = KISS_TOY_BASIC_CONTROL_CMD
    data[2] = KISS_TOY_BASIC_CONTROL_LENGTH
    data[3] = if (data[0] == 0x60) 1 else 0
    content.copyInto(data, destinationOffset = 4)
    data[data.lastIndex] = data.take(data.lastIndex).sum() and 0xff
    return byteArrayOf(data[0].toByte()) + kissToyBleEncryptionSendBytes(data.drop(1))
}

internal fun Int.scaleKissToyOfficialV2BasicControlValue(intensityMax: Int, startUp: Double): Int {
    val normalized = coerceIn(0, intensityMax).toDouble() / intensityMax.coerceAtLeast(1).toDouble()
    val adjusted = if (normalized <= 0.0) 0.0 else startUp.coerceIn(0.0, 1.0) + normalized * (1.0 - startUp.coerceIn(0.0, 1.0))
    return (adjusted.coerceIn(0.0, 1.0) * 255.0).toInt().coerceIn(0, 255)
}

internal fun kissToyBleEncryptionSendBytes(data: List<Int>): ByteArray {
    val raw = IntArray(KISS_TOY_BLE_ENCRYPTION_FRAME_SIZE)
    raw[0] = Random.nextInt(0x100)
    data.take(KISS_TOY_BLE_ENCRYPTION_FRAME_SIZE - 1).forEachIndexed { index, value ->
        raw[index + 1] = value and 0xff
    }
    return kissToyBleEncryptionEncrypt(raw)
}

internal fun kissToyBleEncryptionEncrypt(raw: IntArray): ByteArray {
    require(raw.size == KISS_TOY_BLE_ENCRYPTION_FRAME_SIZE)
    val encrypted = IntArray(KISS_TOY_BLE_ENCRYPTION_FRAME_SIZE)
    encrypted[0] = raw[0] and 0xff
    val seed = encrypted[0]
    for (index in 1 until KISS_TOY_BLE_ENCRYPTION_FRAME_SIZE) {
        val key = KISS_TOY_BLE_ENCRYPTION_KEY_TAB[encrypted[index - 1] and 0x03][index]
        encrypted[index] = (key + (key xor seed xor raw[index])) and 0xff
    }
    return encrypted.map { it.toByte() }.toByteArray()
}

private const val KISS_TOY_OFFICIAL_V2_PROTOCOL_ID = "kisstoy_official_v2"
// 突突二代真机 btsnoop 常量：固定共享密钥、13 字节包、挑战头 0x58。
internal val KISS_TOY_TUTU_FIXED_KEY = bytes(0xea, 0x30, 0xbb, 0xdb, 0xad, 0xb6, 0xc6, 0x2f, 0xcf, 0xe9, 0xe9, 0x96)
private const val KISS_TOY_TUTU_PACKET_PREFIX: Byte = 0x58
private const val KISS_TOY_TUTU_PACKET_SIZE = 13
private const val KISS_TOY_TUTU_AUTH_TIMEOUT_MS = 4_000L
private const val KISS_TOY_TUTU_AUTH_SETTLE_MS = 1_000L
internal val KISS_TOY_HISTORICAL_GATT_SERVICE_UUID = Uuid.parse("00001000-0000-1000-8000-00805f9b34fb")
internal val KISS_TOY_HISTORICAL_GATT_WRITE_UUID = Uuid.parse("00001001-0000-1000-8000-00805f9b34fb")
internal val KISS_TOY_HISTORICAL_GATT_NOTIFY_UUID = Uuid.parse("00001002-0000-1000-8000-00805f9b34fb")
internal val KISS_TOY_OFFICIAL_V2_AE3A_SERVICE_UUID = Uuid.parse("0000ae3a-0000-1000-8000-00805f9b34fb")
internal val KISS_TOY_OFFICIAL_V2_AE3A_WRITE_UUID = Uuid.parse("0000ae3b-0000-1000-8000-00805f9b34fb")
internal val KISS_TOY_OFFICIAL_V2_AE3A_NOTIFY_UUID = Uuid.parse("0000ae3c-0000-1000-8000-00805f9b34fb")
internal val KISS_TOY_OFFICIAL_V2_DISCOVERY_ROUTES = listOf(
    KissToyOfficialV2Route(
        serviceUuid = KISS_TOY_OFFICIAL_V2_AE3A_SERVICE_UUID,
        writeUuid = KISS_TOY_OFFICIAL_V2_AE3A_WRITE_UUID,
        notifyUuid = KISS_TOY_OFFICIAL_V2_AE3A_NOTIFY_UUID,
        subscribeNotifyOnInit = true,
    ),
    KissToyOfficialV2Route(
        serviceUuid = Uuid.parse("0000dddd-0000-1000-8000-00805f9b34fb"),
        writeUuid = Uuid.parse("0000ddd1-0000-1000-8000-00805f9b34fb"),
        notifyUuid = Uuid.parse("0000ddd2-0000-1000-8000-00805f9b34fb"),
    ),
    KissToyOfficialV2Route(
        serviceUuid = Uuid.parse("0000ffe0-0000-1000-8000-00805f9b34fb"),
        writeUuid = Uuid.parse("0000ffe1-0000-1000-8000-00805f9b34fb"),
        notifyUuid = Uuid.parse("0000ffe2-0000-1000-8000-00805f9b34fb"),
    ),
)
private const val KISS_TOY_TUTU_REPEAT_MS = 500
private const val KISS_TOY_BASIC_CONTROL_CMD = 0x02
private const val KISS_TOY_BASIC_CONTROL_LENGTH = 10
private const val KISS_TOY_BASIC_CONTROL_CONTENT_SIZE = 5
private const val KISS_TOY_BASIC_CONTROL_PACKET_DATA_SIZE = 10
private const val KISS_TOY_BLE_ENCRYPTION_FRAME_SIZE = 12
internal val KISS_TOY_BLE_ENCRYPTION_KEY_TAB = arrayOf(
    intArrayOf(0x18, 0x5a, 0x64, 0x42, 0x10, 0xc6, 0xf6, 0x86, 0xb2, 0xd4, 0xfe, 0x92),
    intArrayOf(0xec, 0xb0, 0xbc, 0x0e, 0xc8, 0x7a, 0x54, 0xee, 0x00, 0x9a, 0x5a, 0x7a),
    intArrayOf(0x6e, 0x02, 0x06, 0xbc, 0xa6, 0x24, 0x4c, 0x2e, 0xca, 0xfc, 0x60, 0x72),
    intArrayOf(0xfa, 0x84, 0x26, 0x50, 0x6c, 0x4a, 0xe8, 0xd2, 0x88, 0x3a, 0x98, 0x9e),
)
internal val KISS_TOY_OFFICIAL_V2_TYPE_1 = KissToyOfficialV2Motor(1, "震动")
internal val KISS_TOY_OFFICIAL_V2_TYPE_2 = KissToyOfficialV2Motor(2, "第二震动")
internal val KISS_TOY_OFFICIAL_V2_TYPE_3 = KissToyOfficialV2Motor(3, "吮吸")
internal val KISS_TOY_OFFICIAL_V2_TYPE_4 = KissToyOfficialV2Motor(4, "伸缩")
internal val KISS_TOY_OFFICIAL_V2_TYPE_6 = KissToyOfficialV2Motor(6, "拍打")
internal val KISS_TOY_TUTU_CHANNEL_NAMES = listOf("底座伸缩", "头部伸缩")
internal val KISS_TOY_TUTU_OFFICIAL_V2_PROFILE = KissToyOfficialV2Profile(
    code = "QCTT",
    displayName = "突突二代",
    motors = listOf(KISS_TOY_OFFICIAL_V2_TYPE_1, KISS_TOY_OFFICIAL_V2_TYPE_4),
    toyType = 8,
    channelNames = KISS_TOY_TUTU_CHANNEL_NAMES,
    // 该 profile 仅用于按产品码 QCTT 识别与展示（通道名等）；控制帧由 KissToyTutuIIProtocolSession
    // 按真机 btsnoop 的 `15 31 b9 … 16 16 69` + XOR(K_FIXED) 格式独立构造，不走通用 V2 的 BasicControlMode。
    usesDedicatedTutuRoute = true,
)
internal val KISS_TOY_OFFICIAL_V2_PROFILES = listOf(
    KissToyOfficialV2Profile("JY11", "太正鲸", listOf(KISS_TOY_OFFICIAL_V2_TYPE_1), toyType = 1),
    KissToyOfficialV2Profile("SYQ1", "糖蛋蛋", listOf(KISS_TOY_OFFICIAL_V2_TYPE_1), toyType = 3),
    KissToyOfficialV2Profile("PJ01", "炮机", listOf(KISS_TOY_OFFICIAL_V2_TYPE_4), toyType = 2),
    KissToyOfficialV2Profile("KX01", "Cathy Pro", listOf(KISS_TOY_OFFICIAL_V2_TYPE_3, KISS_TOY_OFFICIAL_V2_TYPE_4), toyType = 4),
    KissToyOfficialV2Profile(
        "KX02",
        "Cathy III",
        listOf(KISS_TOY_OFFICIAL_V2_TYPE_3.copy(startUp = 0.4), KISS_TOY_OFFICIAL_V2_TYPE_4),
        toyType = 4,
    ),
    KissToyOfficialV2Profile("PLY1", "Polly pro", listOf(KISS_TOY_OFFICIAL_V2_TYPE_1, KISS_TOY_OFFICIAL_V2_TYPE_3), toyType = 5),
    KissToyOfficialV2Profile(
        "QCKH",
        "BOBO",
        listOf(KISS_TOY_OFFICIAL_V2_TYPE_3),
        toyType = 6,
    ),
    KissToyOfficialV2Profile("PLMX", "Polly max", listOf(KISS_TOY_OFFICIAL_V2_TYPE_1, KISS_TOY_OFFICIAL_V2_TYPE_3.copy(startUp = 0.4)), toyType = 7),
    KissToyOfficialV2Profile("QCPJ", "TUTU", listOf(KISS_TOY_OFFICIAL_V2_TYPE_1, KISS_TOY_OFFICIAL_V2_TYPE_4), toyType = 8),
    KissToyOfficialV2Profile("QCPW", "迷路-入体版", listOf(KISS_TOY_OFFICIAL_V2_TYPE_1, KISS_TOY_OFFICIAL_V2_TYPE_3), toyType = 9),
    KissToyOfficialV2Profile("QCSW", "迷路-吮吸版", listOf(KISS_TOY_OFFICIAL_V2_TYPE_1, KISS_TOY_OFFICIAL_V2_TYPE_3), toyType = 10),
    KissToyOfficialV2Profile("QCVW", "迷路-震动版", listOf(KISS_TOY_OFFICIAL_V2_TYPE_1, KISS_TOY_OFFICIAL_V2_TYPE_2), toyType = 11),
    KissToyOfficialV2Profile("QCFW", "迷路-拍打版", listOf(KISS_TOY_OFFICIAL_V2_TYPE_1, KISS_TOY_OFFICIAL_V2_TYPE_6), toyType = 14),
    KISS_TOY_TUTU_OFFICIAL_V2_PROFILE,
    KissToyOfficialV2Profile("QCFBH25", "粉饼盒", listOf(KISS_TOY_OFFICIAL_V2_TYPE_1), toyType = 1),
    KissToyOfficialV2Profile("KXMINI25", "Cathy Mini", listOf(KISS_TOY_OFFICIAL_V2_TYPE_1, KISS_TOY_OFFICIAL_V2_TYPE_3), toyType = 10),
    KissToyOfficialV2Profile("PLY5", "Polly 5", listOf(KISS_TOY_OFFICIAL_V2_TYPE_1, KISS_TOY_OFFICIAL_V2_TYPE_3), toyType = 12),
).associateBy { it.code.normalizedDeviceName() }

internal object KissToyProtocol : BleDeviceProtocol {
    private val knownNames = setOf(
        "JY11",
        "SYQ1",
        "PJ01",
        "KX01",
        "KX02",
        "PLY1",
        "QCKH",
        "PLMX",
        "QCPJ",
        "QCPW",
        "QCSW",
        "QCVW",
        "HMML",
        "Dual-SPP",
    )

    override val status = BleProtocolStatus(
        id = "kisstoy_gatt",
        displayName = "KissToy",
        controllable = true,
        intensityMax = 100,
        supportsMode = true,
        modeMax = 4,
        controlStyle = ToyControlStyle.CombinedPatternAndIntensity,
        modeNames = listOf("主电机", "第二电机", "抽插", "双通道"),
        automatic = true,
    )

    override fun matches(fingerprint: BleGattFingerprint): Boolean {
        if (fingerprint.kissToyOfficialV2Profile()?.usesDedicatedTutuRoute == true) return false
        if (!fingerprint.hasKissToyHistoricalGattRoute()) return false
        val knownName = knownNames.any { it.equals(fingerprint.name, ignoreCase = true) }
        val hasNotify = fingerprint.characteristicUuids.contains(KISS_TOY_HISTORICAL_GATT_NOTIFY_UUID)
        return knownName || hasNotify
    }

    override fun commandsFor(action: ToyControlAction): List<BleProtocolOperation> =
        when (action) {
            is ToyControlAction.Combined -> run(action.mode, action.intensity)
            is ToyControlAction.Intensity -> run(1, action.value)
            is ToyControlAction.DualMotor -> run(action.mode, action.strongestIntensity(status.intensityMax))
            is ToyControlAction.Pattern -> run(action.mode, 30)
            ToyControlAction.Stop -> stop()
        }

    private fun run(mode: Int, intensity: Int): List<BleProtocolOperation> {
        val normalizedMode = mode.coerceIn(1, status.modeMax)
        val normalizedIntensity = intensity.coerceIn(0, 100)
        val clearCommands = when (normalizedMode) {
            1 -> listOf(command(0x32, 0), command(0x71, 0))
            2 -> listOf(command(0x31, 0), command(0x71, 0))
            3 -> listOf(command(0x31, 0), command(0x32, 0))
            else -> listOf(command(0x71, 0))
        }
        val runCommands = if (normalizedMode == 4) {
            listOf(
                command(0x32, normalizedIntensity),
                command(0x31, normalizedIntensity),
            )
        } else {
            listOf(commandForMode(normalizedMode, normalizedIntensity))
        }
        return clearCommands + runCommands
    }

    private fun stop(): List<BleProtocolOperation> =
        listOf(
            command(0x40, 0, 0),
            command(0x31, 0),
            command(0x32, 0),
            command(0x71, 0),
        )

    private fun command(command: Int, first: Int, second: Int = 0): BleProtocolOperation.Write =
        kissToyHistoricalCommand(command, first, second)

    private fun commandForMode(mode: Int, intensity: Int): BleProtocolOperation.Write =
        when (mode) {
            1 -> command(0x31, intensity)
            2 -> command(0x32, intensity)
            3 -> command(0x71, intensity)
            else -> command(0x31, intensity)
        }
}

internal fun BleGattFingerprint.hasKissToyHistoricalGattRoute(): Boolean =
    serviceUuids.contains(KISS_TOY_HISTORICAL_GATT_SERVICE_UUID) &&
        characteristicUuids.contains(KISS_TOY_HISTORICAL_GATT_WRITE_UUID)

internal fun kissToyHistoricalCommand(command: Int, first: Int, second: Int = 0): BleProtocolOperation.Write =
    BleProtocolOperation.Write(
        characteristicUuid = KISS_TOY_HISTORICAL_GATT_WRITE_UUID,
        bytes = kissToyHistoricalEncrypt(kissToyHistoricalPayload(command, first, second)),
        withResponse = true,
    )

internal fun kissToyHistoricalPayload(command: Int, first: Int, second: Int = 0): ByteArray =
    byteArrayOf(
        0x5A,
        0x00,
        0x00,
        0x01,
        command.toByte(),
        if (command == 0x40) 0x03 else first.toByte(),
        if (command == 0x40) first.toByte() else second.toByte(),
        if (command == 0x40) second.toByte() else 0x00,
        0x00,
        0x00,
    )

internal fun kissToyHistoricalEncrypt(payload: ByteArray): ByteArray {
    val plain = ByteArray(12)
    plain[0] = 0x23
    payload.copyInto(
        plain,
        destinationOffset = 1,
        startIndex = 0,
        endIndex = payload.size.coerceAtMost(10)
    )
    plain[11] = plain.take(11).sumOf { it.toInt() }.toByte()
    val encrypted = ByteArray(12)
    val seed = plain[0]
    encrypted[0] = seed
    for (index in 1 until encrypted.size) {
        val key = kissToyHistoricalKey(encrypted[index - 1], index)
        encrypted[index] =
            (((key.toInt() xor seed.toInt()) xor plain[index].toInt()) + key).toByte()
    }
    return encrypted
}

internal fun kissToyHistoricalKey(previous: Byte, index: Int): Byte =
    KISS_TOY_HISTORICAL_KEY_TAB[previous.toInt() and 0x03][index]

internal val KISS_TOY_HISTORICAL_KEY_TAB = arrayOf(
    byteArrayOf(0, 24, -104, -9, -91, 61, 13, 41, 37, 80, 68, 70),
    byteArrayOf(0, 69, 110, 106, 111, 120, 32, 83, 45, 49, 46, 55),
    byteArrayOf(0, 101, 120, 32, 84, 111, 121, 115, 10, -114, -99, -93),
    byteArrayOf(0, -59, -42, -25, -8, 10, 50, 32, 111, 98, 13, 10),
)

