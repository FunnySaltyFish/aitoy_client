package com.funny.aitoy.ble

import com.funny.aitoy.buttplug.ButtplugConfigRepository
import kotlin.uuid.Uuid

internal sealed interface BleProtocolOperation {
    data class Read(val characteristicUuid: Uuid) : BleProtocolOperation

    data class SubscribeNotify(val characteristicUuid: Uuid) : BleProtocolOperation

    data class UnsubscribeNotify(val characteristicUuid: Uuid) : BleProtocolOperation

    data class Sleep(val millis: Long) : BleProtocolOperation

    data class WaitForProtocolReady(val timeoutMs: Long) : BleProtocolOperation

    data class Write(
        val characteristicUuid: Uuid,
        val bytes: ByteArray,
        val withResponse: Boolean,
    ) : BleProtocolOperation {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Write) return false

            if (withResponse != other.withResponse) return false
            if (characteristicUuid != other.characteristicUuid) return false
            if (!bytes.contentEquals(other.bytes)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = withResponse.hashCode()
            result = 31 * result + characteristicUuid.hashCode()
            result = 31 * result + bytes.contentHashCode()
            return result
        }
    }
}

internal data class BleGattFingerprint(
    val name: String,
    val address: String = "",
    val manufacturerData: String,
    val scanRecordHex: String,
    val serviceUuids: Set<Uuid>,
    val characteristicUuids: Set<Uuid>,
    val preferredServiceUuid: Uuid? = null,
    val preferredWriteUuid: Uuid? = null,
    val preferredNotifyUuid: Uuid? = null,
)

internal interface BleDeviceProtocol {
    val status: BleProtocolStatus

    fun matches(fingerprint: BleGattFingerprint): Boolean

    fun createInstance(fingerprint: BleGattFingerprint): BleDeviceProtocol = this

    fun initialize(fingerprint: BleGattFingerprint): List<BleProtocolOperation> = emptyList()

    fun keepaliveIntervalMs(): Long = 0L

    fun keepaliveCommands(lastCommands: List<BleProtocolOperation>): List<BleProtocolOperation> = lastCommands

    fun onRead(characteristicUuid: Uuid, bytes: ByteArray): List<BleProtocolOperation> = emptyList()

    fun onNotify(characteristicUuid: Uuid, bytes: ByteArray): List<BleProtocolOperation> = emptyList()

    fun isProtocolReady(): Boolean = true

    fun commandsFor(action: ToyControlAction): List<BleProtocolOperation>
}

internal object BleProtocolRegistry {
    private val nativeProtocols =
        listOf(AnkniProtocol) +
                ankniDdddProfileProtocols +
                listOf(
                    AnkniQd1XhtkjProtocol,
                    AnkniQd1Ff15XhtkjProtocol,
                    AnkniQd1Ff12GattProtocol,
                    AnkniQd1GattProtocol,
                    AnkniBxProtocol,
                    Ankni0010Protocol,
                    CachitoMbProGattProtocol,
                    JiuaiJellyfishProtocol,
                    LoyoProtocol,
                    KissToyTutuIIProtocol,
                    KissToyOfficialV2Protocol,
                    AnkniYwtdProtocol,
                    KissToyProtocol,
                    WeVibeSyncLiteProtocol,
                    SistalkPopocatProtocol,
                    SistalkMonsterPartyMp2Protocol,
                    SistalkWeightless808Protocol,
                    BeYourLoverV1Protocol,
                    BeYourLoverProtocol,
                    SistalkMonsterPartyProtocol,
                    SistalkMixPwmProtocol,
                    SistalkMonsterPubMultiMotorProtocol,
                    SistalkMonsterPubDualMotorProtocol,
                    SistalkMonsterPubSingleMotorProtocol,
                    SvakomZemaliaProtocol,
                    SvakomV2MultiFunctionProtocol,
                    SvakomVibrateSuckProtocol,
                    SvakomSt419Protocol,
                    SvakomQhSx045Protocol,
                    ChokerMiyinProtocol,
                    MesanelProtocol,
                    JissbonProtocol,
                    SosexyBoboBeiProtocol,
                    MasolinWaiwaimaFfa0Protocol,
                    MizzzeeXhtkjProtocol,
                    SenseeCcpa10S2Protocol,
                    OhMiBodEsca2Protocol,
                    XiuxiudaOfficialProtocol,
                    PinkPunchProtocol,
                    LovenutsProtocol,
                ) +
                jissbonVariantProtocols

    suspend fun resolve(fingerprint: BleGattFingerprint): BleDeviceProtocol? =
        resolveAll(fingerprint).firstOrNull()

    internal fun resolveNative(fingerprint: BleGattFingerprint): BleDeviceProtocol? =
        resolveNativeAll(fingerprint).firstOrNull()

    internal fun resolveNativeAll(fingerprint: BleGattFingerprint): List<BleDeviceProtocol> =
        nativeProtocols.mapNotNull { protocol ->
            protocol.takeIf { it.matches(fingerprint) }?.createInstance(fingerprint)
        }

    suspend fun resolveAll(fingerprint: BleGattFingerprint): List<BleDeviceProtocol> {
        val native = resolveNativeAll(fingerprint)
        val nativeIds = native.map { it.status.id }.toSet()
        val buttplug = ButtplugConfigRepository
            .matchAll(fingerprint.toButtplugFingerprint())
            .mapNotNull { match -> ButtplugBleProtocolFactory.create(match) }
            .filterNot { it.status.id in nativeIds }
        return native + buttplug
    }
}

