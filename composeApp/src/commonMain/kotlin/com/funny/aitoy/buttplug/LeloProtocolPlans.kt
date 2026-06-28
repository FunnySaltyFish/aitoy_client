package com.funny.aitoy.buttplug

object LeloProtocolPlans {
    val protocolIds: Set<String> = setOf("lelo-harmony", "lelo-f1sv2")

    fun supportedKinds(protocolId: String): Set<ToyOutputKind> =
        when (protocolId) {
            "lelo-harmony" -> setOf(ToyOutputKind.Vibrate, ToyOutputKind.Rotate)
            "lelo-f1sv2" -> setOf(ToyOutputKind.Vibrate)
            else -> emptySet()
        }

    fun harmonyPayload(featureIndex: Int, speed: Int): ByteArray =
        byteArrayOf(
            0x0A,
            0x12,
            (featureIndex + 1).coerceIn(0, 0xFF).toByte(),
            0x08,
            0x00,
            0x00,
            0x00,
            0x00,
            speed.coerceIn(0, 0xFF).toByte(),
            0x00,
        )

    fun f1sPayload(internalSpeed: Int, externalSpeed: Int): ByteArray =
        byteArrayOf(
            0x01,
            internalSpeed.coerceIn(0, 0xFF).toByte(),
            externalSpeed.coerceIn(0, 0xFF).toByte(),
        )
}
