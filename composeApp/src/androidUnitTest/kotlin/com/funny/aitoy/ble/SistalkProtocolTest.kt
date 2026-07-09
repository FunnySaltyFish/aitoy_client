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
        val fingerprint = weightless808Fingerprint()
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
        assertEquals(3, run.size)
        val startupWrite = assertIs<BleProtocolOperation.Write>(run[0])
        assertEquals(FUNCTION_UUID, startupWrite.characteristicUuid)
        assertFalse(startupWrite.withResponse)
        assertEquals("A098024646", startupWrite.bytes.hexUpper())
        assertEquals(BleProtocolOperation.Sleep(120L), run[1])
        val runWrite = assertIs<BleProtocolOperation.Write>(run[2])
        assertEquals("A098021946", runWrite.bytes.hexUpper())

        val lower = protocol.commandsFor(ToyControlAction.DualMotor(mode = 1, internalIntensity = 20, externalIntensity = 30))
        val lowerWrite = assertIs<BleProtocolOperation.Write>(lower.single())
        assertEquals("A09802141E", lowerWrite.bytes.hexUpper())

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

    @Test
    fun localizedWeightless808NameAlsoMatchesDedicatedRoute() {
        val protocol = BleProtocolRegistry.resolveNative(
            sistalkV2Fingerprint(name = "失重808", manufacturerData = "0xffff:02 00 00 00")
        ) ?: error("localized 失重808 protocol not resolved")

        assertEquals("sistalk_weightless_808", protocol.status.id)
    }

    @Test
    fun catTailWeightless808FingerprintMatchesDedicatedRoute() {
        val protocol = BleProtocolRegistry.resolveNative(
            sistalkV2Fingerprint(name = "猫尾巴", manufacturerData = "0x2512:02 00 1C 00 00 00 00 00 00 00")
        ) ?: error("猫尾巴 失重808 protocol not resolved")

        assertEquals("sistalk_weightless_808", protocol.status.id)
        val run = protocol.commandsFor(ToyControlAction.Combined(mode = 1, intensity = 23))
        assertEquals(3, run.size)
        val startupWrite = assertIs<BleProtocolOperation.Write>(run[0])
        assertEquals("A098024600", startupWrite.bytes.hexUpper())
        assertEquals(BleProtocolOperation.Sleep(120L), run[1])
        val targetWrite = assertIs<BleProtocolOperation.Write>(run[2])
        assertEquals("A098021700", targetWrite.bytes.hexUpper())
    }

    @Test
    fun weightless808ProductIdMatchesEvenWhenAdvertisedNameChanges() {
        val protocol = BleProtocolRegistry.resolveNative(
            sistalkV2Fingerprint(name = "SISTALK", manufacturerData = "0x2512:02 00 1C 00 00 00 00 00 00 00")
        ) ?: error("renamed 失重808 protocol not resolved")

        assertEquals("sistalk_weightless_808", protocol.status.id)
    }

    @Test
    fun monsterPubWithoutWeightlessManufacturerKeepsGenericRoute() {
        val protocol = BleProtocolRegistry.resolveNative(
            sistalkV2Fingerprint(name = "MonsterPub", manufacturerData = "0x2512:02 00 08 00 0D")
        ) ?: error("generic MonsterPub V2 protocol not resolved")

        assertEquals("sistalk_monsterparty", protocol.status.id)
    }

    private fun weightless808Fingerprint(): BleGattFingerprint =
        sistalkV2Fingerprint(
            name = "MonsterPub",
            manufacturerData = "0x2512:02 00 07 00 0D 00 00 00 00 00",
        )

    private fun sistalkV2Fingerprint(name: String): BleGattFingerprint =
        BleGattFingerprint(
            name = name,
            manufacturerData = "0xffff:02 00 00 00",
            scanRecordHex = "",
            serviceUuids = setOf(SERVICE_UUID),
            characteristicUuids = setOf(OP_UUID, FUNCTION_UUID),
        )

    private fun sistalkV2Fingerprint(name: String, manufacturerData: String): BleGattFingerprint =
        BleGattFingerprint(
            name = name,
            manufacturerData = manufacturerData,
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
