package com.funny.aitoy.buttplug

object WeVibeProtocolPlans {
    val packedPatternCodes: IntArray = intArrayOf(0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x0E, 0x0F, 0x10, 0x11)
    val packedPatternNames: List<String> =
        listOf("长振", "峰值", "脉冲", "回响", "波浪", "潮汐", "冲浪", "弹跳", "按摩", "挑逗")

    fun stopPayload(): ByteArray =
        bytes(0x0F, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00)

    fun legacyInitializePayloads(): List<ByteArray> =
        listOf(
            bytes(0x0F, 0x03, 0x00, 0x99, 0x00, 0x03, 0x00, 0x00),
            stopPayload(),
        )

    fun packedPatternPayload(
        mode: Int,
        internalIntensity: Int,
        externalIntensity: Int,
    ): ByteArray {
        val internal = internalIntensity.coerceIn(0, 0x0F)
        val external = externalIntensity.coerceIn(0, 0x0F)
        val motorBits = packedMotorBits(internal, external)
        return if (motorBits == 0) {
            stopPayload()
        } else {
            bytes(
                0x0F,
                packedPatternCodes[mode.coerceIn(1, packedPatternCodes.size) - 1],
                0x00,
                (internal shl 4) or external,
                0x00,
                motorBits,
                0x00,
                0x00,
            )
        }
    }

    fun legacyPayload(
        numVibrators: Int,
        internalIntensity: Int,
        externalIntensity: Int,
    ): ByteArray {
        val internal = internalIntensity.coerceIn(0, 0x0F)
        val external = externalIntensity.coerceIn(0, 0x0F)
        val selectedExternal = if (numVibrators > 1) external else internal
        return if (internal == 0 && selectedExternal == 0) {
            stopPayload()
        } else {
            bytes(
                0x0F,
                0x03,
                0x00,
                selectedExternal or (internal shl 4),
                0x00,
                0x03,
                0x00,
                0x00,
            )
        }
    }

    fun eightBitPayload(
        numVibrators: Int,
        internalIntensity: Int,
        externalIntensity: Int,
    ): ByteArray {
        val internal = internalIntensity.coerceIn(0, 0xFC)
        val external = externalIntensity.coerceIn(0, 0xFC)
        val selectedExternal = if (numVibrators > 1) external else internal
        return if (internal == 0 && selectedExternal == 0) {
            stopPayload()
        } else {
            bytes(
                0x0F,
                0x03,
                0x00,
                (selectedExternal + 3).coerceIn(0, 0xFF),
                (internal + 3).coerceIn(0, 0xFF),
                chorusMotorBits(internal, selectedExternal),
                0x00,
                0x00,
            )
        }
    }

    fun chorusPayload(
        numVibrators: Int,
        internalIntensity: Int,
        externalIntensity: Int,
    ): ByteArray {
        val internal = internalIntensity.coerceIn(0, 0xFF)
        val external = externalIntensity.coerceIn(0, 0xFF)
        val selectedExternal = if (numVibrators > 1) external else internal
        return if (internal == 0 && selectedExternal == 0) {
            stopPayload()
        } else {
            bytes(
                0x0F,
                0x03,
                0x00,
                internal,
                selectedExternal,
                chorusMotorBits(internal, selectedExternal),
                0x00,
                0x00,
            )
        }
    }

    private fun packedMotorBits(internalIntensity: Int, externalIntensity: Int): Int =
        (if (internalIntensity > 0) 0x02 else 0x00) or (if (externalIntensity > 0) 0x01 else 0x00)

    private fun chorusMotorBits(internalIntensity: Int, externalIntensity: Int): Int =
        (if (externalIntensity > 0) 0x02 else 0x00) or (if (internalIntensity > 0) 0x01 else 0x00)

    private fun bytes(vararg values: Int): ByteArray =
        ByteArray(values.size) { index -> values[index].toByte() }
}
