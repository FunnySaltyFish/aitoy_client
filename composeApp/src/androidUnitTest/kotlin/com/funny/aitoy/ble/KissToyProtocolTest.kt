package com.funny.aitoy.ble

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs

class KissToyProtocolTest {
    @Test
    fun existingKissToyProtocolKeepsHistoricalControlShape() {
        val protocol = BleProtocolRegistry.resolveNative(kissToyFingerprint()) ?: error("KissToy protocol not resolved")

        assertEquals("kisstoy_gatt", protocol.status.id)
        assertEquals(ToyControlStyle.CombinedPatternAndIntensity, protocol.status.controlStyle)
        assertEquals(listOf("主电机", "第二电机", "抽插", "双通道"), protocol.status.modeNames)

        val operations = protocol.commandsFor(ToyControlAction.DualMotor(mode = 1, internalIntensity = 20, externalIntensity = 40))

        assertEquals(3, operations.size)
        operations.forEach { operation ->
            val write = assertIs<BleProtocolOperation.Write>(operation)
            assertEquals(KISSTOY_WRITE_UUID, write.characteristicUuid)
            assertEquals(true, write.withResponse)
        }
    }

    @Test
    fun tutuIIUsesChallengeAuthAndFixedXorMotorPacket() {
        val protocol = BleProtocolRegistry.resolveNative(tutuFingerprint()) ?: error("KissToy Tutu II protocol not resolved")

        assertEquals("kisstoy_tutu2", protocol.status.id)
        assertEquals(ToyControlStyle.DualIntensityOnly, protocol.status.controlStyle)
        assertEquals(listOf("伸缩", "震动"), protocol.status.channelNames)

        val init = protocol.initialize(tutuFingerprint())
        assertIs<BleProtocolOperation.SubscribeNotify>(init[0])
        assertIs<BleProtocolOperation.WaitForProtocolReady>(init[1])

        val challenge = "58000102030405060708090a0b".hexBytes()
        val auth = protocol.onNotify(TUTU_NOTIFY_UUID, challenge).first()
        val authWrite = assertIs<BleProtocolOperation.Write>(auth)
        assertEquals(TUTU_WRITE_UUID, authWrite.characteristicUuid)
        assertFalse(authWrite.withResponse)
        assertEquals("58EA31B9D8A9B3C028C7E0E39D", authWrite.bytes.hexUpper())

        val run = protocol.commandsFor(ToyControlAction.DualMotor(mode = 1, internalIntensity = 50, externalIntensity = 50))
        val write = assertIs<BleProtocolOperation.Write>(run.single())
        assertEquals(TUTU_WRITE_UUID, write.characteristicUuid)
        assertFalse(write.withResponse)
        assertEquals("58FF0102DB8492C6FFCFFFFFFF", write.bytes.hexUpper())
    }

    private fun ByteArray.hexUpper(): String =
        joinToString("") { byte -> "%02X".format(byte.toInt() and 0xff) }

    private fun String.hexBytes(): ByteArray {
        require(length % 2 == 0)
        return chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }

    private fun kissToyFingerprint() = BleGattFingerprint(
        name = "QCPW",
        manufacturerData = "",
        scanRecordHex = "",
        serviceUuids = setOf(KISSTOY_SERVICE_UUID),
        characteristicUuids = setOf(KISSTOY_WRITE_UUID, KISSTOY_NOTIFY_UUID),
    )

    private fun tutuFingerprint() = BleGattFingerprint(
        name = "迷路",
        manufacturerData = "",
        scanRecordHex = "",
        serviceUuids = setOf(TUTU_SERVICE_UUID),
        characteristicUuids = setOf(TUTU_WRITE_UUID, TUTU_NOTIFY_UUID),
    )

    private companion object {
        val KISSTOY_SERVICE_UUID: UUID = UUID.fromString("00001000-0000-1000-8000-00805f9b34fb")
        val KISSTOY_WRITE_UUID: UUID = UUID.fromString("00001001-0000-1000-8000-00805f9b34fb")
        val KISSTOY_NOTIFY_UUID: UUID = UUID.fromString("00001002-0000-1000-8000-00805f9b34fb")

        val TUTU_SERVICE_UUID: UUID = UUID.fromString("0000dddd-0000-1000-8000-00805f9b34fb")
        val TUTU_WRITE_UUID: UUID = UUID.fromString("0000ddd1-0000-1000-8000-00805f9b34fb")
        val TUTU_NOTIFY_UUID: UUID = UUID.fromString("0000ddd2-0000-1000-8000-00805f9b34fb")
    }
}
