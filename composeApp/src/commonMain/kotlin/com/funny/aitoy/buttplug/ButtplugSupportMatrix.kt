package com.funny.aitoy.buttplug

enum class ButtplugSupportStatus {
    Controllable,
    PartiallyControllable,
    RecognizedNoOutput,
    RecognizedMissingHandler,
    RecognizedUnsupportedTransport,
}

enum class ButtplugHandlerSource {
    ButtplugProtocolImpl,
    NativeSpecialized,
    ExternalButtplugTransport,
}

data class ButtplugLocalHandlerDescriptor(
    val protocolId: String,
    val source: ButtplugHandlerSource,
    val supportedOutputKinds: Set<ToyOutputKind>,
    val communicationTypes: Set<String> = setOf(COMMUNICATION_BTLE),
    val status: ButtplugSupportStatus,
    val notes: String = "",
)

data class ButtplugSupportEntry(
    val protocolId: String,
    val displayName: String,
    val communicationTypes: Set<String>,
    val outputKinds: Set<ToyOutputKind>,
    val status: ButtplugSupportStatus,
    val handler: ButtplugLocalHandlerDescriptor?,
) {
    val controllable: Boolean
        get() = status == ButtplugSupportStatus.Controllable ||
            status == ButtplugSupportStatus.PartiallyControllable
}

