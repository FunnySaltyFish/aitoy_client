package com.funny.aitoy.ble

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class LoyoProtocolTest {
    @Test
    fun xw49UsesLoyoElectricProtocolBeforeHistoricalKissToy() {
        val protocols = BleProtocolRegistry.resolveNativeAll(loyoFingerprint("XW49"))

        assertEquals(listOf("loyo_xw49"), protocols.map { it.status.id })
        val protocol = protocols.single()
        assertEquals("LOYO 千羽", protocol.status.displayName)
        assertEquals(ToyControlStyle.CombinedPatternAndIntensity, protocol.status.controlStyle)
        assertEquals("电流频率", protocol.status.modeLabel)
        assertEquals("电流强度", protocol.status.intensityLabel)
        assertEquals(listOf(BleProtocolFeature(type = "shock", min = 0, max = 100, index = 0, label = "电流")), protocol.status.features)

        val init = protocol.initialize(loyoFingerprint("XW49"))
        assertEquals(1, init.size)
        assertEquals(LOYO_NOTIFY_UUID, assertIs<BleProtocolOperation.SubscribeNotify>(init.single()).characteristicUuid)
    }

    @Test
    fun xw49CombinedControlWritesOfficialElectricFrame() {
        val protocol = BleProtocolRegistry.resolveNative(loyoFingerprint("XW49")) ?: error("LOYO XW49 protocol not resolved")

        val run = protocol.commandsFor(ToyControlAction.Combined(mode = 3, intensity = 40))
        val write = assertSingleLoyoWrite(run)

        assertEquals(listOf(0x5A, 0x00, 0x00, 0x01, 0x90, 0x03, 0x28, 0x00, 0x00, 0x00), decryptLoyoPayload(write.bytes))
    }

    @Test
    fun xw49StopClearsMotorAndElectricOutput() {
        val protocol = BleProtocolRegistry.resolveNative(loyoFingerprint("XW49")) ?: error("LOYO XW49 protocol not resolved")

        val stop = protocol.commandsFor(ToyControlAction.Stop)

        assertEquals(3, stop.size)
        val motorStop = assertIs<BleProtocolOperation.Write>(stop[0])
        assertEquals(listOf(0x5A, 0x00, 0x00, 0x01, 0x31, 0x00, 0x00, 0x00, 0x00, 0x00), decryptLoyoPayload(motorStop.bytes))
        assertEquals(100L, assertIs<BleProtocolOperation.Sleep>(stop[1]).millis)
        val electricStop = assertIs<BleProtocolOperation.Write>(stop[2])
        assertEquals(listOf(0x5A, 0x00, 0x00, 0x01, 0x90, 0x00, 0x00, 0x00, 0x00, 0x00), decryptLoyoPayload(electricStop.bytes))
    }

    @Test
    fun xw54UsesDualMotorLoyoFrame() {
        val protocol = BleProtocolRegistry.resolveNative(loyoFingerprint("XW54")) ?: error("LOYO XW54 protocol not resolved")

        assertEquals("loyo_xw54", protocol.status.id)
        assertEquals(ToyControlStyle.DualIntensityOnly, protocol.status.controlStyle)
        assertEquals(listOf("外震", "内震"), protocol.status.channelNames)

        val run = protocol.commandsFor(ToyControlAction.DualMotor(mode = 1, internalIntensity = 30, externalIntensity = 70))
        val write = assertSingleLoyoWrite(run)

        assertEquals(listOf(0x5A, 0x00, 0x00, 0x01, 0x40, 0x03, 0x1E, 0x46, 0x00, 0x00), decryptLoyoPayload(write.bytes))
    }

    @Test
    fun nameAloneDoesNotMatchLoyo() {
        val protocol = BleProtocolRegistry.resolveNative(
            loyoFingerprint("XW49").copy(
                serviceUuids = emptySet(),
                characteristicUuids = emptySet(),
            )
        )

        assertEquals(null, protocol)
    }

    private fun assertSingleLoyoWrite(operations: List<BleProtocolOperation>): BleProtocolOperation.Write {
        assertEquals(1, operations.size)
        val write = assertIs<BleProtocolOperation.Write>(operations.single())
        assertEquals(LOYO_WRITE_UUID, write.characteristicUuid)
        assertEquals(12, write.bytes.size)
        assertEquals(0x23, write.bytes[0].toInt() and 0xff)
        assertEquals(true, write.withResponse)
        return write
    }

    private fun decryptLoyoPayload(encryptedBytes: ByteArray): List<Int> {
        assertEquals(12, encryptedBytes.size)
        val encrypted = encryptedBytes.map { it.toInt() and 0xff }
        val raw = MutableList(12) { 0 }
        raw[0] = encrypted[0]
        val seed = raw[0]
        for (index in 1 until raw.size) {
            val key = LoyoEncryptedFrameCodec.key(encrypted[index - 1], index)
            raw[index] = (((encrypted[index] - key) and 0xff) xor seed xor key) and 0xff
        }
        val checksum = raw.take(11).sum() and 0xff
        assertEquals(checksum, raw[11])
        return raw.drop(1).take(10)
    }

    private fun loyoFingerprint(name: String): BleGattFingerprint =
        BleGattFingerprint(
            name = name,
            manufacturerData = "0x642:",
            scanRecordHex = "",
            serviceUuids = setOf(LOYO_SERVICE_UUID),
            characteristicUuids = setOf(LOYO_WRITE_UUID, LOYO_NOTIFY_UUID),
        )

    private companion object {
        val LOYO_SERVICE_UUID: Uuid = Uuid.parse("00001000-0000-1000-8000-00805f9b34fb")
        val LOYO_WRITE_UUID: Uuid = Uuid.parse("00001001-0000-1000-8000-00805f9b34fb")
        val LOYO_NOTIFY_UUID: Uuid = Uuid.parse("00001002-0000-1000-8000-00805f9b34fb")
    }
}
