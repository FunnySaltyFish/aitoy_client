package com.funny.aitoy.ble

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class FeelBoxProtocolTest {
    @Test
    fun officialDeviceNamesResolveToFeelBoxProfiles() {
        assertEquals("feelbox_planet", resolve("S071").status.id)
        assertEquals("feelbox_planet", resolve("FEEL PLANET").status.id)
        assertEquals("feelbox_moon", resolve("FEELBOX 02").status.id)
        assertEquals("feelbox_moon", resolve("FEEL MOON").status.id)
        assertEquals("feelbox_bobo", resolve("FB-BOBO").status.id)
        assertEquals("feelbox_bobo", resolve("FEEL BOBO").status.id)
        assertEquals("feelbox_lucky", resolve("FB-LUCKY").status.id)
        assertEquals("feelbox_lucky", resolve("FB-LUCKY BAG").status.id)
        assertEquals("feelbox_lucky", resolve("FEEL LUCKY").status.id)
    }

    @Test
    fun matcherRequiresOfficialNameAndGattShape() {
        assertEquals(null, BleProtocolRegistry.resolveNative(fingerprint("FEELBOX")))
        assertEquals(null, BleProtocolRegistry.resolveNative(fingerprint("FEEL MOON", characteristicUuids = setOf(WRITE_UUID))))
        assertEquals(
            null,
            BleProtocolRegistry.resolveNative(fingerprint("FEEL MOON", serviceUuids = setOf(Uuid.parse("0000fff0-0000-1000-8000-00805f9b34fb")))),
        )
    }

    @Test
    fun planetIsSingleSuctionIntensityProtocol() {
        val protocol = resolve("FEEL PLANET")

        assertEquals("感觉星球", protocol.status.displayName)
        assertEquals(ToyControlStyle.IntensityOnly, protocol.status.controlStyle)
        assertEquals(40, protocol.status.intensityMax)
        assertEquals(listOf("吮吸"), protocol.status.channelNames)
        assertEquals("constrict", protocol.status.features.single().type)

        val command = protocol.commandsFor(ToyControlAction.Intensity(12)).single()
        assertEquals("AA030102030C000000000000000000000000", assertIs<BleProtocolOperation.Write>(command).bytes.hexUpper())
        assertEquals(false, assertIs<BleProtocolOperation.Write>(command).withResponse)

        val stop = protocol.commandsFor(ToyControlAction.Stop).single()
        assertEquals("AA0301020300000000000000000000000000", assertIs<BleProtocolOperation.Write>(stop).bytes.hexUpper())
    }

    @Test
    fun moonExposesIndependentSuctionVibrationAndPatternFunctions() {
        val protocol = resolve("FEEL MOON")

        assertEquals("紫月", protocol.status.displayName)
        assertEquals(ToyControlStyle.IndependentFunctions, protocol.status.controlStyle)
        assertEquals(listOf("吮吸", "震动", "拍打"), protocol.status.channelNames)
        assertEquals(listOf("constrict", "vibrate", "thrust"), protocol.status.features.map { it.type })
        assertEquals(listOf(0, 0, 9), protocol.status.features.map { it.modeMax })

        val suction = protocol.commandsFor(ToyControlAction.Combined(mode = 1 * 100 + 1, intensity = 15)).single()
        assertEquals("AA030102030F000000000000000000000000", assertIs<BleProtocolOperation.Write>(suction).bytes.hexUpper())

        val vibration = protocol.commandsFor(ToyControlAction.Combined(mode = 2 * 100 + 1, intensity = 20)).single()
        assertEquals("AA0503140314000000000000000000000000", assertIs<BleProtocolOperation.Write>(vibration).bytes.hexUpper())

        val pattern = protocol.commandsFor(ToyControlAction.Combined(mode = 3 * 100 + 4, intensity = 20)).single()
        assertEquals("AA0504140414000000000000000000000000", assertIs<BleProtocolOperation.Write>(pattern).bytes.hexUpper())
    }

    @Test
    fun initializeSubscribesNotifyAndSendsOfficialBootFrames() {
        val fp = fingerprint("FEEL PLANET")
        val protocol = BleProtocolRegistry.resolveNative(fp)
            ?: error("FeelBox protocol not resolved")

        val init = protocol.initialize(fp)
        assertEquals(NOTIFY_UUID, assertIs<BleProtocolOperation.SubscribeNotify>(init[0]).characteristicUuid)
        assertEquals("AA0100000000000000000000000000000000", assertIs<BleProtocolOperation.Write>(init[1]).bytes.hexUpper())
        assertEquals("AA0400000000000000000000000000000000", assertIs<BleProtocolOperation.Write>(init[2]).bytes.hexUpper())
        assertEquals(false, assertIs<BleProtocolOperation.Write>(init[1]).withResponse)
    }

    @Test
    fun multiFunctionStopZerosEveryOfficialControlPath() {
        val protocol = resolve("FEEL MOON")

        val stop = protocol.commandsFor(ToyControlAction.Stop)
        assertEquals(3, stop.size)
        assertEquals("AA0301020300000000000000000000000000", assertIs<BleProtocolOperation.Write>(stop[0]).bytes.hexUpper())
        assertEquals("AA0503000300000000000000000000000000", assertIs<BleProtocolOperation.Write>(stop[1]).bytes.hexUpper())
        assertEquals("AA0501000100000000000000000000000000", assertIs<BleProtocolOperation.Write>(stop[2]).bytes.hexUpper())
    }

    private fun resolve(name: String): BleDeviceProtocol =
        BleProtocolRegistry.resolveNative(fingerprint(name))
            ?: error("FeelBox protocol not resolved for $name")

    private fun fingerprint(
        name: String,
        serviceUuids: Set<Uuid> = setOf(SERVICE_UUID),
        characteristicUuids: Set<Uuid> = setOf(WRITE_UUID, NOTIFY_UUID),
    ): BleGattFingerprint = BleGattFingerprint(
        name = name,
        address = "12:34:56:78:9A:BC",
        manufacturerData = "",
        scanRecordHex = "",
        serviceUuids = serviceUuids,
        characteristicUuids = characteristicUuids,
    )

    private fun ByteArray.hexUpper(): String =
        joinToString("") { byte -> "%02X".format(byte.toInt() and 0xff) }

    private companion object {
        val SERVICE_UUID: Uuid = Uuid.parse("0000bae0-0000-1000-8000-00805f9b34fb")
        val WRITE_UUID: Uuid = Uuid.parse("0000bae1-0000-1000-8000-00805f9b34fb")
        val NOTIFY_UUID: Uuid = Uuid.parse("0000bae2-0000-1000-8000-00805f9b34fb")
    }
}
