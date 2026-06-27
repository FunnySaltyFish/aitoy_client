package com.funny.aitoy.buttplug

data class SimpleVibrateInitWrite(
    val endpointRole: String = "tx",
    val bytes: ByteArray,
    val withResponse: Boolean,
)

data class SimpleVibrateProtocolPlan(
    val protocolId: String,
    val withResponse: Boolean,
    val initSubscriptions: List<String> = emptyList(),
    val initPayloads: List<ByteArray> = emptyList(),
    val initWrites: List<SimpleVibrateInitWrite> = emptyList(),
    val initDelayMs: Long = 0L,
    val keepaliveIntervalMs: Long = 0L,
    val payload: (featureIndex: Int, speed: Int) -> ByteArray,
) {
    var devicePayload: ((deviceName: String, featureIndex: Int, speed: Int) -> ByteArray)? = null

    fun payloadFor(deviceName: String, featureIndex: Int, speed: Int): ByteArray =
        devicePayload?.invoke(deviceName, featureIndex, speed) ?: payload(featureIndex, speed)
}

object SimpleVibrateProtocolPlans {
    private val plans = listOf(
        SimpleVibrateProtocolPlan(
            protocolId = "activejoy",
            withResponse = false,
            payload = { featureIndex, speed ->
                bytes(0xB0, 0x01, 0x00, featureIndex, if (speed == 0) 0x00 else 0x01, speed)
            },
        ),
        SimpleVibrateProtocolPlan(
            protocolId = "adrienlastic",
            withResponse = true,
            payload = { _, speed -> "MotorValue:${speed.coerceIn(0, 99).toString().padStart(2, '0')};".encodeToByteArray() },
        ),
        SimpleVibrateProtocolPlan(
            protocolId = "amorelie-joy",
            withResponse = false,
            initPayloads = listOf(bytes(0x03)),
            payload = { _, speed -> bytes(0x01, 0x01, speed) },
        ),
        SimpleVibrateProtocolPlan(
            protocolId = "aneros",
            withResponse = false,
            payload = { featureIndex, speed -> bytes(0xF1 + featureIndex, speed) },
        ),
        SimpleVibrateProtocolPlan(
            protocolId = "cupido",
            withResponse = false,
            payload = { _, speed -> bytes(0xB0, 0x03, 0x00, 0x00, 0x00, speed, 0xAA) },
        ),
        SimpleVibrateProtocolPlan(
            protocolId = "cowgirl-cone",
            withResponse = false,
            initPayloads = listOf(bytes(0xAA, 0x56, 0x00, 0x00)),
            initDelayMs = 3000,
            payload = { _, speed -> bytes(0xF1, 0x01, speed, 0x00) },
        ),
        SimpleVibrateProtocolPlan(
            protocolId = "deepsire",
            withResponse = false,
            payload = { _, speed -> bytes(0x55, 0x04, 0x01, 0x00, 0x00, speed, 0xAA) },
        ),
        SimpleVibrateProtocolPlan(
            protocolId = "fox",
            withResponse = false,
            payload = { _, speed -> bytes(0x03, 0x01, 0x01, 0xFE, speed) },
        ),
        SimpleVibrateProtocolPlan(
            protocolId = "foreo",
            withResponse = true,
            payload = { _, speed -> bytes(0x01, 0x00, speed) },
        ).apply {
            devicePayload = { deviceName, _, speed -> bytes(0x01, foreoMode(deviceName), speed) }
        },
        SimpleVibrateProtocolPlan(
            protocolId = "hgod",
            withResponse = false,
            keepaliveIntervalMs = 100,
            payload = { _, speed -> bytes(0x55, 0x04, 0x00, 0x00, 0x00, speed) },
        ),
        SimpleVibrateProtocolPlan(
            protocolId = "kiiroo-prowand",
            withResponse = false,
            payload = { _, speed -> bytes(0x00, 0x00, 0x64, if (speed == 0) 0x00 else 0xFF, speed, speed) },
        ),
        SimpleVibrateProtocolPlan(
            protocolId = "kiiroo-spot",
            withResponse = false,
            payload = { _, speed -> bytes(0x00, 0xFF, 0x00, 0x00, 0x00, speed) },
        ),
        SimpleVibrateProtocolPlan(
            protocolId = "lovedistance",
            withResponse = false,
            initPayloads = listOf(bytes(0xF3, 0x00, 0x00), bytes(0xF4, 0x01)),
            payload = { _, speed -> bytes(0xF3, 0x00, speed) },
        ),
        SimpleVibrateProtocolPlan(
            protocolId = "leten",
            withResponse = true,
            initPayloads = listOf(bytes(0x04, 0x01)),
            keepaliveIntervalMs = 1000,
            payload = { _, speed -> bytes(0x02, speed) },
        ),
        SimpleVibrateProtocolPlan(
            protocolId = "lioness",
            withResponse = false,
            initSubscriptions = listOf("rx"),
            initWrites = listOf(initWrite(bytes(0x01, 0xAA, 0xAA, 0xBB, 0xCC, 0x10), withResponse = true)),
            payload = { _, speed -> bytes(0x02, 0xAA, 0xBB, 0xCC, 0xCC, speed) },
        ),
        SimpleVibrateProtocolPlan(
            protocolId = "magic-motion-3",
            withResponse = false,
            payload = { _, speed -> bytes(0x0B, 0xFF, 0x04, 0x0A, 0x46, 0x46, 0x00, 0x04, 0x08, speed, 0x64, 0x00) },
        ),
        SimpleVibrateProtocolPlan(
            protocolId = "mannuo",
            withResponse = true,
            payload = { _, speed -> xorChecksum(bytes(0xAA, 0x55, 0x06, 0x01, 0x01, 0x01, speed, 0xFA)) },
        ),
        SimpleVibrateProtocolPlan(
            protocolId = "maxpro",
            withResponse = false,
            payload = { _, speed -> maxproPayload(speed) },
        ),
        SimpleVibrateProtocolPlan(
            protocolId = "meese",
            withResponse = true,
            payload = { featureIndex, speed -> bytes(0x01, 0x80, 0x01 + featureIndex, speed) },
        ),
        SimpleVibrateProtocolPlan(
            protocolId = "mizzzee-v2",
            withResponse = false,
            payload = { _, speed -> bytes(0x69, 0x96, 0x04, 0x02, speed, 0x2C, speed) },
        ),
        SimpleVibrateProtocolPlan(
            protocolId = "mizzzee-v3",
            withResponse = true,
            keepaliveIntervalMs = 200,
            payload = { _, speed -> mizzzeeV3Payload(speed) },
        ),
        SimpleVibrateProtocolPlan(
            protocolId = "mymuselinkplus",
            withResponse = false,
            payload = { _, speed ->
                if (speed == 0) {
                    bytes(0xAA, 0x55, 0x06, 0xAA, 0x00, 0x00, 0x00, 0x00)
                } else {
                    bytes(0xAA, 0x55, 0x06, 0x01, 0x01, 0x01, speed, 0xFF)
                }
            },
        ),
        SimpleVibrateProtocolPlan(
            protocolId = "nobra",
            withResponse = false,
            initPayloads = listOf(bytes(0x70)),
            payload = { _, speed -> bytes(if (speed == 0) 0x70 else 0x60 + speed) },
        ),
        SimpleVibrateProtocolPlan(
            protocolId = "omobo",
            withResponse = true,
            payload = { _, speed -> bytes(0xA1, 0x04, 0x04, 0x01, speed, 0xFF, 0x55) },
        ),
        SimpleVibrateProtocolPlan(
            protocolId = "picobong",
            withResponse = false,
            payload = { _, speed -> bytes(0x01, if (speed == 0) 0xFF else 0x01, speed) },
        ),
        SimpleVibrateProtocolPlan(
            protocolId = "prettylove",
            withResponse = true,
            payload = { _, speed -> bytes(0x00, speed) },
        ),
        SimpleVibrateProtocolPlan(
            protocolId = "realov",
            withResponse = false,
            payload = { _, speed -> bytes(0xC5, 0x55, speed, 0xAA) },
        ),
        SimpleVibrateProtocolPlan(
            protocolId = "sexverse-v4",
            withResponse = true,
            payload = { _, speed -> bytes(0xBB, 0x01, speed, 0x66) },
        ),
        SimpleVibrateProtocolPlan(
            protocolId = "sexverse-v5",
            withResponse = false,
            payload = { _, speed -> bytes(0xAA, 0x03, 0x03, speed, 0x00, 0x00, 0x00) },
        ),
        SimpleVibrateProtocolPlan(
            protocolId = "svakom-alex",
            withResponse = false,
            payload = { _, speed -> bytes(0x12, 0x01, 0x03, 0x00, if (speed == 0) 0xFF else speed, 0x00) },
        ),
        SimpleVibrateProtocolPlan(
            protocolId = "svakom-alex-v2",
            withResponse = false,
            payload = { _, speed -> bytes(0x55, 0x03, 0x03, 0x00, speed, speed + 5) },
        ),
        SimpleVibrateProtocolPlan(
            protocolId = "svakom-dice",
            withResponse = false,
            payload = { _, speed -> bytes(0x55, 0x04, 0x00, 0x00, 0x01, speed, 0xAA) },
        ),
        SimpleVibrateProtocolPlan(
            protocolId = "svakom-pulse",
            withResponse = false,
            payload = { _, speed -> bytes(0x55, 0x03, 0x03, 0x00, if (speed == 0) 0x00 else 0x01, speed + 1) },
        ),
        SimpleVibrateProtocolPlan(
            protocolId = "svakom-suitcase",
            withResponse = false,
            payload = { featureIndex, speed -> svakomSuitcasePayload(featureIndex, speed) },
        ),
        SimpleVibrateProtocolPlan(
            protocolId = "svakom-tarax",
            withResponse = false,
            payload = { featureIndex, speed -> svakomTaraxPayload(featureIndex, speed) },
        ),
        SimpleVibrateProtocolPlan(
            protocolId = "svakom-v1",
            withResponse = false,
            payload = { _, speed -> bytes(0x55, 0x04, 0x03, 0x00, if (speed == 0) 0x00 else 0x01, speed) },
        ),
        SimpleVibrateProtocolPlan(
            protocolId = "svakom-v2",
            withResponse = true,
            payload = { featureIndex, speed ->
                if (featureIndex == 1) {
                    bytes(0x55, 0x06, 0x01, 0x00, speed, speed)
                } else {
                    bytes(0x55, 0x03, 0x03, 0x00, if (speed == 0) 0x00 else 0x01, speed)
                }
            },
        ),
        SimpleVibrateProtocolPlan(
            protocolId = "wetoy",
            withResponse = true,
            initPayloads = listOf(bytes(0x80, 0x03)),
            payload = { _, speed ->
                if (speed == 0) {
                    bytes(0x80, 0x03)
                } else {
                    bytes(0xB2, speed - 1)
                }
            },
        ),
        SimpleVibrateProtocolPlan(
            protocolId = "xiuxiuda",
            withResponse = false,
            payload = { _, speed -> bytes(0x00, 0x00, 0x00, 0x00, 0x65, 0x3A, 0x30, speed, 0x64) },
        ),
        SimpleVibrateProtocolPlan(
            protocolId = "xuanhuan",
            withResponse = true,
            keepaliveIntervalMs = 300,
            payload = { _, speed -> bytes(0x03, 0x02, 0x00, speed) },
        ),
        SimpleVibrateProtocolPlan(
            protocolId = "youcups",
            withResponse = false,
            payload = { _, speed -> "\$SYS,$speed?".encodeToByteArray() },
        ),
    ).associateBy { it.protocolId }

