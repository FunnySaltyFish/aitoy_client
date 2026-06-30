package com.funny.aitoy.buttplug

object LegacyStpihkalProtocolPlans {
    val protocolIds: Set<String> = setOf("cueme", "kiiroo-v1", "muse", "sayberx")
    val supportedKinds: Set<ToyOutputKind> = setOf(ToyOutputKind.Vibrate, ToyOutputKind.Linear)
    const val museKeepaliveIntervalMs: Long = 500L

    fun cuemePayload(motorIndex: Int, speed: Int): ByteArray {
        val motor = motorIndex.coerceIn(0, 7) + 1
        val level = speed.coerceIn(0, 15)
        return byteArrayOf(((motor shl 4) or level).toByte())
    }

    fun kiirooV1Payload(value: Int): ByteArray =
        "${value.coerceIn(0, 4)},\n".encodeToByteArray()

    fun musePayload(speed: Int): ByteArray {
        val level = speed.coerceIn(0, 9)
        return byteArrayOf(level.toByte(), if (level == 0) 0x00 else 0x01)
    }

    fun sayberXPayload(speed: Int): ByteArray =
        "AT+SPD${speed.coerceIn(0, 4)}0".encodeToByteArray()
}
