package com.funny.aitoy.ble

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
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
    fun shortOfficialUuidRouteUsesTraceWriteCharacteristic() {
        val fingerprint = xiuxiudaFingerprint(
            name = "XXD-Lush12P-XT",
            address = "41:42:2C:0F:D5:CE",
            serviceUuid = SHORT_SERVICE_UUID,
            notifyUuid = SHORT_NOTIFY_UUID,
            writeUuid = SHORT_WRITE_UUID,
        )
        val protocol = BleProtocolRegistry.resolveNative(fingerprint) ?: error("Xiuxiuda protocol not resolved")

        assertEquals("xiuxiuda_official", protocol.status.id)

        val init = protocol.initialize(fingerprint)
        assertEquals(SHORT_NOTIFY_UUID, assertIs<BleProtocolOperation.SubscribeNotify>(init[0]).characteristicUuid)
        assertEquals(SHORT_WRITE_UUID, assertIs<BleProtocolOperation.Write>(init[1]).characteristicUuid)
        assertEquals("838F3F33653D33016A", assertIs<BleProtocolOperation.Write>(init[1]).bytes.hexUpper())
        assertEquals("838F3F33653A30006F", assertIs<BleProtocolOperation.Write>(init[3]).bytes.hexUpper())

        val write = assertIs<BleProtocolOperation.Write>(
            protocol.commandsFor(ToyControlAction.Intensity(9)).single(),
        )
        assertEquals(SHORT_WRITE_UUID, write.characteristicUuid)
        assertEquals("838F3F33653A300966", write.bytes.hexUpper())
    }

    @Test
    fun littleDevilXtUsesOfficialDualMotorCommands() {
        val fingerprint = xiuxiudaFingerprint(
            name = "XXD-Lush125-XT",
            address = "02:26:01:36:E7:19",
            serviceUuid = SHORT_SERVICE_UUID,
            notifyUuid = SHORT_NOTIFY_UUID,
            writeUuid = SHORT_WRITE_UUID,
        )
        val protocol = BleProtocolRegistry.resolveNative(fingerprint) ?: error("Xiuxiuda protocol not resolved")

        assertEquals("xiuxiuda_official", protocol.status.id)
        assertEquals(ToyControlStyle.DualIntensityOnly, protocol.status.controlStyle)
        assertEquals(listOf("吸力", "震动"), protocol.status.channelNames)

        val intensity = assertIs<BleProtocolOperation.Write>(
            protocol.commandsFor(ToyControlAction.Intensity(9)).single(),
        )
        assertEquals("8E89E9EE963C310992", intensity.bytes.hexUpper())

        val suction = assertIs<BleProtocolOperation.Write>(
            protocol.commandsFor(ToyControlAction.DualMotor(mode = 1, internalIntensity = 7, externalIntensity = 0)).single(),
        )
        assertEquals("8E89E9EE763A31077A", suction.bytes.hexUpper())

        val vibration = assertIs<BleProtocolOperation.Write>(
            protocol.commandsFor(ToyControlAction.DualMotor(mode = 1, internalIntensity = 0, externalIntensity = 7)).single(),
        )
        assertEquals("8E89E9EE863B31078B", vibration.bytes.hexUpper())

        val both = assertIs<BleProtocolOperation.Write>(
            protocol.commandsFor(ToyControlAction.DualMotor(mode = 1, internalIntensity = 6, externalIntensity = 11)).single(),
        )
        assertEquals("8E89E9EE963C310B90", both.bytes.hexUpper())
    }

    @Test
    fun traceFingerprintWithFf12AndShortRoutePrefersXiuxiudaOfficialProtocol() {
        val fingerprint = BleGattFingerprint(
            name = "XXD-Lush12P-XT",
            address = "41:42:2C:0F:D5:CE",
            manufacturerData = "0x642:",
            scanRecordHex = "",
            serviceUuids = setOf(FF12_SERVICE_UUID, SHORT_SERVICE_UUID),
            characteristicUuids = setOf(FF15_WRITE_UUID, FF14_NOTIFY_UUID, SHORT_WRITE_UUID, SHORT_NOTIFY_UUID),
        )

        val protocol = BleProtocolRegistry.resolveNative(fingerprint) ?: error("Xiuxiuda protocol not resolved")
        val write = assertIs<BleProtocolOperation.Write>(
            protocol.commandsFor(ToyControlAction.Intensity(9)).single(),
        )

        assertEquals("xiuxiuda_official", protocol.status.id)
        assertEquals(SHORT_WRITE_UUID, write.characteristicUuid)
    }

    @Test
    fun ff12Ff15RouteAloneDoesNotClaimXiuxiudaOfficialProtocol() {
        val protocol = BleProtocolRegistry.resolveNative(
            xiuxiudaFingerprint(
                name = "XXD-Lush12P-XT",
                serviceUuid = FF12_SERVICE_UUID,
                notifyUuid = FF14_NOTIFY_UUID,
                writeUuid = FF15_WRITE_UUID,
            ),
        )

        assertEquals(null, protocol)
    }

    @Test
    fun invalidAddressDoesNotClaimOfficialProtocol() {
        val protocol = BleProtocolRegistry.resolveNative(xiuxiudaFingerprint(address = ""))

        assertEquals(null, protocol)
    }

    private fun xiuxiudaFingerprint(
        name: String = "XXD-Lush12B",
        address: String = "AA:BB:CC:DD:EE:FF",
        serviceUuid: Uuid = SERVICE_UUID,
        notifyUuid: Uuid = NOTIFY_UUID,
        writeUuid: Uuid = WRITE_UUID,
    ) = BleGattFingerprint(
        name = name,
        address = address,
        manufacturerData = "",
        scanRecordHex = "",
        serviceUuids = setOf(serviceUuid),
        characteristicUuids = setOf(writeUuid, notifyUuid),
    )

    private fun ByteArray.hexUpper(): String =
        joinToString("") { byte -> "%02X".format(byte.toInt() and 0xff) }

    private companion object {
        val SERVICE_UUID: Uuid = Uuid.parse("53300001-0023-4bd4-bbd5-a6920e4c5653")
        val NOTIFY_UUID: Uuid = Uuid.parse("53300002-0023-4bd4-bbd5-a6920e4c5653")
        val WRITE_UUID: Uuid = Uuid.parse("53300003-0023-4bd4-bbd5-a6920e4c5653")
        val SHORT_SERVICE_UUID: Uuid = Uuid.parse("00000001-0000-1000-8000-00805f9b34fb")
        val SHORT_NOTIFY_UUID: Uuid = Uuid.parse("00000002-0000-1000-8000-00805f9b34fb")
        val SHORT_WRITE_UUID: Uuid = Uuid.parse("00000003-0000-1000-8000-00805f9b34fb")
        val FF12_SERVICE_UUID: Uuid = Uuid.parse("0000ff12-0000-1000-8000-00805f9b34fb")
        val FF14_NOTIFY_UUID: Uuid = Uuid.parse("0000ff14-0000-1000-8000-00805f9b34fb")
        val FF15_WRITE_UUID: Uuid = Uuid.parse("0000ff15-0000-1000-8000-00805f9b34fb")
    }
}