    val protocolIds: Set<String> = plans.keys

    fun forProtocolId(protocolId: String): SimpleVibrateProtocolPlan? =
        plans[protocolId]

    private fun initWrite(
        bytes: ByteArray,
        endpointRole: String = "tx",
        withResponse: Boolean,
    ): SimpleVibrateInitWrite =
        SimpleVibrateInitWrite(endpointRole = endpointRole, bytes = bytes, withResponse = withResponse)

    private fun bytes(vararg values: Int): ByteArray =
        ByteArray(values.size) { index -> values[index].coerceIn(0, 0xFF).toByte() }

    private fun xorChecksum(prefix: ByteArray): ByteArray {
        var crc = 0
        prefix.forEach { crc = crc xor (it.toInt() and 0xFF) }
        return prefix + crc.toByte()
    }

    private fun maxproPayload(speed: Int): ByteArray {
        val data = bytes(0x55, 0x04, 0x07, 0xFF, 0xFF, 0x3F, speed, 0x5F, speed, 0x00)
        val checksum = data.dropLast(1).fold(0) { acc, byte -> (acc + (byte.toInt() and 0xFF)) and 0xFF }
        data[data.lastIndex] = checksum.toByte()
        return data
    }

    private fun mizzzeeV3Payload(speed: Int): ByteArray {
        if (speed == 0) {
            return bytes(
                0x03, 0x12, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00, 0x00,
            )
        }
        val scaled = ((speed / 1000.0f) * 0.7f + 0.3f) * 1023.0f
        val value = ((scaled.toInt() shl 6) or 60).coerceIn(0, 0xFFFF)
        val low = value and 0xFF
        val high = (value shr 8) and 0xFF
        return bytes(
            0x03, 0x12, 0xF3,
            0x00, 0xFC, 0x00, 0xFE, 0x40, 0x01,
            low, high,
            0x00, 0xFC, 0x00, 0xFE, 0x40, 0x01,
            low, high,
            0x00,
        )
    }

