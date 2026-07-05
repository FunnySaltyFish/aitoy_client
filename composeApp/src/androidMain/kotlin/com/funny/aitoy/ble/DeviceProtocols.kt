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
import java.util.UUID
import kotlin.random.Random

internal sealed interface BleProtocolOperation {
    data class Read(val characteristicUuid: UUID) : BleProtocolOperation

    data class SubscribeNotify(val characteristicUuid: UUID) : BleProtocolOperation

    data class UnsubscribeNotify(val characteristicUuid: UUID) : BleProtocolOperation

    data class Sleep(val millis: Long) : BleProtocolOperation

    data class WaitForProtocolReady(val timeoutMs: Long) : BleProtocolOperation

    data class Write(
        val characteristicUuid: UUID,
        val bytes: ByteArray,
        val withResponse: Boolean,
    ) : BleProtocolOperation {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Write

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
    val serviceUuids: Set<UUID>,
    val characteristicUuids: Set<UUID>,
    val preferredServiceUuid: UUID? = null,
    val preferredWriteUuid: UUID? = null,
    val preferredNotifyUuid: UUID? = null,
) {
    val sistalkProtocolVersion: Int
        get() = manufacturerData.firstManufacturerByte() ?: 0
}

internal interface BleDeviceProtocol {
    val status: BleProtocolStatus

    fun matches(fingerprint: BleGattFingerprint): Boolean

    fun createInstance(fingerprint: BleGattFingerprint): BleDeviceProtocol = this

    fun initialize(fingerprint: BleGattFingerprint): List<BleProtocolOperation> = emptyList()

    fun keepaliveIntervalMs(): Long = 0L

    fun keepaliveCommands(lastCommands: List<BleProtocolOperation>): List<BleProtocolOperation> = lastCommands

    fun onRead(characteristicUuid: UUID, bytes: ByteArray): List<BleProtocolOperation> = emptyList()

    fun onNotify(characteristicUuid: UUID, bytes: ByteArray): List<BleProtocolOperation> = emptyList()

    fun isProtocolReady(): Boolean = true

