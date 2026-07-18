package com.funny.aitoy.ble

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class AnkniProtocolTest {
    @Test
    fun ankniMxResolvesBeforeGenericYwtdRoute() {
        val protocol = BleProtocolRegistry.resolveNative(ankniMxFingerprint())
            ?: error("ANKNI MX protocol not resolved")

        assertEquals("ankni_mx", protocol.status.id)
        assertEquals(ToyControlStyle.IndependentFunctions, protocol.status.controlStyle)
        assertEquals(listOf("吮吸", "震动"), protocol.status.channelNames)
        assertEquals(10, protocol.status.modeMax)
        assertEquals("constrict", protocol.status.features[0].type)
        assertEquals("vibrate", protocol.status.features[1].type)
    }

    @Test
    fun ankniMxKeepsGenericYwtdForUnknownDdddDevices() {
        val protocol = BleProtocolRegistry.resolveNative(ankniMxFingerprint(name = "ANKNI Other"))
            ?: error("generic ANKNI DDDD protocol not resolved")

        assertEquals("ankni_ywtd", protocol.status.id)
    }

    @Test
    fun ankniBxUsesFfe1SingleSuctionRoute() {
        val fingerprint = BleGattFingerprint(
            name = "ANKNI BX",
            address = "8B:C2:D2:16:6D:33",
            manufacturerData = "0xf0f:06 75 72 74 2D 30 31 00 00 00 00 00 00 00",
            scanRecordHex = "",
            serviceUuids = setOf(FFE0_SERVICE_UUID),
            characteristicUuids = setOf(FFE1_WRITE_UUID),
        )
        val protocol = BleProtocolRegistry.resolveNative(fingerprint)
            ?: error("ANKNI BX protocol not resolved")

        assertEquals("ankni_bx", protocol.status.id)
        assertEquals(ToyControlStyle.IntensityOnly, protocol.status.controlStyle)
        assertEquals("吮吸", protocol.status.intensityLabel)
        assertEquals(10, protocol.status.intensityMax)

        val init = protocol.initialize(fingerprint).single()
        assertEquals(FFE1_WRITE_UUID, assertIs<BleProtocolOperation.SubscribeNotify>(init).characteristicUuid)

        val run = protocol.commandsFor(ToyControlAction.Intensity(5)).single()
        assertEquals("AA080232C8AE", assertIs<BleProtocolOperation.Write>(run).bytes.hexUpper())

        val stop = protocol.commandsFor(ToyControlAction.Stop).single()
        assertEquals("AA08020000B4", assertIs<BleProtocolOperation.Write>(stop).bytes.hexUpper())
    }

    @Test
    fun ankniMxWritesIndependentModeStateForSuctionAndVibration() {
        val protocol = BleProtocolRegistry.resolveNative(ankniMxFingerprint())
            ?: error("ANKNI MX protocol not resolved")

        val suction = protocol.commandsFor(ToyControlAction.Combined(mode = 1 * 100 + 5, intensity = 0))
        assertEquals("AA0F020500C0", assertIs<BleProtocolOperation.Write>(suction.single()).bytes.hexUpper())

        val vibration = protocol.commandsFor(ToyControlAction.Combined(mode = 2 * 100 + 3, intensity = 0))
        assertEquals("AA0F020503C3", assertIs<BleProtocolOperation.Write>(vibration.single()).bytes.hexUpper())
    }

    @Test
    fun ankniMxDualControlWritesTwoMotorSlideFrame() {
        val protocol = BleProtocolRegistry.resolveNative(ankniMxFingerprint())
            ?: error("ANKNI MX protocol not resolved")

        val dual = protocol.commandsFor(ToyControlAction.DualMotor(mode = 1, internalIntensity = 25, externalIntensity = 40))

        assertEquals(1, dual.size)
        assertEquals("AA08031928C8BE", assertIs<BleProtocolOperation.Write>(dual.single()).bytes.hexUpper())
    }

    @Test
    fun ankniMxStopClearsModeAndSlideState() {
        val protocol = BleProtocolRegistry.resolveNative(ankniMxFingerprint())
            ?: error("ANKNI MX protocol not resolved")

        val stop = protocol.commandsFor(ToyControlAction.Stop)

        assertEquals(2, stop.size)
        assertEquals("AA0F020000BB", assertIs<BleProtocolOperation.Write>(stop[0]).bytes.hexUpper())
        assertEquals("AA0803000000B5", assertIs<BleProtocolOperation.Write>(stop[1]).bytes.hexUpper())
    }

    private fun ankniMxFingerprint(
        name: String = "ANKNI MX",
    ) = BleGattFingerprint(
        name = name,
        address = "10:11:DD:0E:BA:E8",
        manufacturerData = "",
        scanRecordHex = "",
        serviceUuids = setOf(SERVICE_UUID),
        characteristicUuids = setOf(WRITE_UUID, NOTIFY_UUID),
    )

    private fun ByteArray.hexUpper(): String =
        joinToString("") { byte -> "%02X".format(byte.toInt() and 0xff) }

    private companion object {
        val SERVICE_UUID: Uuid = Uuid.parse("0000dddd-0000-1000-8000-00805f9b34fb")
        val WRITE_UUID: Uuid = Uuid.parse("0000ddd1-0000-1000-8000-00805f9b34fb")
        val NOTIFY_UUID: Uuid = Uuid.parse("0000ddd2-0000-1000-8000-00805f9b34fb")
        val FFE0_SERVICE_UUID: Uuid = Uuid.parse("0000ffe0-0000-1000-8000-00805f9b34fb")
        val FFE1_WRITE_UUID: Uuid = Uuid.parse("0000ffe1-0000-1000-8000-00805f9b34fb")
    }
}
