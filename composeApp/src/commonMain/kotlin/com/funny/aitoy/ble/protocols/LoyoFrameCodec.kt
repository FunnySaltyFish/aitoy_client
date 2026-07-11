package com.funny.aitoy.ble

import kotlin.uuid.Uuid

internal object LoyoEncryptedFrameCodec {
    val serviceUuid: Uuid = Uuid.parse("00001000-0000-1000-8000-00805f9b34fb")
    val writeUuid: Uuid = Uuid.parse("00001001-0000-1000-8000-00805f9b34fb")
    val notifyUuid: Uuid = Uuid.parse("00001002-0000-1000-8000-00805f9b34fb")

    fun hasRoute(fingerprint: BleGattFingerprint): Boolean =
        fingerprint.serviceUuids.contains(serviceUuid) &&
            fingerprint.characteristicUuids.contains(writeUuid) &&
            fingerprint.characteristicUuids.contains(notifyUuid)

    fun command(
        command: Int,
        first: Int,
        second: Int = 0,
        withResponse: Boolean = true,
    ): BleProtocolOperation.Write =
        BleProtocolOperation.Write(
            characteristicUuid = writeUuid,
            bytes = encrypt(payload(command, first, second)),
            withResponse = withResponse,
        )

    fun payload(command: Int, first: Int, second: Int = 0): ByteArray =
        byteArrayOf(
            0x5A,
            0x00,
            0x00,
            0x01,
            command.toByte(),
            if (command == 0x40) 0x03 else first.toByte(),
            if (command == 0x40) first.toByte() else second.toByte(),
            if (command == 0x40) second.toByte() else 0x00,
            0x00,
            0x00,
        )

    fun electricPayload(frequency: Int, intensity: Int): ByteArray =
        byteArrayOf(
            0x5A,
            0x00,
            0x00,
            0x01,
            0x90.toByte(),
            frequency.coerceIn(0, 0xff).toByte(),
            intensity.coerceIn(0, 0xff).toByte(),
            0x00,
            0x00,
            0x00,
        )

    fun encryptedWrite(
        payload: ByteArray,
        withResponse: Boolean = true,
    ): BleProtocolOperation.Write =
        BleProtocolOperation.Write(
            characteristicUuid = writeUuid,
            bytes = encrypt(payload),
            withResponse = withResponse,
        )

    fun encrypt(payload: ByteArray): ByteArray {
        val plain = ByteArray(12)
        plain[0] = 0x23
        payload.copyInto(
            plain,
            destinationOffset = 1,
            startIndex = 0,
            endIndex = payload.size.coerceAtMost(10),
        )
        plain[11] = plain.take(11).sumOf { it.toInt() }.toByte()

        val encrypted = ByteArray(12)
        val seed = plain[0].toInt() and 0xff
        encrypted[0] = seed.toByte()
        for (index in 1 until encrypted.size) {
            val previous = encrypted[index - 1].toInt() and 0xff
            val key = key(previous, index)
            val raw = plain[index].toInt() and 0xff
            encrypted[index] = ((key xor seed xor raw) + key).toByte()
        }
        return encrypted
    }

    fun key(previous: Int, index: Int): Int =
        keyTab[previous and 0x03][index]

    val keyTab: Array<IntArray> = arrayOf(
        intArrayOf(0, 24, 152, 247, 165, 61, 13, 41, 37, 80, 68, 70),
        intArrayOf(0, 69, 110, 106, 111, 120, 32, 83, 45, 49, 46, 55),
        intArrayOf(0, 101, 120, 32, 84, 111, 121, 115, 10, 142, 157, 163),
        intArrayOf(0, 197, 214, 231, 248, 10, 50, 32, 111, 98, 13, 10),
    )
}
