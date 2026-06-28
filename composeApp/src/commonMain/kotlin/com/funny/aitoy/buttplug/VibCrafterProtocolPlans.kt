package com.funny.aitoy.buttplug

import kotlin.random.Random

object VibCrafterProtocolPlans {
    const val protocolId: String = "vibcrafter"
    val protocolIds: Set<String> = setOf("vibcrafter")
    val supportedKinds: Set<ToyOutputKind> = setOf(ToyOutputKind.Vibrate)
    const val handshakeTimeoutMs: Long = 10_000L

    fun randomAuthToken(): String {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        return buildString {
            repeat(6) {
                append(alphabet[Random.nextInt(alphabet.length)])
            }
        }
    }

    fun authPayload(token: String): ByteArray =
        encryptText("Auth:$token;")

    fun challengeResponsePayload(challenge: String): ByteArray =
        encryptText(challengeResponseText(challenge))

    fun controlPayload(internalSpeed: Int, externalSpeed: Int): ByteArray =
        encryptText("MtInt:${internalSpeed.coerceIn(0, 99).twoDigits()}${externalSpeed.coerceIn(0, 99).twoDigits()};")

    fun decryptNotification(bytes: ByteArray): String =
        Aes128EcbPkcs7.decrypt(KEY, bytes).decodeToString()

    fun challengeResponseText(notificationText: String): String {
        val marker = notificationText.indexOf(':')
        require(marker == 4 && notificationText.endsWith(";")) { "Invalid VibCrafter challenge" }
        val challenge = notificationText.substring(marker + 1, notificationText.length - 1)
        require(challenge.all { it.isLetterOrDigit() }) { "Invalid VibCrafter challenge" }
        val digest = Sha256Digest.digest(challenge.encodeToByteArray())
        return "Auth:${digest[0].unsigned().hex2()}${digest[1].unsigned().hex2()};"
    }

    private fun encryptText(text: String): ByteArray =
        Aes128EcbPkcs7.encrypt(KEY, text.encodeToByteArray())

    private fun Int.twoDigits(): String =
        toString().padStart(2, '0')

    private fun Int.hex2(): String =
        toString(16).padStart(2, '0')

    private fun Byte.unsigned(): Int = toInt() and 0xFF

    private val KEY = "jdk#Cra%f5Vib28r".encodeToByteArray()
}
