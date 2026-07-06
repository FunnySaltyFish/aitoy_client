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

internal object ButtplugBleProtocolFactory {
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

internal class ButtplugUnsupportedProtocol(
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

internal class ButtplugHoneyPlayBoxProtocol(
    private val match: ButtplugDeviceMatch,
) : BleDeviceProtocol {
    private val endpointByRole = match.endpoint.characteristics
    private val txUuid: Uuid = Uuid.parse(endpointByRole.getValue("tx"))
    private val rxUuid: Uuid = Uuid.parse(endpointByRole.getValue("rx"))
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

    override fun onNotify(characteristicUuid: Uuid, bytes: ByteArray): List<BleProtocolOperation> {
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

internal class ButtplugFlufferProtocol(
    private val match: ButtplugDeviceMatch,
) : BleDeviceProtocol {
    private val endpointByRole = match.endpoint.characteristics
    private val txUuid: Uuid = Uuid.parse(endpointByRole.getValue("tx"))
    private val rxUuid: Uuid = Uuid.parse(endpointByRole.getValue("rx"))
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

    override fun onNotify(characteristicUuid: Uuid, bytes: ByteArray): List<BleProtocolOperation> {
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

internal class ButtplugLovenseProtocol(
    private val match: ButtplugDeviceMatch,
) : BleDeviceProtocol {
    private val endpointByRole = match.endpoint.characteristics
    private val txUuid: Uuid = Uuid.parse(match.endpoint.txUuid.orEmpty())
    private val rxUuid: Uuid? = match.endpoint.rxUuid?.let { Uuid.parse(it) }
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

    override fun onNotify(characteristicUuid: Uuid, bytes: ByteArray): List<BleProtocolOperation> {
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
            characteristicUuid = Uuid.parse(requireNotNull(endpointByRole[write.endpointRole]) {
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

internal class ButtplugSpecializedProtocol(
    private val match: ButtplugDeviceMatch,
) : BleDeviceProtocol {
    private val endpointByRole = match.endpoint.characteristics
    private val txUuid: Uuid = Uuid.parse(match.endpoint.txUuid.orEmpty())
    private val commandUuid: Uuid? = endpointByRole["command"]?.let { Uuid.parse(it) }
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
        val characteristicUuid = requireNotNull(endpointByRole[endpointRole]?.let { Uuid.parse(it) }) {
            "${match.protocol.id} endpoint $endpointRole is missing"
        }
        return BleProtocolOperation.Write(
            characteristicUuid = characteristicUuid,
            bytes = bytes,
            withResponse = withResponse,
        )
    }
}

internal class ButtplugLegacyStpihkalProtocol(
    private val match: ButtplugDeviceMatch,
) : BleDeviceProtocol {
    private val txUuid: Uuid = Uuid.parse(match.endpoint.txUuid.orEmpty())
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

internal class ButtplugHandyProtocol(
    private val match: ButtplugDeviceMatch,
) : BleDeviceProtocol {
    private val txUuid: Uuid = Uuid.parse(match.endpoint.txUuid.orEmpty())
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

internal class ButtplugHandyV3Protocol(
    private val match: ButtplugDeviceMatch,
) : BleDeviceProtocol {
    private val endpointByRole = match.endpoint.characteristics
    private val txUuid: Uuid = Uuid.parse(endpointByRole.getValue("tx"))
    private val rxUuid: Uuid? = endpointByRole["rx"]?.let { Uuid.parse(it) }
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

internal class ButtplugVibCrafterProtocol(
    private val match: ButtplugDeviceMatch,
) : BleDeviceProtocol {
    private val endpointByRole = match.endpoint.characteristics
    private val txUuid: Uuid = Uuid.parse(endpointByRole.getValue("tx"))
    private val rxUuid: Uuid = Uuid.parse(endpointByRole.getValue("rx"))
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

    override fun onNotify(characteristicUuid: Uuid, bytes: ByteArray): List<BleProtocolOperation> {
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

internal class ButtplugLeloHarmonyProtocol(
    private val match: ButtplugDeviceMatch,
) : BleDeviceProtocol {
    private val endpointByRole = match.endpoint.characteristics
    private val txUuid: Uuid = Uuid.parse(endpointByRole.getValue("tx"))
    private val security = LeloSecurityHandshake(Uuid.parse(endpointByRole.getValue("whitelist")))
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

    override fun onNotify(characteristicUuid: Uuid, bytes: ByteArray): List<BleProtocolOperation> =
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

internal class ButtplugLeloF1sV2Protocol(
    private val match: ButtplugDeviceMatch,
) : BleDeviceProtocol {
    private val endpointByRole = match.endpoint.characteristics
    private val txUuid: Uuid = Uuid.parse(endpointByRole.getValue("tx"))
    private val securityEndpointRole = if (endpointByRole.containsKey("whitelist")) "whitelist" else "generic0"
    private val security = LeloSecurityHandshake(Uuid.parse(endpointByRole.getValue(securityEndpointRole)))
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

    override fun onNotify(characteristicUuid: Uuid, bytes: ByteArray): List<BleProtocolOperation> =
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

internal class LeloSecurityHandshake(
    private val securityUuid: Uuid,
) {
    var authorized: Boolean = false
        private set

    fun initialize(): List<BleProtocolOperation> =
        listOf(
            BleProtocolOperation.SubscribeNotify(securityUuid),
            BleProtocolOperation.WaitForProtocolReady(LELO_SECURITY_TIMEOUT_MS),
        )

    fun onNotify(characteristicUuid: Uuid, bytes: ByteArray): List<BleProtocolOperation> {
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

internal abstract class ButtplugVibrateProtocol(
    protected val match: ButtplugDeviceMatch,
) : BleDeviceProtocol {
    protected val txUuid: Uuid = Uuid.parse(match.endpoint.txUuid.orEmpty())
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

internal class ButtplugSimpleVibrateProtocol(
    private val match: ButtplugDeviceMatch,
    private val plan: SimpleVibrateProtocolPlan,
) : BleDeviceProtocol {
    private val txUuid: Uuid = Uuid.parse(match.endpoint.txUuid.orEmpty())
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
                add(BleProtocolOperation.SubscribeNotify(Uuid.parse(endpoint)))
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
                        characteristicUuid = Uuid.parse(endpoint),
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

internal class ButtplugStatefulVibrateProtocol(
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
                add(BleProtocolOperation.SubscribeNotify(Uuid.parse(endpoint)))
            }
            for (write in plan.initWrites) {
                val endpoint = requireNotNull(endpointByRole[write.endpointRole]) {
                    "${plan.protocolId} endpoint ${write.endpointRole} is missing"
                }
                add(
                    BleProtocolOperation.Write(
                        characteristicUuid = Uuid.parse(endpoint),
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
                characteristicUuid = Uuid.parse(endpoint),
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

internal class ButtplugScalarProtocol(
    private val match: ButtplugDeviceMatch,
    private val plan: ScalarProtocolPlan,
) : BleDeviceProtocol {
    private val endpointByRole = match.endpoint.characteristics
    private val txUuid: Uuid = Uuid.parse(match.endpoint.txUuid.orEmpty())
    private val scalarFeatures = match.scalarOutputs.sortedBy { it.featureIndex }
    private val state = plan.createState(match)
    private val endpointRoleByUuid = endpointByRole.entries.associate { Uuid.parse(it.value) to it.key }
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
                add(BleProtocolOperation.SubscribeNotify(Uuid.parse(endpoint)))
            }
            addAll(operationsForWrites(plan.initWrites))
            for (read in plan.initReads) {
                val endpoint = requireNotNull(endpointByRole[read.endpointRole]) {
                    "${plan.protocolId} endpoint ${read.endpointRole} is missing"
                }
                add(BleProtocolOperation.Read(Uuid.parse(endpoint)))
            }
        }

    override fun keepaliveIntervalMs(): Long = plan.keepaliveIntervalMs

    override fun keepaliveCommands(lastCommands: List<BleProtocolOperation>): List<BleProtocolOperation> =
        plan.keepaliveWritesFor(state)?.let(::operationsForWrites) ?: lastCommands

    override fun onRead(characteristicUuid: Uuid, bytes: ByteArray): List<BleProtocolOperation> {
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
                        characteristicUuid = Uuid.parse(endpoint),
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

internal class ButtplugLinearProtocol(
    private val match: ButtplugDeviceMatch,
    private val plan: LinearProtocolPlan,
) : BleDeviceProtocol {
    private val endpointByRole = match.endpoint.characteristics
    private val txUuid: Uuid = Uuid.parse(match.endpoint.txUuid.orEmpty())
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
                add(BleProtocolOperation.SubscribeNotify(Uuid.parse(endpoint)))
            }
            for (write in plan.initWrites) {
                val endpoint = requireNotNull(endpointByRole[write.endpointRole]) {
                    "${plan.protocolId} endpoint ${write.endpointRole} is missing"
                }
                add(
                    BleProtocolOperation.Write(
                        characteristicUuid = Uuid.parse(endpoint),
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
                characteristicUuid = Uuid.parse(endpoint),
                bytes = write.bytes,
                withResponse = write.withResponse,
            )
        }

    private companion object {
        const val DEFAULT_LINEAR_DURATION_MS = 1000
    }
}

internal class ButtplugMixedOutputProtocol(
    private val match: ButtplugDeviceMatch,
    private val plan: MixedOutputProtocolPlan,
) : BleDeviceProtocol {
    private val endpointByRole = match.endpoint.characteristics
    private val txUuid: Uuid = Uuid.parse(match.endpoint.txUuid.orEmpty())
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
                characteristicUuid = Uuid.parse(endpoint),
                bytes = write.bytes,
                withResponse = write.withResponse,
            )
        }

    private companion object {
        const val DEFAULT_LINEAR_DURATION_MS = 1000
    }
}

internal class ButtplugWeVibePackedProtocol(
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

internal class ButtplugWeVibe8BitProtocol(
    match: ButtplugDeviceMatch,
) : ButtplugVibrateProtocol(match) {
    override fun payloadFor(internalIntensity: Int, externalIntensity: Int): ByteArray =
        WeVibeProtocolPlans.eightBitPayload(channelCount, internalIntensity, externalIntensity)
}

internal class ButtplugWeVibeChorusProtocol(
    match: ButtplugDeviceMatch,
) : ButtplugVibrateProtocol(match) {
    override fun payloadFor(internalIntensity: Int, externalIntensity: Int): ByteArray =
        WeVibeProtocolPlans.chorusPayload(channelCount, internalIntensity, externalIntensity)
}

