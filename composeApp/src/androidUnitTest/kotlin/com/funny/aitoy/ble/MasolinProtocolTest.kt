@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package com.funny.aitoy.ble

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.uuid.Uuid

class MasolinProtocolTest {
    @Test
    fun traceFingerprintUsesOfficialMasolinFfa0Route() {
        val fingerprint = masolinTraceFingerprint()
        val protocols = BleProtocolRegistry.resolveNativeAll(fingerprint)

        assertEquals(listOf("masolin_waiwaima_ffa0"), protocols.map { it.status.id })

        val protocol = protocols.single()
        assertEquals("MASOLIN 小纤蛋", protocol.status.displayName)
        assertEquals(ToyControlStyle.CombinedPatternAndIntensity, protocol.status.controlStyle)
        assertEquals(8, protocol.status.modeMax)
        assertEquals(100, protocol.status.intensityMax)
        assertEquals(emptyList(), protocol.initialize(fingerprint))
    }

    @Test
    fun combinedControlUsesOfficialFiveByteFrame() {
        val protocol = BleProtocolRegistry.resolveNative(masolinTraceFingerprint())
            ?: error("MASOLIN protocol not resolved")

        val writes = protocol.commandsFor(ToyControlAction.Combined(mode = 3, intensity = 50))
            .map { assertIs<BleProtocolOperation.Write>(it) }

        assertEquals(1, writes.size)
        assertEquals(FFA1_WRITE_UUID, writes[0].characteristicUuid)
        assertEquals(true, writes[0].withResponse)
        assertEquals("AA0B010305", writes[0].bytes.hexUpper())
    }

    @Test
    fun intensityControlUsesOfficialContinuousFrameAndStopClearsAllMotors() {
        val protocol = BleProtocolRegistry.resolveNative(masolinTraceFingerprint())
            ?: error("MASOLIN protocol not resolved")

        val intensity = assertIs<BleProtocolOperation.Write>(
            protocol.commandsFor(ToyControlAction.Intensity(73)).single(),
        )
        assertEquals(FFA1_WRITE_UUID, intensity.characteristicUuid)
        assertEquals("AA0B000008", intensity.bytes.hexUpper())

        val stop = assertIs<BleProtocolOperation.Write>(
            protocol.commandsFor(ToyControlAction.Stop).single(),
        )
        assertEquals(FFA1_WRITE_UUID, stop.characteristicUuid)
        assertEquals("AA0B140000", stop.bytes.hexUpper())
    }

    @Test
    fun officialBleNameCanMatchWhenManufacturerDataIsMissing() {
        val protocols = BleProtocolRegistry.resolveNativeAll(
            masolinTraceFingerprint().copy(
                name = "MY_123456",
                manufacturerData = "",
                scanRecordHex = "",
            ),
        )

        assertEquals(listOf("masolin_waiwaima_ffa0"), protocols.map { it.status.id })
    }

    @Test
    fun mideaTraceFingerprintDoesNotMatchMasolinRoute() {
        val protocols = BleProtocolRegistry.resolveNativeAll(
            BleGattFingerprint(
                name = "midea",
                address = "8C:3F:44:BF:1F:C5",
                manufacturerData = "0x6a8:01 32 32 32 35 31 36 37 31 41 43 30 31 30 35",
                scanRecordHex = "",
                serviceUuids = setOf(
                    Uuid.parse("00001800-0000-1000-8000-00805f9b34fb"),
                    Uuid.parse("00001801-0000-1000-8000-00805f9b34fb"),
                    Uuid.parse("0000ff80-0000-1000-8000-00805f9b34fb"),
                    Uuid.parse("0000ff90-0000-1000-8000-00805f9b34fb"),
                    Uuid.parse("0000ffa0-0000-1000-8000-00805f9b34fb"),
                ),
                characteristicUuids = setOf(
                    Uuid.parse("0000ff81-0000-1000-8000-00805f9b34fb"),
                    Uuid.parse("0000ff82-0000-1000-8000-00805f9b34fb"),
                    Uuid.parse("0000ff91-0000-1000-8000-00805f9b34fb"),
                    Uuid.parse("0000ff92-0000-1000-8000-00805f9b34fb"),
                    FFA1_WRITE_UUID,
                    Uuid.parse("0000ffa2-0000-1000-8000-00805f9b34fb"),
                ),
            ),
        )

        assertEquals(emptyList(), protocols.map { it.status.id })
    }

    private fun masolinTraceFingerprint(): BleGattFingerprint =
        BleGattFingerprint(
            name = "未命名设备",
            address = "20:25:E3:E1:58:AE",
            manufacturerData = "0x642:",
            scanRecordHex = "02 01 06 03 FF 42 06",
            serviceUuids = setOf(FF12_SERVICE_UUID, FFA0_SERVICE_UUID),
            characteristicUuids = setOf(FF15_UUID, FF14_UUID, FFA1_WRITE_UUID, FFA2_UUID),
        )

    private fun ByteArray.hexUpper(): String =
        joinToString("") { byte -> "%02X".format(byte.toInt() and 0xff) }

    private companion object {
        val FF12_SERVICE_UUID: Uuid = Uuid.parse("0000ff12-0000-1000-8000-00805f9b34fb")
        val FF14_UUID: Uuid = Uuid.parse("0000ff14-0000-1000-8000-00805f9b34fb")
        val FF15_UUID: Uuid = Uuid.parse("0000ff15-0000-1000-8000-00805f9b34fb")
        val FFA0_SERVICE_UUID: Uuid = Uuid.parse("0000ffa0-0000-1000-8000-00805f9b34fb")
        val FFA1_WRITE_UUID: Uuid = Uuid.parse("0000ffa1-0000-1000-8000-00805f9b34fb")
        val FFA2_UUID: Uuid = Uuid.parse("0000ffa2-0000-1000-8000-00805f9b34fb")
    }
}