    private fun foreoMode(deviceName: String): Int {
        val name = deviceName.lowercase()
        return when {
            "smart" in name && "2" in name -> 3
            "fofo" in name || "ufo" in name -> 1
            else -> 0
        }
    }

    private fun svakomSuitcasePayload(featureIndex: Int, speed: Int): ByteArray {
        if (featureIndex != 0) {
            return bytes(0x55, 0x09, 0x00, 0x00, speed, 0x00)
        }
        var pulseSpeed = speed % 10
        var intensity = if (speed == 0) 0 else speed / 10 + 1
        if (pulseSpeed == 0 && intensity != 0) {
            pulseSpeed = 10
            intensity -= 1
        }
        return bytes(0x55, 0x03, 0x00, 0x00, intensity, pulseSpeed)
    }

    private fun svakomTaraxPayload(featureIndex: Int, speed: Int): ByteArray =
        if (featureIndex == 0) {
            bytes(
                0x55,
                0x03,
                0x00,
                0x00,
                if (speed == 0) 0x01 else speed,
                if (speed == 0) 0x01 else 0x02,
            )
        } else {
            bytes(0x55, 0x09, 0x00, 0x00, speed, 0x00)
        }
}

object SimpleVibrateProtocolHandlers {
    fun forProtocolId(protocolId: String): ToyProtocolHandler? =
        SimpleVibrateProtocolPlans.forProtocolId(protocolId)?.let(::SimpleVibrateHandler)
}

