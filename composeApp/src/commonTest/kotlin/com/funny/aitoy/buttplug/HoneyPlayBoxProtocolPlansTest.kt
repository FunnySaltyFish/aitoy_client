package com.funny.aitoy.buttplug

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class HoneyPlayBoxProtocolPlansTest {
    @Test
    fun handshakeFrameMatchesButtplugProtocolImpl() {
        assertContentEquals(
            hex("02 a5 5a 55 aa f0 00 b1 01 00 02 00 10 03 36 86"),
            HoneyPlayBoxProtocolPlans.handshakeFrame(counter = 0),
        )
    }

    @Test
    fun controlFrameSignsVibrateGroupsWithMd5() {
        assertContentEquals(
            hex(
                "02 a5 5a 55 aa f0 02 b1 03 00 18 " +
                    "01 00 05 00 3c 00 00 20 11 00 05 00 3c 00 00 30 " +
                    "d7 92 01 f2 18 72 1e d4 03 91 7b",
            ),
            HoneyPlayBoxProtocolPlans.controlFrame(
                random = ByteArray(16) { it.toByte() },
                speeds = intArrayOf(0x20, 0x30),
                counter = 2,
            ),
        )
    }

    @Test
    fun frameCollectorAssemblesSplitResponseAndExtractsRandom() {
        val frame = hex(
            "02 a5 5a 55 aa f0 00 b1 01 00 12 90 00 " +
                "00 01 02 03 04 05 06 07 08 09 0a 0b 0c 0d 0e 0f 03 28 ba",
        )
        val collector = HoneyPlayBoxFrameCollector()

        assertEquals(emptyList(), collector.pushBytes(frame.copyOfRange(0, 8)))
        val frames = collector.pushBytes(frame.copyOfRange(8, frame.size))
        val response = HoneyPlayBoxProtocolPlans.parseResponse(frames.single())

        assertEquals(0x9000, response?.responseCode)
        assertContentEquals(ByteArray(16) { it.toByte() }, response?.random)
    }

    private fun hex(value: String): ByteArray =
        value.trim().split(Regex("\\s+")).map { it.toInt(16).toByte() }.toByteArray()
}
