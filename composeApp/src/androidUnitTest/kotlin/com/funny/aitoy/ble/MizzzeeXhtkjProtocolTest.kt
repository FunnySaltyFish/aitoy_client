@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package com.funny.aitoy.ble

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class MizzzeeXhtkjProtocolTest {
    @Test
    fun xhtkjFingerprintExposesOfficialModesAndStrength() {
        val fingerprint = mizzzeeXhtkjFingerprint(name = "小猫爪")
        val protocol = BleProtocolRegistry.resolveNative(fingerprint)
            ?: error("Mizzzee XHTKJ protocol not resolved")

        assertEquals("mizzzee_xhtkj", protocol.status.id)
        assertEquals(ToyControlStyle.ExclusivePatternOrIntensity, protocol.status.controlStyle)
        assertEquals(10, protocol.status.modeMax)
        assertEquals(100, protocol.status.intensityMax)

        val init = protocol.initialize(fingerprint)
        val initSubscribe = assertIs<BleProtocolOperation.SubscribeNotify>(init[0])
        assertEquals(NOTIFY_UUID, initSubscribe.characteristicUuid)
        val initWrite = assertIs<BleProtocolOperation.Write>(init[1])
        assertEquals(WRITE_UUID, initWrite.characteristicUuid)
        assertFalse(initWrite.withResponse)
        assertEquals("0312F600", initWrite.bytes.hexUpper())

        val mode = protocol.commandsFor(ToyControlAction.Pattern(4)).single()
        val modeWrite = assertIs<BleProtocolOperation.Write>(mode)
        assertEquals(WRITE_UUID, modeWrite.characteristicUuid)
        assertEquals("031204" + "00".repeat(17), modeWrite.bytes.hexUpper())

        val strength = protocol.commandsFor(ToyControlAction.Intensity(50)).single()
        val strengthWrite = assertIs<BleProtocolOperation.Write>(strength)
        assertEquals("0312F300FC00FE40013CA600FC00FE40013CA600", strengthWrite.bytes.hexUpper())

        val stop = protocol.commandsFor(ToyControlAction.Stop)
        assertEquals("0312F007" + "00".repeat(16), assertIs<BleProtocolOperation.Write>(stop[0]).bytes.hexUpper())
        assertEquals("0312F400" + "00".repeat(16), assertIs<BleProtocolOperation.Write>(stop[1]).bytes.hexUpper())
    }

    @Test
    fun ankniQd1WithTraceManufacturerUsesOfficialXhtFramesBeforeLegacyAaRoute() {
        val fingerprint = mizzzeeXhtkjFingerprint(
            name = "ANKNI QD 1",
            manufacturerData = "0x642:",
        )
        val protocols = BleProtocolRegistry.resolveNativeAll(fingerprint)

        assertEquals("ankni_qd1_xhtkj", protocols.first().status.id)
        assertTrue(protocols.any { it.status.id == "ankni_qd1_ff12_gatt" })

        val protocol = protocols.first()
        val init = protocol.initialize(fingerprint)
        assertEquals(NOTIFY_UUID, assertIs<BleProtocolOperation.SubscribeNotify>(init[0]).characteristicUuid)
        assertEquals("0312F600", assertIs<BleProtocolOperation.Write>(init[1]).bytes.hexUpper())

        val strength = assertIs<BleProtocolOperation.Write>(
            protocol.commandsFor(ToyControlAction.Intensity(50)).single(),
        )
        assertEquals("0312F300FC00FE40013CA600FC00FE40013CA600", strength.bytes.hexUpper())
    }

    @Test
    fun xhtkjInitializationStillWritesHandshakeWhenNotifyCharacteristicIsAbsent() {
        val fingerprint = mizzzeeXhtkjFingerprint(
            name = "XHTKJ",
            includeNotify = false,
        )
        val protocol = BleProtocolRegistry.resolveNative(fingerprint)
            ?: error("Mizzzee XHTKJ protocol not resolved")

        val init = protocol.initialize(fingerprint).single()
        val initWrite = assertIs<BleProtocolOperation.Write>(init)
        assertEquals("0312F600", initWrite.bytes.hexUpper())
    }

    private fun mizzzeeXhtkjFingerprint(
        name: String,
        manufacturerData: String = "",
        includeNotify: Boolean = true,
    ): BleGattFingerprint =
        BleGattFingerprint(
            name = name,
            manufacturerData = manufacturerData,
            scanRecordHex = "",
            serviceUuids = setOf(SERVICE_UUID),
            characteristicUuids = buildSet {
                add(WRITE_UUID)
                if (includeNotify) add(NOTIFY_UUID)
            },
        )

    private fun ByteArray.hexUpper(): String =
        joinToString("") { byte -> "%02X".format(byte.toInt() and 0xff) }

    private companion object {
        val SERVICE_UUID: Uuid = Uuid.parse("0000ff10-0000-1000-8000-00805f9b34fb")
        val NOTIFY_UUID: Uuid = Uuid.parse("0000ff11-0000-1000-8000-00805f9b34fb")
        val WRITE_UUID: Uuid = Uuid.parse("0000ff12-0000-1000-8000-00805f9b34fb")
    }
}
