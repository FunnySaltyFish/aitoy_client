package com.funny.aitoy.ble

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class SvakomQhSx007eProtocolTest {
    @Test
    fun sx119bLiveAdvertisementResolvesToOfficialVibrateSuckProfile() {
        val protocol = BleProtocolRegistry.resolveNative(sx119bFingerprint())
            ?: error("SX119B protocol not resolved")

        assertEquals("svakom_sx119b", protocol.status.id)
        assertEquals(ToyControlStyle.IndependentFunctions, protocol.status.controlStyle)
        assertEquals(listOf("吮吸", "震动"), protocol.status.channelNames)
        assertEquals(11, protocol.status.modeMax)
        assertEquals(5, protocol.status.features.first { it.type == "constrict" }.max)
        assertEquals(10, protocol.status.features.first { it.type == "vibrate" }.max)
    }

    @Test
    fun sx119bUsesEightSuckGearsAndElevenVibrateGears() {
        val protocol = BleProtocolRegistry.resolveNative(sx119bFingerprint())
            ?: error("SX119B protocol not resolved")

        val suck = protocol.commandsFor(ToyControlAction.Combined(mode = 3 * 100 + 8, intensity = 5))
        assertEquals(1, suck.size)
        assertEquals("55090000080500", assertIs<BleProtocolOperation.Write>(suck[0]).bytes.hexUpper())

        val vibrate = protocol.commandsFor(ToyControlAction.Combined(mode = 2 * 100 + 11, intensity = 10))
        assertEquals(1, vibrate.size)
        assertEquals("550300000B0A00", assertIs<BleProtocolOperation.Write>(vibrate[0]).bytes.hexUpper())
    }

    @Test
    fun qhSx007eExposesIndependentVibrateAndSuckPanels() {
        val protocol = BleProtocolRegistry.resolveNative(qhSx007eFingerprint())
            ?: error("QH-SX007E protocol not resolved")

        assertEquals("svakom_vibrate_suck", protocol.status.id)
        assertEquals(ToyControlStyle.IndependentFunctions, protocol.status.controlStyle)
        assertEquals(listOf("震动", "吮吸"), protocol.status.channelNames)
        // 震动 10 档、吮吸 5 档：面板取两者最大值作为模式上限。
        assertEquals(10, protocol.status.modeMax)
    }

    @Test
    fun vibrateChannelWritesOnlyItsOwnFrameWithGearInByte4AndStrengthInByte5() {
        val protocol = BleProtocolRegistry.resolveNative(qhSx007eFingerprint())
            ?: error("QH-SX007E protocol not resolved")

        // 只驱动震动（功能码 2，档位 7，强度 6）：官方每次只发当前功能单帧，
        // 不再连发吮吸帧，避免设备背靠背收到无关功能帧后卡死。
        val vibrate = protocol.commandsFor(ToyControlAction.Combined(mode = 2 * 100 + 7, intensity = 6))
        assertEquals(1, vibrate.size)
        // 官方 55 03 00 00 <档位> <强度> 00：档位在 byte[4]、强度在 byte[5]。
        assertEquals("55030000070600", assertIs<BleProtocolOperation.Write>(vibrate[0]).bytes.hexUpper())
    }

    @Test
    fun suckChannelWritesOnlyItsOwnFrameWithFiveGears() {
        val protocol = BleProtocolRegistry.resolveNative(qhSx007eFingerprint())
            ?: error("QH-SX007E protocol not resolved")

        // 只驱动吮吸（功能码 3，档位 5，强度 8）：只发吮吸单帧。
        val suck = protocol.commandsFor(ToyControlAction.Combined(mode = 3 * 100 + 5, intensity = 8))
        assertEquals(1, suck.size)
        // 官方吮吸 55 09 00 00 <档位1..5> <强度> 00。
        assertEquals("55090000050800", assertIs<BleProtocolOperation.Write>(suck[0]).bytes.hexUpper())
    }

    @Test
    fun stopSendsBothChannelStopsAndGlobalFallback() {
        val protocol = BleProtocolRegistry.resolveNative(qhSx007eFingerprint())
            ?: error("QH-SX007E protocol not resolved")

        val stop = protocol.commandsFor(ToyControlAction.Stop)
        assertEquals("55030000000000", assertIs<BleProtocolOperation.Write>(stop[0]).bytes.hexUpper())
        assertEquals("55090000000000", assertIs<BleProtocolOperation.Write>(stop[1]).bytes.hexUpper())
        // 全局停止兜底帧。
        assertEquals("550400000000AA", assertIs<BleProtocolOperation.Write>(stop[2]).bytes.hexUpper())
    }

    @Test
    fun matcherStaysAheadOfButtplugAndRequiresSvakomManufacturerData() {
        val protocol = BleProtocolRegistry.resolveNative(qhSx007eFingerprint())
            ?: error("QH-SX007E protocol not resolved")
        assertEquals("svakom_vibrate_suck", protocol.status.id)

        // 缺司康沃厂商数据时不匹配 native 协议。
        assertEquals(null, BleProtocolRegistry.resolveNative(qhSx007eFingerprint(manufacturerData = "")))
    }

    // 真实广播（线上 trace 2026-07-09）：name=SX119B manufacturer=0x3500:53 56 41 02 FF 35 31 CF E7 2A CE FF 35 00 00 39 18 08 1B FF
    private fun sx119bFingerprint(
        name: String = "SX119B",
        manufacturerData: String = "0x3500:53 56 41 02 FF 35 31 CF E7 2A CE FF 35 00 00 39 18 08 1B FF",
    ) = BleGattFingerprint(
        name = name,
        address = "17:31:CF:E7:2A:CE",
        manufacturerData = manufacturerData,
        scanRecordHex = "",
        serviceUuids = setOf(SERVICE_UUID),
        characteristicUuids = setOf(WRITE_UUID, NOTIFY_UUID),
    )

    // 真实广播（线上 trace 2026-07-07）：name=QH-SX007E manufacturer=0x101:53 56 41 10 F7 FF 24 09 07 12 97 20
    private fun qhSx007eFingerprint(
        name: String = "QH-SX007E",
        manufacturerData: String = "0x101:53 56 41 10 F7 FF 24 09 07 12 97 20",
    ) = BleGattFingerprint(
        name = name,
        address = "FF:24:09:07:12:97",
        manufacturerData = manufacturerData,
        scanRecordHex = "",
        serviceUuids = setOf(SERVICE_UUID),
        characteristicUuids = setOf(WRITE_UUID, NOTIFY_UUID),
    )

    private fun ByteArray.hexUpper(): String =
        joinToString("") { byte -> "%02X".format(byte.toInt() and 0xff) }

    private companion object {
        val SERVICE_UUID: Uuid = Uuid.parse("0000ffe0-0000-1000-8000-00805f9b34fb")
        val WRITE_UUID: Uuid = Uuid.parse("0000ffe1-0000-1000-8000-00805f9b34fb")
        val NOTIFY_UUID: Uuid = Uuid.parse("0000ffe2-0000-1000-8000-00805f9b34fb")
    }
}
