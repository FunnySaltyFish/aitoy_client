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
    fun monsterPartyMp2ProductIdUsesDedicatedDualVibrationControl() {
        val fingerprint = sistalkV2Fingerprint(
            name = "Ankni",
            manufacturerData = "0x2512:02 00 18 00 3E 00 00 00 00 00",
        )
        val protocol = BleProtocolRegistry.resolveNative(fingerprint)
            ?: error("小怪兽二代 protocol not resolved")

        assertEquals("sistalk_monsterparty_mp2", protocol.status.id)
        assertEquals("小怪兽二代", protocol.status.displayName)
        assertEquals(ToyControlStyle.DualIntensityOnly, protocol.status.controlStyle)
        assertEquals(listOf("内侧震动", "外侧震动"), protocol.status.channelNames)
        assertEquals("vibrate", protocol.status.features[0].type)
        assertEquals("vibrate", protocol.status.features[1].type)

        protocol.initialize(fingerprint)
        val run = protocol.commandsFor(ToyControlAction.DualMotor(mode = 1, internalIntensity = 25, externalIntensity = 70))
        assertEquals(3, run.size)
        val startupWrite = assertIs<BleProtocolOperation.Write>(run[0])
        assertEquals("A098024646", startupWrite.bytes.hexUpper())
        assertEquals(BleProtocolOperation.Sleep(120L), run[1])
        val targetWrite = assertIs<BleProtocolOperation.Write>(run[2])
        assertEquals("A098021946", targetWrite.bytes.hexUpper())

        val stop = protocol.commandsFor(ToyControlAction.Stop)
        val stopWrite = assertIs<BleProtocolOperation.Write>(stop.single())
        assertEquals("A098020000", stopWrite.bytes.hexUpper())
    }

    @Test
    fun popocatProductIdUsesDedicatedSuctionAndCatHeadMapping() {
        val fingerprint = sistalkV2Fingerprint(
            name = "MoulinRouge",
            manufacturerData = "0x2512:02 00 27 00 00 00 00 00 00 00",
        )
        val protocol = BleProtocolRegistry.resolveNative(fingerprint)
            ?: error("popocat protocol not resolved")

        assertEquals("sistalk_popocat", protocol.status.id)
        assertEquals(ToyControlStyle.IndependentFunctions, protocol.status.controlStyle)
        assertEquals(listOf("吮吸", "猫头震动", "拓展震动"), protocol.status.channelNames)
        assertEquals("constrict", protocol.status.features[0].type)
        assertEquals("vibrate", protocol.status.features[1].type)
        assertEquals("vibrate", protocol.status.features[2].type)

        val suction = protocol.commandsFor(ToyControlAction.Combined(mode = 100, intensity = 25))
        val suctionWrite = assertIs<BleProtocolOperation.Write>(suction.single())
        assertEquals("A0A003001900", suctionWrite.bytes.hexUpper())

        val catHead = protocol.commandsFor(ToyControlAction.Combined(mode = 200, intensity = 70))
        val catHeadWrite = assertIs<BleProtocolOperation.Write>(catHead.single())
        assertEquals("A0A003461900", catHeadWrite.bytes.hexUpper())

        val expansion = protocol.commandsFor(ToyControlAction.Combined(mode = 300, intensity = 60))
        val expansionWrite = assertIs<BleProtocolOperation.Write>(expansion.single())
        assertEquals("A0A00346193C", expansionWrite.bytes.hexUpper())

        val dual = protocol.commandsFor(ToyControlAction.DualMotor(mode = 1, internalIntensity = 30, externalIntensity = 80))
        val dualWrite = assertIs<BleProtocolOperation.Write>(dual.single())
        assertEquals("A0A003501E3C", dualWrite.bytes.hexUpper())

        val stop = protocol.commandsFor(ToyControlAction.Stop)
        val stopWrite = assertIs<BleProtocolOperation.Write>(stop.single())
        assertEquals("A0A003000000", stopWrite.bytes.hexUpper())
    }

    @Test
    fun localizedWeightless808NameAlsoMatchesDedicatedRoute() {
        val protocol = BleProtocolRegistry.resolveNative(
            sistalkV2Fingerprint(name = "失重808", manufacturerData = "0xffff:02 00 00 00")
        ) ?: error("localized 失重808 protocol not resolved")

        assertEquals("sistalk_weightless_808", protocol.status.id)
    }

    @Test
    fun whaleTailProductIdUsesSingleEightLevelVibrationControl() {
        val protocol = BleProtocolRegistry.resolveNative(
            sistalkV2Fingerprint(name = "猫尾巴", manufacturerData = "0x2512:02 00 1C 00 00 00 00 00 00 00")
        ) ?: error("小鲸尾 protocol not resolved")

        assertEquals("sistalk_whale_tail", protocol.status.id)
        assertEquals("小鲸尾", protocol.status.displayName)
        assertEquals(ToyControlStyle.IntensityOnly, protocol.status.controlStyle)
        assertEquals(8, protocol.status.intensityMax)
        assertFalse(protocol.status.supportsMode)
        assertEquals(listOf("震动"), protocol.status.channelNames)
        assertEquals("vibrate", protocol.status.features.single().type)

        val run = protocol.commandsFor(ToyControlAction.Intensity(2))
        assertEquals(3, run.size)
        val startupWrite = assertIs<BleProtocolOperation.Write>(run[0])
        assertEquals(FUNCTION_UUID, startupWrite.characteristicUuid)
        assertEquals("A0900146", startupWrite.bytes.hexUpper())
        assertEquals(BleProtocolOperation.Sleep(120L), run[1])
        val targetWrite = assertIs<BleProtocolOperation.Write>(run[2])
        assertEquals("A0900119", targetWrite.bytes.hexUpper())

        val full = protocol.commandsFor(ToyControlAction.Intensity(8))
        val fullWrite = assertIs<BleProtocolOperation.Write>(full.single())
        assertEquals("A0900164", fullWrite.bytes.hexUpper())

        val stop = protocol.commandsFor(ToyControlAction.Stop)
        val stopWrite = assertIs<BleProtocolOperation.Write>(stop.single())
        assertEquals("A0900100", stopWrite.bytes.hexUpper())
    }

    @Test
    fun whaleTailProductIdMatchesEvenWhenAdvertisedNameChanges() {
        val protocol = BleProtocolRegistry.resolveNative(
            sistalkV2Fingerprint(name = "SISTALK", manufacturerData = "0x2512:02 00 1C 00 00 00 00 00 00 00")
        ) ?: error("renamed 小鲸尾 protocol not resolved")

        assertEquals("sistalk_whale_tail", protocol.status.id)
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