    fun commandsFor(action: ToyControlAction): List<BleProtocolOperation>
}

internal object BleProtocolRegistry {
    private val nativeProtocols =
        listOf(AnkniProtocol) +
                ankniDdddProfileProtocols +
                listOf(
                    AnkniQd1Ff12GattProtocol,
                    AnkniQd1GattProtocol,
                    Ankni0010Protocol,
                    CachitoMbProGattProtocol,
                    KissToyTutuIIProtocol,
                    KissToyOfficialV2Protocol,
                    AnkniYwtdProtocol,
                    KissToyProtocol,
                    WeVibeSyncLiteProtocol,
                    SistalkMonsterPartyProtocol,
                    SistalkMixPwmProtocol,
                    SistalkMonsterPubMultiMotorProtocol,
                    SistalkMonsterPubDualMotorProtocol,
                    SistalkMonsterPubSingleMotorProtocol,
                    SvakomSl278hProtocol,
                    SvakomSt419Protocol,
                    SvakomQhSx045Protocol,
                    MizzzeeXhtkjProtocol,
                    SenseeCcpa10S2Protocol,
                    OhMiBodEsca2Protocol,
                    XiuxiudaOfficialProtocol,
                    PinkPunchProtocol,
                    LovenutsProtocol,
                )

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

private object ButtplugBleProtocolFactory {
    fun create(match: ButtplugDeviceMatch): BleDeviceProtocol? =
        when (match.protocol.id.takeIf(ButtplugLocalHandlerRegistry.controllableButtplugHandlerIds::contains)) {
            "wevibe" -> ButtplugWeVibePackedProtocol(match)
            "wevibe-8bit" -> ButtplugWeVibe8BitProtocol(match)
            "wevibe-chorus" -> ButtplugWeVibeChorusProtocol(match)
            "honeyplaybox" -> ButtplugHoneyPlayBoxProtocol(match)
            "lelo-harmony" -> ButtplugLeloHarmonyProtocol(match)
            "lelo-f1sv2" -> ButtplugLeloF1sV2Protocol(match)
            "vibcrafter" -> ButtplugVibCrafterProtocol(match)
            "fluffer" -> ButtplugFlufferProtocol(match)
            "thehandy" -> ButtplugHandyProtocol(match)
            "thehandy-v3" -> ButtplugHandyV3Protocol(match)
            "lovense" -> ButtplugLovenseProtocol(match)
            "cachito", "jejoue", "monsterpub", "satisfyer" -> ButtplugSpecializedProtocol(match)
            "cueme", "kiiroo-v1", "muse", "sayberx" -> ButtplugLegacyStpihkalProtocol(match)
            else -> SimpleVibrateProtocolPlans.forProtocolId(match.protocol.id)
                ?.let { ButtplugSimpleVibrateProtocol(match, it) }
                ?: StatefulVibrateProtocolPlans.forProtocolId(match.protocol.id)
                    ?.let { ButtplugStatefulVibrateProtocol(match, it) }
                ?: MixedOutputProtocolPlans.forProtocolId(match.protocol.id)
                    ?.let { ButtplugMixedOutputProtocol(match, it) }
                ?: ScalarProtocolPlans.forProtocolId(match.protocol.id)
                    ?.let { ButtplugScalarProtocol(match, it) }
                ?: LinearProtocolPlans.forProtocolId(match.protocol.id)
                    ?.let { ButtplugLinearProtocol(match, it) }
                ?: ButtplugUnsupportedProtocol(match)
        }
}

private class ButtplugUnsupportedProtocol(
    match: ButtplugDeviceMatch,
) : BleDeviceProtocol {
    override val status: BleProtocolStatus = match.toBleStatus(
        id = "buttplug_${match.protocol.id}",
        controllable = false,
        supportsMode = false,
        controlStyle = ToyControlStyle.IntensityOnly,
    )

    override fun matches(fingerprint: BleGattFingerprint): Boolean = false

    override fun commandsFor(action: ToyControlAction): List<BleProtocolOperation> = emptyList()
}

private class ButtplugHoneyPlayBoxProtocol(
    private val match: ButtplugDeviceMatch,
) : BleDeviceProtocol {
    private val endpointByRole = match.endpoint.characteristics
    private val txUuid: UUID = uuid(endpointByRole.getValue("tx"))
    private val rxUuid: UUID = uuid(endpointByRole.getValue("rx"))
    private val features = match.scalarOutputs.sortedBy { it.featureIndex }
    private val maxByFeature = features.associate { it.featureIndex to it.max.coerceAtLeast(1) }
    private val speeds = IntArray(features.size.coerceAtLeast(1))
    private val collector = HoneyPlayBoxFrameCollector()
    private var randomKey: ByteArray? = null
    private var packetId = 0

    override val status: BleProtocolStatus = match.toBleStatus(
        id = "buttplug_${match.protocol.id}",
        controllable = endpointByRole.containsKey("tx") && endpointByRole.containsKey("rx"),
        supportsMode = false,
        controlStyle = if (features.size > 1) ToyControlStyle.DualIntensityOnly else ToyControlStyle.IntensityOnly,
    )

    override fun matches(fingerprint: BleGattFingerprint): Boolean = false

    override fun initialize(fingerprint: BleGattFingerprint): List<BleProtocolOperation> =
        listOf(
            BleProtocolOperation.SubscribeNotify(rxUuid),
            BleProtocolOperation.Write(
                characteristicUuid = txUuid,
                bytes = HoneyPlayBoxProtocolPlans.handshakeFrame(counter = 0),
                withResponse = true,
            ),
            BleProtocolOperation.WaitForProtocolReady(HoneyPlayBoxProtocolPlans.handshakeTimeoutMs),
        )

    override fun keepaliveIntervalMs(): Long = HoneyPlayBoxProtocolPlans.keepaliveIntervalMs

    override fun keepaliveCommands(lastCommands: List<BleProtocolOperation>): List<BleProtocolOperation> =
        if (isProtocolReady()) listOf(writeState()) else emptyList()

    override fun isProtocolReady(): Boolean = randomKey != null

    override fun onNotify(characteristicUuid: UUID, bytes: ByteArray): List<BleProtocolOperation> {
        if (characteristicUuid != rxUuid) return emptyList()
        for (frame in collector.pushBytes(bytes)) {
            val response = HoneyPlayBoxProtocolPlans.parseResponse(frame) ?: continue
            val code = response.responseCode
            if (code == 0x9000 && response.random != null) {
                randomKey = response.random
                packetId = 1
                return emptyList()
            }
            if (code != null && code != 0x9000) {
                error("HoneyPlayBox handshake failed: code=${code.toString(16)}")
            }
        }
        return emptyList()
    }

    override fun commandsFor(action: ToyControlAction): List<BleProtocolOperation> {
        when (action) {
            is ToyControlAction.Intensity -> setAll(action.value)
            is ToyControlAction.Combined -> setAll(action.intensity)
            is ToyControlAction.Pattern -> setAll(maxByFeature.values.maxOrNull() ?: 1)
            is ToyControlAction.DualMotor -> {
                if (speeds.size > 1) {
                    speeds[0] = action.internalIntensity.coerceIn(0, maxForFeature(0))
                    speeds[1] = action.externalIntensity.coerceIn(0, maxForFeature(1))
                } else {
                    setAll(action.strongestIntensity(maxForFeature(0)))
                }
            }
            ToyControlAction.Stop -> speeds.fill(0)
        }
        return listOf(writeState())
    }

    private fun setAll(value: Int) {
        repeat(speeds.size) { index ->
            speeds[index] = value.coerceIn(0, maxForFeature(index))
        }
    }

    private fun maxForFeature(featureIndex: Int): Int =
        maxByFeature[featureIndex] ?: maxByFeature.values.firstOrNull() ?: 100

    private fun writeState(): BleProtocolOperation.Write {
        val random = requireNotNull(randomKey) { "HoneyPlayBox handshake is not complete" }
        packetId = (packetId + 1) and 0xFF
        return BleProtocolOperation.Write(
            characteristicUuid = txUuid,
            bytes = HoneyPlayBoxProtocolPlans.controlFrame(random, speeds, packetId),
            withResponse = true,
        )
    }
}

private class ButtplugFlufferProtocol(
    private val match: ButtplugDeviceMatch,
) : BleDeviceProtocol {
    private val endpointByRole = match.endpoint.characteristics
    private val txUuid: UUID = uuid(endpointByRole.getValue("tx"))
    private val rxUuid: UUID = uuid(endpointByRole.getValue("rx"))
    private val scalarFeatures = match.scalarOutputs.sortedBy { it.featureIndex }
    private val channelCount = scalarFeatures.size.coerceAtLeast(1).coerceAtMost(2)
    private val speeds = IntArray(2)
    private val advertisementData = FlufferProtocolPlans.manufacturerAdvertisement(match.fingerprint.manufacturerData)
    private var authorized = advertisementData.isEmpty()

    override val status: BleProtocolStatus = match.toBleStatus(
        id = "buttplug_${match.protocol.id}",
        controllable = endpointByRole.containsKey("tx") && endpointByRole.containsKey("rx"),
        supportsMode = false,
        controlStyle = if (channelCount > 1) ToyControlStyle.DualIntensityOnly else ToyControlStyle.IntensityOnly,
    )

    override fun matches(fingerprint: BleGattFingerprint): Boolean = false

    override fun initialize(fingerprint: BleGattFingerprint): List<BleProtocolOperation> =
        if (advertisementData.isEmpty()) {
            emptyList()
        } else {
            listOf(
                BleProtocolOperation.SubscribeNotify(rxUuid),
                BleProtocolOperation.Write(
                    characteristicUuid = txUuid,
                    bytes = FlufferProtocolPlans.authPayload(advertisementData, FlufferProtocolPlans.randomNonce()),
                    withResponse = false,
                ),
                BleProtocolOperation.WaitForProtocolReady(FlufferProtocolPlans.handshakeTimeoutMs),
            )
        }

    override fun keepaliveIntervalMs(): Long = FlufferProtocolPlans.keepaliveIntervalMs

    override fun keepaliveCommands(lastCommands: List<BleProtocolOperation>): List<BleProtocolOperation> =
        if (authorized) {
            listOf(
                BleProtocolOperation.Write(
                    characteristicUuid = txUuid,
                    bytes = FlufferProtocolPlans.keepalivePayload(),
                    withResponse = false,
                ),
            )
        } else {
            emptyList()
        }

    override fun isProtocolReady(): Boolean = authorized

    override fun onNotify(characteristicUuid: UUID, bytes: ByteArray): List<BleProtocolOperation> {
        if (characteristicUuid != rxUuid) return emptyList()
        val authenticated = runCatching { FlufferProtocolPlans.isAuthSuccess(bytes) }.getOrDefault(false)
        if (!authenticated) return emptyList()
        authorized = true
        return listOf(
            BleProtocolOperation.Write(
                characteristicUuid = txUuid,
                bytes = FlufferProtocolPlans.enablePayload(),
                withResponse = false,
            ),
        )
    }

    override fun commandsFor(action: ToyControlAction): List<BleProtocolOperation> {
        when (action) {
            is ToyControlAction.Intensity -> setAll(action.value)
            is ToyControlAction.Combined -> setAll(action.intensity)
            is ToyControlAction.Pattern -> setAll(maxValue())
            is ToyControlAction.DualMotor -> {
                setFeature(0, action.internalIntensity)
                if (channelCount > 1) {
                    setFeature(1, action.externalIntensity)
                } else {
                    setFeature(1, speeds[0])
                }
            }
            ToyControlAction.Stop -> speeds.fill(0)
        }
        return listOf(
            BleProtocolOperation.Write(
                characteristicUuid = txUuid,
                bytes = FlufferProtocolPlans.controlPayload(speeds[0], speeds[1]),
                withResponse = false,
            ),
        )
    }

    private fun setAll(value: Int) {
        repeat(channelCount) { index -> setFeature(index, value) }
        if (channelCount == 1) speeds[1] = speeds[0]
    }

    private fun setFeature(index: Int, value: Int) {
        val feature = scalarFeatures.getOrNull(index)
        val normalized = when {
            feature == null -> value.coerceIn(0, 100)
            feature.type.toToyOutputKind() == ToyOutputKind.Rotate && feature.min < 0 ->
                FlufferProtocolPlans.rotateSpeed(value.coerceIn(feature.min, feature.max))
            else -> value.coerceIn(feature.min, feature.max)
        }
        speeds[index.coerceIn(0, 1)] = normalized
    }

    private fun maxValue(): Int =
        scalarFeatures.maxOfOrNull { maxOf(kotlin.math.abs(it.min), kotlin.math.abs(it.max)) } ?: 100
}

private class ButtplugLovenseProtocol(
    private val match: ButtplugDeviceMatch,
) : BleDeviceProtocol {
    private val endpointByRole = match.endpoint.characteristics
    private val txUuid: UUID = uuid(match.endpoint.txUuid.orEmpty())
    private val rxUuid: UUID? = match.endpoint.rxUuid?.let(::uuid)
    private var activeDevice = resolveDevice(LovenseProtocolPlans.deviceTypeFromBleName(match.fingerprint.name)) ?: match.device
    private var activeDeviceType = activeDevice.identifiers.firstOrNull()
        ?: LovenseProtocolPlans.deviceTypeFromBleName(match.fingerprint.name).orEmpty()
    private var state = newState(activeDevice)

    override val status: BleProtocolStatus = match.toBleStatus(
        id = "buttplug_${match.protocol.id}",
        controllable = txUuid.toString().isNotBlank(),
        supportsMode = false,
        controlStyle = if (activeOutputFeatures().size > 1) ToyControlStyle.DualIntensityOnly else ToyControlStyle.IntensityOnly,
    )

    override fun matches(fingerprint: BleGattFingerprint): Boolean = false

    override fun initialize(fingerprint: BleGattFingerprint): List<BleProtocolOperation> =
        buildList {
            rxUuid?.let { add(BleProtocolOperation.SubscribeNotify(it)) }
            add(write(LovenseProtocolPlans.keepaliveWrite()))
        }

    override fun keepaliveIntervalMs(): Long = LovenseProtocolPlans.keepaliveIntervalMs

    override fun keepaliveCommands(lastCommands: List<BleProtocolOperation>): List<BleProtocolOperation> =
        listOf(write(LovenseProtocolPlans.keepaliveWrite()))

    override fun onNotify(characteristicUuid: UUID, bytes: ByteArray): List<BleProtocolOperation> {
        if (rxUuid != null && characteristicUuid != rxUuid) return emptyList()
        val text = bytes.decodeToString().trim()
        if (':' !in text) return emptyList()
        val deviceType = LovenseProtocolPlans.deviceTypeFromResponse(text)
        val device = resolveDevice(deviceType) ?: return emptyList()
        activeDeviceType = deviceType
        activeDevice = device
        state = newState(device)
        return emptyList()
    }

    override fun commandsFor(action: ToyControlAction): List<BleProtocolOperation> {
        val features = activeOutputFeatures()
        val maxValue = features.maxOfOrNull { maxOf(kotlin.math.abs(it.min), kotlin.math.abs(it.max)) } ?: 20
        return when (action) {
            is ToyControlAction.Intensity -> writeAll(features, action.value)
            is ToyControlAction.Combined -> writeAll(features, action.intensity)
            is ToyControlAction.Pattern -> writeAll(features, maxValue)
            is ToyControlAction.DualMotor -> {
                if (features.size > 1) {
                    buildList {
                        addAll(writeFeature(features[0], action.internalIntensity))
                        addAll(writeFeature(features[1], action.externalIntensity))
                    }
                } else {
                    writeAll(features, action.strongestIntensity(maxValue))
                }
            }
            ToyControlAction.Stop -> features.flatMap { feature -> writeFeature(feature, feature.min.coerceAtMost(0)) }
        }
    }

    private fun resolveDevice(deviceType: String?): com.funny.aitoy.buttplug.ButtplugDeviceDefinition? {
        val type = deviceType?.takeIf(String::isNotBlank) ?: return null
        return match.protocol.configurations
            .filter { config -> config.identifiers.any { it == type } }
            .maxByOrNull { config -> config.identifiers.maxOfOrNull { it.length } ?: 0 }
    }

    private fun activeOutputFeatures(): List<com.funny.aitoy.buttplug.ButtplugOutputFeature> =
        activeDevice.features
            .filter { feature -> feature.type.toToyOutputKind() in LovenseProtocolPlans.supportedKinds }
            .sortedWith(compareBy({ it.featureIndex }, { it.type }))

    private fun writeAll(
        features: List<com.funny.aitoy.buttplug.ButtplugOutputFeature>,
        value: Int,
    ): List<BleProtocolOperation.Write> =
        features.flatMap { feature -> writeFeature(feature, value) }

    private fun writeFeature(
        feature: com.funny.aitoy.buttplug.ButtplugOutputFeature,
        value: Int,
    ): List<BleProtocolOperation.Write> {
        val profile = LovenseProtocolPlans.profile(activeDeviceType, activeOutputFeatures())
        val writes = if (feature.type == com.funny.aitoy.buttplug.OUTPUT_HW_POSITION_WITH_DURATION) {
            LovenseProtocolPlans.linearWrites(profile, value.coerceIn(feature.min, feature.max))
        } else {
            LovenseProtocolPlans.scalarWrites(profile, state, feature, value)
        }
        return writes.map(::write)
    }

    private fun write(write: com.funny.aitoy.buttplug.LovenseProtocolWrite): BleProtocolOperation.Write =
        BleProtocolOperation.Write(
            characteristicUuid = uuid(requireNotNull(endpointByRole[write.endpointRole]) {
                "lovense endpoint ${write.endpointRole} is missing"
            }),
            bytes = write.bytes,
            withResponse = write.withResponse,
        )

    private fun newState(device: com.funny.aitoy.buttplug.ButtplugDeviceDefinition): IntArray {
        val features = device.features.filter { it.type.toToyOutputKind() in LovenseProtocolPlans.supportedKinds }
        val size = maxOf(features.size, (features.maxOfOrNull { it.featureIndex } ?: 0) + 1, 1)
        return IntArray(size)
    }
}

private class ButtplugSpecializedProtocol(
    private val match: ButtplugDeviceMatch,
) : BleDeviceProtocol {
    private val endpointByRole = match.endpoint.characteristics
    private val txUuid: UUID = uuid(match.endpoint.txUuid.orEmpty())
    private val commandUuid: UUID? = endpointByRole["command"]?.let(::uuid)
    private val features = match.vibrateOutputs.sortedBy { it.featureIndex }
    private val state = IntArray((features.maxOfOrNull { it.featureIndex } ?: 0) + 1)
    private val maxValue = features.maxOfOrNull { it.max.coerceAtLeast(1) } ?: 1

    override val status: BleProtocolStatus = match.toBleStatus(
        id = "buttplug_${match.protocol.id}",
        controllable = txUuid.toString().isNotBlank(),
        supportsMode = false,
        controlStyle = if (features.size > 1) ToyControlStyle.DualIntensityOnly else ToyControlStyle.IntensityOnly,
    )

    override fun matches(fingerprint: BleGattFingerprint): Boolean = false

    override fun initialize(fingerprint: BleGattFingerprint): List<BleProtocolOperation> =
        if (match.protocol.id == "satisfyer" && commandUuid != null) {
            listOf(BleProtocolOperation.Write(commandUuid, SpecializedProtocolPlans.satisfyerInitPayload(), withResponse = true))
        } else {
            emptyList()
        }

    override fun keepaliveIntervalMs(): Long =
        if (match.protocol.id == "satisfyer") SpecializedProtocolPlans.satisfyerKeepaliveIntervalMs else 0L

    override fun commandsFor(action: ToyControlAction): List<BleProtocolOperation> {
        when (action) {
            is ToyControlAction.Intensity -> setAll(action.value)
            is ToyControlAction.Combined -> setAll(action.intensity)
            is ToyControlAction.Pattern -> setAll(maxValue)
            is ToyControlAction.DualMotor -> {
                setFeature(0, action.internalIntensity)
                if (features.size > 1) setFeature(1, action.externalIntensity) else setFeature(0, action.strongestIntensity(maxValue))
            }
            ToyControlAction.Stop -> state.fill(0)
        }
        return writeState()
    }

    private fun setAll(value: Int) {
        for (feature in features) setFeature(feature.featureIndex, value)
    }

    private fun setFeature(featureIndex: Int, value: Int) {
        val feature = features.firstOrNull { it.featureIndex == featureIndex }
        state[featureIndex.coerceIn(state.indices)] = when {
            feature == null -> value.coerceIn(0, maxValue)
            else -> value.coerceIn(feature.min, feature.max)
        }
    }

    private fun writeState(): List<BleProtocolOperation> =
        when (match.protocol.id) {
            "cachito" -> features.map { feature ->
                write(SpecializedProtocolPlans.cachitoPayload(feature.featureIndex, state[feature.featureIndex]))
            }
            "jejoue" -> listOf(write(SpecializedProtocolPlans.jejouePayload(state.getOrElse(0) { 0 }, state.getOrElse(1) { 0 })))
            "monsterpub" -> {
                val endpointRole = SpecializedProtocolPlans.monsterPubEndpointRole(endpointByRole.keys, state)
                listOf(
                    write(
                        SpecializedProtocolPlans.monsterPubPayload(endpointRole, state, features.size.coerceAtLeast(1)),
                        endpointRole = endpointRole,
                        withResponse = endpointRole == "txmode",
                    ),
                )
            }
            "satisfyer" -> listOf(write(SpecializedProtocolPlans.satisfyerPayload(state, features.size.coerceAtLeast(1))))
            else -> emptyList()
        }

    private fun write(
        bytes: ByteArray,
        endpointRole: String = "tx",
        withResponse: Boolean = false,
    ): BleProtocolOperation.Write {
        val characteristicUuid = requireNotNull(endpointByRole[endpointRole]?.let(::uuid)) {
            "${match.protocol.id} endpoint $endpointRole is missing"
        }
        return BleProtocolOperation.Write(
            characteristicUuid = characteristicUuid,
            bytes = bytes,
            withResponse = withResponse,
        )
    }
}

private class ButtplugLegacyStpihkalProtocol(
    private val match: ButtplugDeviceMatch,
) : BleDeviceProtocol {
    private val txUuid: UUID = uuid(match.endpoint.txUuid.orEmpty())
    private val scalarFeatures = match.scalarOutputs.sortedBy { it.featureIndex }
    private val linearFeatures = match.linearOutputs.sortedBy { it.featureIndex }
    private val features = (scalarFeatures + linearFeatures).sortedBy { it.featureIndex }
    private val maxValue = features.maxOfOrNull { it.max.coerceAtLeast(1) } ?: 1
    private val state = IntArray((features.maxOfOrNull { it.featureIndex } ?: 0) + 1)

    override val status: BleProtocolStatus = match.toBleStatus(
        id = "buttplug_${match.protocol.id}",
        controllable = txUuid.toString().isNotBlank(),
        supportsMode = false,
        controlStyle = if (features.size > 1) ToyControlStyle.DualIntensityOnly else ToyControlStyle.IntensityOnly,
    )

    override fun matches(fingerprint: BleGattFingerprint): Boolean = false

    override fun keepaliveIntervalMs(): Long =
        if (match.protocol.id == "muse") LegacyStpihkalProtocolPlans.museKeepaliveIntervalMs else 0L

    override fun commandsFor(action: ToyControlAction): List<BleProtocolOperation> {
        when (action) {
            is ToyControlAction.Intensity -> setAll(action.value)
            is ToyControlAction.Combined -> setAll(action.intensity)
            is ToyControlAction.Pattern -> setAll(maxValue)
            is ToyControlAction.DualMotor -> {
                setFeature(0, action.internalIntensity)
                if (features.size > 1) setFeature(1, action.externalIntensity) else setFeature(0, action.strongestIntensity(maxValue))
            }
            ToyControlAction.Stop -> state.fill(0)
        }
        return writeState()
    }

    private fun setAll(value: Int) {
        for (feature in features) setFeature(feature.featureIndex, value)
    }

    private fun setFeature(featureIndex: Int, value: Int) {
        val feature = features.firstOrNull { it.featureIndex == featureIndex }
        state[featureIndex.coerceIn(state.indices)] = when {
            feature == null -> value.coerceIn(0, maxValue)
            else -> value.coerceIn(feature.min, feature.max)
        }
    }

    private fun writeState(): List<BleProtocolOperation> =
        when (match.protocol.id) {
            "cueme" -> features.map { feature ->
                write(LegacyStpihkalProtocolPlans.cuemePayload(feature.featureIndex, state[feature.featureIndex]))
            }
            "kiiroo-v1" -> listOf(write(LegacyStpihkalProtocolPlans.kiirooV1Payload(state.firstOrNull() ?: 0)))
            "muse" -> listOf(write(LegacyStpihkalProtocolPlans.musePayload(state.firstOrNull() ?: 0)))
            "sayberx" -> listOf(write(LegacyStpihkalProtocolPlans.sayberXPayload(state.firstOrNull() ?: 0)))
            else -> emptyList()
        }

    private fun write(bytes: ByteArray): BleProtocolOperation.Write =
        BleProtocolOperation.Write(
            characteristicUuid = txUuid,
            bytes = bytes,
            withResponse = false,
        )
}

private class ButtplugHandyProtocol(
    private val match: ButtplugDeviceMatch,
) : BleDeviceProtocol {
    private val txUuid: UUID = uuid(match.endpoint.txUuid.orEmpty())
    private val linearFeatures = match.linearOutputs.sortedBy { it.featureIndex }
    private val maxPosition = linearFeatures.maxOfOrNull { it.max.coerceAtLeast(1) } ?: 100

    override val status: BleProtocolStatus = match.toBleStatus(
        id = "buttplug_${match.protocol.id}",
        controllable = txUuid.toString().isNotBlank(),
        supportsMode = false,
        controlStyle = ToyControlStyle.IntensityOnly,
    )

    override fun matches(fingerprint: BleGattFingerprint): Boolean = false

    override fun keepaliveIntervalMs(): Long = HandyProtocolPlans.keepaliveIntervalMs

    override fun keepaliveCommands(lastCommands: List<BleProtocolOperation>): List<BleProtocolOperation> =
        listOf(write(HandyProtocolPlans.handyPingPayload()))

    override fun commandsFor(action: ToyControlAction): List<BleProtocolOperation> =
        when (action) {
            is ToyControlAction.Intensity -> writePosition(action.value)
            is ToyControlAction.Combined -> writePosition(action.intensity)
            is ToyControlAction.Pattern -> writePosition(maxPosition)
            is ToyControlAction.DualMotor -> writePosition(action.strongestIntensity(maxPosition))
            ToyControlAction.Stop -> writePosition(0)
        }

    private fun writePosition(position: Int): List<BleProtocolOperation> =
        listOf(write(HandyProtocolPlans.handyLinearPayload(position.coerceIn(0, maxPosition), DEFAULT_LINEAR_DURATION_MS)))

    private fun write(bytes: ByteArray): BleProtocolOperation.Write =
        BleProtocolOperation.Write(
            characteristicUuid = txUuid,
            bytes = bytes,
            withResponse = true,
        )

    private companion object {
        const val DEFAULT_LINEAR_DURATION_MS = 1000
    }
}

private class ButtplugHandyV3Protocol(
    private val match: ButtplugDeviceMatch,
) : BleDeviceProtocol {
    private val endpointByRole = match.endpoint.characteristics
    private val txUuid: UUID = uuid(endpointByRole.getValue("tx"))
    private val rxUuid: UUID? = endpointByRole["rx"]?.let(::uuid)
    private val linearFeatures = match.linearOutputs.sortedBy { it.featureIndex }
    private val scalarFeatures = match.scalarOutputs.sortedBy { it.featureIndex }
    private val maxValue = (linearFeatures + scalarFeatures).maxOfOrNull { it.max.coerceAtLeast(1) } ?: 100
    private var sequence = 1

    override val status: BleProtocolStatus = match.toBleStatus(
        id = "buttplug_${match.protocol.id}",
        controllable = endpointByRole.containsKey("tx"),
        supportsMode = false,
        controlStyle = if (scalarFeatures.size > 1) ToyControlStyle.DualIntensityOnly else ToyControlStyle.IntensityOnly,
    )

    override fun matches(fingerprint: BleGattFingerprint): Boolean = false

    override fun initialize(fingerprint: BleGattFingerprint): List<BleProtocolOperation> =
        buildList {
            rxUuid?.let { add(BleProtocolOperation.SubscribeNotify(it)) }
            add(write(HandyProtocolPlans.handyV3BatteryRequestPayload(nextSequence()), withResponse = false))
            add(write(HandyProtocolPlans.handyV3CapabilitiesRequestPayload(nextSequence()), withResponse = false))
        }

    override fun keepaliveIntervalMs(): Long = HandyProtocolPlans.keepaliveIntervalMs

    override fun keepaliveCommands(lastCommands: List<BleProtocolOperation>): List<BleProtocolOperation> =
        listOf(write(HandyProtocolPlans.handyV3KeepalivePayload(nextSequence()), withResponse = true))

    override fun commandsFor(action: ToyControlAction): List<BleProtocolOperation> =
        when (action) {
            is ToyControlAction.Intensity -> writeValue(action.value)
            is ToyControlAction.Combined -> writeValue(action.intensity)
            is ToyControlAction.Pattern -> writeValue(maxValue)
            is ToyControlAction.DualMotor -> writeValue(action.strongestIntensity(maxValue))
            ToyControlAction.Stop -> writeValue(0)
        }

    private fun writeValue(value: Int): List<BleProtocolOperation> =
        if (linearFeatures.isNotEmpty()) {
            listOf(
                write(
                    HandyProtocolPlans.handyV3LinearPayload(nextSequence(), value.coerceIn(0, maxValue), DEFAULT_LINEAR_DURATION_MS),
                    withResponse = true,
                ),
            )
        } else {
            listOf(write(HandyProtocolPlans.handyV3VibratePayload(nextSequence(), value.coerceIn(0, maxValue)), withResponse = true))
        }

    private fun write(bytes: ByteArray, withResponse: Boolean): BleProtocolOperation.Write =
        BleProtocolOperation.Write(
            characteristicUuid = txUuid,
            bytes = bytes,
            withResponse = withResponse,
        )

    private fun nextSequence(): Int {
        val current = sequence
        sequence += 1
        return current
    }

    private companion object {
        const val DEFAULT_LINEAR_DURATION_MS = 1000
    }
}

private class ButtplugVibCrafterProtocol(
    private val match: ButtplugDeviceMatch,
) : BleDeviceProtocol {
    private val endpointByRole = match.endpoint.characteristics
    private val txUuid: UUID = uuid(endpointByRole.getValue("tx"))
    private val rxUuid: UUID = uuid(endpointByRole.getValue("rx"))
    private val features = match.vibrateOutputs.sortedBy { it.featureIndex }
    private val channelCount = features.size.coerceAtLeast(1).coerceAtMost(2)
    private val speeds = IntArray(2)
    private val maxByFeature = features.associate { it.featureIndex to it.max.coerceAtLeast(1) }
    private var authorized = false

    override val status: BleProtocolStatus = match.toBleStatus(
        id = "buttplug_${match.protocol.id}",
        controllable = endpointByRole.containsKey("tx") && endpointByRole.containsKey("rx"),
        supportsMode = false,
        controlStyle = if (channelCount > 1) ToyControlStyle.DualIntensityOnly else ToyControlStyle.IntensityOnly,
    )

    override fun matches(fingerprint: BleGattFingerprint): Boolean = false

    override fun initialize(fingerprint: BleGattFingerprint): List<BleProtocolOperation> =
        listOf(
            BleProtocolOperation.SubscribeNotify(rxUuid),
            BleProtocolOperation.Write(
                characteristicUuid = txUuid,
                bytes = VibCrafterProtocolPlans.authPayload(VibCrafterProtocolPlans.randomAuthToken()),
                withResponse = false,
            ),
            BleProtocolOperation.WaitForProtocolReady(VibCrafterProtocolPlans.handshakeTimeoutMs),
        )

    override fun isProtocolReady(): Boolean = authorized

    override fun onNotify(characteristicUuid: UUID, bytes: ByteArray): List<BleProtocolOperation> {
        if (characteristicUuid != rxUuid) return emptyList()
        val text = VibCrafterProtocolPlans.decryptNotification(bytes)
        return when {
            text == "OK;" -> {
                authorized = true
                emptyList()
            }
            else -> listOf(
                BleProtocolOperation.Write(
                    characteristicUuid = txUuid,
                    bytes = VibCrafterProtocolPlans.challengeResponsePayload(text),
                    withResponse = false,
                ),
                BleProtocolOperation.WaitForProtocolReady(VibCrafterProtocolPlans.handshakeTimeoutMs),
            )
        }
    }

    override fun commandsFor(action: ToyControlAction): List<BleProtocolOperation> {
        when (action) {
            is ToyControlAction.Intensity -> setAll(action.value)
            is ToyControlAction.Combined -> setAll(action.intensity)
            is ToyControlAction.Pattern -> setAll(maxByFeature.values.maxOrNull() ?: 1)
            is ToyControlAction.DualMotor -> {
                speeds[0] = action.internalIntensity.coerceIn(0, maxForFeature(0))
                speeds[1] = if (channelCount > 1) action.externalIntensity.coerceIn(0, maxForFeature(1)) else speeds[0]
            }
            ToyControlAction.Stop -> speeds.fill(0)
        }
        return listOf(writeState())
    }

    private fun setAll(value: Int) {
        repeat(channelCount) { index ->
            speeds[index] = value.coerceIn(0, maxForFeature(index))
        }
        if (channelCount == 1) speeds[1] = speeds[0]
    }

    private fun maxForFeature(featureIndex: Int): Int =
        maxByFeature[featureIndex] ?: maxByFeature.values.firstOrNull() ?: 99

    private fun writeState(): BleProtocolOperation.Write =
        BleProtocolOperation.Write(
            characteristicUuid = txUuid,
            bytes = VibCrafterProtocolPlans.controlPayload(speeds[0], speeds[1]),
            withResponse = false,
        )
}

private class ButtplugLeloHarmonyProtocol(
    private val match: ButtplugDeviceMatch,
) : BleDeviceProtocol {
    private val endpointByRole = match.endpoint.characteristics
    private val txUuid: UUID = uuid(endpointByRole.getValue("tx"))
    private val security = LeloSecurityHandshake(uuid(endpointByRole.getValue("whitelist")))
    private val features = match.scalarOutputs.sortedBy { it.featureIndex }
    private val maxByFeature = features.associate { it.featureIndex to it.max.coerceAtLeast(1) }

    override val status: BleProtocolStatus = match.toBleStatus(
        id = "buttplug_${match.protocol.id}",
        controllable = endpointByRole.containsKey("tx") && endpointByRole.containsKey("whitelist"),
        supportsMode = false,
        controlStyle = if (features.size > 1) ToyControlStyle.DualIntensityOnly else ToyControlStyle.IntensityOnly,
    )

    override fun matches(fingerprint: BleGattFingerprint): Boolean = false

    override fun initialize(fingerprint: BleGattFingerprint): List<BleProtocolOperation> =
        security.initialize()

    override fun isProtocolReady(): Boolean = security.authorized

    override fun onNotify(characteristicUuid: UUID, bytes: ByteArray): List<BleProtocolOperation> =
        security.onNotify(characteristicUuid, bytes)

    override fun commandsFor(action: ToyControlAction): List<BleProtocolOperation> =
        when (action) {
            is ToyControlAction.Intensity -> writeAll(action.value)
            is ToyControlAction.Combined -> writeAll(action.intensity)
            is ToyControlAction.Pattern -> writeAll(maxByFeature.values.maxOrNull() ?: 1)
            is ToyControlAction.DualMotor -> {
                if (features.size > 1) {
                    listOf(
                        write(features[0].featureIndex, action.internalIntensity),
                        write(features[1].featureIndex, action.externalIntensity),
                    )
                } else {
                    writeAll(action.strongestIntensity(maxByFeature[0] ?: 1))
                }
            }
            ToyControlAction.Stop -> writeAll(0)
        }

    private fun writeAll(speed: Int): List<BleProtocolOperation> =
        features.ifEmpty { listOf(null) }.mapIndexed { index, feature ->
            write(feature?.featureIndex ?: index, speed)
        }

    private fun write(featureIndex: Int, speed: Int): BleProtocolOperation.Write =
        BleProtocolOperation.Write(
            characteristicUuid = txUuid,
            bytes = LeloProtocolPlans.harmonyPayload(featureIndex, speed.coerceIn(0, maxByFeature[featureIndex] ?: 100)),
            withResponse = false,
        )

}

private class ButtplugLeloF1sV2Protocol(
    private val match: ButtplugDeviceMatch,
) : BleDeviceProtocol {
    private val endpointByRole = match.endpoint.characteristics
    private val txUuid: UUID = uuid(endpointByRole.getValue("tx"))
    private val securityEndpointRole = if (endpointByRole.containsKey("whitelist")) "whitelist" else "generic0"
    private val security = LeloSecurityHandshake(uuid(endpointByRole.getValue(securityEndpointRole)))
    private val features = match.vibrateOutputs.sortedBy { it.featureIndex }
    private val channelCount = features.size.coerceAtLeast(1).coerceAtMost(2)
    private val speeds = IntArray(2)
    private val maxByFeature = features.associate { it.featureIndex to it.max.coerceAtLeast(1) }

    override val status: BleProtocolStatus = match.toBleStatus(
        id = "buttplug_${match.protocol.id}",
        controllable = endpointByRole.containsKey("tx") && endpointByRole.containsKey(securityEndpointRole),
        supportsMode = false,
        controlStyle = if (channelCount > 1) ToyControlStyle.DualIntensityOnly else ToyControlStyle.IntensityOnly,
    )

    override fun matches(fingerprint: BleGattFingerprint): Boolean = false

    override fun initialize(fingerprint: BleGattFingerprint): List<BleProtocolOperation> =
        security.initialize()

    override fun isProtocolReady(): Boolean = security.authorized

    override fun onNotify(characteristicUuid: UUID, bytes: ByteArray): List<BleProtocolOperation> =
        security.onNotify(characteristicUuid, bytes)

    override fun commandsFor(action: ToyControlAction): List<BleProtocolOperation> {
        when (action) {
            is ToyControlAction.Intensity -> setAll(action.value)
            is ToyControlAction.Combined -> setAll(action.intensity)
            is ToyControlAction.Pattern -> setAll(maxByFeature.values.maxOrNull() ?: 1)
            is ToyControlAction.DualMotor -> {
                speeds[0] = action.internalIntensity.coerceIn(0, maxForFeature(0))
                speeds[1] = if (channelCount > 1) {
                    action.externalIntensity.coerceIn(0, maxForFeature(1))
                } else {
                    speeds[0]
                }
            }
            ToyControlAction.Stop -> speeds.fill(0)
        }
        return listOf(writeState())
    }

    private fun setAll(value: Int) {
        repeat(channelCount) { index ->
            speeds[index] = value.coerceIn(0, maxForFeature(index))
        }
        if (channelCount == 1) speeds[1] = speeds[0]
    }

    private fun maxForFeature(featureIndex: Int): Int =
        maxByFeature[featureIndex] ?: maxByFeature.values.firstOrNull() ?: 100

    private fun writeState(): BleProtocolOperation.Write =
        BleProtocolOperation.Write(
            characteristicUuid = txUuid,
            bytes = LeloProtocolPlans.f1sPayload(speeds[0], speeds[1]),
            withResponse = true,
        )
}

private class LeloSecurityHandshake(
    private val securityUuid: UUID,
) {
    var authorized: Boolean = false
        private set

    fun initialize(): List<BleProtocolOperation> =
        listOf(
            BleProtocolOperation.SubscribeNotify(securityUuid),
            BleProtocolOperation.WaitForProtocolReady(LELO_SECURITY_TIMEOUT_MS),
        )

    fun onNotify(characteristicUuid: UUID, bytes: ByteArray): List<BleProtocolOperation> {
        if (characteristicUuid != securityUuid) return emptyList()
        return when {
            bytes.isEmpty() || bytes.all { it == 0.toByte() } -> emptyList()
            bytes.size >= 8 && bytes[0] == 1.toByte() && bytes.drop(1).all { it == 0.toByte() } -> {
                authorized = true
                emptyList()
            }
            else -> listOf(
                BleProtocolOperation.UnsubscribeNotify(securityUuid),
                BleProtocolOperation.Write(
                    characteristicUuid = securityUuid,
                    bytes = bytes,
                    withResponse = true,
                ),
                BleProtocolOperation.SubscribeNotify(securityUuid),
                BleProtocolOperation.WaitForProtocolReady(LELO_SECURITY_TIMEOUT_MS),
            )
        }
    }

    private companion object {
        const val LELO_SECURITY_TIMEOUT_MS = 30_000L
    }
}

private abstract class ButtplugVibrateProtocol(
    protected val match: ButtplugDeviceMatch,
) : BleDeviceProtocol {
    protected val txUuid: UUID = uuid(match.endpoint.txUuid.orEmpty())
    protected val vibrateFeatures = match.vibrateOutputs.sortedBy { it.featureIndex }
    protected val channelCount = vibrateFeatures.size.coerceAtLeast(1).coerceAtMost(2)
    protected val intensities = IntArray(2)
    protected val maxIntensity = vibrateFeatures.maxOfOrNull { it.max }?.coerceAtLeast(1) ?: 1

    override val status: BleProtocolStatus = match.toBleStatus(
        id = "buttplug_${match.protocol.id}",
        controllable = txUuid.toString().isNotBlank(),
        supportsMode = false,
        controlStyle = if (channelCount > 1) ToyControlStyle.DualIntensityOnly else ToyControlStyle.IntensityOnly,
    )

    override fun matches(fingerprint: BleGattFingerprint): Boolean = false

    override fun commandsFor(action: ToyControlAction): List<BleProtocolOperation> {
        when (action) {
            is ToyControlAction.Intensity -> setAll(action.value)
            is ToyControlAction.Combined -> setAll(action.intensity)
            is ToyControlAction.DualMotor -> {
                intensities[0] = action.internalIntensity.coerceIn(0, maxIntensity)
                intensities[1] = if (channelCount > 1) {
                    action.externalIntensity.coerceIn(0, maxIntensity)
                } else {
                    intensities[0]
                }
            }
            is ToyControlAction.Pattern -> setAll(maxIntensity)
            ToyControlAction.Stop -> intensities.fill(0)
        }
        return listOf(writePayload(payloadFor(intensities[0], intensities[if (channelCount > 1) 1 else 0])))
    }

    protected abstract fun payloadFor(internalIntensity: Int, externalIntensity: Int): ByteArray

    private fun setAll(value: Int) {
        val next = value.coerceIn(0, maxIntensity)
        intensities[0] = next
        intensities[1] = next
    }

    private fun writePayload(payload: ByteArray): BleProtocolOperation.Write =
        BleProtocolOperation.Write(
            characteristicUuid = txUuid,
            bytes = payload,
            withResponse = true,
        )
}

private class ButtplugSimpleVibrateProtocol(
    private val match: ButtplugDeviceMatch,
    private val plan: SimpleVibrateProtocolPlan,
) : BleDeviceProtocol {
    private val txUuid: UUID = uuid(match.endpoint.txUuid.orEmpty())
    private val deviceName = match.device.name
    private val vibrateFeatures = match.vibrateOutputs.sortedBy { it.featureIndex }
    private val channelCount = vibrateFeatures.size.coerceAtLeast(1)
    private val maxIntensity = vibrateFeatures.maxOfOrNull { it.max }?.coerceAtLeast(1) ?: 1

    override val status: BleProtocolStatus = match.toBleStatus(
        id = "buttplug_${match.protocol.id}",
        controllable = txUuid.toString().isNotBlank(),
        supportsMode = false,
        controlStyle = if (channelCount > 1) ToyControlStyle.DualIntensityOnly else ToyControlStyle.IntensityOnly,
    )

    override fun matches(fingerprint: BleGattFingerprint): Boolean = false

    override fun initialize(fingerprint: BleGattFingerprint): List<BleProtocolOperation> =
        buildList {
            for (endpointRole in plan.initSubscriptions) {
                val endpoint = requireNotNull(match.endpoint.characteristics[endpointRole]) {
                    "${plan.protocolId} endpoint $endpointRole is missing"
                }
                add(BleProtocolOperation.SubscribeNotify(uuid(endpoint)))
            }
            for (payload in plan.initPayloads) {
                add(
                    BleProtocolOperation.Write(
                        characteristicUuid = txUuid,
                        bytes = payload,
                        withResponse = plan.withResponse,
                    ),
                )
            }
            for (write in plan.initWrites) {
                val endpoint = requireNotNull(match.endpoint.characteristics[write.endpointRole]) {
                    "${plan.protocolId} endpoint ${write.endpointRole} is missing"
                }
                add(
                    BleProtocolOperation.Write(
                        characteristicUuid = uuid(endpoint),
                        bytes = write.bytes,
                        withResponse = write.withResponse,
                    ),
                )
            }
            if (plan.initDelayMs > 0) {
                add(BleProtocolOperation.Sleep(plan.initDelayMs))
            }
        }

    override fun keepaliveIntervalMs(): Long = plan.keepaliveIntervalMs

    override fun commandsFor(action: ToyControlAction): List<BleProtocolOperation> =
        when (action) {
            is ToyControlAction.Intensity -> writeAll(action.value)
            is ToyControlAction.Combined -> writeAll(action.intensity)
            is ToyControlAction.Pattern -> writeAll(maxIntensity)
            is ToyControlAction.DualMotor -> {
                if (channelCount > 1) {
                    listOf(
                        write(featureIndex = 0, speed = action.internalIntensity),
                        write(featureIndex = 1, speed = action.externalIntensity),
                    )
                } else {
                    writeAll(action.strongestIntensity(maxIntensity))
                }
            }
            ToyControlAction.Stop -> writeAll(0)
        }

    private fun writeAll(speed: Int): List<BleProtocolOperation.Write> =
        (0 until channelCount).map { index -> write(index, speed) }

    private fun write(featureIndex: Int, speed: Int): BleProtocolOperation.Write =
        BleProtocolOperation.Write(
            characteristicUuid = txUuid,
            bytes = plan.payloadFor(deviceName, featureIndex, speed.coerceIn(0, maxIntensity)),
            withResponse = plan.withResponse,
        )
}

private class ButtplugStatefulVibrateProtocol(
    private val match: ButtplugDeviceMatch,
    private val plan: StatefulVibrateProtocolPlan,
) : BleDeviceProtocol {
    private val vibrateFeatures = match.vibrateOutputs.sortedBy { it.featureIndex }
    private val channelCount = maxOf(vibrateFeatures.size, plan.minimumChannels)
    private val maxByFeature = vibrateFeatures.associate { it.featureIndex to it.max.coerceAtLeast(1) }
    private val speeds = IntArray(channelCount)
    private var packetId = 0
    private val endpointByRole = match.endpoint.characteristics

    override val status: BleProtocolStatus = match.toBleStatus(
        id = "buttplug_${match.protocol.id}",
        controllable = endpointByRole.isNotEmpty(),
        supportsMode = false,
        controlStyle = if (channelCount > 1) ToyControlStyle.DualIntensityOnly else ToyControlStyle.IntensityOnly,
    )

    override fun matches(fingerprint: BleGattFingerprint): Boolean = false

    override fun initialize(fingerprint: BleGattFingerprint): List<BleProtocolOperation> =
        buildList {
            for (endpointRole in plan.initSubscriptions) {
                val endpoint = requireNotNull(endpointByRole[endpointRole]) {
                    "${plan.protocolId} endpoint $endpointRole is missing"
                }
                add(BleProtocolOperation.SubscribeNotify(uuid(endpoint)))
            }
            for (write in plan.initWrites) {
                val endpoint = requireNotNull(endpointByRole[write.endpointRole]) {
                    "${plan.protocolId} endpoint ${write.endpointRole} is missing"
                }
                add(
                    BleProtocolOperation.Write(
                        characteristicUuid = uuid(endpoint),
                        bytes = write.bytes,
                        withResponse = write.withResponse,
                    ),
                )
            }
        }

    override fun keepaliveIntervalMs(): Long = plan.keepaliveIntervalMs

    override fun commandsFor(action: ToyControlAction): List<BleProtocolOperation> =
        when (action) {
            is ToyControlAction.Intensity -> writeAll(action.value)
            is ToyControlAction.Combined -> writeAll(action.intensity)
            is ToyControlAction.Pattern -> writeAll(maxByFeature.values.maxOrNull() ?: 1)
            is ToyControlAction.DualMotor -> {
                if (channelCount > 1) {
                    speeds[0] = action.internalIntensity.coerceIn(0, maxForFeature(0))
                    speeds[1] = action.externalIntensity.coerceIn(0, maxForFeature(1))
                    writeEachChannel()
                } else {
                    writeAll(action.strongestIntensity(maxForFeature(0)))
                }
            }
            ToyControlAction.Stop -> {
                speeds.fill(0)
                writeEachChannel()
            }
        }

    private fun writeAll(speed: Int): List<BleProtocolOperation.Write> {
        repeat(channelCount) { index -> speeds[index] = speed.coerceIn(0, maxForFeature(index)) }
        return writeEachChannel()
    }

    private fun writeEachChannel(): List<BleProtocolOperation.Write> =
        (0 until channelCount).flatMap { index -> writeState(changedFeatureIndex = index) }

    private fun writeState(changedFeatureIndex: Int): List<BleProtocolOperation.Write> =
        plan.writesFor(speeds, changedFeatureIndex, nextPacketId()).map { write ->
            val endpoint = requireNotNull(endpointByRole[write.endpointRole]) {
                "${plan.protocolId} endpoint ${write.endpointRole} is missing"
            }
            BleProtocolOperation.Write(
                characteristicUuid = uuid(endpoint),
                bytes = write.bytes,
                withResponse = write.withResponse,
            )
        }

    private fun nextPacketId(): Int {
        if (!plan.usesPacketSequence) return 0
        val current = packetId
        packetId = (packetId + 1) and 0xFF
        return current
    }

    private fun maxForFeature(featureIndex: Int): Int =
        maxByFeature[featureIndex] ?: maxByFeature.values.firstOrNull() ?: 1
}

private class ButtplugScalarProtocol(
    private val match: ButtplugDeviceMatch,
    private val plan: ScalarProtocolPlan,
) : BleDeviceProtocol {
    private val endpointByRole = match.endpoint.characteristics
    private val txUuid: UUID = uuid(match.endpoint.txUuid.orEmpty())
    private val scalarFeatures = match.scalarOutputs.sortedBy { it.featureIndex }
    private val state = plan.createState(match)
    private val endpointRoleByUuid = endpointByRole.entries.associate { uuid(it.value) to it.key }
    private var packetId = 0
    private val maxValue = scalarFeatures.maxOfOrNull { it.max.coerceAtLeast(1) } ?: 1

    override val status: BleProtocolStatus = match.toBleStatus(
        id = "buttplug_${match.protocol.id}",
        controllable = txUuid.toString().isNotBlank(),
        supportsMode = false,
        controlStyle = if (scalarFeatures.size > 1) ToyControlStyle.DualIntensityOnly else ToyControlStyle.IntensityOnly,
    )

    override fun matches(fingerprint: BleGattFingerprint): Boolean = false

    override fun initialize(fingerprint: BleGattFingerprint): List<BleProtocolOperation> =
        buildList {
            for (endpointRole in plan.initSubscriptions) {
                val endpoint = requireNotNull(endpointByRole[endpointRole]) {
                    "${plan.protocolId} endpoint $endpointRole is missing"
                }
                add(BleProtocolOperation.SubscribeNotify(uuid(endpoint)))
            }
            addAll(operationsForWrites(plan.initWrites))
            for (read in plan.initReads) {
                val endpoint = requireNotNull(endpointByRole[read.endpointRole]) {
                    "${plan.protocolId} endpoint ${read.endpointRole} is missing"
                }
                add(BleProtocolOperation.Read(uuid(endpoint)))
            }
        }

    override fun keepaliveIntervalMs(): Long = plan.keepaliveIntervalMs

    override fun keepaliveCommands(lastCommands: List<BleProtocolOperation>): List<BleProtocolOperation> =
        plan.keepaliveWritesFor(state)?.let(::operationsForWrites) ?: lastCommands

    override fun onRead(characteristicUuid: UUID, bytes: ByteArray): List<BleProtocolOperation> {
        val endpointRole = endpointRoleByUuid[characteristicUuid] ?: return emptyList()
        plan.onRead?.invoke(state, endpointRole, bytes)
        return emptyList()
    }

    override fun commandsFor(action: ToyControlAction): List<BleProtocolOperation> =
        when (action) {
            is ToyControlAction.Intensity -> writeAll(action.value)
            is ToyControlAction.Combined -> writeAll(action.intensity)
            is ToyControlAction.Pattern -> writeAll(maxValue)
            is ToyControlAction.DualMotor -> {
                if (scalarFeatures.size > 1) {
                    buildList {
                        addAll(writeFeature(scalarFeatures[0], action.internalIntensity))
                        addAll(writeFeature(scalarFeatures[1], action.externalIntensity))
                    }
                } else {
                    writeAll(action.strongestIntensity(maxValue))
                }
            }
            ToyControlAction.Stop -> scalarFeatures.flatMap { writeFeature(it, 0) }
        }

    private fun writeAll(value: Int): List<BleProtocolOperation> =
        scalarFeatures.flatMap { feature -> writeFeature(feature, value) }

    private fun writeFeature(feature: com.funny.aitoy.buttplug.ButtplugOutputFeature, value: Int): List<BleProtocolOperation> {
        val kind = feature.type.toToyOutputKind()
        val signedValue = if (kind == ToyOutputKind.Rotate && feature.min < 0) {
            value.coerceIn(0, maxOf(kotlin.math.abs(feature.min), kotlin.math.abs(feature.max)))
        } else {
            value.coerceIn(feature.min, feature.max)
        }
        return plan.writesFor(
            state,
            com.funny.aitoy.buttplug.ScalarProtocolCommand(feature.featureIndex, kind, signedValue),
            nextPacketId(),
        ).let(::operationsForWrites)
    }

    private fun operationsForWrites(writes: List<ScalarProtocolWrite>): List<BleProtocolOperation> =
        buildList {
            for (write in writes) {
                if (write.delayBeforeMs > 0L) {
                    add(BleProtocolOperation.Sleep(write.delayBeforeMs))
                }
                val endpoint = requireNotNull(endpointByRole[write.endpointRole]) {
                    "${plan.protocolId} endpoint ${write.endpointRole} is missing"
                }
                add(
                    BleProtocolOperation.Write(
                        characteristicUuid = uuid(endpoint),
                        bytes = write.bytes,
                        withResponse = write.withResponse,
                    ),
                )
            }
        }

    private fun nextPacketId(): Int {
        if (!plan.usesPacketSequence) return 0
        val current = packetId
        packetId = (packetId + 1) and 0xFF
        return current
    }
}

private class ButtplugLinearProtocol(
    private val match: ButtplugDeviceMatch,
    private val plan: LinearProtocolPlan,
) : BleDeviceProtocol {
    private val endpointByRole = match.endpoint.characteristics
    private val txUuid: UUID = uuid(match.endpoint.txUuid.orEmpty())
    private val linearFeatures = match.linearOutputs.sortedBy { it.featureIndex }
    private val state = IntArray(maxOf(plan.stateSize, linearFeatures.size))
    private val maxPosition = linearFeatures.maxOfOrNull { it.max.coerceAtLeast(1) } ?: 1

    override val status: BleProtocolStatus = match.toBleStatus(
        id = "buttplug_${match.protocol.id}",
        controllable = txUuid.toString().isNotBlank(),
        supportsMode = false,
        controlStyle = ToyControlStyle.IntensityOnly,
    )

    override fun matches(fingerprint: BleGattFingerprint): Boolean = false

    override fun initialize(fingerprint: BleGattFingerprint): List<BleProtocolOperation> =
        buildList {
            for (endpointRole in plan.initSubscriptions) {
                val endpoint = requireNotNull(endpointByRole[endpointRole]) {
                    "${plan.protocolId} endpoint $endpointRole is missing"
                }
                add(BleProtocolOperation.SubscribeNotify(uuid(endpoint)))
            }
            for (write in plan.initWrites) {
                val endpoint = requireNotNull(endpointByRole[write.endpointRole]) {
                    "${plan.protocolId} endpoint ${write.endpointRole} is missing"
                }
                add(
                    BleProtocolOperation.Write(
                        characteristicUuid = uuid(endpoint),
                        bytes = write.bytes,
                        withResponse = write.withResponse,
                    ),
                )
            }
        }

    override fun commandsFor(action: ToyControlAction): List<BleProtocolOperation> =
        when (action) {
            is ToyControlAction.Intensity -> writeAll(action.value)
            is ToyControlAction.Combined -> writeAll(action.intensity)
            is ToyControlAction.Pattern -> writeAll(maxPosition)
            is ToyControlAction.DualMotor -> writeAll(action.strongestIntensity(maxPosition))
            ToyControlAction.Stop -> linearFeatures.flatMap { writeFeature(it, it.min) }
        }

    private fun writeAll(value: Int): List<BleProtocolOperation.Write> =
        linearFeatures.flatMap { feature ->
            val position = value.coerceIn(feature.min, feature.max)
            writeFeature(feature, position)
        }

    private fun writeFeature(
        feature: com.funny.aitoy.buttplug.ButtplugOutputFeature,
        position: Int,
    ): List<BleProtocolOperation.Write> =
        plan.writes(
            state,
            com.funny.aitoy.buttplug.LinearProtocolCommand(
                featureIndex = feature.featureIndex,
                position = position.coerceIn(feature.min, feature.max),
                durationMs = DEFAULT_LINEAR_DURATION_MS,
            ),
        ).map { write ->
            val endpoint = requireNotNull(endpointByRole[write.endpointRole]) {
                "${plan.protocolId} endpoint ${write.endpointRole} is missing"
            }
            BleProtocolOperation.Write(
                characteristicUuid = uuid(endpoint),
                bytes = write.bytes,
                withResponse = write.withResponse,
            )
        }

    private companion object {
        const val DEFAULT_LINEAR_DURATION_MS = 1000
    }
}

private class ButtplugMixedOutputProtocol(
    private val match: ButtplugDeviceMatch,
    private val plan: MixedOutputProtocolPlan,
) : BleDeviceProtocol {
    private val endpointByRole = match.endpoint.characteristics
    private val txUuid: UUID = uuid(match.endpoint.txUuid.orEmpty())
    private val scalarFeatures = match.scalarOutputs.sortedBy { it.featureIndex }
    private val linearFeatures = match.linearOutputs.sortedBy { it.featureIndex }
    private val state = plan.createState(match)
    private val maxValue = (scalarFeatures + linearFeatures).maxOfOrNull { it.max.coerceAtLeast(1) } ?: 1

    override val status: BleProtocolStatus = match.toBleStatus(
        id = "buttplug_${match.protocol.id}",
        controllable = txUuid.toString().isNotBlank(),
        supportsMode = false,
        controlStyle = if (scalarFeatures.size > 1) ToyControlStyle.DualIntensityOnly else ToyControlStyle.IntensityOnly,
    )

    override fun matches(fingerprint: BleGattFingerprint): Boolean = false

    override fun commandsFor(action: ToyControlAction): List<BleProtocolOperation> =
        when (action) {
            is ToyControlAction.Intensity -> writeAll(action.value)
            is ToyControlAction.Combined -> writeAll(action.intensity)
            is ToyControlAction.Pattern -> writeAll(maxValue)
            is ToyControlAction.DualMotor -> {
                if (scalarFeatures.size > 1) {
                    buildList {
                        addAll(writeScalarFeature(scalarFeatures[0], action.internalIntensity))
                        addAll(writeScalarFeature(scalarFeatures[1], action.externalIntensity))
                    }
                } else {
                    writeAll(action.strongestIntensity(maxValue))
                }
            }
            ToyControlAction.Stop -> buildList {
                for (feature in scalarFeatures) addAll(writeScalarFeature(feature, 0))
                for (feature in linearFeatures) addAll(writeLinearFeature(feature, feature.min))
            }
        }

    private fun writeAll(value: Int): List<BleProtocolOperation.Write> =
        buildList {
            for (feature in scalarFeatures) addAll(writeScalarFeature(feature, value))
            for (feature in linearFeatures) addAll(writeLinearFeature(feature, value))
        }

    private fun writeScalarFeature(feature: com.funny.aitoy.buttplug.ButtplugOutputFeature, value: Int): List<BleProtocolOperation.Write> {
        val kind = feature.type.toToyOutputKind()
        val coerced = value.coerceIn(feature.min, feature.max)
        return plan.scalarWrites(
            state,
            com.funny.aitoy.buttplug.ScalarProtocolCommand(feature.featureIndex, kind, coerced),
        ).toBleWrites()
    }

    private fun writeLinearFeature(feature: com.funny.aitoy.buttplug.ButtplugOutputFeature, value: Int): List<BleProtocolOperation.Write> =
        plan.linearWrites(
            state,
            com.funny.aitoy.buttplug.LinearProtocolCommand(
                featureIndex = feature.featureIndex,
                position = value.coerceIn(feature.min, feature.max),
                durationMs = DEFAULT_LINEAR_DURATION_MS,
            ),
        ).toBleWrites()

    private fun List<com.funny.aitoy.buttplug.MixedOutputProtocolWrite>.toBleWrites(): List<BleProtocolOperation.Write> =
        map { write ->
            val endpoint = requireNotNull(endpointByRole[write.endpointRole]) {
                "${plan.protocolId} endpoint ${write.endpointRole} is missing"
            }
            BleProtocolOperation.Write(
                characteristicUuid = uuid(endpoint),
                bytes = write.bytes,
                withResponse = write.withResponse,
            )
        }

    private companion object {
        const val DEFAULT_LINEAR_DURATION_MS = 1000
    }
}

private class ButtplugWeVibePackedProtocol(
    match: ButtplugDeviceMatch,
) : ButtplugVibrateProtocol(match) {
    private var currentPattern = 1

    override val status: BleProtocolStatus = match.toBleStatus(
        id = "buttplug_wevibe",
        controllable = true,
        supportsMode = true,
        modeMax = WeVibeProtocolPlans.packedPatternCodes.size,
        controlStyle = if (channelCount > 1) {
            ToyControlStyle.PatternAndDualIntensity
        } else {
            ToyControlStyle.CombinedPatternAndIntensity
        },
        modeNames = WeVibeProtocolPlans.packedPatternNames,
    )

    override fun commandsFor(action: ToyControlAction): List<BleProtocolOperation> {
        when (action) {
            is ToyControlAction.Pattern -> currentPattern = action.mode.coerceIn(1, WeVibeProtocolPlans.packedPatternCodes.size)
            is ToyControlAction.Combined -> currentPattern = action.mode.coerceIn(1, WeVibeProtocolPlans.packedPatternCodes.size)
            is ToyControlAction.DualMotor -> currentPattern = action.mode.coerceIn(1, WeVibeProtocolPlans.packedPatternCodes.size)
            else -> Unit
        }
        return super.commandsFor(action)
    }

    override fun payloadFor(internalIntensity: Int, externalIntensity: Int): ByteArray =
        WeVibeProtocolPlans.packedPatternPayload(currentPattern, internalIntensity, externalIntensity)
}

private class ButtplugWeVibe8BitProtocol(
    match: ButtplugDeviceMatch,
) : ButtplugVibrateProtocol(match) {
    override fun payloadFor(internalIntensity: Int, externalIntensity: Int): ByteArray =
        WeVibeProtocolPlans.eightBitPayload(channelCount, internalIntensity, externalIntensity)
}

private class ButtplugWeVibeChorusProtocol(
    match: ButtplugDeviceMatch,
) : ButtplugVibrateProtocol(match) {
    override fun payloadFor(internalIntensity: Int, externalIntensity: Int): ByteArray =
        WeVibeProtocolPlans.chorusPayload(channelCount, internalIntensity, externalIntensity)
}

private object WeVibeSyncLiteProtocol : BleDeviceProtocol {
    private val serviceUuid = uuid("f000bb03-0451-4000-b000-000000000000")
    private val txUuid = uuid("f000c000-0451-4000-b000-000000000000")
    private val rxUuid = uuid("f000b000-0451-4000-b000-000000000000")
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

private object SistalkMixPwmProtocol : BleDeviceProtocol {
    private val pwmUuid = uuid("00010203-0405-0607-0809-0a0b0c0d2b12")
    private val sistalkV2ServiceUuid = uuid("00009000-0000-1000-8000-00805f9b34fb")
    private val sistalkV2OpUuid = uuid("00009001-0000-1000-8000-00805f9b34fb")
    private val sistalkV2FunctionUuid = uuid("00009002-0000-1000-8000-00805f9b34fb")

