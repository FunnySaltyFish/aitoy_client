package com.funny.aitoy.ble

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs

class SvakomSl278hProtocolTest {
    @Test
    fun sl278hVibrationStickUsesSeparateStretchAndVibrateCommands() {
        val protocol = BleProtocolRegistry.resolveNative(svakomFingerprint(productCode = 100))
            ?: error("SL278H vibration stick protocol not resolved")

        assertEquals("svakom_sl278h_v", protocol.status.id)
        assertEquals(ToyControlStyle.PatternAndDualIntensity, protocol.status.controlStyle)
        assertEquals(listOf("伸缩", "震动"), protocol.status.channelNames)

        val init = protocol.initialize(svakomFingerprint(productCode = 100))
        assertEquals(NOTIFY_UUID, assertIs<BleProtocolOperation.SubscribeNotify>(init.single()).characteristicUuid)

        val operations = protocol.commandsFor(ToyControlAction.DualMotor(mode = 3, internalIntensity = 6, externalIntensity = 4))
        assertEquals("5508000003FF00", assertIs<BleProtocolOperation.Write>(operations[0]).bytes.hexUpper())
        assertEquals("55030000010400", assertIs<BleProtocolOperation.Write>(operations[1]).bytes.hexUpper())
        operations.forEach { operation ->
            val write = assertIs<BleProtocolOperation.Write>(operation)
            assertEquals(WRITE_UUID, write.characteristicUuid)
            assertFalse(write.withResponse)
        }
    }

    @Test
    fun sl278hSuctionSideUsesOfficialFlapCommandShape() {
        val protocol = BleProtocolRegistry.resolveNative(svakomFingerprint(productCode = 101))
            ?: error("SL278H suction side protocol not resolved")

        assertEquals("svakom_sl278h_f", protocol.status.id)
        assertEquals(ToyControlStyle.CombinedPatternAndIntensity, protocol.status.controlStyle)
        assertEquals("吮吸强度", protocol.status.intensityLabel)

        val write = assertIs<BleProtocolOperation.Write>(
            protocol.commandsFor(ToyControlAction.Combined(mode = 2, intensity = 7)).single(),
        )

        assertEquals(WRITE_UUID, write.characteristicUuid)
        assertFalse(write.withResponse)
        assertEquals("55070000020700", write.bytes.hexUpper())
    }

    @Test
    fun sl278hMatcherStaysAheadOfSt419AndRequiresSvakomManufacturerData() {
        val protocols = BleProtocolRegistry.resolveNativeAll(svakomFingerprint(productCode = 100))

        assertEquals("svakom_sl278h_v", protocols.first().status.id)
        assertEquals(null, BleProtocolRegistry.resolveNative(svakomFingerprint(productCode = 100, manufacturerData = "")))
    }

    @Test
    fun sl278kLiveAdvertisementMatchesVibrationStickProfile() {
        val protocol = BleProtocolRegistry.resolveNative(
            svakomFingerprint(
                name = "SL278K",
                manufacturerData = "0x32:53 56 41 02 FF 32 26 01 E8 B0 C9 FF 32 00 00 80 19 04 0A 01 00",
            ),
        ) ?: error("SL278K live advertisement did not resolve")

        assertEquals("svakom_sl278h_v", protocol.status.id)
    }

    @Test
    fun sl278kSuctionProductCodeMatchesSuctionProfile() {
        val protocol = BleProtocolRegistry.resolveNative(svakomFingerprint(productCode = 0x81, name = "SL278K"))
            ?: error("SL278K suction product code did not resolve")

        assertEquals("svakom_sl278h_f", protocol.status.id)
    }

    private fun svakomFingerprint(
        productCode: Int = 100,
        name: String = "SL278H",
        manufacturerData: String = "0xffff:53 56 41 01 ${"%02X".format(productCode)} 00 00 00 00 00 00 00",
    ) = BleGattFingerprint(
        name = name,
        address = "AA:BB:CC:DD:EE:FF",
        manufacturerData = manufacturerData,
        scanRecordHex = "",
        serviceUuids = setOf(SERVICE_UUID),
        characteristicUuids = setOf(WRITE_UUID, NOTIFY_UUID),
    )

    private fun ByteArray.hexUpper(): String =
        joinToString("") { byte -> "%02X".format(byte.toInt() and 0xff) }

    private companion object {
        val SERVICE_UUID: UUID = UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb")
        val WRITE_UUID: UUID = UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb")
        val NOTIFY_UUID: UUID = UUID.fromString("0000ffe2-0000-1000-8000-00805f9b34fb")
    }
}
