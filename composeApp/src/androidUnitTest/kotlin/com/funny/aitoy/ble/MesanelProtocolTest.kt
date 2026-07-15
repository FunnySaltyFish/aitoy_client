package com.funny.aitoy.ble

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class MesanelProtocolTest {
    @Test
    fun cocoResolvesToTwoFunctionProfile() {
        val protocol = BleProtocolRegistry.resolveNative(fingerprint("coco"))
            ?: error("Mesanel coco protocol not resolved")

        assertEquals("mesanel_coco", protocol.status.id)
        assertEquals(ToyControlStyle.IndependentFunctions, protocol.status.controlStyle)
        assertEquals(listOf("舔动", "吮吸"), protocol.status.channelNames)
        assertEquals(1, protocol.status.features[0].max)
        assertEquals(10, protocol.status.features[0].modeMax)
        assertEquals(10, protocol.status.independentFunctionModeMax(protocol.status.features[0]))
        assertEquals(1, protocol.status.features[1].max)
        assertEquals(3, protocol.status.features[1].modeMax)
        assertEquals(3, protocol.status.independentFunctionModeMax(protocol.status.features[1]))
    }

    @Test
    fun cocoProKeepsOfficialThirdFunctionCode() {
        val protocol = BleProtocolRegistry.resolveNative(fingerprint("cocoPro"))
            ?: error("Mesanel cocopro protocol not resolved")

        assertEquals("mesanel_cocopro", protocol.status.id)
        assertEquals(listOf("舔动", "吮吸", "震动棒"), protocol.status.channelNames)
        assertEquals(4, protocol.status.independentFunctionCode(protocol.status.features[2]))
        assertEquals(0, protocol.status.features[2].modeMax)

        val third = protocol.commandsFor(ToyControlAction.Combined(mode = 4 * 100 + 1, intensity = 7))
        assertEquals("C37C216554", assertIs<BleProtocolOperation.Write>(third.single()).bytes.hexUpper())
    }

    @Test
    fun controlFramesMatchOfficialEncryption() {
        val protocol = BleProtocolRegistry.resolveNative(fingerprint("coco"))
            ?: error("Mesanel coco protocol not resolved")

        val licking = protocol.commandsFor(ToyControlAction.Combined(mode = 1 * 100 + 10, intensity = 1))
        assertEquals("C37C246854", assertIs<BleProtocolOperation.Write>(licking.single()).bytes.hexUpper())

        val suction = protocol.commandsFor(ToyControlAction.Combined(mode = 2 * 100 + 3, intensity = 1))
        assertEquals("C37C27616A", assertIs<BleProtocolOperation.Write>(suction.single()).bytes.hexUpper())
    }

    @Test
    fun zeroIntensityStopsSelectedFunctionAndGlobalStopStopsAll() {
        val protocol = BleProtocolRegistry.resolveNative(fingerprint("coco"))
            ?: error("Mesanel coco protocol not resolved")

        val selectedStop = protocol.commandsFor(ToyControlAction.Combined(mode = 2 * 100 + 1, intensity = 0))
        assertEquals("C37E276261", assertIs<BleProtocolOperation.Write>(selectedStop.single()).bytes.hexUpper())

        val globalStop = protocol.commandsFor(ToyControlAction.Stop)
        assertEquals("C37E256263", assertIs<BleProtocolOperation.Write>(globalStop.single()).bytes.hexUpper())
    }

    @Test
    fun initializeSubscribesNotifyAndQueriesDeviceInfo() {
        val fp = fingerprint("handou")
        val protocol = BleProtocolRegistry.resolveNative(fp)
            ?: error("Mesanel handou protocol not resolved")

        val init = protocol.initialize(fp)
        assertEquals(NOTIFY_UUID, assertIs<BleProtocolOperation.SubscribeNotify>(init[0]).characteristicUuid)
        assertEquals("C34E256273", assertIs<BleProtocolOperation.Write>(init[1]).bytes.hexUpper())
    }

    @Test
    fun unknownFf60DeviceFallsBackToOfficialGenericProfile() {
        val protocol = BleProtocolRegistry.resolveNative(fingerprint("XY-123"))
            ?: error("Mesanel generic protocol not resolved")

        assertEquals("mesanel_generic", protocol.status.id)
        assertEquals(listOf("功能 1", "功能 2", "功能 3"), protocol.status.channelNames)
        assertEquals(0, protocol.status.independentFunctionModeMax(protocol.status.features[1]))
    }

    private fun fingerprint(name: String): BleGattFingerprint = BleGattFingerprint(
        name = name,
        address = "12:34:56:78:9A:BC",
        manufacturerData = "",
        scanRecordHex = "",
        serviceUuids = setOf(SERVICE_UUID),
        characteristicUuids = setOf(WRITE_UUID, NOTIFY_UUID),
    )

    private fun ByteArray.hexUpper(): String =
        joinToString("") { byte -> "%02X".format(byte.toInt() and 0xff) }

    private companion object {
        val SERVICE_UUID: Uuid = Uuid.parse("0000ff60-0000-1000-8000-00805f9b34fb")
        val WRITE_UUID: Uuid = Uuid.parse("0000ff61-0000-1000-8000-00805f9b34fb")
        val NOTIFY_UUID: Uuid = Uuid.parse("0000ff62-0000-1000-8000-00805f9b34fb")
    }
}