    // MonsterPub V1 Hub 的电机服务/特征。这类设备同样会暴露 2b12，但向 2b12 写入会让其在 ~4 秒后掉电关机
    // （2026-06-24 23:35 白夜魔炮 Trace：写 2b12 → status=8 链路超时）。因此只要存在 6000 电机服务就禁用 Mix PWM，
    // 改走官方 6003/600a/6001 路线。
    private val sistalkMotorServiceUuid = uuid("00006000-0000-1000-8000-00805f9b34fb")
    private val sistalkMotorWriteUuids = setOf(
        uuid("0000600a-0000-1000-8000-00805f9b34fb"),
        uuid("00006003-0000-1000-8000-00805f9b34fb"),
        uuid("00006001-0000-1000-8000-00805f9b34fb"),
    )
    private val currentPwm = IntArray(2)

    override val status = BleProtocolStatus(
        id = "sistalk_mix_pwm",
        displayName = "SISTALK Mix PWM",
        controllable = true,
        intensityMax = 100,
        supportsMode = true,
        modeMax = 2,
        controlStyle = ToyControlStyle.CombinedPatternAndIntensity,
        modeLabel = "部位",
        modeNames = listOf("吮吸", "尾部震动"),
        automatic = true,
    )

    override fun matches(fingerprint: BleGattFingerprint): Boolean =
        fingerprint.characteristicUuids.contains(pwmUuid) &&
                !fingerprint.hasSistalkV2Gatt() &&
                !fingerprint.hasSistalkMotorService()

