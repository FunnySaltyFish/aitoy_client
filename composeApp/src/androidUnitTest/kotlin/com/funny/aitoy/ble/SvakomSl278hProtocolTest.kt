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

    private fun svakomFingerprint(
        productCode: Int,
        manufacturerData: String = "0xffff:53 56 41 01 ${"%02X".format(productCode)} 00 00 00 00 00 00 00",
    ) = BleGattFingerprint(
        name = "SL278H",
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
