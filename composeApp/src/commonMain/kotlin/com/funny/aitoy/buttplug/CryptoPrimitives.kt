package com.funny.aitoy.buttplug

object Aes128EcbPkcs7 {
    fun encryptBlock(key: ByteArray, block: ByteArray): ByteArray {
        require(key.size == 16) { "AES-128 key must be 16 bytes" }
        require(block.size == 16) { "AES block must be 16 bytes" }
        val expandedKey = expandKey(key)
        return ByteArray(16).also { output ->
            encryptBlock(block, 0, output, 0, expandedKey)
        }
    }

    fun encrypt(key: ByteArray, data: ByteArray): ByteArray {
        require(key.size == 16) { "AES-128 key must be 16 bytes" }
        val expandedKey = expandKey(key)
        val padded = pkcs7Pad(data)
        return ByteArray(padded.size).also { output ->
            for (offset in padded.indices step 16) {
                encryptBlock(padded, offset, output, offset, expandedKey)
            }
        }
    }

    fun decrypt(key: ByteArray, data: ByteArray): ByteArray {
        require(key.size == 16) { "AES-128 key must be 16 bytes" }
        require(data.size % 16 == 0) { "AES-ECB data must be block aligned" }
        val expandedKey = expandKey(key)
        val decrypted = ByteArray(data.size)
        for (offset in data.indices step 16) {
            decryptBlock(data, offset, decrypted, offset, expandedKey)
        }
        return pkcs7Unpad(decrypted)
    }

    private fun encryptBlock(input: ByteArray, inputOffset: Int, output: ByteArray, outputOffset: Int, roundKey: ByteArray) {
        val state = ByteArray(16) { input[inputOffset + it] }
        addRoundKey(state, roundKey, 0)
        for (round in 1 until 10) {
            subBytes(state)
            shiftRows(state)
            mixColumns(state)
            addRoundKey(state, roundKey, round * 16)
        }
        subBytes(state)
        shiftRows(state)
        addRoundKey(state, roundKey, 160)
        state.copyInto(output, outputOffset)
    }

    private fun decryptBlock(input: ByteArray, inputOffset: Int, output: ByteArray, outputOffset: Int, roundKey: ByteArray) {
        val state = ByteArray(16) { input[inputOffset + it] }
        addRoundKey(state, roundKey, 160)
        for (round in 9 downTo 1) {
            invShiftRows(state)
            invSubBytes(state)
            addRoundKey(state, roundKey, round * 16)
            invMixColumns(state)
        }
        invShiftRows(state)
        invSubBytes(state)
        addRoundKey(state, roundKey, 0)
        state.copyInto(output, outputOffset)
    }

    private fun expandKey(key: ByteArray): ByteArray {
        val expanded = ByteArray(176)
        key.copyInto(expanded)
        var bytesGenerated = 16
        var rconIndex = 1
        val temp = ByteArray(4)
        while (bytesGenerated < expanded.size) {
            repeat(4) { temp[it] = expanded[bytesGenerated - 4 + it] }
            if (bytesGenerated % 16 == 0) {
                val first = temp[0]
                temp[0] = SBOX[temp[1].unsigned()].toByte()
                temp[1] = SBOX[temp[2].unsigned()].toByte()
                temp[2] = SBOX[temp[3].unsigned()].toByte()
                temp[3] = SBOX[first.unsigned()].toByte()
                temp[0] = (temp[0].unsigned() xor RCON[rconIndex]).toByte()
                rconIndex += 1
            }
            repeat(4) {
                expanded[bytesGenerated] = (expanded[bytesGenerated - 16].unsigned() xor temp[it].unsigned()).toByte()
                bytesGenerated += 1
            }
        }
        return expanded
    }

    private fun addRoundKey(state: ByteArray, roundKey: ByteArray, offset: Int) {
        repeat(16) { state[it] = (state[it].unsigned() xor roundKey[offset + it].unsigned()).toByte() }
    }

    private fun subBytes(state: ByteArray) {
        repeat(16) { state[it] = SBOX[state[it].unsigned()].toByte() }
    }

    private fun invSubBytes(state: ByteArray) {
        repeat(16) { state[it] = INV_SBOX[state[it].unsigned()].toByte() }
    }

    private fun shiftRows(state: ByteArray) {
        val copy = state.copyOf()
        for (row in 1..3) {
            for (col in 0..3) {
                state[col * 4 + row] = copy[((col + row) and 3) * 4 + row]
            }
        }
    }