    override fun initialize(fingerprint: BleGattFingerprint): List<BleProtocolOperation> {
        currentPwm.fill(0)
        return emptyList()
    }

    override fun commandsFor(action: ToyControlAction): List<BleProtocolOperation> =
        when (action) {
            is ToyControlAction.Intensity -> listOf(updateAllChannels(action.value))
            is ToyControlAction.Combined -> listOf(updateChannel(action.mode, action.intensity))
            is ToyControlAction.DualMotor -> listOf(updateAllChannels(action.strongestIntensity(status.intensityMax)))
            is ToyControlAction.Pattern -> listOf(updateChannel(action.mode, status.intensityMax.coerceAtLeast(1)))
            ToyControlAction.Stop -> stop()?.let(::listOf).orEmpty()
        }

    private fun updateAllChannels(intensity: Int): BleProtocolOperation.Write {
        currentPwm.fill(intensity.coerceIn(0, status.intensityMax))
        return pwm(currentPwm[0], currentPwm[1])
    }

    private fun updateChannel(channel: Int, intensity: Int): BleProtocolOperation.Write {
        val index = channel.coerceIn(1, 2) - 1
        currentPwm[index] = intensity.coerceIn(0, status.intensityMax)
        return pwm(currentPwm[0], currentPwm[1])
    }

    private fun stop(): BleProtocolOperation.Write? {
        if (currentPwm.all { it == 0 }) return null
        currentPwm.fill(0)
        return pwm(0, 0)
    }

    private fun pwm(suction: Int, tailVibration: Int): BleProtocolOperation.Write =
        BleProtocolOperation.Write(
            characteristicUuid = pwmUuid,
            bytes = bytes(
                suction.coerceIn(0, status.intensityMax),
                tailVibration.coerceIn(0, status.intensityMax),
            ),
            withResponse = false,
        )

    private fun BleGattFingerprint.hasSistalkV2Gatt(): Boolean =
        serviceUuids.contains(sistalkV2ServiceUuid) &&
                characteristicUuids.contains(sistalkV2OpUuid) &&
                characteristicUuids.contains(sistalkV2FunctionUuid)

    private fun BleGattFingerprint.hasSistalkMotorService(): Boolean =
        serviceUuids.contains(sistalkMotorServiceUuid) ||
                characteristicUuids.any { it in sistalkMotorWriteUuids }

}

private object SistalkMonsterPartyProtocol : BleDeviceProtocol {
    private val serviceUuid = uuid("00009000-0000-1000-8000-00805f9b34fb")
    private val opUuid = uuid("00009001-0000-1000-8000-00805f9b34fb")
    private val functionUuid = uuid("00009002-0000-1000-8000-00805f9b34fb")

    private const val COMMAND_MOTOR = 0xA0
    private const val COMMAND_POWER_OFF = 0xA2
    private val currentPwm = IntArray(2)

    override val status = BleProtocolStatus(
        id = "sistalk_monsterparty",
        displayName = "SISTALK Monster Party",
        controllable = true,
        intensityMax = 100,
        supportsMode = true,
        modeMax = 2,
        controlStyle = ToyControlStyle.CombinedPatternAndIntensity,
        modeLabel = "部位",
        modeNames = listOf("吮吸", "尾部震动"),
        automatic = true,
    )

    override fun matches(fingerprint: BleGattFingerprint): Boolean =
        fingerprint.sistalkProtocolVersion != 1 &&
                fingerprint.serviceUuids.contains(serviceUuid) &&
                fingerprint.characteristicUuids.contains(opUuid) &&
                fingerprint.characteristicUuids.contains(functionUuid)

    override fun initialize(fingerprint: BleGattFingerprint): List<BleProtocolOperation> =
        listOf(
            BleProtocolOperation.SubscribeNotify(opUuid),
            BleProtocolOperation.SubscribeNotify(functionUuid),
        ).also {
            currentPwm.fill(0)
        }

    override fun commandsFor(action: ToyControlAction): List<BleProtocolOperation> =
        when (action) {
            is ToyControlAction.Intensity -> listOf(updateAllChannels(action.value))
            is ToyControlAction.Combined -> listOf(updateChannel(action.mode, action.intensity))
            is ToyControlAction.DualMotor -> listOf(updateAllChannels(action.strongestIntensity(status.intensityMax)))
            is ToyControlAction.Pattern -> listOf(updateChannel(action.mode, status.intensityMax.coerceAtLeast(1)))
            ToyControlAction.Stop -> stop()?.let(::listOf).orEmpty()
        }

    private fun updateAllChannels(intensity: Int): BleProtocolOperation.Write {
        currentPwm.fill(intensity.coerceIn(0, status.intensityMax))
        return motorCommand(currentPwm[0], currentPwm[1])
    }

    private fun updateChannel(channel: Int, intensity: Int): BleProtocolOperation.Write {
        val index = channel.coerceIn(1, 2) - 1
        currentPwm[index] = intensity.coerceIn(0, status.intensityMax)
        return motorCommand(currentPwm[0], currentPwm[1])
    }

    private fun stop(): BleProtocolOperation.Write? {
        if (currentPwm.all { it == 0 }) return null
        currentPwm.fill(0)
        return motorCommand(0, 0)
    }

    private fun motorCommand(suction: Int, tailVibration: Int): BleProtocolOperation.Write =
        functionCommand(
            COMMAND_MOTOR,
            0x02,
            suction.coerceIn(0, status.intensityMax),
            tailVibration.coerceIn(0, status.intensityMax),
        )

    private fun functionCommand(command: Int, vararg payload: Int): BleProtocolOperation.Write =
        BleProtocolOperation.Write(
            characteristicUuid = functionUuid,
            bytes = bytes(command, ((payload.size shl 3) or 0x80), *payload),
            withResponse = false,
        )

    @Suppress("unused")
    private fun powerOffVibrationCommand(): BleProtocolOperation.Write =
        functionCommand(COMMAND_POWER_OFF)
}

private object SistalkMonsterPubMultiMotorProtocol : SistalkMonsterPubProtocolBase(
    id = "sistalk_monsterpub_multi",
    displayName = "SISTALK MonsterPub",
    writeUuid = uuid("0000600a-0000-1000-8000-00805f9b34fb"),
    channelCount = 10,
    intensityMax = 100,
)

private object SistalkMonsterPubDualMotorProtocol : SistalkMonsterPubProtocolBase(
    id = "sistalk_monsterpub_dual",
    displayName = "SISTALK MonsterPub",
    writeUuid = uuid("00006003-0000-1000-8000-00805f9b34fb"),
    channelCount = 2,
    intensityMax = 100,
    // 白夜魔炮等双电机 Hub：吮吸 + 震动两个模式，各有档位。
    // 通道顺序（字节0=吮吸 / 字节1=震动）以真机反馈为准，如反了把这里两项对调即可。
    channelNames = listOf("吮吸", "震动"),
)

private object SistalkMonsterPubSingleMotorProtocol : SistalkMonsterPubProtocolBase(
    id = "sistalk_monsterpub",
    displayName = "SISTALK MonsterPub",
    writeUuid = uuid("00006001-0000-1000-8000-00805f9b34fb"),
    channelCount = 1,
    intensityMax = 20,
    useLegacyLevelMap = true,
)

private sealed class SistalkMonsterPubProtocolBase(
    id: String,
    displayName: String,
    private val writeUuid: UUID,
    private val channelCount: Int,
    private val intensityMax: Int,
    private val useLegacyLevelMap: Boolean = false,
    channelNames: List<String>? = null,
) : BleDeviceProtocol {
    private val motorServiceUuid = uuid("00006000-0000-1000-8000-00805f9b34fb")
    // 官方 BleDeviceV1._fetchInfo 在连接后会订阅心跳(600b)与读取电量(6051)等通知通道，
    // 让设备认定为合法 App 会话。缺少该握手是历史「6000 路线无反应」的主要原因。
    private val heartbeatUuid = uuid("0000600b-0000-1000-8000-00805f9b34fb")
    private val batteryNotifyUuid = uuid("00006051-0000-1000-8000-00805f9b34fb")
    private val resolvedChannelNames = channelNames
    private val currentPwm = IntArray(channelCount)
    private val levelMap = intArrayOf(
        0,
        10,
        14,
        18,
        22,
        26,
        30,
        34,
        38,
        42,
        46,
        50,
        54,
        58,
        62,
        66,
        70,
        74,
        78,
        82,
        86,
    )

    override val status = BleProtocolStatus(
        id = id,
        displayName = displayName,
        controllable = true,
        intensityMax = intensityMax,
        supportsMode = channelCount > 1,
        modeMax = channelCount,
        controlStyle = if (channelCount > 1) {
            ToyControlStyle.CombinedPatternAndIntensity
        } else {
            ToyControlStyle.IntensityOnly
        },
        modeLabel = "部位",
        modeNames = resolvedChannelNames ?: (1..channelCount).map { "部位 $it" },
        automatic = true,
    )

    override fun matches(fingerprint: BleGattFingerprint): Boolean {
        return fingerprint.serviceUuids.contains(motorServiceUuid) &&
                fingerprint.characteristicUuids.contains(writeUuid) &&
                fingerprint.sistalkProtocolVersion <= 1
    }

    override fun initialize(fingerprint: BleGattFingerprint): List<BleProtocolOperation> {
        currentPwm.fill(0)
        // 还原官方 V1 连接握手：订阅设备实际存在的心跳/电量通知通道，再开始电机写入。
        return buildList {
            if (fingerprint.characteristicUuids.contains(heartbeatUuid)) {
                add(BleProtocolOperation.SubscribeNotify(heartbeatUuid))
            }
            if (fingerprint.characteristicUuids.contains(batteryNotifyUuid)) {
                add(BleProtocolOperation.SubscribeNotify(batteryNotifyUuid))
            }
        }
    }

    override fun commandsFor(action: ToyControlAction): List<BleProtocolOperation> =
        when (action) {
            is ToyControlAction.Intensity -> listOf(updateAllChannels(action.value))
            is ToyControlAction.Combined -> listOf(updateChannel(action.mode, action.intensity))
            is ToyControlAction.DualMotor -> listOf(updateAllChannels(action.strongestIntensity(status.intensityMax)))
            is ToyControlAction.Pattern -> listOf(updateChannel(action.mode, status.intensityMax.coerceAtLeast(1)))
            ToyControlAction.Stop -> stop()?.let(::listOf).orEmpty()
        }

    private fun updateAllChannels(intensity: Int): BleProtocolOperation.Write {
        currentPwm.fill(toLevel(intensity))
        return motorWrite(currentPwm)
    }

    private fun updateChannel(channel: Int, intensity: Int): BleProtocolOperation.Write {
        val index = channel.coerceIn(1, channelCount) - 1
        currentPwm[index] = toLevel(intensity)
        return motorWrite(currentPwm)
    }

    private fun toLevel(intensity: Int): Int =
        if (useLegacyLevelMap) {
            levelMap[intensity.coerceIn(0, status.intensityMax)]
        } else {
            intensity.coerceIn(0, status.intensityMax)
        }

    private fun stop(): BleProtocolOperation.Write? {
        if (currentPwm.all { it == 0 }) return null
        currentPwm.fill(0)
        return motorWrite(currentPwm)
    }

    private fun motorWrite(levels: IntArray): BleProtocolOperation.Write =
        BleProtocolOperation.Write(
            characteristicUuid = writeUuid,
            bytes = levels.toByteArrayForSistalkV1(),
            withResponse = false,
        )
}

private object SvakomQhSx045Protocol : BleDeviceProtocol {
    private val serviceUuid = uuid("0000ffe0-0000-1000-8000-00805f9b34fb")
    private val writeUuid = uuid("0000ffe1-0000-1000-8000-00805f9b34fb")

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

private object SvakomSl278hProtocol : BleDeviceProtocol {
    private val serviceUuid = uuid("0000ffe0-0000-1000-8000-00805f9b34fb")
    private val writeUuid = uuid("0000ffe1-0000-1000-8000-00805f9b34fb")
    private val notifyUuid = uuid("0000ffe2-0000-1000-8000-00805f9b34fb")

    override val status = SL278H_VIBRATION_STICK.status

    override fun matches(fingerprint: BleGattFingerprint): Boolean {
        val name = fingerprint.name.normalizedDeviceName()
        val hasSvakomGatt = fingerprint.serviceUuids.contains(serviceUuid) &&
                fingerprint.characteristicUuids.contains(writeUuid) &&
                fingerprint.characteristicUuids.contains(notifyUuid)
        val productCode = fingerprint.svakomProductCode()
        val isSl278h = productCode == SL278H_V_CODE ||
                productCode == SL278H_F_CODE ||
                productCode == SL278K_V_CODE ||
                productCode == SL278K_S_CODE ||
                name.contains("sl278h") ||
                name.contains("sl278k")
        return isSl278h && hasSvakomGatt && fingerprint.hasSvakomManufacturerData()
    }

    override fun createInstance(fingerprint: BleGattFingerprint): BleDeviceProtocol =
        SvakomSl278hSession(fingerprint.svakomProductCode().sl278hProfile())

    override fun commandsFor(action: ToyControlAction): List<BleProtocolOperation> = emptyList()

