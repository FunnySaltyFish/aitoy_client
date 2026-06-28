package com.funny.aitoy.buttplug

object HandyProtocolPlans {
    const val handyProtocolId: String = "thehandy"
    const val handyV3ProtocolId: String = "thehandy-v3"
    val protocolIds: Set<String> = setOf("thehandy", "thehandy-v3")
    val supportedKinds: Set<ToyOutputKind> = setOf(ToyOutputKind.Linear, ToyOutputKind.Vibrate)
    const val keepaliveIntervalMs: Long = 1_000L

    fun handyPingPayload(id: Int = 999): ByteArray {
        val ping = ProtoWriter().uint32(1, id).toByteArray()
        val message = ProtoWriter().message(102, ping).toByteArray()
        return ProtoWriter().message(1, message).toByteArray()
    }

    fun handyLinearPayload(position: Int, durationMs: Int): ByteArray {
        val vector = ProtoWriter()
            .uint32(1, 0)
            .uint32(2, durationMs.coerceAtLeast(0))
            .double(3, position.coerceIn(0, 100).toDouble() / 100.0)
            .toByteArray()
        val linear = ProtoWriter()
            .uint32(1, 2)
            .uint32(2, 0)
            .message(3, vector)
            .toByteArray()
        val message = ProtoWriter().message(403, linear).toByteArray()
        return ProtoWriter().message(1, message).toByteArray()
    }

    fun handyV3BatteryRequestPayload(id: Int = 1): ByteArray =
        handyV3Request(id, requestTag = 710, requestPayload = byteArrayOf())

    fun handyV3CapabilitiesRequestPayload(id: Int): ByteArray =
        handyV3Request(id, requestTag = 713, requestPayload = byteArrayOf())

    fun handyV3KeepalivePayload(id: Int): ByteArray =
        handyV3RequestBody(id, requestTag = 713, requestPayload = byteArrayOf())

    fun handyV3LinearPayload(id: Int, position: Int, durationMs: Int): ByteArray {
        val requestPayload = ProtoWriter()
            .float(1, position.coerceIn(0, 100).toFloat() / 100f)
            .uint32(2, durationMs.coerceAtLeast(0))
            .bool(3, true)
            .toByteArray()
        return handyV3Request(id, requestTag = 744, requestPayload = requestPayload)
    }

    fun handyV3VibratePayload(id: Int, speed: Int): ByteArray {
        val coerced = speed.coerceIn(0, 100)
        if (coerced == 0) {
            return handyV3Request(id, requestTag = 901, requestPayload = byteArrayOf())
        }
        val requestPayload = ProtoWriter()
            .float(1, coerced.toFloat() / 100f)
            .uint32(2, 100)
            .float(3, 0f)
            .toByteArray()
        return handyV3Request(id, requestTag = 900, requestPayload = requestPayload)
    }

    private fun handyV3Request(id: Int, requestTag: Int, requestPayload: ByteArray): ByteArray {
        return ProtoWriter()
            .uint32(1, 1)
            .message(2, handyV3RequestBody(id, requestTag, requestPayload))
            .toByteArray()
    }

    private fun handyV3RequestBody(id: Int, requestTag: Int, requestPayload: ByteArray): ByteArray =
        ProtoWriter()
            .uint32(2, id.coerceAtLeast(0))
            .message(requestTag, requestPayload)
            .toByteArray()
}

private class ProtoWriter {
    private val bytes = mutableListOf<Byte>()

    fun uint32(field: Int, value: Int): ProtoWriter {
        if (value == 0) return this
        tag(field, 0)
        varint(value)
        return this
    }

    fun bool(field: Int, value: Boolean): ProtoWriter {
        if (!value) return this
        tag(field, 0)
        varint(1)
        return this
    }

    fun message(field: Int, payload: ByteArray): ProtoWriter {
        tag(field, 2)
        varint(payload.size)
        bytes += payload.toList()
        return this
    }

    fun float(field: Int, value: Float): ProtoWriter {
        if (value == 0f) return this
        tag(field, 5)
        fixed32(value.toRawBits())
        return this
    }

    fun double(field: Int, value: Double): ProtoWriter {
        if (value == 0.0) return this
        tag(field, 1)
        fixed64(value.toRawBits())
        return this
    }

    fun toByteArray(): ByteArray =
        bytes.toByteArray()

    private fun tag(field: Int, wireType: Int) {
        varint((field shl 3) or wireType)
    }

    private fun varint(value: Int) {
        var current = value
        while (true) {
            val next = current and 0x7F
            current = current ushr 7
            if (current == 0) {
                bytes += next.toByte()
                return
            }
            bytes += (next or 0x80).toByte()
        }
    }

    private fun fixed32(value: Int) {
        repeat(4) { index -> bytes += ((value ushr (index * 8)) and 0xFF).toByte() }
    }

    private fun fixed64(value: Long) {
        repeat(8) { index -> bytes += ((value ushr (index * 8)) and 0xFF).toByte() }
    }
}
