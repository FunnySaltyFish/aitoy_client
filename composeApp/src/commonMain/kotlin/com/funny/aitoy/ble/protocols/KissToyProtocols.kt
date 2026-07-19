package com.funny.aitoy.ble

import com.funny.aitoy.buttplug.ButtplugConfigRepository
import com.funny.aitoy.buttplug.ButtplugDeviceFingerprint
import com.funny.aitoy.buttplug.ButtplugDeviceMatch
import com.funny.aitoy.buttplug.ButtplugLocalHandlerRegistry
import com.funny.aitoy.buttplug.Aes128EcbPkcs7
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
import com.funny.aitoy.core.utils.nowMs
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
        if (fingerprint.prefersKissToyHistoricalRoute(profile)) return false
        return !profile.usesDedicatedTutuRoute && fingerprint.kissToyOfficialV2Route() != null
    }

    override fun createInstance(fingerprint: BleGattFingerprint): BleDeviceProtocol {
        val profile = fingerprint.kissToyOfficialV2Profile() ?: return this
        if (fingerprint.prefersKissToyHistoricalRoute(profile)) return this
        if (profile.usesDedicatedTutuRoute) return this
        val route = fingerprint.kissToyOfficialV2Route() ?: return this
        return Session(route, profile)
    }

    override fun commandsFor(action: ToyControlAction): List<BleProtocolOperation> = emptyList()

    private class Session(
        private val route: KissToyOfficialV2Route,
        private val profile: KissToyOfficialV2Profile,
    ) : BleDeviceProtocol {
        private val controlState = KissToyOfficialV2BasicControlState(profile)

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
            buildList {
                route.notifyUuid
                    ?.takeIf { route.subscribeNotifyOnInit }
                    ?.let { add(BleProtocolOperation.SubscribeNotify(it)) }
                if (profile.bleHeader == KISS_TOY_AES_CCM_HEADER) {
                    kissToyAesCcmDeviceInfoHandshake().forEach { operation ->
                        add(
                            when (operation) {
                                is KissToyAesCcmDeviceInfoStep.Write -> write(operation.bytes)
                                is KissToyAesCcmDeviceInfoStep.Sleep -> BleProtocolOperation.Sleep(operation.millis)
                            }
                        )
                    }
                }
            }

        override fun commandsFor(action: ToyControlAction): List<BleProtocolOperation> =
            profile.commandsForOfficialV2(
                action = action,
                intensityMax = status.intensityMax,
                controlState = controlState,
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

internal fun BleGattFingerprint.prefersKissToyHistoricalRoute(profile: KissToyOfficialV2Profile): Boolean =
    profile.code == KISS_TOY_BOBO_CODE &&
        hasKissToyHistoricalGattRoute() &&
        hasKissToyOfficialV2Route(KISS_TOY_OFFICIAL_V2_AE3A_ROUTE)

internal fun KissToyOfficialV2Profile.commandsForOfficialV2(
    action: ToyControlAction,
    intensityMax: Int,
    controlState: KissToyOfficialV2BasicControlState = KissToyOfficialV2BasicControlState(this),
    write: (ByteArray) -> BleProtocolOperation.Write,
): List<BleProtocolOperation> =
    when (action) {
        is ToyControlAction.DualMotor -> runOfficialV2(dualIntensities(action.internalIntensity, action.externalIntensity), intensityMax, controlState, write)
        is ToyControlAction.Intensity -> runOfficialV2(sameIntensities(action.value), intensityMax, controlState, write)
        is ToyControlAction.Combined -> runOfficialV2(sameIntensities(action.intensity), intensityMax, controlState, write)
        is ToyControlAction.Pattern -> runOfficialV2(sameIntensities(50), intensityMax, controlState, write)
        ToyControlAction.Stop -> stopOfficialV2(controlState, write)
    }

internal fun KissToyOfficialV2Profile.runOfficialV2(
    intensities: List<Int>,
    intensityMax: Int,
    controlState: KissToyOfficialV2BasicControlState,
    write: (ByteArray) -> BleProtocolOperation.Write,
): List<BleProtocolOperation> =
    listOf(write(controlState.update(intensities, intensityMax)))

internal fun KissToyOfficialV2Profile.stopOfficialV2(
    controlState: KissToyOfficialV2BasicControlState,
    write: (ByteArray) -> BleProtocolOperation.Write,
): List<BleProtocolOperation> =
    controlState.stop().map(write)

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

internal class KissToyOfficialV2BasicControlState(
    private val profile: KissToyOfficialV2Profile,
) {
    private val content = IntArray(KISS_TOY_BASIC_CONTROL_CONTENT_SIZE)

    fun update(
        intensities: List<Int>,
        intensityMax: Int,
    ): ByteArray {
        profile.motors.zip(intensities).forEach { (motor, intensity) ->
            setMotor(motor, intensity, intensityMax)
        }
        return profile.kissToyOfficialV2BasicControlPacket(content)
    }

    fun stop(): List<ByteArray> =
        profile.motors.map { motor ->
            setMotor(motor, 0, 100)
            profile.kissToyOfficialV2BasicControlPacket(content)
        }

    private fun setMotor(
        motor: KissToyOfficialV2Motor,
        intensity: Int,
        intensityMax: Int,
    ) {
        val value = intensity.scaleKissToyOfficialV2BasicControlValue(intensityMax, motor.startUp)
        when (motor.type) {
            1 -> content[0] = value
            2 -> if (profile.code == "QCVW") content[2] = value else content[1] = value
            3, 6 -> content[2] = value
            4 -> content[3] = value
            5 -> content[4] = value
        }
    }
}

internal fun KissToyOfficialV2Profile.kissToyOfficialV2BasicControlPacket(
    content: IntArray,
): ByteArray {
    val data = IntArray(KISS_TOY_BASIC_CONTROL_PACKET_DATA_SIZE)
    data[0] = bleHeader and 0xff
    data[1] = KISS_TOY_BASIC_CONTROL_CMD
    data[2] = KISS_TOY_BASIC_CONTROL_LENGTH
    data[3] = if (data[0] == 0x60) 1 else 0
    content.copyInto(data, destinationOffset = 4)
    data[data.lastIndex] = data.take(data.lastIndex).sum() and 0xff
    if (data[0] == KISS_TOY_AES_CCM_HEADER) {
        return kissToyAesCcmBuildPacket(
            header = data[0],
            cmdId = data[1],
            subId = data[3],
            plaintext = content.map { it.toByte() }.toByteArray(),
        )
    }
    return byteArrayOf(data[0].toByte()) + kissToyBleEncryptionSendBytes(data.drop(1))
}

internal fun kissToyAesCcmBuildPacket(
    header: Int,
    cmdId: Int,
    subId: Int,
    plaintext: ByteArray,
    nonce: ByteArray = kissToyAesCcmNonce(),
): ByteArray {
    require(header in 0..0xff)
    require(cmdId in 0..0xff)
    require(subId in 0..0xff)
    require(nonce.size == KISS_TOY_AES_CCM_NONCE_SIZE)
    val encrypted = kissToyAesCcmEncrypt(
        key = KISS_TOY_AES_CCM_KEY,
        nonce = nonce,
        plaintext = plaintext,
        tagSize = KISS_TOY_AES_CCM_TAG_SIZE,
    )
    val length = nonce.size + encrypted.size
    require(length <= 0xff)
    return byteArrayOf(header.toByte(), cmdId.toByte(), subId.toByte(), length.toByte()) + nonce + encrypted
}

internal fun kissToyAesCcmGetDeviceMacAddressPacket(
    nonce: ByteArray = kissToyAesCcmNonce(),
): ByteArray =
    kissToyAesCcmDeviceInfoPacket(
        subId = KISS_TOY_AES_CCM_GET_DEVICE_MAC_SUB_ID,
        nonce = nonce,
    )

internal sealed interface KissToyAesCcmDeviceInfoStep {
    data class Write(val bytes: ByteArray) : KissToyAesCcmDeviceInfoStep
    data class Sleep(val millis: Long) : KissToyAesCcmDeviceInfoStep
}

internal fun kissToyAesCcmDeviceInfoHandshake(): List<KissToyAesCcmDeviceInfoStep> =
    listOf(
        KissToyAesCcmDeviceInfoStep.Write(kissToyAesCcmDeviceInfoPacket(KISS_TOY_AES_CCM_GET_DEVICE_MAC_SUB_ID)),
        KissToyAesCcmDeviceInfoStep.Sleep(KISS_TOY_AES_CCM_DEVICE_MAC_SETTLE_MS),
        KissToyAesCcmDeviceInfoStep.Write(kissToyAesCcmDeviceInfoPacket(KISS_TOY_AES_CCM_GET_DEVICE_CHIP_INFO_SUB_ID)),
        KissToyAesCcmDeviceInfoStep.Sleep(KISS_TOY_AES_CCM_DEVICE_INFO_SETTLE_MS),
        KissToyAesCcmDeviceInfoStep.Write(kissToyAesCcmDeviceInfoPacket(KISS_TOY_AES_CCM_GET_DEVELOPER_ID_SUB_ID)),
        KissToyAesCcmDeviceInfoStep.Sleep(KISS_TOY_AES_CCM_DEVICE_INFO_SETTLE_MS),
        KissToyAesCcmDeviceInfoStep.Write(kissToyAesCcmDeviceInfoPacket(KISS_TOY_AES_CCM_GET_DEVICE_ID_SUB_ID)),
        KissToyAesCcmDeviceInfoStep.Sleep(KISS_TOY_AES_CCM_DEVICE_INFO_SETTLE_MS),
    )

internal fun kissToyAesCcmDeviceInfoPacket(
    subId: Int,
    nonce: ByteArray = kissToyAesCcmNonce(),
): ByteArray =
    kissToyAesCcmBuildPacket(
        header = KISS_TOY_AES_CCM_HEADER,
        cmdId = KISS_TOY_AES_CCM_DEVICE_INFO_CMD,
        subId = subId,
        plaintext = byteArrayOf(),
        nonce = nonce,
    )

internal fun kissToyAesCcmEncrypt(
    key: ByteArray,
    nonce: ByteArray,
    plaintext: ByteArray,
    tagSize: Int,
): ByteArray {
    val tag = kissToyAesCcmAuthTag(key, nonce, plaintext, tagSize)
    val encrypted = kissToyAesCtrCrypt(key, nonce, plaintext)
    val s0 = Aes128EcbPkcs7.encryptBlock(key, kissToyAesCcmCounterBlock(nonce, counter = 0))
    val maskedTag = ByteArray(tagSize) { index -> (tag[index].unsigned() xor s0[index].unsigned()).toByte() }
    return encrypted + maskedTag
}

internal fun kissToyAesCcmDecrypt(
    key: ByteArray,
    nonce: ByteArray,
    encrypted: ByteArray,
    tagSize: Int,
): ByteArray {
    require(encrypted.size >= tagSize)
    val ciphertext = encrypted.copyOf(encrypted.size - tagSize)
    return kissToyAesCtrCrypt(key, nonce, ciphertext)
}

internal fun kissToyAesCcmNonce(
    deviceId: ByteArray = KISS_TOY_AES_CCM_DEFAULT_DEVICE_ID,
    timestampMs: Long = nowMs(),
): ByteArray {
    require(deviceId.size == KISS_TOY_AES_CCM_DEVICE_ID_SIZE)
    val timeBytes = ByteArray(Long.SIZE_BYTES) { index ->
        ((timestampMs ushr (8 * (Long.SIZE_BYTES - 1 - index))) and 0xff).toByte()
    }
    return timeBytes.copyOfRange(2, Long.SIZE_BYTES) + deviceId
}

private fun kissToyAesCcmAuthTag(
    key: ByteArray,
    nonce: ByteArray,
    plaintext: ByteArray,
    tagSize: Int,
): ByteArray {
    require(key.size == 16)
    require(nonce.size == KISS_TOY_AES_CCM_NONCE_SIZE)
    require(tagSize == 8)
    require(plaintext.size < (1 shl (8 * KISS_TOY_AES_CCM_LENGTH_FIELD_SIZE)))
    var state = Aes128EcbPkcs7.encryptBlock(key, kissToyAesCcmB0(nonce, plaintext.size, tagSize))
    plaintext.asIterable()
        .chunked(16)
        .forEach { chunk ->
            val block = ByteArray(16)
            chunk.forEachIndexed { index, value -> block[index] = value }
            state = Aes128EcbPkcs7.encryptBlock(key, state.xorBlock(block))
        }
    return state.copyOf(tagSize)
}

private fun kissToyAesCtrCrypt(
    key: ByteArray,
    nonce: ByteArray,
    input: ByteArray,
): ByteArray {
    require(nonce.size == KISS_TOY_AES_CCM_NONCE_SIZE)
    val output = ByteArray(input.size)
    var offset = 0
    var counter = 1
    while (offset < input.size) {
        val stream = Aes128EcbPkcs7.encryptBlock(key, kissToyAesCcmCounterBlock(nonce, counter))
        val count = minOf(16, input.size - offset)
        repeat(count) { index ->
            output[offset + index] = (input[offset + index].unsigned() xor stream[index].unsigned()).toByte()
        }
        offset += count
        counter += 1
    }
    return output
}

private fun kissToyAesCcmB0(nonce: ByteArray, plaintextSize: Int, tagSize: Int): ByteArray =
    ByteArray(16).also { block ->
        block[0] = (((tagSize - 2) / 2 shl 3) or (KISS_TOY_AES_CCM_LENGTH_FIELD_SIZE - 1)).toByte()
        nonce.copyInto(block, destinationOffset = 1)
        block.writeLength(13, plaintextSize)
    }

private fun kissToyAesCcmCounterBlock(nonce: ByteArray, counter: Int): ByteArray =
    ByteArray(16).also { block ->
        block[0] = (KISS_TOY_AES_CCM_LENGTH_FIELD_SIZE - 1).toByte()
        nonce.copyInto(block, destinationOffset = 1)
        block.writeLength(13, counter)
    }

private fun ByteArray.writeLength(offset: Int, value: Int) {
    require(value in 0 until (1 shl (8 * KISS_TOY_AES_CCM_LENGTH_FIELD_SIZE)))
    repeat(KISS_TOY_AES_CCM_LENGTH_FIELD_SIZE) { index ->
        this[offset + index] = ((value ushr (8 * (KISS_TOY_AES_CCM_LENGTH_FIELD_SIZE - 1 - index))) and 0xff).toByte()
    }
}

private fun ByteArray.xorBlock(other: ByteArray): ByteArray =
    ByteArray(16) { index -> (this[index].unsigned() xor other[index].unsigned()).toByte() }

private fun Byte.unsigned(): Int = toInt() and 0xff

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
internal val KISS_TOY_HISTORICAL_GATT_SERVICE_UUID = LoyoEncryptedFrameCodec.serviceUuid
internal val KISS_TOY_HISTORICAL_GATT_WRITE_UUID = LoyoEncryptedFrameCodec.writeUuid
internal val KISS_TOY_HISTORICAL_GATT_NOTIFY_UUID = LoyoEncryptedFrameCodec.notifyUuid
internal val KISS_TOY_OFFICIAL_V2_AE3A_SERVICE_UUID = Uuid.parse("0000ae3a-0000-1000-8000-00805f9b34fb")
internal val KISS_TOY_OFFICIAL_V2_AE3A_WRITE_UUID = Uuid.parse("0000ae3b-0000-1000-8000-00805f9b34fb")
internal val KISS_TOY_OFFICIAL_V2_AE3A_NOTIFY_UUID = Uuid.parse("0000ae3c-0000-1000-8000-00805f9b34fb")
private const val KISS_TOY_BOBO_CODE = "QCKH"
internal val KISS_TOY_OFFICIAL_V2_AE3A_ROUTE = KissToyOfficialV2Route(
    serviceUuid = KISS_TOY_OFFICIAL_V2_AE3A_SERVICE_UUID,
    writeUuid = KISS_TOY_OFFICIAL_V2_AE3A_WRITE_UUID,
    notifyUuid = KISS_TOY_OFFICIAL_V2_AE3A_NOTIFY_UUID,
    subscribeNotifyOnInit = true,
)
internal val KISS_TOY_OFFICIAL_V2_DISCOVERY_ROUTES = listOf(
    KISS_TOY_OFFICIAL_V2_AE3A_ROUTE,
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
private const val KISS_TOY_AES_CCM_HEADER = 0x60
private const val KISS_TOY_AES_CCM_TAG_SIZE = 8
private const val KISS_TOY_AES_CCM_NONCE_SIZE = 12
private const val KISS_TOY_AES_CCM_DEVICE_ID_SIZE = 6
private const val KISS_TOY_AES_CCM_TIME_NONCE_SIZE = KISS_TOY_AES_CCM_NONCE_SIZE - KISS_TOY_AES_CCM_DEVICE_ID_SIZE
private const val KISS_TOY_AES_CCM_LENGTH_FIELD_SIZE = 3
private const val KISS_TOY_AES_CCM_DEVICE_INFO_CMD = 0x00
private const val KISS_TOY_AES_CCM_GET_DEVICE_MAC_SUB_ID = 0x02
private const val KISS_TOY_AES_CCM_GET_DEVICE_CHIP_INFO_SUB_ID = 0x00
private const val KISS_TOY_AES_CCM_GET_DEVELOPER_ID_SUB_ID = 0xf1
private const val KISS_TOY_AES_CCM_GET_DEVICE_ID_SUB_ID = 0xf2
private const val KISS_TOY_AES_CCM_DEVICE_MAC_SETTLE_MS = 500L
private const val KISS_TOY_AES_CCM_DEVICE_INFO_SETTLE_MS = 200L
internal val KISS_TOY_AES_CCM_KEY = bytes(0x7d, 0xbf, 0x2c, 0x2b, 0x97, 0x74, 0xb8, 0x86, 0xaa, 0xdb, 0xbc, 0x32, 0x36, 0xc3, 0x60, 0xf0)
private val KISS_TOY_AES_CCM_DEFAULT_DEVICE_ID = ByteArray(KISS_TOY_AES_CCM_DEVICE_ID_SIZE) { 0xff.toByte() }
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
    KissToyOfficialV2Profile("QCBBT25", "嘟嘟糖", listOf(KISS_TOY_OFFICIAL_V2_TYPE_3), toyType = 10, bleHeader = 0x60),
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
        if (fingerprint.loyoProfile() != null) return false
        if (!fingerprint.hasKissToyHistoricalGattRoute()) return false
        val knownName = knownNames.any { it.equals(fingerprint.name, ignoreCase = true) }
        val hasNotify = fingerprint.characteristicUuids.contains(KISS_TOY_HISTORICAL_GATT_NOTIFY_UUID)
        return knownName || hasNotify
    }

    // 迷路系列（QCPW/QCSW/QCVW/QCFW）走历史通道时马达静摩擦大，从静止直接给小 PWM 转不起来，
    // 需要冷启动脉冲；其余历史设备沿用无脉冲逻辑，保持既有真机反馈。
    override fun createInstance(fingerprint: BleGattFingerprint): BleDeviceProtocol =
        KissToyHistoricalSession(status, kickStart = fingerprint.needsKissToyKickStart())

    override fun commandsFor(action: ToyControlAction): List<BleProtocolOperation> =
        KissToyHistoricalSession(status, kickStart = false).commandsFor(action)
}

// 历史 Kisstoy Lost / 迷路通道的有状态会话：记录每个电机命令字节（0x31/0x32/0x71）的上次强度，
// 以便判断某通道是否“从静止启动”，只有这种情况才补冷启动脉冲。
internal class KissToyHistoricalSession(
    override val status: BleProtocolStatus,
    private val kickStart: Boolean,
) : BleDeviceProtocol {
    private val lastIntensityByCommand = mutableMapOf(
        0x31 to 0,
        0x32 to 0,
        0x71 to 0,
    )

    override fun matches(fingerprint: BleGattFingerprint): Boolean = false

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
            1 -> listOf(clearMotor(0x32), clearMotor(0x71))
            2 -> listOf(clearMotor(0x31), clearMotor(0x71))
            3 -> listOf(clearMotor(0x31), clearMotor(0x32))
            else -> listOf(clearMotor(0x71))
        }.flatten()
        val runCommands = if (normalizedMode == 4) {
            driveMotor(0x32, normalizedIntensity) + driveMotor(0x31, normalizedIntensity)
        } else {
            driveMotor(motorCommandForMode(normalizedMode), normalizedIntensity)
        }
        return clearCommands + runCommands
    }

    private fun stop(): List<BleProtocolOperation> {
        lastIntensityByCommand.keys.forEach { lastIntensityByCommand[it] = 0 }
        return listOf(
            command(0x40, 0, 0),
            command(0x31, 0),
            command(0x32, 0),
            command(0x71, 0),
        )
    }

    private fun clearMotor(motorCommand: Int): List<BleProtocolOperation> {
        lastIntensityByCommand[motorCommand] = 0
        return listOf(command(motorCommand, 0))
    }

    // 冷启动脉冲：迷路系列且该电机上次强度为 0（从静止启动）且目标落在启动阈值以下时，
    // 先满档踢转、短暂等待，再落到目标强度；目标已够大或马达仍在转（微调）时直接写目标，避免顿挫。
    private fun driveMotor(motorCommand: Int, intensity: Int): List<BleProtocolOperation> {
        val previous = lastIntensityByCommand[motorCommand] ?: 0
        val needsPulse = kickStart &&
            previous == 0 &&
            intensity in 1 until KISS_TOY_KICK_START_THRESHOLD
        lastIntensityByCommand[motorCommand] = intensity
        return if (needsPulse) {
            listOf(
                command(motorCommand, KISS_TOY_KICK_START_PULSE),
                BleProtocolOperation.Sleep(KISS_TOY_KICK_START_DELAY_MS),
                command(motorCommand, intensity),
            )
        } else {
            listOf(command(motorCommand, intensity))
        }
    }

    private fun command(command: Int, first: Int, second: Int = 0): BleProtocolOperation.Write =
        kissToyHistoricalCommand(command, first, second)

    private fun motorCommandForMode(mode: Int): Int =
        when (mode) {
            1 -> 0x31
            2 -> 0x32
            3 -> 0x71
            else -> 0x31
        }
}