    private const val SL278H_V_CODE = 100
    private const val SL278H_F_CODE = 101
    private const val SL278K_V_CODE = 0x80
    private const val SL278K_S_CODE = 0x81
}

private class SvakomSl278hSession(
    private val profile: SvakomSl278hProfile,
) : BleDeviceProtocol {
    private val writeUuid = uuid("0000ffe1-0000-1000-8000-00805f9b34fb")
    private val notifyUuid = uuid("0000ffe2-0000-1000-8000-00805f9b34fb")
    private var currentMode = 1
    private var currentPrimaryIntensity = 0
    private var currentSecondaryIntensity = 0

    override val status: BleProtocolStatus = profile.status

    override fun matches(fingerprint: BleGattFingerprint): Boolean = false

    override fun initialize(fingerprint: BleGattFingerprint): List<BleProtocolOperation> {
        currentMode = 1
        currentPrimaryIntensity = 0
        currentSecondaryIntensity = 0
        return listOf(BleProtocolOperation.SubscribeNotify(notifyUuid))
    }

    override fun keepaliveIntervalMs(): Long = 1_500L

    override fun commandsFor(action: ToyControlAction): List<BleProtocolOperation> =
        when (action) {
            is ToyControlAction.Pattern -> run(
                mode = action.mode,
                primaryIntensity = currentPrimaryIntensity,
                secondaryIntensity = currentSecondaryIntensity,
            )
            is ToyControlAction.Intensity -> run(
                mode = currentMode,
                primaryIntensity = action.value,
                secondaryIntensity = if (profile.dualChannel) action.value else 0,
            )
            is ToyControlAction.Combined -> run(
                mode = action.mode,
                primaryIntensity = action.intensity,
                secondaryIntensity = if (profile.dualChannel) action.intensity else 0,
            )
            is ToyControlAction.DualMotor -> run(
                mode = action.mode,
                primaryIntensity = action.internalIntensity,
                secondaryIntensity = action.externalIntensity,
            )
            ToyControlAction.Stop -> stopAll()
        }

    private fun run(mode: Int, primaryIntensity: Int, secondaryIntensity: Int): List<BleProtocolOperation> {
        currentMode = mode.coerceIn(1, status.modeMax.coerceAtLeast(1))
        currentPrimaryIntensity = primaryIntensity.coerceIn(0, status.intensityMax)
        currentSecondaryIntensity = secondaryIntensity.coerceIn(0, status.intensityMax)
        return profile.commands(
            write = ::write,
            mode = currentMode,
            primaryIntensity = currentPrimaryIntensity,
            secondaryIntensity = currentSecondaryIntensity,
        )
    }

    private fun stopAll(): List<BleProtocolOperation> {
        currentPrimaryIntensity = 0
        currentSecondaryIntensity = 0
        return profile.stopCommands(::write) + write(bytes(0x55, 0x04, 0x00, 0x00, 0x00, 0x00, 0xaa))
    }

    private fun write(bytes: ByteArray): BleProtocolOperation.Write =
        BleProtocolOperation.Write(
            characteristicUuid = writeUuid,
            bytes = bytes,
            withResponse = false,
        )
}

private sealed class SvakomSl278hProfile(
    val status: BleProtocolStatus,
    val dualChannel: Boolean,
) {
    abstract fun commands(
        write: (ByteArray) -> BleProtocolOperation.Write,
        mode: Int,
        primaryIntensity: Int,
        secondaryIntensity: Int,
    ): List<BleProtocolOperation>

    abstract fun stopCommands(write: (ByteArray) -> BleProtocolOperation.Write): List<BleProtocolOperation>

    protected fun modeCommand(command: Int, mode: Int, level: Int): ByteArray =
        bytes(
            0x55,
            command,
            0x00,
            0x00,
            mode.coerceIn(0, 0xff),
            level.coerceIn(0, 0xff),
            0x00,
        )

    protected fun activeMode(mode: Int, intensity: Int): Int =
        if (intensity > 0) mode.coerceAtLeast(1) else 0

    protected fun activeLevel(intensity: Int): Int =
        if (intensity > 0) intensity else 0
}

private object SL278H_VIBRATION_STICK : SvakomSl278hProfile(
    status = BleProtocolStatus(
        id = "svakom_sl278h_v",
        displayName = "SVAKOM SL278H / 分欣",
        controllable = true,
        intensityMax = 10,
        supportsMode = true,
        modeMax = 7,
        controlStyle = ToyControlStyle.PatternAndDualIntensity,
        modeLabel = "伸缩模式",
        intensityLabel = "强度",
        channelNames = listOf("伸缩", "震动"),
        features = listOf(
            BleProtocolFeature(type = "oscillate", min = 0, max = 10, index = 0, label = "伸缩"),
            BleProtocolFeature(type = "vibrate", min = 0, max = 10, index = 1, label = "震动"),
        ),
        automatic = true,
    ),
    dualChannel = true,
) {
    override fun commands(
        write: (ByteArray) -> BleProtocolOperation.Write,
        mode: Int,
        primaryIntensity: Int,
        secondaryIntensity: Int,
    ): List<BleProtocolOperation> =
        listOf(
            write(modeCommand(0x08, activeMode(mode, primaryIntensity), if (primaryIntensity > 0) 0xff else 0x00)),
            write(modeCommand(0x03, activeMode(1, secondaryIntensity), activeLevel(secondaryIntensity))),
        )

    override fun stopCommands(write: (ByteArray) -> BleProtocolOperation.Write): List<BleProtocolOperation> =
        listOf(
            write(modeCommand(0x08, 0, 0)),
            write(modeCommand(0x03, 0, 0)),
            write(modeCommand(0x05, 0, 0)),
        )
}

private object SL278H_SUCTION : SvakomSl278hProfile(
    status = BleProtocolStatus(
        id = "svakom_sl278h_f",
        displayName = "SVAKOM SL278H / 分欣",
        controllable = true,
        intensityMax = 10,
        supportsMode = true,
        modeMax = 7,
        controlStyle = ToyControlStyle.CombinedPatternAndIntensity,
        modeLabel = "吮吸模式",
        intensityLabel = "吮吸强度",
        features = listOf(
            BleProtocolFeature(type = "constrict", min = 0, max = 10, index = 0, label = "吮吸"),
        ),
        automatic = true,
    ),
    dualChannel = false,
) {
    override fun commands(
        write: (ByteArray) -> BleProtocolOperation.Write,
        mode: Int,
        primaryIntensity: Int,
        secondaryIntensity: Int,
    ): List<BleProtocolOperation> =
        listOf(write(modeCommand(0x07, activeMode(mode, primaryIntensity), activeLevel(primaryIntensity))))

    override fun stopCommands(write: (ByteArray) -> BleProtocolOperation.Write): List<BleProtocolOperation> =
        listOf(
            write(modeCommand(0x07, 0, 0)),
            write(modeCommand(0x05, 0, 0)),
        )
}

private fun Int?.sl278hProfile(): SvakomSl278hProfile =
    when (this) {
        101 -> SL278H_SUCTION
        0x81 -> SL278H_SUCTION
        else -> SL278H_VIBRATION_STICK
    }

private object SvakomSt419Protocol : BleDeviceProtocol {
    private val serviceUuid = uuid("0000ffe0-0000-1000-8000-00805f9b34fb")
    private val writeUuid = uuid("0000ffe1-0000-1000-8000-00805f9b34fb")
    private val notifyUuid = uuid("0000ffe2-0000-1000-8000-00805f9b34fb")

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

private object MizzzeeXhtkjProtocol : BleDeviceProtocol {
    private val serviceUuid = uuid("0000ff10-0000-1000-8000-00805f9b34fb")
    private val writeUuid = uuid("0000ff12-0000-1000-8000-00805f9b34fb")

    override val status = BleProtocolStatus(
        id = "mizzzee_xhtkj",
        displayName = "Mizzzee XHTKJ",
        controllable = true,
        intensityMax = 100,
        supportsMode = false,
        controlStyle = ToyControlStyle.IntensityOnly,
        automatic = true,
        repeatIntervalMs = 200,
    )

    override fun matches(fingerprint: BleGattFingerprint): Boolean {
        return fingerprint.serviceUuids.contains(serviceUuid) &&
                fingerprint.characteristicUuids.contains(writeUuid)
    }

    override fun commandsFor(action: ToyControlAction): List<BleProtocolOperation> =
        when (action) {
            is ToyControlAction.Intensity -> listOf(command(action.value.coerceIn(0, status.intensityMax)))
            is ToyControlAction.Combined -> listOf(command(action.intensity.coerceIn(0, status.intensityMax)))
            is ToyControlAction.DualMotor -> listOf(command(action.strongestIntensity(status.intensityMax)))
            is ToyControlAction.Pattern -> emptyList()
            ToyControlAction.Stop -> listOf(command(0))
        }

    private fun command(intensity: Int): BleProtocolOperation.Write {
        val encoded = encodeStrength(intensity)
        val low = (encoded and 0xff).toByte()
        val high = ((encoded ushr 8) and 0xff).toByte()
        return BleProtocolOperation.Write(
            characteristicUuid = writeUuid,
            bytes = byteArrayOf(
                0x03,
                0x12,
                0xf3.toByte(),
                0x00,
                0xfc.toByte(),
                0x00,
                0xfe.toByte(),
                0x40,
                0x01,
                low,
                high,
                0x00,
                0xfc.toByte(),
                0x00,
                0xfe.toByte(),
                0x40,
                0x01,
                low,
                high,
                0x00,
            ),
            withResponse = false,
        )
    }

    private fun encodeStrength(intensity: Int): Int {
        if (intensity <= 0) return 0x003c
        val scaled = (intensity / 100.0 * 0.7) + 0.3
        return (((scaled * 1023).toInt() shl 6) or 0x3c).coerceIn(0, 0xffff)
    }
}

private object XiuxiudaOfficialProtocol : BleDeviceProtocol {
    private val serviceUuid = uuid("53300001-0023-4bd4-bbd5-a6920e4c5653")
    private val notifyUuid = uuid("53300002-0023-4bd4-bbd5-a6920e4c5653")
    private val writeUuid = uuid("53300003-0023-4bd4-bbd5-a6920e4c5653")
    private val highPowerNames = setOf(
        "XXD-Lush12C",
        "XXD-Lush12D",
        "XXD-Lush12E",
        "XXD-Lush12L",
        "XXD-Lush12R",
        "XXD-Lush12S",
        "XXD-Lush12T",
        "XXD-Lush12V",
        "XXD-Lush141",
        "XXD-Lush142",
        "XXD-Lush143",
        "XXD-Lush144",
        "XXD-Lush145",
        "XXD-Lush146",
        "XXD-Lush149",
        "XXD-Lush14E",
    ).map { it.normalizedDeviceName() }.toSet()

    override val status = BleProtocolStatus(
        id = "xiuxiuda_official",
        displayName = "羞羞哒",
        controllable = true,
        intensityMax = 19,
        supportsMode = false,
        controlStyle = ToyControlStyle.IntensityOnly,
        intensityLabel = "强度",
        automatic = true,
    )

    override fun matches(fingerprint: BleGattFingerprint): Boolean {
        val name = fingerprint.name.normalizedDeviceName()
        return name.startsWith("xxdlush") &&
                fingerprint.address.xiuxiudaAddressPrefix() != null &&
                fingerprint.serviceUuids.contains(serviceUuid) &&
                fingerprint.characteristicUuids.contains(writeUuid)
    }

    override fun initialize(fingerprint: BleGattFingerprint): List<BleProtocolOperation> =
        buildList {
            if (fingerprint.characteristicUuids.contains(notifyUuid)) {
                add(BleProtocolOperation.SubscribeNotify(notifyUuid))
                add(write(fingerprint, command(0x65, 0x3D, 0x33, 0x01)))
                add(BleProtocolOperation.Sleep(300))
            }
            add(write(fingerprint, controlCommand(fingerprint.name, 0)))
        }

    override fun commandsFor(action: ToyControlAction): List<BleProtocolOperation> = emptyList()

    override fun createInstance(fingerprint: BleGattFingerprint): BleDeviceProtocol =
        Session(fingerprint)

    private class Session(
        private val fingerprint: BleGattFingerprint,
    ) : BleDeviceProtocol {
        override val status: BleProtocolStatus = XiuxiudaOfficialProtocol.status

        override fun matches(fingerprint: BleGattFingerprint): Boolean =
            XiuxiudaOfficialProtocol.matches(fingerprint)

        override fun initialize(fingerprint: BleGattFingerprint): List<BleProtocolOperation> =
            XiuxiudaOfficialProtocol.initialize(fingerprint)

        override fun commandsFor(action: ToyControlAction): List<BleProtocolOperation> =
            when (action) {
                is ToyControlAction.Intensity -> listOf(write(fingerprint, controlCommand(fingerprint.name, action.value)))
                is ToyControlAction.Combined -> listOf(write(fingerprint, controlCommand(fingerprint.name, action.intensity)))
                is ToyControlAction.DualMotor -> listOf(write(fingerprint, controlCommand(fingerprint.name, action.strongestIntensity(status.intensityMax))))
                is ToyControlAction.Pattern -> listOf(write(fingerprint, controlCommand(fingerprint.name, status.intensityMax)))
                ToyControlAction.Stop -> listOf(write(fingerprint, controlCommand(fingerprint.name, 0)))
            }
    }

    private fun controlCommand(name: String, intensity: Int): IntArray {
        val normalizedName = name.normalizedDeviceName()
        val gear = intensity.coerceIn(0, status.intensityMax)
        return if (normalizedName in highPowerNames) {
            command(0x95, 0x3C, 0x30, gear)
        } else {
            command(0x65, 0x3A, 0x30, gear)
        }
    }

    private fun write(fingerprint: BleGattFingerprint, command: IntArray): BleProtocolOperation.Write =
        BleProtocolOperation.Write(
            characteristicUuid = writeUuid,
            bytes = fingerprint.address.xiuxiudaPacket(command),
            withResponse = false,
        )

    private fun command(logo: Int, category1: Int, category2: Int, gear: Int): IntArray =
        intArrayOf(logo, category1, category2, gear)
}

private object SenseeCcpa10S2Protocol : BleDeviceProtocol {
    private val serviceUuid = uuid("0000fff0-0000-1000-8000-00805f9b34fb")
    private val writeUuid = uuid("0000fff5-0000-1000-8000-00805f9b34fb")
    private val suctionCommands = arrayOf(
        bytes(0x55, 0xaa, 0xf0, 0x01, 0x00, 0x11, 0x66, 0xf2, 0xf1, 0x00, 0x00),
        bytes(0x55, 0xaa, 0xf0, 0x01, 0x00, 0x11, 0x66, 0xf2, 0xf2, 0x00, 0x00),
        bytes(0x55, 0xaa, 0xf0, 0x01, 0x00, 0x11, 0x66, 0xf2, 0xf3, 0x00, 0x00),
    )
    private val vibrationCommands = arrayOf(
        bytes(0x55, 0xaa, 0xf0, 0x01, 0x0d, 0x12, 0x66, 0xf9, 0xf1),
        bytes(0x55, 0xaa, 0xf0, 0x01, 0x0e, 0x12, 0x66, 0xf9, 0xf2),
        bytes(0x55, 0xaa, 0xf0, 0x01, 0x0f, 0x12, 0x66, 0xf9, 0xf3),
        bytes(0x55, 0xaa, 0xf0, 0x01, 0x11, 0x12, 0x66, 0xf9, 0xfa),
    )
    private val suctionOff = bytes(0x55, 0xaa, 0xf0, 0x01, 0x00, 0x11, 0x66, 0xf2, 0xf0, 0x00, 0x00)
    private val vibrationOff = bytes(0x55, 0xaa, 0xf0, 0x01, 0x10, 0x12, 0x66, 0xf9, 0xf0)

    override val status = BleProtocolStatus(
        id = "sensee_ccpa10s2",
        displayName = "Sensee CCPA10S2",
        controllable = true,
        intensityMax = 3,
        supportsMode = true,
        modeMax = 2,
        controlStyle = ToyControlStyle.CombinedPatternAndIntensity,
        modeLabel = "功能",
        modeNames = listOf("吸力", "震动"),
        automatic = true,
    )

    override fun matches(fingerprint: BleGattFingerprint): Boolean {
        val name = fingerprint.name.normalizedDeviceName()
        val knownName = name.contains("ccpa") || name.contains("sensee")
        val hasSenseeGatt = fingerprint.serviceUuids.contains(serviceUuid) &&
                fingerprint.characteristicUuids.contains(writeUuid)
        return knownName && hasSenseeGatt
    }

    override fun commandsFor(action: ToyControlAction): List<BleProtocolOperation> =
        when (action) {
            is ToyControlAction.Combined -> listOf(command(action.mode, action.intensity))
            is ToyControlAction.Intensity -> listOf(command(2, action.value))
            is ToyControlAction.DualMotor -> listOf(command(action.mode, action.strongestIntensity(status.intensityMax)))
            is ToyControlAction.Pattern -> listOf(command(action.mode, status.intensityMax.coerceAtLeast(1)))
            ToyControlAction.Stop -> listOf(write(suctionOff), write(vibrationOff))
        }

    private fun command(mode: Int, intensity: Int): BleProtocolOperation.Write {
        val normalizedMode = mode.coerceIn(1, status.modeMax)
        val level = intensity.coerceIn(0, status.intensityMax)
        val payload = when {
            level == 0 && normalizedMode == 1 -> suctionOff
            level == 0 -> vibrationOff
            normalizedMode == 1 -> suctionCommands[level - 1]
            else -> vibrationCommands[level - 1]
        }
        return write(payload)
    }

    private fun write(payload: ByteArray) = BleProtocolOperation.Write(
        characteristicUuid = writeUuid,
        bytes = payload,
        withResponse = false,
    )
}

private object CachitoMbProGattProtocol : BleDeviceProtocol {
    private val serviceUuid = uuid("0000ffe0-0000-1000-8000-00805f9b34fb")
    private val writeUuid = uuid("0000ffe1-0000-1000-8000-00805f9b34fb")
    private val notifyUuid = uuid("0000ffe2-0000-1000-8000-00805f9b34fb")
    private val aliases = listOf("MB08", "MB Pro", "MBPro", "漫步者Pro", "漫步Pro")

    override val status = BleProtocolStatus(
        id = "cachito_mb_pro_gatt",
        displayName = "Cachito MB Pro",
        controllable = true,
        intensityMax = 100,
        supportsMode = false,
        controlStyle = ToyControlStyle.IntensityOnly,
        intensityLabel = "吮吸强度",
        automatic = true,
    )

    override fun matches(fingerprint: BleGattFingerprint): Boolean {
        val name = fingerprint.name.normalizedDeviceName()
        val hasKnownName = aliases.any { alias -> name.contains(alias.normalizedDeviceName()) }
        return hasKnownName &&
            fingerprint.serviceUuids.contains(serviceUuid) &&
            fingerprint.characteristicUuids.contains(writeUuid)
    }

    override fun initialize(fingerprint: BleGattFingerprint): List<BleProtocolOperation> =
        if (fingerprint.characteristicUuids.contains(notifyUuid)) {
            listOf(BleProtocolOperation.SubscribeNotify(notifyUuid))
        } else {
            emptyList()
        }

    override fun commandsFor(action: ToyControlAction): List<BleProtocolOperation> =
        when (action) {
            is ToyControlAction.Pattern ->
                listOf(write(command(action.mode.coerceIn(0, status.intensityMax))))
            is ToyControlAction.Intensity ->
                listOf(write(command(action.value)))
            is ToyControlAction.Combined ->
                listOf(write(command(action.intensity)))
            is ToyControlAction.DualMotor ->
                listOf(write(command(action.strongestIntensity(status.intensityMax))))
            ToyControlAction.Stop ->
                listOf(write(stopCommand()))
        }

    private fun command(intensity: Int): ByteArray {
        val scaled = (intensity.coerceIn(0, status.intensityMax) * 0.75 + 25).toInt()
        return commandPayload(scaled)
    }

    private fun stopCommand(): ByteArray =
        commandPayload(0)

    private fun commandPayload(level: Int): ByteArray {
        val commandId = Random.nextInt(0x64, 0x100)
        val bytes = bytes(
            0x71,
            0x00,
            0x06,
            commandId,
            0x00,
            0x00,
            0x04,
            0x31,
            0x03,
            0x02,
            level.coerceIn(0, 0xff),
            0x00,
            0x00,
            0x00,
            0x00,
        )
        return bytes + ((bytes.sumOf { it.toInt() and 0xff } and 0xff).toByte())
    }

