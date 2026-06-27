package com.funny.aitoy.buttplug

object WeVibeProtocolHandlers {
    val legacy: ToyProtocolHandler = WeVibeHandler(
        protocolId = "wevibe",
        profile = WeVibeProfile.Legacy,
    )
    val eightBit: ToyProtocolHandler = WeVibeHandler(
        protocolId = "wevibe-8bit",
        profile = WeVibeProfile.EightBit,
    )
    val chorus: ToyProtocolHandler = WeVibeHandler(
        protocolId = "wevibe-chorus",
        profile = WeVibeProfile.Chorus,
    )
    val packedPattern: ToyProtocolHandler = WeVibeHandler(
        protocolId = "wevibe-packed-pattern",
        profile = WeVibeProfile.PackedPattern,
    )

    fun forProtocolId(protocolId: String): ToyProtocolHandler? =
        when (protocolId) {
            legacy.protocolId -> legacy
            eightBit.protocolId -> eightBit
            chorus.protocolId -> chorus
            packedPattern.protocolId -> packedPattern
            else -> null
        }
}

private enum class WeVibeProfile {
    Legacy,
    EightBit,
    Chorus,
    PackedPattern,
}

private class WeVibeHandler(
    override val protocolId: String,
    private val profile: WeVibeProfile,
) : ToyProtocolHandler {
    override val supportedOutputKinds: Set<ToyOutputKind> = setOf(ToyOutputKind.Vibrate)

    override fun createSession(match: ButtplugDeviceMatch, transport: ToyTransport): ToyProtocolSession =
        WeVibeSession(
            match = match,
            transport = transport,
            profile = profile,
        )
}

private class WeVibeSession(
    match: ButtplugDeviceMatch,
    private val transport: ToyTransport,
    private val profile: WeVibeProfile,
) : ToyProtocolSession {
    private val endpoint = requireNotNull(match.endpoint.txUuid) { "We-Vibe tx endpoint is missing" }
    private val channelCount = match.vibrateOutputs.size.coerceAtLeast(1).coerceAtMost(2)
    private val intensities = IntArray(2)
    private var currentPattern = 1

    override val supportEntry: ButtplugSupportEntry =
        ButtplugLocalHandlerRegistry.supportEntryFor(match.protocol)

    override suspend fun initialize() {
        if (profile == WeVibeProfile.Legacy) {
            WeVibeProtocolPlans.legacyInitializePayloads().forEach { payload ->
                write(payload)
            }
        }
    }

    override suspend fun handle(command: ToyOutputCommand) {
        when (command) {
            is ToyOutputCommand.Scalar -> {
                require(command.kind == ToyOutputKind.Vibrate) { "We-Vibe only supports vibrate scalar output" }
                setIntensity(command.featureIndex, command.value)
                writePayloadForCurrentState()
            }
            is ToyOutputCommand.Pattern -> {
                require(profile == WeVibeProfile.PackedPattern) { "This We-Vibe profile does not support pattern output" }
                currentPattern = command.patternIndex.coerceIn(1, WeVibeProtocolPlans.packedPatternCodes.size)
                command.values.forEach { (featureIndex, value) -> setIntensity(featureIndex, value) }
                writePayloadForCurrentState()
            }
            is ToyOutputCommand.Linear -> error("We-Vibe does not support linear output")
            ToyOutputCommand.Stop -> stop()
        }
    }

    override suspend fun stop() {
        intensities.fill(0)
        write(WeVibeProtocolPlans.stopPayload())
    }

    private fun setIntensity(featureIndex: Int, value: Int) {
        val index = featureIndex.coerceIn(0, channelCount - 1)
        val next = value.coerceIn(0, maxIntensity())
        intensities[index] = next
        if (channelCount == 1) {
            intensities[1] = next
        }
    }

    private fun maxIntensity(): Int =
        when (profile) {
            WeVibeProfile.Legacy,
            WeVibeProfile.PackedPattern -> 0x0F
            WeVibeProfile.EightBit -> 0xFC
            WeVibeProfile.Chorus -> 0xFF
        }

    private suspend fun writePayloadForCurrentState() {
        val payload = when (profile) {
            WeVibeProfile.Legacy -> WeVibeProtocolPlans.legacyPayload(
                numVibrators = channelCount,
                internalIntensity = intensities[0],
                externalIntensity = intensities[1],
            )
            WeVibeProfile.EightBit -> WeVibeProtocolPlans.eightBitPayload(
                numVibrators = channelCount,
                internalIntensity = intensities[0],
                externalIntensity = intensities[1],
            )
            WeVibeProfile.Chorus -> WeVibeProtocolPlans.chorusPayload(
                numVibrators = channelCount,
                internalIntensity = intensities[0],
                externalIntensity = intensities[1],
            )
            WeVibeProfile.PackedPattern -> WeVibeProtocolPlans.packedPatternPayload(
                mode = currentPattern,
                internalIntensity = intensities[0],
                externalIntensity = intensities[1],
            )
        }
        write(payload)
    }

    private suspend fun write(payload: ByteArray) {
        when (val result = transport.execute(HardwareOperation.Write(endpoint, payload, withResponse = true))) {
            HardwareResult.Success,
            is HardwareResult.ReadResult -> Unit
            is HardwareResult.Failure -> error(result.message)
        }
    }
}