object ButtplugLocalHandlerRegistry {
    private val descriptors = listOf(
        ButtplugLocalHandlerDescriptor(
            protocolId = "wevibe",
            source = ButtplugHandlerSource.ButtplugProtocolImpl,
            supportedOutputKinds = setOf(ToyOutputKind.Vibrate),
            status = ButtplugSupportStatus.Controllable,
            notes = "We-Vibe 8 字节双强度控制帧",
        ),
        ButtplugLocalHandlerDescriptor(
            protocolId = "wevibe-8bit",
            source = ButtplugHandlerSource.ButtplugProtocolImpl,
            supportedOutputKinds = setOf(ToyOutputKind.Vibrate),
            status = ButtplugSupportStatus.Controllable,
        ),
        ButtplugLocalHandlerDescriptor(
            protocolId = "wevibe-chorus",
            source = ButtplugHandlerSource.ButtplugProtocolImpl,
            supportedOutputKinds = setOf(ToyOutputKind.Vibrate),
            status = ButtplugSupportStatus.Controllable,
        ),
        *SimpleVibrateProtocolPlans.protocolIds.map { protocolId ->
            ButtplugLocalHandlerDescriptor(
                protocolId = protocolId,
                source = ButtplugHandlerSource.ButtplugProtocolImpl,
                supportedOutputKinds = setOf(ToyOutputKind.Vibrate),
                status = ButtplugSupportStatus.Controllable,
                notes = "Simple vibrate handler ported from Buttplug protocol_impl",
            )
        }.toTypedArray(),
        *StatefulVibrateProtocolPlans.protocolIds.map { protocolId ->
            ButtplugLocalHandlerDescriptor(
                protocolId = protocolId,
                source = ButtplugHandlerSource.ButtplugProtocolImpl,
                supportedOutputKinds = setOf(ToyOutputKind.Vibrate),
                status = ButtplugSupportStatus.Controllable,
                notes = "Stateful vibrate handler ported from Buttplug protocol_impl",
            )
        }.toTypedArray(),
        *ScalarProtocolPlans.protocolIds.map { protocolId ->
            ButtplugLocalHandlerDescriptor(
                protocolId = protocolId,
                source = ButtplugHandlerSource.ButtplugProtocolImpl,
                supportedOutputKinds = ScalarProtocolPlans.supportedKinds(protocolId),
                status = ButtplugSupportStatus.Controllable,
                notes = "Scalar output handler ported from Buttplug protocol_impl",
            )
        }.toTypedArray(),
        *LinearProtocolPlans.protocolIds.map { protocolId ->
            ButtplugLocalHandlerDescriptor(
                protocolId = protocolId,
                source = ButtplugHandlerSource.ButtplugProtocolImpl,
                supportedOutputKinds = setOf(ToyOutputKind.Linear),
                status = ButtplugSupportStatus.Controllable,
                notes = "Linear output handler ported from Buttplug protocol_impl",
            )
        }.toTypedArray(),
        *MixedOutputProtocolPlans.protocolIds.map { protocolId ->
            ButtplugLocalHandlerDescriptor(
                protocolId = protocolId,
                source = ButtplugHandlerSource.ButtplugProtocolImpl,
                supportedOutputKinds = MixedOutputProtocolPlans.supportedKinds(protocolId),
                status = ButtplugSupportStatus.Controllable,
                notes = "Mixed scalar/linear output handler ported from Buttplug protocol_impl",
            )
        }.toTypedArray(),
        *LeloProtocolPlans.protocolIds.map { protocolId ->
            ButtplugLocalHandlerDescriptor(
                protocolId = protocolId,
                source = ButtplugHandlerSource.ButtplugProtocolImpl,
                supportedOutputKinds = LeloProtocolPlans.supportedKinds(protocolId),
                status = ButtplugSupportStatus.Controllable,
                notes = "Lelo security handshake and output handler ported from Buttplug protocol_impl",
            )
        }.toTypedArray(),
        *HoneyPlayBoxProtocolPlans.protocolIds.map { protocolId ->
            ButtplugLocalHandlerDescriptor(
                protocolId = protocolId,
                source = ButtplugHandlerSource.ButtplugProtocolImpl,
                supportedOutputKinds = HoneyPlayBoxProtocolPlans.supportedKinds,
                status = ButtplugSupportStatus.Controllable,
                notes = "HoneyPlayBox signed frame handler ported from Buttplug protocol_impl",
            )
        }.toTypedArray(),
        *VibCrafterProtocolPlans.protocolIds.map { protocolId ->
            ButtplugLocalHandlerDescriptor(
                protocolId = protocolId,
                source = ButtplugHandlerSource.ButtplugProtocolImpl,
                supportedOutputKinds = VibCrafterProtocolPlans.supportedKinds,
                status = ButtplugSupportStatus.Controllable,
                notes = "VibCrafter AES authentication handler ported from Buttplug protocol_impl",
            )
        }.toTypedArray(),
        *FlufferProtocolPlans.protocolIds.map { protocolId ->
            ButtplugLocalHandlerDescriptor(
                protocolId = protocolId,
                source = ButtplugHandlerSource.ButtplugProtocolImpl,
                supportedOutputKinds = FlufferProtocolPlans.supportedKinds,
                status = ButtplugSupportStatus.Controllable,
                notes = "Fluffer AES authentication and scalar handler ported from Buttplug protocol_impl",
            )
        }.toTypedArray(),
        *HandyProtocolPlans.protocolIds.map { protocolId ->
            ButtplugLocalHandlerDescriptor(
                protocolId = protocolId,
                source = ButtplugHandlerSource.ButtplugProtocolImpl,
                supportedOutputKinds = HandyProtocolPlans.supportedKinds,
                status = ButtplugSupportStatus.Controllable,
                notes = "The Handy protobuf handler ported from Buttplug protocol_impl",
            )
        }.toTypedArray(),
        *LegacyStpihkalProtocolPlans.protocolIds.map { protocolId ->
            ButtplugLocalHandlerDescriptor(
                protocolId = protocolId,
                source = ButtplugHandlerSource.ButtplugProtocolImpl,
                supportedOutputKinds = LegacyStpihkalProtocolPlans.supportedKinds,
                status = ButtplugSupportStatus.Controllable,
                notes = "Legacy STPIHKAL LevelCmd/LinearCmd handler ported from Buttplug device config",
            )
        }.toTypedArray(),
        ButtplugLocalHandlerDescriptor(
            protocolId = LovenseProtocolPlans.protocolId,
            source = ButtplugHandlerSource.ButtplugProtocolImpl,
            supportedOutputKinds = LovenseProtocolPlans.supportedKinds,
            status = ButtplugSupportStatus.Controllable,
            notes = "Lovense DeviceType, scalar, rotate, constrict and stroker handler ported from Buttplug protocol_impl",
        ),
        *SpecializedProtocolPlans.protocolIds.map { protocolId ->
            ButtplugLocalHandlerDescriptor(
                protocolId = protocolId,
                source = ButtplugHandlerSource.ButtplugProtocolImpl,
                supportedOutputKinds = SpecializedProtocolPlans.supportedKinds(protocolId),
                status = ButtplugSupportStatus.Controllable,
                notes = "Specialized handler ported from Buttplug protocol_impl",
            )
        }.toTypedArray(),
        *ExternalButtplugTransportPlans.protocolIds.map { protocolId ->
            ButtplugLocalHandlerDescriptor(
                protocolId = protocolId,
                source = ButtplugHandlerSource.ExternalButtplugTransport,
                supportedOutputKinds = ExternalButtplugTransportPlans.supportedKinds,
                communicationTypes = emptySet(),
                status = ButtplugSupportStatus.Controllable,
                notes = "Controlled through external Buttplug/Intiface transport",
            )
        }.toTypedArray(),
        ButtplugLocalHandlerDescriptor(
            protocolId = "ankni",
            source = ButtplugHandlerSource.NativeSpecialized,
            supportedOutputKinds = setOf(ToyOutputKind.Vibrate),
            status = ButtplugSupportStatus.Controllable,
            notes = "当前工程已有 Android 特化实现，覆盖 YAML 振动 output",
        ),
        ButtplugLocalHandlerDescriptor(
            protocolId = "pink_punch",
            source = ButtplugHandlerSource.NativeSpecialized,
            supportedOutputKinds = setOf(ToyOutputKind.Vibrate),
            status = ButtplugSupportStatus.Controllable,
        ),
        ButtplugLocalHandlerDescriptor(
            protocolId = "lovenuts",
            source = ButtplugHandlerSource.NativeSpecialized,
            supportedOutputKinds = setOf(ToyOutputKind.Vibrate),
            status = ButtplugSupportStatus.Controllable,
        ),
        ButtplugLocalHandlerDescriptor(
            protocolId = "mizzzee",
            source = ButtplugHandlerSource.NativeSpecialized,
            supportedOutputKinds = setOf(ToyOutputKind.Vibrate),
            status = ButtplugSupportStatus.Controllable,
        ),
        ButtplugLocalHandlerDescriptor(
            protocolId = "sensee",
            source = ButtplugHandlerSource.NativeSpecialized,
            supportedOutputKinds = setOf(ToyOutputKind.Vibrate),
            status = ButtplugSupportStatus.Controllable,
        ),
    ).associateBy { it.protocolId }