    private fun write(bytes: ByteArray): BleProtocolOperation.Write =
        BleProtocolOperation.Write(
            characteristicUuid = writeUuid,
            bytes = bytes,
            withResponse = false,
        )
}

private data class KissToyTutuIIRoute(
    val id: String,
    val displayName: String,
    val serviceUuid: UUID,
    val writeUuid: UUID,
    val notifyUuid: UUID,
    val requireRouteForMatch: Boolean = true,
)

private val KissToyTutuIIProtocol = KissToyTutuIIProtocolVariant(
    KissToyTutuIIRoute(
        id = "kisstoy_tutu2",
        displayName = "KissToy 突突二代",
        serviceUuid = uuid("0000ae3a-0000-1000-8000-00805f9b34fb"),
        writeUuid = uuid("0000ae3b-0000-1000-8000-00805f9b34fb"),
        notifyUuid = uuid("0000ae3c-0000-1000-8000-00805f9b34fb"),
    )
)

private data class KissToyOfficialV2Motor(
    val type: Int,
    val label: String,
    val command: ByteArray,
)

private data class KissToyOfficialV2Profile(
    val code: String,
    val displayName: String,
    val motors: List<KissToyOfficialV2Motor>,
    val channelNames: List<String> = motors.take(2).map { it.label },
    val requiresAuth: Boolean = false,
)

private data class KissToyOfficialV2Route(
    val serviceUuid: UUID?,
    val writeUuid: UUID,
    val notifyUuid: UUID?,
    val subscribeNotifyOnInit: Boolean = false,
)

private object KissToyOfficialV2Protocol : BleDeviceProtocol {
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
        return !profile.requiresAuth && fingerprint.kissToyOfficialV2Route() != null
    }

    override fun createInstance(fingerprint: BleGattFingerprint): BleDeviceProtocol {
        val profile = fingerprint.kissToyOfficialV2Profile() ?: return this
        if (profile.requiresAuth) return this
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
            intensityMax = 100,
            supportsMode = false,
            controlStyle = if (profile.motors.size >= 2) ToyControlStyle.DualIntensityOnly else ToyControlStyle.IntensityOnly,
            intensityLabel = "强度",
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
                trailingRunSettle = false,
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

private class KissToyTutuIIProtocolVariant(
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
        return profile.requiresAuth && fingerprint.hasRoute(route)
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
            fingerprint.kissToyOfficialV2Profile()?.requiresAuth == true && fingerprint.hasRoute(route)

        override fun initialize(fingerprint: BleGattFingerprint): List<BleProtocolOperation> =
            listOf(
                BleProtocolOperation.SubscribeNotify(route.notifyUuid),
                BleProtocolOperation.WaitForProtocolReady(KISS_TOY_TUTU_AUTH_TIMEOUT_MS),
            )

        override fun onNotify(characteristicUuid: UUID, bytes: ByteArray): List<BleProtocolOperation> {
            if (characteristicUuid != route.notifyUuid || authenticated) return emptyList()
            if (bytes.size < KISS_TOY_TUTU_AUTH_CHALLENGE_SIZE || bytes[0] != KISS_TOY_TUTU_PACKET_PREFIX) return emptyList()
            authenticated = true
            return listOf(
                write(authPacket(bytes)),
                BleProtocolOperation.Sleep(KISS_TOY_TUTU_AUTH_SETTLE_MS),
            )
        }

        override fun isProtocolReady(): Boolean = authenticated

        override fun keepaliveIntervalMs(): Long = KISS_TOY_TUTU_REPEAT_MS.toLong()

        override fun commandsFor(action: ToyControlAction): List<BleProtocolOperation> =
            KISS_TOY_TUTU_OFFICIAL_V2_PROFILE.commandsForOfficialV2(
                action = action,
                intensityMax = status.intensityMax,
                trailingRunSettle = true,
                write = ::write,
            )

        private fun authPacket(challenge: ByteArray): ByteArray =
            byteArrayOf(KISS_TOY_TUTU_PACKET_PREFIX) +
                challenge.copyOfRange(1, KISS_TOY_TUTU_AUTH_CHALLENGE_SIZE).xorKissToyTutuFixedKey()

        private fun write(bytes: ByteArray): BleProtocolOperation.Write =
            BleProtocolOperation.Write(
                characteristicUuid = route.writeUuid,
                bytes = bytes,
                withResponse = false,
            )
    }

}

private fun BleGattFingerprint.hasRoute(route: KissToyTutuIIRoute): Boolean =
    serviceUuids.contains(route.serviceUuid) &&
        characteristicUuids.contains(route.writeUuid) &&
        characteristicUuids.contains(route.notifyUuid)

private fun BleGattFingerprint.kissToyOfficialV2Route(): KissToyOfficialV2Route? {
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

private fun BleGattFingerprint.hasKissToyOfficialV2Route(route: KissToyOfficialV2Route): Boolean {
    val serviceUuid = route.serviceUuid
    val writeUuid = route.writeUuid
    val notifyUuid = route.notifyUuid
    if (serviceUuid != null && !serviceUuids.contains(serviceUuid)) return false
    if (!characteristicUuids.contains(writeUuid)) return false
    if (notifyUuid != null && !characteristicUuids.contains(notifyUuid)) return false
    if (isKissToyHistoricalGattRoute(serviceUuid, writeUuid)) return false
    return true
}

private fun isKissToyHistoricalGattRoute(serviceUuid: UUID?, writeUuid: UUID): Boolean =
    serviceUuid == KISS_TOY_HISTORICAL_GATT_SERVICE_UUID ||
        writeUuid == KISS_TOY_HISTORICAL_GATT_WRITE_UUID

private fun isKissToyOfficialV2Ae3aRoute(serviceUuid: UUID?, writeUuid: UUID, notifyUuid: UUID?): Boolean =
    serviceUuid == KISS_TOY_OFFICIAL_V2_AE3A_SERVICE_UUID &&
        writeUuid == KISS_TOY_OFFICIAL_V2_AE3A_WRITE_UUID &&
        notifyUuid == KISS_TOY_OFFICIAL_V2_AE3A_NOTIFY_UUID

private fun BleGattFingerprint.kissToyOfficialV2Profile(): KissToyOfficialV2Profile? {
    val normalizedName = name.normalizedDeviceName()
    return KISS_TOY_OFFICIAL_V2_PROFILES[normalizedName]
}

private fun KissToyOfficialV2Profile.commandsForOfficialV2(
    action: ToyControlAction,
    intensityMax: Int,
    trailingRunSettle: Boolean,
    write: (ByteArray) -> BleProtocolOperation.Write,
): List<BleProtocolOperation> =
    when (action) {
        is ToyControlAction.DualMotor -> runOfficialV2(dualIntensities(action.internalIntensity, action.externalIntensity), intensityMax, trailingRunSettle, write)
        is ToyControlAction.Intensity -> runOfficialV2(sameIntensities(action.value), intensityMax, trailingRunSettle, write)
        is ToyControlAction.Combined -> runOfficialV2(sameIntensities(action.intensity), intensityMax, trailingRunSettle, write)
        is ToyControlAction.Pattern -> runOfficialV2(sameIntensities(50), intensityMax, trailingRunSettle, write)
        ToyControlAction.Stop -> stopOfficialV2(write)
    }

private fun KissToyOfficialV2Profile.runOfficialV2(
    intensities: List<Int>,
    intensityMax: Int,
    trailingRunSettle: Boolean,
    write: (ByteArray) -> BleProtocolOperation.Write,
): List<BleProtocolOperation> {
    if (intensities.all { it <= 0 }) return stopOfficialV2(write)
    val operations = motors.zip(intensities).flatMapIndexed { index, (motor, intensity) ->
        val chunk = mutableListOf<BleProtocolOperation>()
        chunk += write(kissToyOfficialV2MotorPacket(motor.command, intensity.scaleKissToyOfficialV2Percent(intensityMax)))
        if (index != motors.lastIndex || trailingRunSettle) {
            chunk += BleProtocolOperation.Sleep(KISS_TOY_OFFICIAL_V2_WRITE_SETTLE_MS)
        }
        chunk
    }
    return operations
}

private fun KissToyOfficialV2Profile.stopOfficialV2(
    write: (ByteArray) -> BleProtocolOperation.Write,
): List<BleProtocolOperation> =
    motors.flatMapIndexed { index, motor ->
        val operations = mutableListOf<BleProtocolOperation>()
        operations += write(kissToyOfficialV2MotorPacket(motor.command, 0))
        if (index != motors.lastIndex) {
            operations += BleProtocolOperation.Sleep(KISS_TOY_OFFICIAL_V2_STOP_REPEAT_DELAY_MS)
        }
        operations
    } + listOf(BleProtocolOperation.Sleep(KISS_TOY_OFFICIAL_V2_STOP_REPEAT_DELAY_MS)) +
        motors.map { motor -> write(kissToyOfficialV2MotorPacket(motor.command, 0)) }

private fun KissToyOfficialV2Profile.sameIntensities(value: Int): List<Int> =
    List(motors.size) { value }

private fun KissToyOfficialV2Profile.dualIntensities(first: Int, second: Int): List<Int> =
    motors.mapIndexed { index, _ ->
        when (index) {
            0 -> first
            1 -> second
            else -> first.coerceAtLeast(second)
        }
    }

private fun Int.scaleKissToyOfficialV2Percent(intensityMax: Int): Int =
    (coerceIn(0, intensityMax) * 100 / intensityMax.coerceAtLeast(1)).coerceIn(0, 100)

private fun ByteArray.xorKissToyTutuFixedKey(): ByteArray =
    ByteArray(size.coerceAtMost(KISS_TOY_TUTU_FIXED_KEY.size)) { index ->
        (this[index].toInt() xor KISS_TOY_TUTU_FIXED_KEY[index].toInt()).toByte()
    }

private fun kissToyOfficialV2Command(command: Int, subType: Int = 0): ByteArray =
    bytes(
        0xb4,
        0x00,
        subType,
        0x02,
        command,
        0x00,
        0x00,
        0x00,
        0x00,
        0x00,
    )

private fun kissToyOfficialV2MotorPacket(command: ByteArray, intensity: Int): ByteArray {
    val payloadCommand = command.copyOf(KISS_TOY_OFFICIAL_V2_COMMAND_SIZE)
    payloadCommand[5] = intensity.coerceIn(0, 100).toByte()
    return kissToyOfficialV2SendBytes(payloadCommand)
}

private fun kissToyOfficialV2SendBytes(command: ByteArray): ByteArray {
    val payload = ByteArray(KISS_TOY_OFFICIAL_V2_FRAME_SIZE)
    payload[0] = KISS_TOY_OFFICIAL_V2_FRAME_PREFIX
    command.copyInto(payload, destinationOffset = 1, endIndex = command.size.coerceAtMost(KISS_TOY_OFFICIAL_V2_COMMAND_SIZE))
    payload[KISS_TOY_OFFICIAL_V2_FRAME_SIZE - 1] =
        payload.take(KISS_TOY_OFFICIAL_V2_FRAME_SIZE - 1).sumOf { it.toInt() and 0xff }.toByte()
    return kissToyOfficialV2Encrypt(payload)
}

private fun kissToyOfficialV2Encrypt(bytes: ByteArray): ByteArray {
    require(bytes.size == KISS_TOY_OFFICIAL_V2_FRAME_SIZE)
    val encrypted = ByteArray(KISS_TOY_OFFICIAL_V2_FRAME_SIZE)
    encrypted[0] = bytes[0]
    val seed = bytes[0].toInt() and 0xff
    for (index in 1 until KISS_TOY_OFFICIAL_V2_FRAME_SIZE) {
        val key = KISS_TOY_OFFICIAL_V2_KEY_TAB[encrypted[index - 1].toInt() and 0x03][index]
        encrypted[index] = (key + (key xor seed xor (bytes[index].toInt() and 0xff))).toByte()
    }
    return encrypted
}

private val KISS_TOY_TUTU_FIXED_KEY = bytes(0xea, 0x30, 0xbb, 0xdb, 0xad, 0xb6, 0xc6, 0x2f, 0xcf, 0xe9, 0xe9, 0x96)
private const val KISS_TOY_OFFICIAL_V2_PROTOCOL_ID = "kisstoy_official_v2"
private val KISS_TOY_HISTORICAL_GATT_SERVICE_UUID = uuid("00001000-0000-1000-8000-00805f9b34fb")
private val KISS_TOY_HISTORICAL_GATT_WRITE_UUID = uuid("00001001-0000-1000-8000-00805f9b34fb")
private val KISS_TOY_OFFICIAL_V2_AE3A_SERVICE_UUID = uuid("0000ae3a-0000-1000-8000-00805f9b34fb")
private val KISS_TOY_OFFICIAL_V2_AE3A_WRITE_UUID = uuid("0000ae3b-0000-1000-8000-00805f9b34fb")
private val KISS_TOY_OFFICIAL_V2_AE3A_NOTIFY_UUID = uuid("0000ae3c-0000-1000-8000-00805f9b34fb")
private val KISS_TOY_OFFICIAL_V2_DISCOVERY_ROUTES = listOf(
    KissToyOfficialV2Route(
        serviceUuid = KISS_TOY_OFFICIAL_V2_AE3A_SERVICE_UUID,
        writeUuid = KISS_TOY_OFFICIAL_V2_AE3A_WRITE_UUID,
        notifyUuid = KISS_TOY_OFFICIAL_V2_AE3A_NOTIFY_UUID,
        subscribeNotifyOnInit = true,
    ),
    KissToyOfficialV2Route(
        serviceUuid = uuid("0000dddd-0000-1000-8000-00805f9b34fb"),
        writeUuid = uuid("0000ddd1-0000-1000-8000-00805f9b34fb"),
        notifyUuid = uuid("0000ddd2-0000-1000-8000-00805f9b34fb"),
    ),
    KissToyOfficialV2Route(
        serviceUuid = uuid("0000ffe0-0000-1000-8000-00805f9b34fb"),
        writeUuid = uuid("0000ffe1-0000-1000-8000-00805f9b34fb"),
        notifyUuid = uuid("0000ffe2-0000-1000-8000-00805f9b34fb"),
    ),
)
private const val KISS_TOY_TUTU_PACKET_PREFIX: Byte = 0x58
private const val KISS_TOY_TUTU_AUTH_CHALLENGE_SIZE = 13
private const val KISS_TOY_TUTU_AUTH_TIMEOUT_MS = 4_000L
private const val KISS_TOY_TUTU_AUTH_SETTLE_MS = 1_000L
private const val KISS_TOY_TUTU_REPEAT_MS = 500
private const val KISS_TOY_OFFICIAL_V2_WRITE_SETTLE_MS = 220L
private const val KISS_TOY_OFFICIAL_V2_STOP_REPEAT_DELAY_MS = 300L
private const val KISS_TOY_OFFICIAL_V2_COMMAND_SIZE = 10
private const val KISS_TOY_OFFICIAL_V2_FRAME_SIZE = 12
private const val KISS_TOY_OFFICIAL_V2_FRAME_PREFIX: Byte = 0x46
private val KISS_TOY_OFFICIAL_V2_KEY_TAB = arrayOf(
    intArrayOf(0x00, 0x30, 0x30, 0xee, 0x4a, 0x7a, 0x1a, 0x52, 0x4a, 0xa0, 0x88, 0x8c),
    intArrayOf(0x00, 0x8a, 0xdc, 0xd4, 0xde, 0xf0, 0x40, 0xa6, 0x5a, 0x62, 0x5c, 0x6e),
    intArrayOf(0x00, 0xca, 0xf0, 0x40, 0xa8, 0xde, 0xf2, 0xe6, 0x14, 0x1c, 0x3a, 0x46),
    intArrayOf(0x00, 0x8a, 0xac, 0xce, 0xf0, 0x14, 0x64, 0x40, 0xde, 0xc4, 0x1a, 0x14),
)
private val KISS_TOY_OFFICIAL_V2_TYPE_1 = KissToyOfficialV2Motor(1, "震动", kissToyOfficialV2Command(0x62))
private val KISS_TOY_OFFICIAL_V2_TYPE_2 = KissToyOfficialV2Motor(2, "第二震动", kissToyOfficialV2Command(0x64, subType = 0x02))
private val KISS_TOY_OFFICIAL_V2_TYPE_3 = KissToyOfficialV2Motor(3, "吮吸", kissToyOfficialV2Command(0x64))
private val KISS_TOY_OFFICIAL_V2_TYPE_4 = KissToyOfficialV2Motor(4, "伸缩", kissToyOfficialV2Command(0xe2))
private val KISS_TOY_OFFICIAL_V2_TYPE_6 = KissToyOfficialV2Motor(6, "拍打", kissToyOfficialV2Command(0x64))
private val KISS_TOY_TUTU_CHANNEL_NAMES = listOf("底座伸缩", "头部伸缩")
private val KISS_TOY_TUTU_OFFICIAL_V2_PROFILE = KissToyOfficialV2Profile(
    code = "QCTT",
    displayName = "突突二代",
    motors = listOf(KISS_TOY_OFFICIAL_V2_TYPE_1, KISS_TOY_OFFICIAL_V2_TYPE_4),
    channelNames = KISS_TOY_TUTU_CHANNEL_NAMES,
    requiresAuth = true,
)
private val KISS_TOY_OFFICIAL_V2_PROFILES = listOf(
    KissToyOfficialV2Profile("JY11", "太正鲸", listOf(KISS_TOY_OFFICIAL_V2_TYPE_1)),
    KissToyOfficialV2Profile("SYQ1", "糖蛋蛋", listOf(KISS_TOY_OFFICIAL_V2_TYPE_1)),
    KissToyOfficialV2Profile("PJ01", "炮机", listOf(KISS_TOY_OFFICIAL_V2_TYPE_4)),
    KissToyOfficialV2Profile("KX01", "Cathy Pro", listOf(KISS_TOY_OFFICIAL_V2_TYPE_3, KISS_TOY_OFFICIAL_V2_TYPE_4)),
    KissToyOfficialV2Profile("KX02", "Cathy III", listOf(KISS_TOY_OFFICIAL_V2_TYPE_3, KISS_TOY_OFFICIAL_V2_TYPE_4)),
    KissToyOfficialV2Profile("PLY1", "Polly pro", listOf(KISS_TOY_OFFICIAL_V2_TYPE_1, KISS_TOY_OFFICIAL_V2_TYPE_3)),
    KissToyOfficialV2Profile("QCKH", "BOBO", listOf(KISS_TOY_OFFICIAL_V2_TYPE_3)),
    KissToyOfficialV2Profile("PLMX", "Polly max", listOf(KISS_TOY_OFFICIAL_V2_TYPE_1, KISS_TOY_OFFICIAL_V2_TYPE_3)),
    KissToyOfficialV2Profile("QCPJ", "TUTU", listOf(KISS_TOY_OFFICIAL_V2_TYPE_1, KISS_TOY_OFFICIAL_V2_TYPE_4)),
    KissToyOfficialV2Profile("QCPW", "迷路-入体版", listOf(KISS_TOY_OFFICIAL_V2_TYPE_1, KISS_TOY_OFFICIAL_V2_TYPE_3)),
    KissToyOfficialV2Profile("QCSW", "迷路-吮吸版", listOf(KISS_TOY_OFFICIAL_V2_TYPE_1, KISS_TOY_OFFICIAL_V2_TYPE_3)),
    KissToyOfficialV2Profile("QCVW", "迷路-震动版", listOf(KISS_TOY_OFFICIAL_V2_TYPE_1, KISS_TOY_OFFICIAL_V2_TYPE_2)),
    KissToyOfficialV2Profile("QCFW", "迷路-拍打版", listOf(KISS_TOY_OFFICIAL_V2_TYPE_1, KISS_TOY_OFFICIAL_V2_TYPE_6)),
    KISS_TOY_TUTU_OFFICIAL_V2_PROFILE,
    KissToyOfficialV2Profile("QCFBH25", "粉饼盒", listOf(KISS_TOY_OFFICIAL_V2_TYPE_1)),
    KissToyOfficialV2Profile("KXMINI25", "Cathy Mini", listOf(KISS_TOY_OFFICIAL_V2_TYPE_1, KISS_TOY_OFFICIAL_V2_TYPE_3)),
    KissToyOfficialV2Profile("PLY5", "Polly 5", listOf(KISS_TOY_OFFICIAL_V2_TYPE_1, KISS_TOY_OFFICIAL_V2_TYPE_3)),
).associateBy { it.code.normalizedDeviceName() }

private object KissToyProtocol : BleDeviceProtocol {
    private val serviceUuid = uuid("00001000-0000-1000-8000-00805f9b34fb")
    private val writeUuid = uuid("00001001-0000-1000-8000-00805f9b34fb")
    private val notifyUuid = uuid("00001002-0000-1000-8000-00805f9b34fb")
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
        if (fingerprint.kissToyOfficialV2Profile()?.requiresAuth == true) return false
        val hasKissToyGatt = fingerprint.serviceUuids.contains(serviceUuid) &&
                fingerprint.characteristicUuids.contains(writeUuid)
        if (!hasKissToyGatt) return false
        val knownName = knownNames.any { it.equals(fingerprint.name, ignoreCase = true) }
        val hasNotify = fingerprint.characteristicUuids.contains(notifyUuid)
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
            1 -> listOf(command(kissToyPayload(0x32, 0)), command(kissToyPayload(0x71, 0)))
            2 -> listOf(command(kissToyPayload(0x31, 0)), command(kissToyPayload(0x71, 0)))
            3 -> listOf(command(kissToyPayload(0x31, 0)), command(kissToyPayload(0x32, 0)))
            else -> listOf(command(kissToyPayload(0x71, 0)))
        }
        val runCommands = if (normalizedMode == 4) {
            listOf(
                command(kissToyPayload(0x32, normalizedIntensity)),
                command(kissToyPayload(0x31, normalizedIntensity)),
            )
        } else {
            listOf(command(commandForMode(normalizedMode, normalizedIntensity)))
        }
        return clearCommands + runCommands
    }

