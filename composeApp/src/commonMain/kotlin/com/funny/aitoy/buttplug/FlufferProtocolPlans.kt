package com.funny.aitoy.buttplug

import kotlin.random.Random

object FlufferProtocolPlans {
    const val protocolId: String = "fluffer"
    val protocolIds: Set<String> = setOf("fluffer")
    val supportedKinds: Set<ToyOutputKind> = setOf(ToyOutputKind.Vibrate, ToyOutputKind.Rotate, ToyOutputKind.Oscillate)
    const val manufacturerCompanyId: Int = 26698
    const val handshakeTimeoutMs: Long = 10_000L
    const val keepaliveIntervalMs: Long = 1_000L

    fun randomNonce(): ByteArray =
        Random.nextBytes(4)

    fun advertisementSeed(advertisementBytes: ByteArray): ByteArray {
        if (advertisementBytes.size < 3) return byteArrayOf()
        val key = advertisementBytes[2].unsigned() xor advertisementBytes[1].unsigned()
        val output = mutableListOf<Byte>()
        var sourceOffset = 0
        while (output.size < 4 && 3 + sourceOffset < advertisementBytes.size) {
            var next = advertisementBytes[3 + sourceOffset].unsigned()
            if (next != 0 && next != key) {
                next = next xor key
            }
            output += next.toByte()
            sourceOffset += 1
        }
        return output.toByteArray()
    }

    fun authPayload(advertisementBytes: ByteArray, nonce: ByteArray): ByteArray {
        require(nonce.size == 4) { "Fluffer auth nonce must be 4 bytes" }
        val digest = Sha256Digest.digest(nonce + advertisementSeed(advertisementBytes))
        return encrypt(byteArrayOf(0xA5.toByte(), 0x01, 0x08) + nonce + digest.copyOfRange(0, 4))
    }

    fun enablePayload(): ByteArray =
        encrypt(byteArrayOf(0x82.toByte(), 0x0E, 0x02, 0x00, 0x01))

    fun keepalivePayload(): ByteArray =
        encrypt(byteArrayOf(0x80.toByte(), 0x02, 0x00))

    fun controlPayload(firstSpeed: Int, secondSpeed: Int): ByteArray =
        encrypt(
            byteArrayOf(
                0x82.toByte(),
                0x0F,
                0x05,
                0x00,
                firstSpeed.coerceIn(0, 255).toByte(),
                secondSpeed.coerceIn(0, 255).toByte(),
                0x00,
                0x00,
            ),
        )

    fun rotateSpeed(value: Int): Int =
        if (value < 0) value.coerceAtLeast(-100).absoluteValue + 100 else value.coerceAtMost(100)

    fun decryptNotification(bytes: ByteArray): ByteArray =
        Aes128EcbPkcs7.decrypt(KEY, bytes)

    fun isAuthSuccess(bytes: ByteArray): Boolean =
        decryptNotification(bytes).contentEquals(byteArrayOf(0xA5.toByte(), 0x01, 0x01, 0x00))

    fun manufacturerAdvertisement(manufacturerData: Map<Int, ByteArray>): ByteArray =
        manufacturerData[manufacturerCompanyId]?.let { data ->
            byteArrayOf(
                (manufacturerCompanyId and 0xFF).toByte(),
                ((manufacturerCompanyId ushr 8) and 0xFF).toByte(),
            ) + data
        } ?: byteArrayOf()

    private fun encrypt(bytes: ByteArray): ByteArray =
        Aes128EcbPkcs7.encrypt(KEY, bytes)

    private val KEY = "jdk#Flu%y6fer32f".encodeToByteArray()
}

private val Int.absoluteValue: Int
    get() = if (this == Int.MIN_VALUE) Int.MAX_VALUE else kotlin.math.abs(this)

private fun Byte.unsigned(): Int = toInt() and 0xFF