    val controllableButtplugHandlerIds: Set<String> = descriptors.values
        .filter { it.source == ButtplugHandlerSource.ButtplugProtocolImpl }
        .filter { it.status == ButtplugSupportStatus.Controllable }
        .map { it.protocolId }
        .toSet()

    fun descriptorFor(protocolId: String): ButtplugLocalHandlerDescriptor? =
        descriptors[protocolId]

    fun supportEntryFor(protocol: ButtplugProtocolDefinition): ButtplugSupportEntry {
        val descriptor = descriptorFor(protocol.id)
        val outputKinds = protocol.allOutputKinds()
        val status = descriptor?.status ?: when {
            outputKinds.isEmpty() && protocol.supportsCurrentAndroidTransport() -> ButtplugSupportStatus.RecognizedNoOutput
            protocol.supportsCurrentAndroidTransport() -> ButtplugSupportStatus.RecognizedMissingHandler
            else -> ButtplugSupportStatus.RecognizedUnsupportedTransport
        }
        return ButtplugSupportEntry(
            protocolId = protocol.id,
            displayName = protocol.displayName,
            communicationTypes = protocol.communicationTypes,
            outputKinds = outputKinds,
            status = status,
            handler = descriptor,
        )
    }
}

fun ButtplugProtocolDefinition.allOutputKinds(): Set<ToyOutputKind> =
    (listOf(defaultDevice) + configurations)
        .flatMap { it.features }
        .map { it.type.toToyOutputKind() }
        .toSet()

fun ButtplugProtocolDefinition.supportsCurrentAndroidTransport(): Boolean =
    communicationTypes.isEmpty() || COMMUNICATION_BTLE in communicationTypes

suspend fun ButtplugConfigRepository.loadSupportMatrix(): List<ButtplugSupportEntry> =
    loadAll().map(ButtplugLocalHandlerRegistry::supportEntryFor)