    private fun stop(): List<BleProtocolOperation> =
        listOf(
            command(kissToyPayload(0x40, 0, 0)),
            command(kissToyPayload(0x31, 0)),
            command(kissToyPayload(0x32, 0)),
            command(kissToyPayload(0x71, 0)),
        )

    private fun command(payload: ByteArray) = BleProtocolOperation.Write(
        characteristicUuid = writeUuid,
        bytes = kissToyEncrypt(payload),
        withResponse = true,
    )

    private fun commandForMode(mode: Int, intensity: Int): ByteArray =
        when (mode) {
            1 -> kissToyPayload(0x31, intensity)
            2 -> kissToyPayload(0x32, intensity)
            3 -> kissToyPayload(0x71, intensity)
            else -> kissToyPayload(0x31, intensity)
        }

    private fun kissToyPayload(command: Int, first: Int, second: Int = 0): ByteArray =
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

    private fun kissToyEncrypt(payload: ByteArray): ByteArray {
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
            val key = kissToyKey(encrypted[index - 1], index)
            encrypted[index] =
                (((key.toInt() xor seed.toInt()) xor plain[index].toInt()) + key).toByte()
        }
        return encrypted
    }

    private fun kissToyKey(previous: Byte, index: Int): Byte =
        keyTab[previous.toInt() and 0x03][index]

    private val keyTab = arrayOf(
        byteArrayOf(0, 24, -104, -9, -91, 61, 13, 41, 37, 80, 68, 70),
        byteArrayOf(0, 69, 110, 106, 111, 120, 32, 83, 45, 49, 46, 55),
        byteArrayOf(0, 101, 120, 32, 84, 111, 121, 115, 10, -114, -99, -93),
        byteArrayOf(0, -59, -42, -25, -8, 10, 50, 32, 111, 98, 13, 10),
    )
}

internal class ManualBleProtocol(
    private val writeUuid: UUID,
    private val template: ProtocolTemplate,
) : BleDeviceProtocol {
    override val status = BleProtocolStatus(
        id = "manual",
        displayName = "高级手动模板",
        controllable = true,
        intensityMax = 5,
        supportsMode = true,
        controlStyle = ToyControlStyle.CombinedPatternAndIntensity,
        automatic = false,
    )

    override fun matches(fingerprint: BleGattFingerprint): Boolean = false

    override fun commandsFor(action: ToyControlAction): List<BleProtocolOperation> {
        val bytes = when (action) {
            is ToyControlAction.Pattern -> template.commandBytes(action.mode, 1)
            is ToyControlAction.Intensity -> template.commandBytes(1, action.value)
            is ToyControlAction.Combined -> template.commandBytes(action.mode, action.intensity)
            is ToyControlAction.DualMotor -> template.commandBytes(action.mode, action.strongestIntensity(status.intensityMax))
            ToyControlAction.Stop -> template.stopBytes()
        }
        return listOf(BleProtocolOperation.Write(writeUuid, bytes, template.writeWithResponse))
    }
}

private object SatisfyerProtocol : BleDeviceProtocol {
    private val controlUuid = uuid("51361501-c5e7-47c7-8a6e-47ebc99d80e8")
    private val speedUuid = uuid("51361502-c5e7-47c7-8a6e-47ebc99d80e8")

    override val status = BleProtocolStatus(
        id = "satisfyer",
        displayName = "Satisfyer",
        controllable = true,
        intensityMax = 100,
        supportsMode = false,
        controlStyle = ToyControlStyle.IntensityOnly,
        automatic = true,
    )

    override fun matches(fingerprint: BleGattFingerprint): Boolean =
        fingerprint.characteristicUuids.contains(controlUuid) &&
                fingerprint.characteristicUuids.contains(speedUuid)

    override fun commandsFor(action: ToyControlAction): List<BleProtocolOperation> =
        when (action) {
            is ToyControlAction.Intensity -> run(action.value)
            is ToyControlAction.Combined -> run(action.intensity)
            is ToyControlAction.DualMotor -> run(action.strongestIntensity(status.intensityMax))
            is ToyControlAction.Pattern -> emptyList()
            ToyControlAction.Stop -> listOf(speedCommand(0), BleProtocolOperation.Write(controlUuid, byteArrayOf(0x07), withResponse = true))
        }

    private fun run(intensity: Int): List<BleProtocolOperation> =
        listOf(
            BleProtocolOperation.Write(controlUuid, byteArrayOf(0x01), withResponse = true),
            speedCommand(intensity),
        )

    private fun speedCommand(intensity: Int) = BleProtocolOperation.Write(
        characteristicUuid = speedUuid,
        bytes = byteArrayOf(0x00, 0x00, 0x00, intensity.coerceIn(0, 100).toByte()),
        withResponse = true,
    )
}

private object OhMiBodEsca2Protocol : BleDeviceProtocol {
    private val writeUuid = uuid("a0d70002-4c16-4ba7-977a-d394920e13a3")

    override val status = BleProtocolStatus(
        id = "ohmibod_esca2",
        displayName = "OhMiBod Esca 2",
        controllable = true,
        intensityMax = 100,
        supportsMode = false,
        controlStyle = ToyControlStyle.IntensityOnly,
        automatic = true,
    )

    override fun matches(fingerprint: BleGattFingerprint): Boolean {
        val name = fingerprint.name.normalizedDeviceName()
        val knownName = name.contains("ohmibod") || name.contains("esca")
        return knownName &&
            fingerprint.characteristicUuids.contains(writeUuid)
    }

    override fun commandsFor(action: ToyControlAction): List<BleProtocolOperation> =
        when (action) {
            is ToyControlAction.Intensity -> listOf(command(action.value))
            is ToyControlAction.Combined -> listOf(command(action.intensity))
            is ToyControlAction.DualMotor -> listOf(command(action.strongestIntensity(status.intensityMax)))
            is ToyControlAction.Pattern -> emptyList()
            ToyControlAction.Stop -> listOf(command(0))
        }

    private fun command(intensity: Int) = BleProtocolOperation.Write(
        characteristicUuid = writeUuid,
        bytes = byteArrayOf(0x01, intensity.coerceIn(0, 100).toByte()),
        withResponse = false,
    )
}

private object PinkPunchProtocol : BleDeviceProtocol {
    private val writeUuid = uuid("0000ffe1-0000-1000-8000-00805f9b34fb")
    private val notifyUuid = uuid("0000ffe2-0000-1000-8000-00805f9b34fb")

    override val status = BleProtocolStatus(
        id = "pink_punch",
        displayName = "Pink Punch",
        controllable = true,
        intensityMax = 100,
        supportsMode = false,
        controlStyle = ToyControlStyle.IntensityOnly,
        automatic = true,
    )

    override fun matches(fingerprint: BleGattFingerprint): Boolean =
        fingerprint.name.normalizedDeviceName().contains("pinkpunch") &&
                fingerprint.characteristicUuids.contains(writeUuid) &&
                fingerprint.characteristicUuids.contains(notifyUuid)

    override fun commandsFor(action: ToyControlAction): List<BleProtocolOperation> =
        when (action) {
            is ToyControlAction.Intensity -> listOf(command(0x09, action.value))
            is ToyControlAction.Combined -> listOf(command(0x09, action.intensity))
            is ToyControlAction.DualMotor -> listOf(command(0x09, action.strongestIntensity(status.intensityMax)))
            is ToyControlAction.Pattern -> emptyList()
            ToyControlAction.Stop -> listOf(command(0x09, 0))
        }

    private fun command(mode: Int, intensity: Int) = BleProtocolOperation.Write(
        characteristicUuid = writeUuid,
        bytes = byteArrayOf(mode.toByte(), intensity.coerceIn(0, 100).toByte()),
        withResponse = false,
    )
}

private object LovenutsProtocol : BleDeviceProtocol {
    private val writeUuid = uuid("0000fff1-0000-1000-8000-00805f9b34fb")

    override val status = BleProtocolStatus(
        id = "lovenuts",
        displayName = "Lovenuts",
        controllable = true,
        intensityMax = 15,
        supportsMode = false,
        controlStyle = ToyControlStyle.IntensityOnly,
        automatic = true,
    )

    override fun matches(fingerprint: BleGattFingerprint): Boolean =
        fingerprint.name.normalizedDeviceName().let { it.contains("lovenuts") || it.contains("love-nuts") } &&
                fingerprint.characteristicUuids.contains(writeUuid)

    override fun commandsFor(action: ToyControlAction): List<BleProtocolOperation> =
        when (action) {
            is ToyControlAction.Intensity -> listOf(patternCommand(action.value.coerceIn(0, status.intensityMax)))
            is ToyControlAction.Combined -> listOf(patternCommand(action.intensity.coerceIn(0, status.intensityMax)))
            is ToyControlAction.DualMotor -> listOf(patternCommand(action.strongestIntensity(status.intensityMax)))
            is ToyControlAction.Pattern -> emptyList()
            ToyControlAction.Stop -> listOf(patternCommand(speed = 0))
        }

    private fun patternCommand(speed: Int): BleProtocolOperation.Write {
        val packed = ((speed and 0x0f) shl 4) or (speed and 0x0f)
        val bytes = ByteArray(16)
        bytes[0] = 0x45
        bytes[1] = 0x56
        bytes[2] = 0x4f
        bytes[3] = 0x4c
        for (index in 4..13) bytes[index] = packed.toByte()
        bytes[14] = 0x00
        bytes[15] = 0xff.toByte()
        return BleProtocolOperation.Write(writeUuid, bytes, withResponse = false)
    }
}

private object JeJoueNuoProtocol : BleDeviceProtocol {
    private val writeUuid = uuid("0000fff1-0000-1000-8000-00805f9b34fb")

    override val status = BleProtocolStatus(
        id = "je_joue_nuo",
        displayName = "Je Joue Nuo",
        controllable = true,
        intensityMax = 5,
        supportsMode = true,
        modeMax = 3,
        controlStyle = ToyControlStyle.CombinedPatternAndIntensity,
        automatic = true,
    )

    override fun matches(fingerprint: BleGattFingerprint): Boolean {
        val knownName = fingerprint.name.contains("Je Joue", ignoreCase = true) ||
                fingerprint.name.contains("Nuo", ignoreCase = true)
        return knownName && fingerprint.characteristicUuids.contains(writeUuid)
    }

    override fun commandsFor(action: ToyControlAction): List<BleProtocolOperation> =
        when (action) {
            is ToyControlAction.Combined -> listOf(command(action.mode.coerceIn(1, status.modeMax), action.intensity.coerceIn(0, status.intensityMax)))
            is ToyControlAction.Intensity -> listOf(command(pattern = 1, speed = action.value.coerceIn(0, status.intensityMax)))
            is ToyControlAction.DualMotor -> listOf(command(action.mode.coerceIn(1, status.modeMax), action.strongestIntensity(status.intensityMax)))
            is ToyControlAction.Pattern -> listOf(command(pattern = action.mode.coerceIn(1, status.modeMax), speed = status.intensityMax.coerceAtLeast(1)))
            ToyControlAction.Stop -> listOf(command(pattern = 1, speed = 0))
        }

    private fun command(pattern: Int, speed: Int) = BleProtocolOperation.Write(
        characteristicUuid = writeUuid,
        bytes = byteArrayOf(pattern.toByte(), speed.toByte()),
        withResponse = false,
    )
}

/**
 * Lovense BLE 字符串协议。
 * 公开资料显示 Lovense 设备名通常以 LVS- 或 LOVE- 开头，振动控制使用 Vibrate:n;。
 */
private object LovenseProtocol : BleDeviceProtocol {
    private val writeCandidates = listOf(
        uuid("50300012-0023-4bd4-bbd5-a6920e4c5653"),
        uuid("0000fff2-0000-1000-8000-00805f9b34fb"),
        uuid("0000ffe1-0000-1000-8000-00805f9b34fb"),
    )
    private var writeUuid = writeCandidates.first()

    override val status = BleProtocolStatus(
        id = "lovense",
        displayName = "Lovense",
        controllable = true,
        intensityMax = 20,
        supportsMode = false,
        controlStyle = ToyControlStyle.IntensityOnly,
        automatic = true,
    )

    override fun matches(fingerprint: BleGattFingerprint): Boolean {
        val nameMatched = fingerprint.name.startsWith("LVS-", ignoreCase = true) ||
                fingerprint.name.startsWith("LOVE-", ignoreCase = true)
        val writeMatched = writeCandidates.any(fingerprint.characteristicUuids::contains)
        return nameMatched && writeMatched
    }

    override fun initialize(fingerprint: BleGattFingerprint): List<BleProtocolOperation> {
        writeUuid = writeCandidates.first(fingerprint.characteristicUuids::contains)
        return emptyList()
    }

    override fun commandsFor(action: ToyControlAction): List<BleProtocolOperation> =
        when (action) {
            is ToyControlAction.Intensity -> listOf(command("Vibrate:${action.value.coerceIn(0, status.intensityMax)};"))
            is ToyControlAction.Combined -> listOf(command("Vibrate:${action.intensity.coerceIn(0, status.intensityMax)};"))
            is ToyControlAction.DualMotor -> listOf(command("Vibrate:${action.strongestIntensity(status.intensityMax)};"))
            is ToyControlAction.Pattern -> emptyList()
            ToyControlAction.Stop -> listOf(command("Vibrate:0;"))
        }

    private fun command(value: String) = BleProtocolOperation.Write(
        characteristicUuid = writeUuid,
        bytes = value.encodeToByteArray(),
        withResponse = false,
    )
}

/**
 * Buttplug ankni 协议的 Android 实现。
 * 上游配置将 DSJM 识别为 Roselex Device，速度范围为 0..3。
 */
private object AnkniProtocol : BleDeviceProtocol {
    private val deviceInfoUuid = uuid("00002a50-0000-1000-8000-00805f9b34fb")
    private val writeCandidates = listOf(
        uuid("0000fe02-0000-1000-8000-00805f9b34fb"),
        uuid("0000fe01-0000-1000-8000-00805f9b34fb"),
    )
    private var writeUuid = writeCandidates.first()

    override val status = BleProtocolStatus(
        id = "ankni",
        displayName = "Roselex / DSJM",
        controllable = true,
        verifiedControl = true,
        intensityMax = 3,
        supportsMode = false,
        controlStyle = ToyControlStyle.IntensityOnly,
        automatic = true,
    )

    override fun matches(fingerprint: BleGattFingerprint): Boolean {
        val hasWrite = writeCandidates.any(fingerprint.characteristicUuids::contains)
        return fingerprint.characteristicUuids.contains(deviceInfoUuid) && hasWrite
    }

    override fun initialize(fingerprint: BleGattFingerprint): List<BleProtocolOperation> {
        writeUuid = writeCandidates.first(fingerprint.characteristicUuids::contains)
        return listOf(BleProtocolOperation.Read(deviceInfoUuid))
    }

    override fun onRead(
        characteristicUuid: UUID,
        bytes: ByteArray,
    ): List<BleProtocolOperation> {
        if (characteristicUuid != deviceInfoUuid || bytes.size > 6) return emptyList()
        require(bytes.isNotEmpty()) { "设备身份信息为空，无法完成握手" }
        val checksum = (crc16(byteArrayOf(0x01) + bytes) ushr 8).toByte()
        return listOf(
            BleProtocolOperation.Write(
                characteristicUuid = writeUuid,
                bytes = ByteArray(20) { 0x01 },
                withResponse = true,
            ),
            BleProtocolOperation.Write(
                characteristicUuid = writeUuid,
                bytes = ByteArray(20).also {
                    it[0] = 0x01
                    it[1] = 0x02
                    for (index in 2..17) it[index] = checksum
                },
                withResponse = true,
            ),
        )
    }

    override fun commandsFor(action: ToyControlAction): List<BleProtocolOperation> =
        when (action) {
            is ToyControlAction.Intensity -> listOf(controlCommand(action.value.coerceIn(1, status.intensityMax)))
            is ToyControlAction.Combined -> listOf(controlCommand(action.intensity.coerceIn(1, status.intensityMax)))
            is ToyControlAction.DualMotor -> listOf(controlCommand(action.strongestIntensity(status.intensityMax).coerceAtLeast(1)))
            is ToyControlAction.Pattern -> emptyList()
            ToyControlAction.Stop -> listOf(controlCommand(0))
        }

    private fun controlCommand(speed: Int) = BleProtocolOperation.Write(
        characteristicUuid = writeUuid,
        bytes = ByteArray(20).also {
            it[0] = 0x03
            it[1] = 0x12
            it[2] = speed.toByte()
        },
        withResponse = true,
    )

    private fun crc16(bytes: ByteArray): Int {
        var remain = 0
        bytes.forEach { byte ->
            remain = remain xor ((byte.toInt() and 0xff) shl 8)
            repeat(8) {
                remain = if (remain and 0x8000 != 0) {
                    ((remain shl 1) xor 0x1021) and 0xffff
                } else {
                    (remain shl 1) and 0xffff
                }
            }
        }
        return remain
    }
}

private object AnkniQd1GattProtocol : BleDeviceProtocol {
    private val writeUuid = uuid("0000ddd1-0000-1000-8000-00805f9b34fb")

    override val status = BleProtocolStatus(
        id = "ankni_qd1_gatt",
        displayName = "ANKNI QD1",
        controllable = true,
        intensityMax = 100,
        supportsMode = false,
        controlStyle = ToyControlStyle.IntensityOnly,
        intensityLabel = "强度",
        automatic = true,
        repeatIntervalMs = QD1_KEEP_ALIVE_MS,
    )

    override fun matches(fingerprint: BleGattFingerprint): Boolean {
        if (!fingerprint.hasAnkniDdddGatt()) return false
        return fingerprint.hasAnkniQd1Name()
    }

    override fun commandsFor(action: ToyControlAction): List<BleProtocolOperation> =
        when (action) {
            is ToyControlAction.Intensity -> listOf(write(slideFrame(action.value)))
            is ToyControlAction.Combined -> listOf(write(slideFrame(action.intensity)))
            is ToyControlAction.DualMotor -> listOf(write(slideFrame(action.strongestIntensity(status.intensityMax))))
            is ToyControlAction.Pattern -> listOf(write(slideFrame(status.intensityMax)))
            ToyControlAction.Stop -> listOf(write(slideFrame(0)))
        }

    private fun slideFrame(intensity: Int): ByteArray {
        val value = intensity.coerceIn(0, status.intensityMax)
        val duration = if (value > 0) SLIDE_DURATION else 0
        return ankniAaFrame(SLIDE_COMMAND, value, duration)
    }

    private fun write(bytes: ByteArray): BleProtocolOperation.Write =
        BleProtocolOperation.Write(
            characteristicUuid = writeUuid,
            bytes = bytes,
            withResponse = false,
        )

    private const val SLIDE_COMMAND = 0x08
    private const val SLIDE_DURATION = 0xC8
    private const val QD1_KEEP_ALIVE_MS = 200
}

private object AnkniQd1Ff12GattProtocol : BleDeviceProtocol {
    private val serviceUuid = uuid("0000ff10-0000-1000-8000-00805f9b34fb")
    private val writeUuid = uuid("0000ff12-0000-1000-8000-00805f9b34fb")

    override val status = BleProtocolStatus(
        id = "ankni_qd1_ff12_gatt",
        displayName = "ANKNI QD1",
        controllable = true,
        intensityMax = 100,
        supportsMode = false,
        controlStyle = ToyControlStyle.IntensityOnly,
        intensityLabel = "强度",
        automatic = true,
        repeatIntervalMs = QD1_KEEP_ALIVE_MS,
    )