    private fun invShiftRows(state: ByteArray) {
        val copy = state.copyOf()
        for (row in 1..3) {
            for (col in 0..3) {
                state[col * 4 + row] = copy[((col - row + 4) and 3) * 4 + row]
            }
        }
    }

    private fun mixColumns(state: ByteArray) {
        for (col in 0..3) {
            val i = col * 4
            val a0 = state[i].unsigned()
            val a1 = state[i + 1].unsigned()
            val a2 = state[i + 2].unsigned()
            val a3 = state[i + 3].unsigned()
            state[i] = (gfMul(a0, 2) xor gfMul(a1, 3) xor a2 xor a3).toByte()
            state[i + 1] = (a0 xor gfMul(a1, 2) xor gfMul(a2, 3) xor a3).toByte()
            state[i + 2] = (a0 xor a1 xor gfMul(a2, 2) xor gfMul(a3, 3)).toByte()
            state[i + 3] = (gfMul(a0, 3) xor a1 xor a2 xor gfMul(a3, 2)).toByte()
        }
    }

    private fun invMixColumns(state: ByteArray) {
        for (col in 0..3) {
            val i = col * 4
            val a0 = state[i].unsigned()
            val a1 = state[i + 1].unsigned()
            val a2 = state[i + 2].unsigned()
            val a3 = state[i + 3].unsigned()
            state[i] = (gfMul(a0, 14) xor gfMul(a1, 11) xor gfMul(a2, 13) xor gfMul(a3, 9)).toByte()
            state[i + 1] = (gfMul(a0, 9) xor gfMul(a1, 14) xor gfMul(a2, 11) xor gfMul(a3, 13)).toByte()
            state[i + 2] = (gfMul(a0, 13) xor gfMul(a1, 9) xor gfMul(a2, 14) xor gfMul(a3, 11)).toByte()
            state[i + 3] = (gfMul(a0, 11) xor gfMul(a1, 13) xor gfMul(a2, 9) xor gfMul(a3, 14)).toByte()
        }
    }

    private fun pkcs7Pad(data: ByteArray): ByteArray {
        val pad = 16 - (data.size % 16)
        return data + ByteArray(pad) { pad.toByte() }
    }

    private fun pkcs7Unpad(data: ByteArray): ByteArray {
        require(data.isNotEmpty()) { "Invalid PKCS7 data" }
        val pad = data.last().unsigned()
        require(pad in 1..16 && data.takeLast(pad).all { it.unsigned() == pad }) { "Invalid PKCS7 padding" }
        return data.copyOf(data.size - pad)
    }

    private fun gfMul(left: Int, right: Int): Int {
        var a = left
        var b = right
        var result = 0
        while (b > 0) {
            if ((b and 1) != 0) result = result xor a
            a = if ((a and 0x80) != 0) ((a shl 1) xor 0x11B) and 0xFF else (a shl 1) and 0xFF
            b = b ushr 1
        }
        return result
    }

    private fun gfPow(value: Int, power: Int): Int {
        var result = 1
        var base = value
        var exponent = power
        while (exponent > 0) {
            if ((exponent and 1) != 0) result = gfMul(result, base)
            base = gfMul(base, base)
            exponent = exponent ushr 1
        }
        return result
    }

    private fun aesSBox(value: Int): Int {
        val inverse = if (value == 0) 0 else gfPow(value, 254)
        var output = 0
        for (bit in 0..7) {
            val next = ((inverse shr bit) xor
                (inverse shr ((bit + 4) and 7)) xor
                (inverse shr ((bit + 5) and 7)) xor
                (inverse shr ((bit + 6) and 7)) xor
                (inverse shr ((bit + 7) and 7)) xor
                (0x63 shr bit)) and 1
            output = output or (next shl bit)
        }
        return output
    }

    private fun Byte.unsigned(): Int = toInt() and 0xFF

    private val SBOX = IntArray(256) { aesSBox(it) }
    private val INV_SBOX = IntArray(256).also { inverse ->
        SBOX.forEachIndexed { index, value -> inverse[value] = index }
    }
    private val RCON = intArrayOf(0, 1, 2, 4, 8, 16, 32, 64, 128, 27, 54)
}