private class SimpleVibrateHandler(
    private val plan: SimpleVibrateProtocolPlan,
) : ToyProtocolHandler {
    override val protocolId: String = plan.protocolId
    override val supportedOutputKinds: Set<ToyOutputKind> = setOf(ToyOutputKind.Vibrate)

    override fun createSession(match: ButtplugDeviceMatch, transport: ToyTransport): ToyProtocolSession =
        SimpleVibrateSession(match, transport, plan)
}

private class SimpleVibrateSession(
    match: ButtplugDeviceMatch,
    private val transport: ToyTransport,
    private val plan: SimpleVibrateProtocolPlan,
) : ToyProtocolSession {
    private val endpoint = requireNotNull(match.endpoint.txUuid) { "${plan.protocolId} tx endpoint is missing" }
    private val deviceName = match.device.name
    private val endpointByRole = match.endpoint.characteristics
    private val channelCount = match.vibrateOutputs.size.coerceAtLeast(1)
    private val maxByFeature = match.vibrateOutputs.associate { it.featureIndex to it.max.coerceAtLeast(1) }

    override val supportEntry: ButtplugSupportEntry =
        ButtplugLocalHandlerRegistry.supportEntryFor(match.protocol)

    override suspend fun initialize() {
        for (endpointRole in plan.initSubscriptions) {
            val endpoint = requireNotNull(endpointByRole[endpointRole]) {
                "${plan.protocolId} endpoint $endpointRole is missing"
            }
            execute(HardwareOperation.Subscribe(endpoint))
        }
        for (payload in plan.initPayloads) {
            execute(HardwareOperation.Write(endpoint, payload, plan.withResponse))
        }
        for (write in plan.initWrites) {
            val endpoint = requireNotNull(endpointByRole[write.endpointRole]) {
                "${plan.protocolId} endpoint ${write.endpointRole} is missing"
            }
            execute(HardwareOperation.Write(endpoint, write.bytes, write.withResponse))
        }
        if (plan.initDelayMs > 0) {
            execute(HardwareOperation.Sleep(plan.initDelayMs))
        }
    }

    override suspend fun handle(command: ToyOutputCommand) {
        when (command) {
            is ToyOutputCommand.Scalar -> {
                require(command.kind == ToyOutputKind.Vibrate) { "${plan.protocolId} only supports vibrate scalar output" }
                write(command.featureIndex, command.value)
            }
            is ToyOutputCommand.Pattern -> {
                val speed = command.values.values.maxOrNull() ?: maxByFeature.values.maxOrNull() ?: 1
                repeat(channelCount) { index -> write(index, speed) }
            }
            is ToyOutputCommand.Linear -> error("${plan.protocolId} does not support linear output")
            ToyOutputCommand.Stop -> stop()
        }
    }

    override suspend fun stop() {
        repeat(channelCount) { index -> write(index, 0) }
    }

    private suspend fun write(featureIndex: Int, speed: Int) {
        val max = maxByFeature[featureIndex] ?: maxByFeature.values.firstOrNull() ?: 1
        val payload = plan.payloadFor(deviceName, featureIndex, speed.coerceIn(0, max))
        execute(HardwareOperation.Write(endpoint, payload, plan.withResponse))
    }

    private suspend fun execute(operation: HardwareOperation) {
        when (val result = transport.execute(operation)) {
            HardwareResult.Success,
            is HardwareResult.ReadResult -> Unit
            is HardwareResult.Failure -> error(result.message)
        }
    }
}
