package com.funny.aitoy.buttplug

data class HoneyPlayBoxResponse(
    val responseCode: Int?,
    val random: ByteArray?,
)

object HoneyPlayBoxProtocolPlans {
    const val protocolId: String = "honeyplaybox"
    val protocolIds: Set<String> = setOf("honeyplaybox")
    const val keepaliveIntervalMs: Long = 500L
    const val handshakeTimeoutMs: Long = 10_000L

    val supportedKinds: Set<ToyOutputKind> = setOf(ToyOutputKind.Vibrate, ToyOutputKind.Oscillate, ToyOutputKind.Rotate)

    fun handshakeFrame(counter: Int): ByteArray =
        buildFrame(commandType = 0xB1, command = 0x01, data = byteArrayOf(0x00, 0x10), counter = counter)

    fun controlFrame(random: ByteArray, speeds: IntArray, counter: Int): ByteArray =
        buildFrame(commandType = 0xB1, command = 0x03, data = vibrateData(random, speeds), counter = counter)

    fun parseResponse(frame: ByteArray): HoneyPlayBoxResponse? {
        if (frame.size < 15) return null
        val length = ((frame[9].unsigned() shl 8) or frame[10].unsigned())
        val dataStart = 11
        val dataEnd = dataStart + length
        if (frame.size < dataEnd + 3) return null
        val data = frame.copyOfRange(dataStart, dataEnd)
        val responseCode = if (data.size >= 2) (data[0].unsigned() shl 8) or data[1].unsigned() else null
        val random = if (data.size >= 18) data.copyOfRange(2, 18) else null
        return HoneyPlayBoxResponse(responseCode = responseCode, random = random)
    }

    fun buildFrame(commandType: Int, command: Int, data: ByteArray, counter: Int): ByteArray {
        val length = data.size
        val crcData = byteArrayOf(commandType.byte(), command.byte(), (length shr 8).byte(), length.byte()) + data
        val crc = crc16Modbus(crcData)
        return byteArrayOf(
            0x02,
            0xA5.toByte(),
            0x5A,
            0x55,
            0xAA.toByte(),
            0xF0.toByte(),
            counter.byte(),
            commandType.byte(),
            command.byte(),
            (length shr 8).byte(),
            length.byte(),
        ) + data + byteArrayOf(0x03, (crc shr 8).byte(), crc.byte())
    }

    fun crc16Modbus(data: ByteArray): Int {
        var crc = 0xFFFF
        for (byte in data) {
            crc = crc xor byte.unsigned()
            repeat(8) {
                crc = if ((crc and 1) != 0) {
                    (crc ushr 1) xor 0xA001
                } else {
                    crc ushr 1
                }
            }
        }
        return crc and 0xFFFF
    }

    private fun vibrateData(random: ByteArray, speeds: IntArray): ByteArray {
        require(random.size == 16) { "HoneyPlayBox random token must be 16 bytes" }
        val groups = speeds.flatMapIndexed { index, speed ->
            val modePosition = ((index and 0x0F) shl 4) or 0x01
            listOf(modePosition, 0x00, 0x05, 0x00, 0x3C, 0x00, 0x00, speed.coerceIn(0, 0xFF))
        }.map { it.byte() }.toByteArray()
        val dataLength = groups.size + 8
        val signature = Md5Digest.digest(
            byteArrayOf(0xB1.toByte(), 0x03, (dataLength shr 8).byte(), dataLength.byte()) +
                groups +
                SECRET +
                random,
        ).copyOfRange(0, 8)
        return groups + signature
    }

    private fun Int.byte(): Byte = (this and 0xFF).toByte()

    private fun Byte.unsigned(): Int = toInt() and 0xFF

    private val SECRET = byteArrayOf(
        0x8B.toByte(),
        0xE3.toByte(),
        0xFD.toByte(),
        0x04,
        0x68,
        0x35,
        0x09,
        0x86.toByte(),
        0x12,
        0x1A,
        0xBF.toByte(),
        0x03,
        0x30,
        0xE9.toByte(),
        0xE3.toByte(),
        0xC5.toByte(),
    )
}

class HoneyPlayBoxFrameCollector {
    private val buffer = mutableListOf<Byte>()