object Sha256Digest {
    fun digest(input: ByteArray): ByteArray {
        val message = input + byteArrayOf(0x80.toByte()) + ByteArray(paddingZeroCount(input.size))
        val bitLength = input.size.toLong() * 8L
        val padded = message + ByteArray(8) { index -> ((bitLength ushr (8 * (7 - index))) and 0xFF).toByte() }
        val hash = INITIAL_HASH.copyOf()
        for (offset in padded.indices step 64) {
            val schedule = IntArray(64)
            for (index in 0 until 16) {
                val pos = offset + index * 4
                schedule[index] = (padded[pos].unsigned() shl 24) or
                    (padded[pos + 1].unsigned() shl 16) or
                    (padded[pos + 2].unsigned() shl 8) or
                    padded[pos + 3].unsigned()
            }
            for (index in 16 until 64) {
                val s0 = Integer.rotateRight(schedule[index - 15], 7) xor
                    Integer.rotateRight(schedule[index - 15], 18) xor
                    (schedule[index - 15] ushr 3)
                val s1 = Integer.rotateRight(schedule[index - 2], 17) xor
                    Integer.rotateRight(schedule[index - 2], 19) xor
                    (schedule[index - 2] ushr 10)
                schedule[index] = schedule[index - 16] + s0 + schedule[index - 7] + s1
            }
            var a = hash[0]
            var b = hash[1]
            var c = hash[2]
            var d = hash[3]
            var e = hash[4]
            var f = hash[5]
            var g = hash[6]
            var h = hash[7]
            for (index in 0 until 64) {
                val s1 = Integer.rotateRight(e, 6) xor Integer.rotateRight(e, 11) xor Integer.rotateRight(e, 25)
                val ch = (e and f) xor (e.inv() and g)
                val temp1 = h + s1 + ch + K[index] + schedule[index]
                val s0 = Integer.rotateRight(a, 2) xor Integer.rotateRight(a, 13) xor Integer.rotateRight(a, 22)
                val maj = (a and b) xor (a and c) xor (b and c)
                val temp2 = s0 + maj
                h = g
                g = f
                f = e
                e = d + temp1
                d = c
                c = b
                b = a
                a = temp1 + temp2
            }
            hash[0] += a
            hash[1] += b
            hash[2] += c
            hash[3] += d
            hash[4] += e
            hash[5] += f
            hash[6] += g
            hash[7] += h
        }
        return hash.flatMap { value ->
            listOf((value ushr 24).toByte(), (value ushr 16).toByte(), (value ushr 8).toByte(), value.toByte())
        }.toByteArray()
    }

    private fun paddingZeroCount(size: Int): Int =
        (56 - ((size + 1) % 64) + 64) % 64

    private fun Byte.unsigned(): Int = toInt() and 0xFF

    private val INITIAL_HASH = intArrayOf(
        0x6A09E667,
        0xBB67AE85.toInt(),
        0x3C6EF372,
        0xA54FF53A.toInt(),
        0x510E527F,
        0x9B05688C.toInt(),
        0x1F83D9AB,
        0x5BE0CD19,
    )

    private val K = intArrayOf(
        0x428A2F98, 0x71374491, 0xB5C0FBCF.toInt(), 0xE9B5DBA5.toInt(), 0x3956C25B, 0x59F111F1, 0x923F82A4.toInt(), 0xAB1C5ED5.toInt(),
        0xD807AA98.toInt(), 0x12835B01, 0x243185BE, 0x550C7DC3, 0x72BE5D74, 0x80DEB1FE.toInt(), 0x9BDC06A7.toInt(), 0xC19BF174.toInt(),
        0xE49B69C1.toInt(), 0xEFBE4786.toInt(), 0x0FC19DC6, 0x240CA1CC, 0x2DE92C6F, 0x4A7484AA, 0x5CB0A9DC, 0x76F988DA,
        0x983E5152.toInt(), 0xA831C66D.toInt(), 0xB00327C8.toInt(), 0xBF597FC7.toInt(), 0xC6E00BF3.toInt(), 0xD5A79147.toInt(), 0x06CA6351, 0x14292967,
        0x27B70A85, 0x2E1B2138, 0x4D2C6DFC, 0x53380D13, 0x650A7354, 0x766A0ABB, 0x81C2C92E.toInt(), 0x92722C85.toInt(),
        0xA2BFE8A1.toInt(), 0xA81A664B.toInt(), 0xC24B8B70.toInt(), 0xC76C51A3.toInt(), 0xD192E819.toInt(), 0xD6990624.toInt(), 0xF40E3585.toInt(), 0x106AA070,
        0x19A4C116, 0x1E376C08, 0x2748774C, 0x34B0BCB5, 0x391C0CB3, 0x4ED8AA4A, 0x5B9CCA4F, 0x682E6FF3,
        0x748F82EE, 0x78A5636F, 0x84C87814.toInt(), 0x8CC70208.toInt(), 0x90BEFFFA.toInt(), 0xA4506CEB.toInt(), 0xBEF9A3F7.toInt(), 0xC67178F2.toInt(),
    )
}
