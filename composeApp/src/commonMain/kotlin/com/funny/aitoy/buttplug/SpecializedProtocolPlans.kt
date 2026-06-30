package com.funny.aitoy.buttplug

object SpecializedProtocolPlans {
    val protocolIds: Set<String> = setOf("cachito", "jejoue", "satisfyer", "monsterpub")
    const val satisfyerKeepaliveIntervalMs: Long = 3_000L

    fun supportedKinds(protocolId: String): Set<ToyOutputKind> =
        when (protocolId) {
            "monsterpub" -> setOf(ToyOutputKind.Vibrate, ToyOutputKind.Oscillate)
            else -> setOf(ToyOutputKind.Vibrate)
        }

    fun cachitoPayload(featureIndex: Int, speed: Int): ByteArray =
        byteArrayOf(
            (2 + featureIndex.coerceIn(0, 1)).toByte(),
            (1 + featureIndex.coerceIn(0, 1)).toByte(),
            speed.coerceIn(0, 100).toByte(),
            0x00,
        )

    fun jejouePayload(firstSpeed: Int, secondSpeed: Int): ByteArray {
        var pattern = 1
        var speed = firstSpeed.coerceIn(0, 5)
        if (speed == 0) {
            speed = secondSpeed.coerceIn(0, 5)
            if (speed != 0) pattern = 3
        }
        if (pattern == 1 && speed != 0 && secondSpeed.coerceIn(0, 5) == 0) {
            pattern = 2
        }
        return byteArrayOf(pattern.toByte(), speed.toByte())
    }

    fun satisfyerInitPayload(): ByteArray =
        byteArrayOf(0x01)

    fun satisfyerPayload(speeds: IntArray, featureCount: Int): ByteArray {
        val output = ByteArray(featureCount.coerceAtLeast(1) * 4)
        repeat(featureCount.coerceAtLeast(1)) { featureIndex ->
            val speed = (speeds.getOrNull(featureIndex) ?: 0).coerceIn(0, 100).toByte()
            repeat(4) { offset -> output[featureIndex * 4 + offset] = speed }
        }
        return output
    }

    fun monsterPubEndpointRole(availableRoles: Set<String>, speeds: IntArray): String {
        val baseRole = when {
            "txvibrate" in availableRoles -> "txvibrate"
            "tx" in availableRoles -> "tx"
            "generic0" in availableRoles -> "generic0"
            else -> "tx"
        }
        return if (baseRole == "tx" && speeds.all { it == 0 } && "txmode" in availableRoles) {
            "txmode"
        } else {
            baseRole
        }
    }

    fun monsterPubPayload(endpointRole: String, speeds: IntArray, featureCount: Int): ByteArray {
        val count = featureCount.coerceAtLeast(1)
        val prefixSize = if (endpointRole == "generic0") 1 else 0
        val output = ByteArray(prefixSize + count)
        if (endpointRole == "generic0") output[0] = 0x03
        repeat(count) { featureIndex ->
            output[prefixSize + featureIndex] = (speeds.getOrNull(featureIndex) ?: 0).coerceIn(0, 100).toByte()
        }
        return output
    }
}
