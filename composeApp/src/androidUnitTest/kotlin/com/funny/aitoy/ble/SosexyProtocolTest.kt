package com.funny.aitoy.ble

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class SosexyProtocolTest {
    @Test
    fun sosexyFingerprintResolvesToBoboBeiProtocol() {
        val protocol = BleProtocolRegistry.resolveNative(sosexyFingerprint())
            ?: error("SOSEXY protocol not resolved")

        assertEquals("sosexy_bobo_bei", protocol.status.id)
        assertEquals(ToyControlStyle.IndependentFunctions, protocol.status.controlStyle)
        assertEquals(listOf("吮吸", "震动", "电流"), protocol.status.channelNames)
        assertEquals(100, protocol.status.intensityMax)
        assertEquals(3, protocol.status.features.size)
        assertEquals("constrict", protocol.status.features[0].type)
        assertEquals("vibrate", protocol.status.features[1].type)
        assertEquals("shock", protocol.status.features[2].type)
    }

    @Test
    fun matcherRequiresOfficialNameAndCharacteristics() {
        assertEquals(null, BleProtocolRegistry.resolveNative(sosexyFingerprint(name = "BOBO")))
        assertEquals(null, BleProtocolRegistry.resolveNative(sosexyFingerprint(characteristicUuids = setOf(WRITE_UUID))))
    }

    @Test
    fun scalarFunctionsWriteOnlySelectedMotorFrameWithResponse() {
        val protocol = BleProtocolRegistry.resolveNative(sosexyFingerprint())
            ?: error("SOSEXY protocol not resolved")

        val suction = protocol.commandsFor(ToyControlAction.Combined(mode = 1 * 100 + 1, intensity = 50))
        assertEquals(1, suction.size)
        assertEquals("010100020007113200081101", assertIs<BleProtocolOperation.Write>(suction[0]).bytes.hexUpper())
        assertEquals(true, assertIs<BleProtocolOperation.Write>(suction[0]).withResponse)

        val vibration = protocol.commandsFor(ToyControlAction.Combined(mode = 2 * 100 + 1, intensity = 30))
        assertEquals("010100020001111E00021101", assertIs<BleProtocolOperation.Write>(vibration[0]).bytes.hexUpper())

        val electric = protocol.commandsFor(ToyControlAction.Combined(mode = 3 * 100 + 1, intensity = 20))
        assertEquals("010100020003111400041101", assertIs<BleProtocolOperation.Write>(electric[0]).bytes.hexUpper())
    }

    @Test
    fun stopSendsAllThreeMotorZeroFrames() {
        val protocol = BleProtocolRegistry.resolveNative(sosexyFingerprint())
            ?: error("SOSEXY protocol not resolved")

        val stop = protocol.commandsFor(ToyControlAction.Stop)
        assertEquals(3, stop.size)
        assertEquals("010100020007110000081101", assertIs<BleProtocolOperation.Write>(stop[0]).bytes.hexUpper())
        assertEquals("010100020001110000021101", assertIs<BleProtocolOperation.Write>(stop[1]).bytes.hexUpper())
        assertEquals("010100020003110000041101", assertIs<BleProtocolOperation.Write>(stop[2]).bytes.hexUpper())
    }

    private fun sosexyFingerprint(
        name: String = "SOSEXY",
        characteristicUuids: Set<Uuid> = setOf(WRITE_UUID, NOTIFY_UUID),
    ) = BleGattFingerprint(
        name = name,
        address = "00:11:22:33:44:55",
        manufacturerData = "",
        scanRecordHex = "",
        serviceUuids = emptySet(),
        characteristicUuids = characteristicUuids,
    )

    private fun ByteArray.hexUpper(): String =
        joinToString("") { byte -> "%02X".format(byte.toInt() and 0xff) }

    private companion object {
        val WRITE_UUID: Uuid = Uuid.parse("0000ee03-0000-1000-8000-00805f9b34fb")
        val NOTIFY_UUID: Uuid = Uuid.parse("0000ee02-0000-1000-8000-00805f9b34fb")
    }
}
