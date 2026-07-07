@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package com.funny.aitoy.ble

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.uuid.Uuid

class SistalkProtocolTest {
    @Test
    fun weightless808UsesDedicatedDualVibrationControl() {
        val fingerprint = sistalkV2Fingerprint(name = "失重808")
        val protocol = BleProtocolRegistry.resolveNative(fingerprint)
            ?: error("失重808 protocol not resolved")

        assertEquals("sistalk_weightless_808", protocol.status.id)
        assertEquals(ToyControlStyle.DualIntensityOnly, protocol.status.controlStyle)
        assertEquals(listOf("头震", "尾震"), protocol.status.channelNames)
        assertEquals(100, protocol.status.intensityMax)
        assertFalse(protocol.status.supportsMode)

        val init = protocol.initialize(fingerprint)
        assertEquals(OP_UUID, assertIs<BleProtocolOperation.SubscribeNotify>(init[0]).characteristicUuid)
        assertEquals(FUNCTION_UUID, assertIs<BleProtocolOperation.SubscribeNotify>(init[1]).characteristicUuid)

        val run = protocol.commandsFor(ToyControlAction.DualMotor(mode = 1, internalIntensity = 25, externalIntensity = 70))
        val runWrite = assertIs<BleProtocolOperation.Write>(run.single())
        assertEquals(FUNCTION_UUID, runWrite.characteristicUuid)
        assertFalse(runWrite.withResponse)
        assertEquals("A098021946", runWrite.bytes.hexUpper())

        val stop = protocol.commandsFor(ToyControlAction.Stop)
        val stopWrite = assertIs<BleProtocolOperation.Write>(stop.single())
        assertEquals(FUNCTION_UUID, stopWrite.characteristicUuid)
        assertEquals("A098020000", stopWrite.bytes.hexUpper())
    }

    @Test
    fun genericMonsterPartyKeepsExistingRoute() {
        val protocol = BleProtocolRegistry.resolveNative(sistalkV2Fingerprint(name = "Monster Party"))
            ?: error("Monster Party protocol not resolved")

        assertEquals("sistalk_monsterparty", protocol.status.id)
        assertEquals(ToyControlStyle.CombinedPatternAndIntensity, protocol.status.controlStyle)
        assertEquals(listOf("吮吸", "尾部震动"), protocol.status.modeNames)

        val run = protocol.commandsFor(ToyControlAction.DualMotor(mode = 1, internalIntensity = 25, externalIntensity = 70))
        val runWrite = assertIs<BleProtocolOperation.Write>(run.single())
        assertEquals("A098024646", runWrite.bytes.hexUpper())
    }

    private fun sistalkV2Fingerprint(name: String): BleGattFingerprint =
        BleGattFingerprint(
            name = name,
            manufacturerData = "0xffff:02 00 00 00",
            scanRecordHex = "",
            serviceUuids = setOf(SERVICE_UUID),
            characteristicUuids = setOf(OP_UUID, FUNCTION_UUID),
        )

    private fun ByteArray.hexUpper(): String =
        joinToString("") { byte -> (byte.toInt() and 0xff).toString(16).uppercase().padStart(2, '0') }
}

private val SERVICE_UUID = Uuid.parse("00009000-0000-1000-8000-00805f9b34fb")
private val OP_UUID = Uuid.parse("00009001-0000-1000-8000-00805f9b34fb")
private val FUNCTION_UUID = Uuid.parse("00009002-0000-1000-8000-00805f9b34fb")