    fun pushBytes(bytes: ByteArray): List<ByteArray> {
        buffer += bytes.toList()
        val frames = mutableListOf<ByteArray>()
        while (true) {
            val start = buffer.windowed(6).indexOfFirst { it == HONEY_PLAYBOX_HEADER }
            if (start < 0) {
                buffer.clear()
                break
            }
            if (start > 0) buffer.subList(0, start).clear()
            if (buffer.size < 11) break
            val dataLength = (buffer[9].unsigned() shl 8) or buffer[10].unsigned()
            val frameLength = 1 + 5 + 1 + 1 + 1 + 2 + dataLength + 1 + 2
            if (buffer.size < frameLength) break
            val etxIndex = 1 + 5 + 1 + 1 + 1 + 2 + dataLength
            if (buffer[etxIndex] != 0x03.toByte()) {
                buffer.removeAt(0)
                continue
            }
            frames += buffer.subList(0, frameLength).toByteArray()
            buffer.subList(0, frameLength).clear()
            if (buffer.size < 11) break
        }
        return frames
    }

    private fun Byte.unsigned(): Int = toInt() and 0xFF

    private companion object {
        val HONEY_PLAYBOX_HEADER = listOf(0x02, 0xA5, 0x5A, 0x55, 0xAA, 0xF0).map { it.toByte() }
    }
}

private object Md5Digest {
    fun digest(input: ByteArray): ByteArray {
        val message = input + byteArrayOf(0x80.toByte()) + ByteArray(paddingZeroCount(input.size))
        val bitLength = input.size.toLong() * 8L
        val lengthBytes = ByteArray(8) { index -> ((bitLength ushr (8 * index)) and 0xFF).toByte() }
        val padded = message + lengthBytes

        var a0 = 0x67452301
        var b0 = 0xEFCDAB89.toInt()
        var c0 = 0x98BADCFE.toInt()
        var d0 = 0x10325476

        for (offset in padded.indices step 64) {
            val words = IntArray(16) { index ->
                val pos = offset + index * 4
                padded[pos].unsigned() or
                    (padded[pos + 1].unsigned() shl 8) or
                    (padded[pos + 2].unsigned() shl 16) or
                    (padded[pos + 3].unsigned() shl 24)
            }
            var a = a0
            var b = b0
            var c = c0
            var d = d0
            for (i in 0 until 64) {
                val (f, g) = when (i) {
                    in 0..15 -> ((b and c) or (b.inv() and d)) to i
                    in 16..31 -> ((d and b) or (d.inv() and c)) to ((5 * i + 1) % 16)
                    in 32..47 -> (b xor c xor d) to ((3 * i + 5) % 16)
                    else -> (c xor (b or d.inv())) to ((7 * i) % 16)
                }
                val next = b + Integer.rotateLeft(a + f + K[i] + words[g], S[i])
                a = d
                d = c
                c = b
                b = next
            }
            a0 += a
            b0 += b
            c0 += c
            d0 += d
        }
        return intToLittleEndian(a0) + intToLittleEndian(b0) + intToLittleEndian(c0) + intToLittleEndian(d0)
    }

    private fun paddingZeroCount(size: Int): Int =
        (56 - ((size + 1) % 64) + 64) % 64

    private fun intToLittleEndian(value: Int): ByteArray =
        ByteArray(4) { index -> ((value ushr (8 * index)) and 0xFF).toByte() }

    private fun Byte.unsigned(): Int = toInt() and 0xFF

    private val S = intArrayOf(
        7, 12, 17, 22, 7, 12, 17, 22, 7, 12, 17, 22, 7, 12, 17, 22,
        5, 9, 14, 20, 5, 9, 14, 20, 5, 9, 14, 20, 5, 9, 14, 20,
        4, 11, 16, 23, 4, 11, 16, 23, 4, 11, 16, 23, 4, 11, 16, 23,
        6, 10, 15, 21, 6, 10, 15, 21, 6, 10, 15, 21, 6, 10, 15, 21,
    )

    private val K = IntArray(64) { index ->
        kotlin.math.floor(kotlin.math.abs(kotlin.math.sin((index + 1).toDouble())) * 4294967296.0).toLong().toInt()
    }
}