    override fun matches(fingerprint: BleGattFingerprint): Boolean {
        return fingerprint.hasAnkniQd1Name() &&
            fingerprint.serviceUuids.contains(serviceUuid) &&
            fingerprint.characteristicUuids.contains(writeUuid)
    }

    override fun commandsFor(action: ToyControlAction): List<BleProtocolOperation> =
        when (action) {
            is ToyControlAction.Intensity -> listOf(write(slideFrame(action.value)))
            is ToyControlAction.Combined -> listOf(write(slideFrame(action.intensity)))
            is ToyControlAction.DualMotor -> listOf(write(slideFrame(action.strongestIntensity(status.intensityMax))))
            is ToyControlAction.Pattern -> listOf(write(slideFrame(status.intensityMax)))
            ToyControlAction.Stop -> listOf(write(slideFrame(0)))
        }

    private fun slideFrame(intensity: Int): ByteArray {
        val value = intensity.coerceIn(0, status.intensityMax)
        val duration = if (value > 0) SLIDE_DURATION else 0
        return ankniAaFrame(SLIDE_COMMAND, value, duration)
    }

    private fun write(bytes: ByteArray): BleProtocolOperation.Write =
        BleProtocolOperation.Write(
            characteristicUuid = writeUuid,
            bytes = bytes,
            withResponse = true,
        )

    private const val SLIDE_COMMAND = 0x08
    private const val SLIDE_DURATION = 0xC8
    private const val QD1_KEEP_ALIVE_MS = 200
}

private object Ankni0010Protocol : BleDeviceProtocol {
    private val writeUuid = uuid("0000ffe1-0000-1000-8000-00805f9b34fb")

    override val status = BleProtocolStatus(
        id = "ankni_0010",
        displayName = "ANKNI 0010",
        controllable = true,
        intensityMax = 100,
        supportsMode = false,
        controlStyle = ToyControlStyle.IntensityOnly,
        intensityLabel = "强度",
        automatic = true,
    )

    override fun matches(fingerprint: BleGattFingerprint): Boolean {
        if (!fingerprint.characteristicUuids.contains(writeUuid)) return false
        val name = fingerprint.name.normalizedDeviceName()
        return name.contains("安可尼0010") ||
            name.contains("ankni0010") ||
            name.contains("ankeni0010")
    }

    override fun commandsFor(action: ToyControlAction): List<BleProtocolOperation> =
        when (action) {
            is ToyControlAction.Intensity -> listOf(write(slideFrame(action.value)))
            is ToyControlAction.Combined -> listOf(write(slideFrame(action.intensity)))
            is ToyControlAction.DualMotor -> listOf(write(slideFrame(action.strongestIntensity(status.intensityMax))))
            is ToyControlAction.Pattern -> listOf(write(slideFrame(status.intensityMax)))
            ToyControlAction.Stop -> listOf(write(slideFrame(0)))
        }

    private fun slideFrame(intensity: Int): ByteArray {
        val value = intensity.coerceIn(0, status.intensityMax)
        val duration = if (value > 0) SLIDE_DURATION else 0
        return ankniAaFrame(SLIDE_COMMAND, value, duration)
    }

    private fun write(bytes: ByteArray): BleProtocolOperation.Write =
        BleProtocolOperation.Write(
            characteristicUuid = writeUuid,
            bytes = bytes,
            withResponse = false,
        )

    private const val SLIDE_COMMAND = 0x08
    private const val SLIDE_DURATION = 0xC8
}

private val ankniDdddProfileProtocols: List<BleDeviceProtocol> = listOf(
    AnkniDdddProfileProtocol(AnkniDdddProfile.tqjD()),
    AnkniDdddProfileProtocol(AnkniDdddProfile.classic("ankni_0001", "ANKNI 0001", 0x0A, 1, 10, listOf("安可尼0001", "ankni0001", "ankeni0001", "intung", "intang"))),
    AnkniDdddProfileProtocol(AnkniDdddProfile.classic("ankni_0002", "ANKNI 0002", 0x0A, 1, 8, listOf("安可尼0002", "ankni0002", "ankeni0002"))),
    AnkniDdddProfileProtocol(AnkniDdddProfile.classic("ankni_0003", "ANKNI 0003", 0x0A, 1, 10, listOf("安可尼0003", "ankni0003", "ankeni0003", "tanghe"))),
    AnkniDdddProfileProtocol(AnkniDdddProfile.classic("ankni_0005", "ANKNI 0005", 0x0A, 1, 10, listOf("安可尼0005", "ankni0005", "ankeni0005", "xiaoshuidi"))),
    AnkniDdddProfileProtocol(AnkniDdddProfile.classic("ankni_0007", "ANKNI 0007", 0x0F, 2, 10, listOf("安可尼0007", "ankni0007", "ankeni0007", "taoxinxiong"))),
    AnkniDdddProfileProtocol(AnkniDdddProfile.classic("ankni_0009", "ANKNI 0009", 0x0F, 2, 10, listOf("安可尼0009", "ankni0009", "ankeni0009", "mitaobaicha"))),
)

private data class AnkniDdddProfile(
    val id: String,
    val displayName: String,
    val aliases: List<String>,
    val modeMax: Int,
    val intensityMax: Int,
    val controlStyle: ToyControlStyle,
    val modeLabel: String,
    val modeNames: List<String>,
    val intensityLabel: String,
    val behavior: Behavior,
    val modeCommand: Int = 0,
    val motorNum: Int = 0,
) {
    enum class Behavior {
        Classic,
        TqjD,
    }

    companion object {
        fun classic(
            id: String,
            displayName: String,
            modeCommand: Int,
            motorNum: Int,
            modeMax: Int,
            aliases: List<String>,
        ) = AnkniDdddProfile(
            id = id,
            displayName = displayName,
            aliases = aliases,
            modeMax = modeMax,
            intensityMax = 100,
            controlStyle = ToyControlStyle.ExclusivePatternOrIntensity,
            modeLabel = "预设节奏",
            modeNames = emptyList(),
            intensityLabel = "滑动强度",
            behavior = Behavior.Classic,
            modeCommand = modeCommand,
            motorNum = motorNum,
        )

        fun tqjD() = AnkniDdddProfile(
            id = "ankni_tqj_d",
            displayName = "ANKNI TQJ-D",
            aliases = listOf("tqjd", "anknitqjd", "ankeni_tqjd"),
            modeMax = 3,
            intensityMax = 10,
            controlStyle = ToyControlStyle.CombinedPatternAndIntensity,
            modeLabel = "功能",
            modeNames = listOf("吮吸", "震动", "电击"),
            intensityLabel = "强度",
            behavior = Behavior.TqjD,
        )
    }
}

private class AnkniDdddProfileProtocol(
    private val profile: AnkniDdddProfile,
) : BleDeviceProtocol {
    private val writeUuid = uuid("0000ddd1-0000-1000-8000-00805f9b34fb")
    private val slideCommand = 0x08

    override val status = BleProtocolStatus(
        id = profile.id,
        displayName = profile.displayName,
        controllable = true,
        intensityMax = profile.intensityMax,
        supportsMode = true,
        modeMax = profile.modeMax,
        controlStyle = profile.controlStyle,
        modeLabel = profile.modeLabel,
        modeNames = profile.modeNames,
        intensityLabel = profile.intensityLabel,
        automatic = true,
        // 小程序对 TQJ-D 滑动控制每 200ms 重发一次；不保活时电机会在帧尾时长耗尽后自动停止。
        repeatIntervalMs = if (profile.behavior == AnkniDdddProfile.Behavior.TqjD) TQJD_KEEP_ALIVE_MS else 0,
    )

    override fun matches(fingerprint: BleGattFingerprint): Boolean {
        if (!fingerprint.hasAnkniDdddGatt()) return false
        val name = fingerprint.name.normalizedDeviceName()
        return profile.aliases.any { alias -> name.contains(alias.normalizedDeviceName()) }
    }

    override fun commandsFor(action: ToyControlAction): List<BleProtocolOperation> =
        when (profile.behavior) {
            AnkniDdddProfile.Behavior.Classic -> classicCommandsFor(action)
            AnkniDdddProfile.Behavior.TqjD -> tqjDCommandsFor(action)
        }

    private fun classicCommandsFor(action: ToyControlAction): List<BleProtocolOperation> {
        return when (action) {
            is ToyControlAction.Pattern ->
                listOf(write(ankniAaFrame(profile.modeCommand, *classicModeValues(action.mode, profile.motorNum))))
            is ToyControlAction.Intensity ->
                listOf(write(ankniAaFrame(slideCommand, *classicSlideValues(action.value, profile.motorNum))))
            is ToyControlAction.Combined ->
                listOf(write(ankniAaFrame(profile.modeCommand, *classicModeValues(action.mode, profile.motorNum))))
            is ToyControlAction.DualMotor ->
                listOf(write(ankniAaFrame(profile.modeCommand, *classicModeValues(action.mode, profile.motorNum))))
            ToyControlAction.Stop ->
                listOf(
                    write(ankniAaFrame(profile.modeCommand, *IntArray(profile.motorNum))),
                    write(ankniAaFrame(slideCommand, *IntArray(profile.motorNum + 1))),
                )
        }
    }

    private fun tqjDCommandsFor(action: ToyControlAction): List<BleProtocolOperation> =
        when (action) {
            is ToyControlAction.Pattern ->
                tqjDControl(action.mode, status.intensityMax)
            is ToyControlAction.Intensity ->
                tqjDControl(1, action.value)
            is ToyControlAction.Combined ->
                tqjDControl(action.mode, action.intensity)
            is ToyControlAction.DualMotor ->
                tqjDControl(action.mode, action.strongestIntensity(status.intensityMax))
            ToyControlAction.Stop ->
                listOf(write(ankniAaFrame(slideCommand, 0, 0, 0, 0)))
        }

    private fun tqjDControl(mode: Int, intensity: Int): List<BleProtocolOperation> {
        val channelValues = IntArray(status.modeMax)
        val channelIndex = mode.coerceIn(1, status.modeMax) - 1
        channelValues[channelIndex] = (intensity.coerceIn(0, status.intensityMax) * 10).coerceIn(0, 100)
        // 小程序 makeSlideOrder(200, ...) 把固定的 200(0xC8) 作为帧尾时长位，与强度无关。
        // 之前误把强度最大值写进时长位，导致低强度时长太短：电机只动一两秒就停、强度小于 4 直接不触发。
        return listOf(write(ankniAaFrame(slideCommand, *channelValues, TQJD_SLIDE_DURATION)))
    }

    private fun classicModeValues(mode: Int, motorNum: Int): IntArray {
        val value = mode.coerceIn(1, status.modeMax)
        return IntArray(motorNum) { value }
    }

    private fun classicSlideValues(intensity: Int, motorNum: Int): IntArray {
        val value = intensity.coerceIn(0, status.intensityMax)
        return IntArray(motorNum) { value } + 0xC8
    }

    private fun write(bytes: ByteArray): BleProtocolOperation.Write =
        BleProtocolOperation.Write(
            characteristicUuid = writeUuid,
            bytes = bytes,
            withResponse = true,
        )

    companion object {
        // 帧尾固定时长位，来源：醉清风小程序 makeSlideOrder(200, ...)
        private const val TQJD_SLIDE_DURATION = 0xC8
        // 与小程序滑动控制 setInterval(200) 保活节奏一致
        private const val TQJD_KEEP_ALIVE_MS = 200
    }
}

private object AnkniYwtdProtocol : BleDeviceProtocol {
    private val serviceUuid = uuid("0000dddd-0000-1000-8000-00805f9b34fb")
    private val writeUuid = uuid("0000ddd1-0000-1000-8000-00805f9b34fb")
    private val notifyUuid = uuid("0000ddd2-0000-1000-8000-00805f9b34fb")
    private const val MODE_COMMAND = 0x0F
    private const val SLIDE_COMMAND = 0x08
    private const val SLIDE_DURATION = 0xC8

    override val status = BleProtocolStatus(
        id = "ankni_ywtd",
        displayName = "ANKNI / Mizzzee DDDD",
        controllable = true,
        intensityMax = 100,
        supportsMode = true,
        modeMax = 10,
        controlStyle = ToyControlStyle.ExclusivePatternOrIntensity,
        modeLabel = "预设节奏",
        intensityLabel = "滑动强度",
        automatic = true,
        repeatIntervalMs = YWTD_KEEP_ALIVE_MS,
    )

    override fun matches(fingerprint: BleGattFingerprint): Boolean =
        fingerprint.serviceUuids.contains(serviceUuid) &&
                fingerprint.characteristicUuids.contains(writeUuid) &&
                fingerprint.characteristicUuids.contains(notifyUuid)

    override fun commandsFor(action: ToyControlAction): List<BleProtocolOperation> =
        when (action) {
            is ToyControlAction.Pattern ->
                listOf(write(ankniAaFrame(MODE_COMMAND, action.mode.coerceIn(1, status.modeMax))))
            is ToyControlAction.Intensity ->
                listOf(write(ankniAaFrame(SLIDE_COMMAND, action.value.coerceIn(0, status.intensityMax), SLIDE_DURATION)))
            is ToyControlAction.Combined ->
                listOf(write(ankniAaFrame(MODE_COMMAND, action.mode.coerceIn(1, status.modeMax))))
            is ToyControlAction.DualMotor ->
                listOf(write(ankniAaFrame(MODE_COMMAND, action.mode.coerceIn(1, status.modeMax))))
            ToyControlAction.Stop ->
                listOf(write(ankniAaFrame(MODE_COMMAND, 0)))
        }

    private const val YWTD_KEEP_ALIVE_MS = 200

    private fun write(bytes: ByteArray): BleProtocolOperation.Write =
        BleProtocolOperation.Write(
            characteristicUuid = writeUuid,
            bytes = bytes,
            withResponse = false,
        )
}

private fun uuid(value: String): UUID = UUID.fromString(value)

private fun bytes(vararg values: Int): ByteArray =
    values.map { (it and 0xff).toByte() }.toByteArray()

private fun BleGattFingerprint.toButtplugFingerprint(): ButtplugDeviceFingerprint =
    ButtplugDeviceFingerprint(
        name = name,
        serviceUuids = serviceUuids.map { it.toString().normalizedUuidText() }.toSet(),
        characteristicUuids = characteristicUuids.map { it.toString().normalizedUuidText() }.toSet(),
        manufacturerData = manufacturerData.toManufacturerDataMap(),
    )

private fun ButtplugDeviceMatch.toBleStatus(
    id: String,
    controllable: Boolean,
    supportsMode: Boolean,
    controlStyle: ToyControlStyle,
    modeMax: Int = 1,
    modeNames: List<String> = emptyList(),
): BleProtocolStatus {
    val vibrateFeatures = vibrateOutputs.sortedBy { it.featureIndex }
    val maxIntensity = vibrateFeatures.maxOfOrNull { it.max }
        ?: device.features.maxOfOrNull { it.max }
        ?: 0
    val channelNames = when {
        vibrateFeatures.size >= 2 -> listOf("内侧", "外侧")
        else -> emptyList()
    }
    return BleProtocolStatus(
        id = id,
        displayName = device.name.ifBlank { protocol.displayName },
        controllable = controllable,
        intensityMax = maxIntensity,
        supportsMode = supportsMode,
        modeMax = modeMax,
        controlStyle = controlStyle,
        modeLabel = "预设节奏",
        modeNames = modeNames,
        intensityLabel = "强度",
        channelNames = channelNames,
        features = device.features.map {
            BleProtocolFeature(
                type = it.type,
                min = it.min,
                max = it.max,
                index = it.featureIndex,
                label = it.description,
            )
        },
        automatic = true,
    )
}

private fun ankniAaFrame(command: Int, vararg payload: Int): ByteArray {
    val body = intArrayOf(0xAA, command, payload.size) + payload.map { it.coerceIn(0, 0xFF) }
    val checksum = body.sum() and 0xFF
    return (body + checksum).map { it.toByte() }.toByteArray()
}

private fun String.normalizedDeviceName(): String =
    lowercase().replace("_", "").replace("-", "").replace(" ", "")

private fun BleGattFingerprint.hasAnkniQd1Name(): Boolean {
    val name = name.normalizedDeviceName()
    return name.contains("ankniqd1") || name.contains("ankniqd01") || name.contains("ankeniqd1")
}

private fun BleGattFingerprint.hasSvakomManufacturerData(): Boolean =
    svakomManufacturerPayload() != null

private fun BleGattFingerprint.svakomProductCode(): Int? {
    val payload = svakomManufacturerPayload() ?: return null
    return when {
        payload.size >= 16 && payload[3].unsignedByte() == 0x02 ->
            (payload[13].unsignedByte() shl 16) or
                    (payload[14].unsignedByte() shl 8) or
                    payload[15].unsignedByte()
        payload.size >= 5 -> payload[4].unsignedByte()
        else -> null
    }
}

private fun BleGattFingerprint.svakomManufacturerPayload(): ByteArray? {
    val mappedPayload = manufacturerData.toManufacturerDataMap()
        .values
        .firstOrNull { it.startsWithSvakomPrefix() }
    if (mappedPayload != null) return mappedPayload
    return manufacturerData.substringAfter(':', missingDelimiterValue = manufacturerData)
        .trim()
        .hexByteArray()
        .takeIf { it.startsWithSvakomPrefix() }
}

private fun ByteArray.startsWithSvakomPrefix(): Boolean =
    size >= 3 &&
            this[0].unsignedByte() == 0x53 &&
            this[1].unsignedByte() == 0x56 &&
            this[2].unsignedByte() == 0x41

private fun Byte.unsignedByte(): Int = toInt() and 0xff

private fun BleGattFingerprint.hasAnkniDdddGatt(): Boolean =
    serviceUuids.contains(uuid("0000dddd-0000-1000-8000-00805f9b34fb")) &&
            characteristicUuids.contains(uuid("0000ddd1-0000-1000-8000-00805f9b34fb")) &&
            characteristicUuids.contains(uuid("0000ddd2-0000-1000-8000-00805f9b34fb"))

private fun String.xiuxiudaPacket(command: IntArray): ByteArray {
    require(command.size == 4) { "羞羞哒指令必须是 4 字节" }
    val prefix = requireNotNull(xiuxiudaAddressPrefix()) { "羞羞哒设备地址无效" }
    val payload = prefix + command.map { it.coerceIn(0, 0xFF) }
    val checksum = payload.fold(0) { acc, value -> acc xor value }
    return (payload + checksum).map { it.toByte() }.toByteArray()
}

private fun String.xiuxiudaAddressPrefix(): IntArray? {
    val addressBytes = split(':')
        .takeIf { it.size == 6 }
        ?.map { part -> part.toIntOrNull(16) ?: return null }
        ?: return null
    val key = 0x04 xor addressBytes.fold(0) { acc, value -> acc xor value }
    val high = (key ushr 4) and 0x0F
    val low = key and 0x0F
    val z1 = 0x80 or high
    val z2 = 0x80 or low
    val z3 = key
    val z4 = z1 xor z2 xor z3
    return intArrayOf(z1, z2, z3, z4)
}

private fun String.firstManufacturerByte(): Int? {
    val payload = substringAfter(':', missingDelimiterValue = this)
    return payload
        .trim()
        .split(Regex("\\s+"))
        .firstOrNull()
        ?.takeIf { it.length in 1..2 && it.all { ch -> ch.isDigit() || ch.lowercaseChar() in 'a'..'f' } }
        ?.toIntOrNull(16)
}

private fun String.toManufacturerDataMap(): Map<Int, ByteArray> =
    split(",")
        .mapNotNull { entry ->
            val idText = entry.substringBefore(":", missingDelimiterValue = "").trim()
            val dataText = entry.substringAfter(":", missingDelimiterValue = "").trim()
            val id = idText.removePrefix("0x").removePrefix("0X").toIntOrNull(16) ?: return@mapNotNull null
            id to dataText.hexByteArray()
        }
        .toMap()

private fun String.hexByteArray(): ByteArray =
    trim()
        .takeIf { it.isNotEmpty() }
        ?.split(Regex("\\s+"))
        ?.mapNotNull { token ->
            token.takeIf { it.length in 1..2 && it.all { ch -> ch.isDigit() || ch.lowercaseChar() in 'a'..'f' } }
                ?.toIntOrNull(16)
                ?.toByte()
        }
        ?.toByteArray()
        ?: byteArrayOf()

private fun IntArray.toByteArrayForSistalkV1(): ByteArray =
    if (size > 2) {
        byteArrayOf(size.toByte()) + map { it.coerceIn(0, 0xff).toByte() }
    } else {
        map { it.coerceIn(0, 0xff).toByte() }.toByteArray()
    }