// 迷路系列历史通道冷启动脉冲参数：满档 100 踢转、持续 180ms，仅在目标强度低于该阈值时触发。
private const val KISS_TOY_KICK_START_THRESHOLD = 50
private const val KISS_TOY_KICK_START_PULSE = 100
private const val KISS_TOY_KICK_START_DELAY_MS = 180L
internal val KISS_TOY_MILU_KICK_START_CODES = setOf("QCPW", "QCSW", "QCVW", "QCFW")

internal fun BleGattFingerprint.needsKissToyKickStart(): Boolean {
    val normalizedName = name.normalizedDeviceName()
    return KISS_TOY_MILU_KICK_START_CODES.any { normalizedName == it.normalizedDeviceName() }
}

internal fun BleGattFingerprint.hasKissToyHistoricalGattRoute(): Boolean =
    serviceUuids.contains(KISS_TOY_HISTORICAL_GATT_SERVICE_UUID) &&
        characteristicUuids.contains(KISS_TOY_HISTORICAL_GATT_WRITE_UUID)

internal fun kissToyHistoricalCommand(command: Int, first: Int, second: Int = 0): BleProtocolOperation.Write =
    LoyoEncryptedFrameCodec.command(command, first, second)

internal fun kissToyHistoricalPayload(command: Int, first: Int, second: Int = 0): ByteArray =
    LoyoEncryptedFrameCodec.payload(command, first, second)

internal fun kissToyHistoricalEncrypt(payload: ByteArray): ByteArray =
    LoyoEncryptedFrameCodec.encrypt(payload)

internal fun kissToyHistoricalKey(previous: Byte, index: Int): Byte =
    LoyoEncryptedFrameCodec.key(previous.toInt(), index).toByte()

internal val KISS_TOY_HISTORICAL_KEY_TAB: Array<IntArray> = LoyoEncryptedFrameCodec.keyTab
