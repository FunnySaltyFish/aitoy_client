package com.funny.aitoy.buttplug

enum class ButtplugSupportStatus {
    Controllable,
    PartiallyControllable,
    RecognizedMissingHandler,
    RecognizedUnsupportedTransport,
}

enum class ButtplugHandlerSource {
    ButtplugProtocolImpl,
    NativeSpecialized,
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
        ButtplugLocalHandlerDescriptor(
            protocolId = "ankni",
            source = ButtplugHandlerSource.NativeSpecialized,
            supportedOutputKinds = setOf(ToyOutputKind.Vibrate),
            status = ButtplugSupportStatus.PartiallyControllable,
            notes = "当前工程已有 Android 特化实现，后续仍需迁移为通用 Buttplug handler",
        ),
        ButtplugLocalHandlerDescriptor(
            protocolId = "lovense",
            source = ButtplugHandlerSource.NativeSpecialized,
            supportedOutputKinds = setOf(ToyOutputKind.Vibrate),
            status = ButtplugSupportStatus.PartiallyControllable,
            notes = "当前实现覆盖基础控制，不等价于 Lovense 全 output 能力",
        ),
        ButtplugLocalHandlerDescriptor(
            protocolId = "satisfyer",
            source = ButtplugHandlerSource.NativeSpecialized,
            supportedOutputKinds = setOf(ToyOutputKind.Vibrate),
            status = ButtplugSupportStatus.PartiallyControllable,
        ),
        ButtplugLocalHandlerDescriptor(
            protocolId = "pink_punch",
            source = ButtplugHandlerSource.NativeSpecialized,
            supportedOutputKinds = setOf(ToyOutputKind.Vibrate),
            status = ButtplugSupportStatus.PartiallyControllable,
        ),
        ButtplugLocalHandlerDescriptor(
            protocolId = "lovenuts",
            source = ButtplugHandlerSource.NativeSpecialized,
            supportedOutputKinds = setOf(ToyOutputKind.Vibrate),
            status = ButtplugSupportStatus.PartiallyControllable,
        ),
        ButtplugLocalHandlerDescriptor(
            protocolId = "jejoue",
            source = ButtplugHandlerSource.NativeSpecialized,
            supportedOutputKinds = setOf(ToyOutputKind.Vibrate),
            status = ButtplugSupportStatus.PartiallyControllable,
        ),
        ButtplugLocalHandlerDescriptor(
            protocolId = "monsterpub",
            source = ButtplugHandlerSource.NativeSpecialized,
            supportedOutputKinds = setOf(ToyOutputKind.Vibrate),
            status = ButtplugSupportStatus.PartiallyControllable,
            notes = "当前工程按 SISTALK Hub 特化路线控制",
        ),
        ButtplugLocalHandlerDescriptor(
            protocolId = "cachito",
            source = ButtplugHandlerSource.NativeSpecialized,
            supportedOutputKinds = setOf(ToyOutputKind.Vibrate),
            status = ButtplugSupportStatus.PartiallyControllable,
            notes = "当前工程按 Cachito 广播特化路线控制",
        ),
        ButtplugLocalHandlerDescriptor(
            protocolId = "mizzzee",
            source = ButtplugHandlerSource.NativeSpecialized,
            supportedOutputKinds = setOf(ToyOutputKind.Vibrate),
            status = ButtplugSupportStatus.PartiallyControllable,
        ),
        ButtplugLocalHandlerDescriptor(
            protocolId = "sensee",
            source = ButtplugHandlerSource.NativeSpecialized,
            supportedOutputKinds = setOf(ToyOutputKind.Vibrate),
            status = ButtplugSupportStatus.PartiallyControllable,
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
