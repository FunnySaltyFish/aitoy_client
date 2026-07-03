package com.funny.aitoy.ble

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs

class XiuxiudaOfficialProtocolTest {
    @Test
    fun smallWhaleUsesOfficialAddressPrefixAndChecksum() {
        val fingerprint = xiuxiudaFingerprint(name = "XXD-Lush12B")
        val protocol = BleProtocolRegistry.resolveNative(fingerprint) ?: error("Xiuxiuda protocol not resolved")

        assertEquals("xiuxiuda_official", protocol.status.id)
        assertEquals(19, protocol.status.intensityMax)

        val init = protocol.initialize(fingerprint)
        assertEquals(NOTIFY_UUID, assertIs<BleProtocolOperation.SubscribeNotify>(init[0]).characteristicUuid)
        assertEquals("81851511653D33016A", assertIs<BleProtocolOperation.Write>(init[1]).bytes.hexUpper())
        assertEquals("81851511653A30006F", assertIs<BleProtocolOperation.Write>(init[3]).bytes.hexUpper())

        val write = assertIs<BleProtocolOperation.Write>(
            protocol.commandsFor(ToyControlAction.Intensity(9)).single(),
        )
        assertEquals(WRITE_UUID, write.characteristicUuid)
        assertFalse(write.withResponse)
        assertEquals("81851511653A300966", write.bytes.hexUpper())
    }

    @Test
    fun enhancedXiuxiudaNamesUseOfficialHighPowerCommandBranch() {
        val fingerprint = xiuxiudaFingerprint(name = "XXD-Lush12C")
        val protocol = BleProtocolRegistry.resolveNative(fingerprint) ?: error("Xiuxiuda protocol not resolved")

        val write = assertIs<BleProtocolOperation.Write>(
            protocol.commandsFor(ToyControlAction.Intensity(9)).single(),
        )

        assertEquals("xiuxiuda_official", protocol.status.id)
        assertEquals("81851511953C300990", write.bytes.hexUpper())
    }

    @Test
    fun invalidAddressDoesNotClaimOfficialProtocol() {
        val protocol = BleProtocolRegistry.resolveNative(xiuxiudaFingerprint(address = ""))

        assertEquals(null, protocol)
    }

    private fun xiuxiudaFingerprint(
        name: String = "XXD-Lush12B",
        address: String = "AA:BB:CC:DD:EE:FF",
    ) = BleGattFingerprint(
        name = name,
        address = address,
        manufacturerData = "",
        scanRecordHex = "",
        serviceUuids = setOf(SERVICE_UUID),
        characteristicUuids = setOf(WRITE_UUID, NOTIFY_UUID),
    )

    private fun ByteArray.hexUpper(): String =
        joinToString("") { byte -> "%02X".format(byte.toInt() and 0xff) }

    private companion object {
        val SERVICE_UUID: UUID = UUID.fromString("53300001-0023-4bd4-bbd5-a6920e4c5653")
        val NOTIFY_UUID: UUID = UUID.fromString("53300002-0023-4bd4-bbd5-a6920e4c5653")
        val WRITE_UUID: UUID = UUID.fromString("53300003-0023-4bd4-bbd5-a6920e4c5653")
    }
}
