@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package com.funny.aitoy.ble

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
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

        val init = protocol.initialize(fingerprint).single()
        val initWrite = assertIs<BleProtocolOperation.Write>(init)
        assertEquals(WRITE_UUID, initWrite.characteristicUuid)
        assertFalse(initWrite.withResponse)
        assertEquals("0312F600", initWrite.bytes.hexUpper())

        val mode = protocol.commandsFor(ToyControlAction.Pattern(4)).single()
        val modeWrite = assertIs<BleProtocolOperation.Write>(mode)
        assertEquals(WRITE_UUID, modeWrite.characteristicUuid)
        assertEquals("0312F4040000000000000000000000000000000000", modeWrite.bytes.hexUpper())

        val strength = protocol.commandsFor(ToyControlAction.Intensity(50)).single()
        val strengthWrite = assertIs<BleProtocolOperation.Write>(strength)
        assertEquals("0312F300FC00FE40013DB000FC00FE40013DB000", strengthWrite.bytes.hexUpper())

        val stop = protocol.commandsFor(ToyControlAction.Stop)
        assertEquals("0312F0070000000000000000000000000000000000", assertIs<BleProtocolOperation.Write>(stop[0]).bytes.hexUpper())
        assertEquals("0312F4000000000000000000000000000000000000", assertIs<BleProtocolOperation.Write>(stop[1]).bytes.hexUpper())
    }

    private fun mizzzeeXhtkjFingerprint(name: String): BleGattFingerprint =
        BleGattFingerprint(
            name = name,
            manufacturerData = "",
            scanRecordHex = "",
            serviceUuids = setOf(SERVICE_UUID),
            characteristicUuids = setOf(WRITE_UUID),
        )

    private fun ByteArray.hexUpper(): String =
        joinToString("") { byte -> "%02X".format(byte.toInt() and 0xff) }

    private companion object {
        val SERVICE_UUID: Uuid = Uuid.parse("0000ff10-0000-1000-8000-00805f9b34fb")
        val WRITE_UUID: Uuid = Uuid.parse("0000ff12-0000-1000-8000-00805f9b34fb")
    }
}
