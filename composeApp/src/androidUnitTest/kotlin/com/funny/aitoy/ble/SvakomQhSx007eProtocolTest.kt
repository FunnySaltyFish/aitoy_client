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
    fun sx589aLiveAdvertisementResolvesToSameStructureAsSx119b() {
        val protocol = BleProtocolRegistry.resolveNative(sx589aFingerprint())
            ?: error("SX589A protocol not resolved")

        assertEquals("svakom_sx589a", protocol.status.id)
        assertEquals(ToyControlStyle.IndependentFunctions, protocol.status.controlStyle)
        assertEquals(listOf("吮吸", "震动"), protocol.status.channelNames)
        assertEquals(11, protocol.status.modeMax)
        assertEquals(5, protocol.status.features.first { it.type == "constrict" }.max)
        assertEquals(10, protocol.status.features.first { it.type == "vibrate" }.max)
    }

    @Test
    fun sx589aUsesEightSuckGearsAndElevenVibrateGears() {
        val protocol = BleProtocolRegistry.resolveNative(sx589aFingerprint())
            ?: error("SX589A protocol not resolved")

        val suck = protocol.commandsFor(ToyControlAction.Combined(mode = 3 * 100 + 8, intensity = 5))
        assertEquals(1, suck.size)
        assertEquals("55090000080500", assertIs<BleProtocolOperation.Write>(suck[0]).bytes.hexUpper())

        val vibrate = protocol.commandsFor(ToyControlAction.Combined(mode = 2 * 100 + 11, intensity = 10))
        assertEquals(1, vibrate.size)
        assertEquals("550300000B0A00", assertIs<BleProtocolOperation.Write>(vibrate[0]).bytes.hexUpper())
    }

    @Test
    fun st462aLiveAdvertisementResolvesToThreeFunctionProfile() {
        val protocol = BleProtocolRegistry.resolveNative(st462aFingerprint())
            ?: error("ST462A protocol not resolved")

        assertEquals("svakom_st462a", protocol.status.id)
        assertEquals(ToyControlStyle.IndependentFunctions, protocol.status.controlStyle)
        // 官方 case 82 顺序：舔、吮吸、震动。
        assertEquals(listOf("舔", "吮吸", "震动"), protocol.status.channelNames)
        // 舔/震动 10 档、吮吸 3 档：面板取最大值 10。
        assertEquals(10, protocol.status.modeMax)
        assertEquals(10, protocol.status.features.first { it.type == "lick" }.max)
        // 吮吸无强度滑杆：intensityMax=1。
        assertEquals(1, protocol.status.features.first { it.type == "constrict" }.max)
        assertEquals(10, protocol.status.features.first { it.type == "vibrate" }.max)
    }

    @Test
    fun st462aLickUsesCommand0x14WithGearAndStrength() {
        val protocol = BleProtocolRegistry.resolveNative(st462aFingerprint())
            ?: error("ST462A protocol not resolved")

        // 舔（功能码 6，档位 10，强度 8）：官方 case 5 命令字 0x14，帧 55 14 00 00 <档> <强> 00。
        val lick = protocol.commandsFor(ToyControlAction.Combined(mode = 6 * 100 + 10, intensity = 8))
        assertEquals(1, lick.size)
        assertEquals("551400000A0800", assertIs<BleProtocolOperation.Write>(lick[0]).bytes.hexUpper())
    }

    @Test
    fun st462aSuckHasThreeGearsWithoutStrength() {
        val protocol = BleProtocolRegistry.resolveNative(st462aFingerprint())
            ?: error("ST462A protocol not resolved")

        // 吮吸（功能码 3，档位 3）：官方无 suck_strong_max，强度字节恒 0，只靠档位启停。
        val suck = protocol.commandsFor(ToyControlAction.Combined(mode = 3 * 100 + 3, intensity = 9))
        assertEquals(1, suck.size)
        assertEquals("55090000030000", assertIs<BleProtocolOperation.Write>(suck[0]).bytes.hexUpper())
    }

    @Test
    fun st462aVibrateUsesCommand0x03WithGearAndStrength() {
        val protocol = BleProtocolRegistry.resolveNative(st462aFingerprint())
            ?: error("ST462A protocol not resolved")

        val vibrate = protocol.commandsFor(ToyControlAction.Combined(mode = 2 * 100 + 10, intensity = 7))
        assertEquals(1, vibrate.size)
        assertEquals("550300000A0700", assertIs<BleProtocolOperation.Write>(vibrate[0]).bytes.hexUpper())
    }

    @Test
    fun va617aSuckEndpointResolvesFromLiveGiftCatAdvertisement() {
        val fingerprint = va617aSuckFingerprint(characteristicUuids = setOf(WRITE_UUID))
        val protocol = BleProtocolRegistry.resolveNative(fingerprint)
            ?: error("VA617A_S protocol not resolved")

        assertEquals("beyourlover_va617a_suck", protocol.status.id)
        assertEquals(ToyControlStyle.IndependentFunctions, protocol.status.controlStyle)
        assertEquals(listOf("吮吸"), protocol.status.channelNames)
        assertEquals(8, protocol.status.modeMax)
        assertEquals(10, protocol.status.features.first { it.type == "constrict" }.max)
        assertEquals(emptyList(), protocol.initialize(fingerprint))

        val suck = protocol.commandsFor(ToyControlAction.Combined(mode = 3 * 100 + 8, intensity = 10))
        assertEquals(1, suck.size)
        assertEquals("55090000080A00", assertIs<BleProtocolOperation.Write>(suck[0]).bytes.hexUpper())
    }

    @Test
    fun va617aVibrateEndpointResolvesFromLiveGiftCatAdvertisement() {
        val fingerprint = va617aVibrateFingerprint()
        val protocol = BleProtocolRegistry.resolveNative(fingerprint)
            ?: error("VA617A_V protocol not resolved")

        assertEquals("beyourlover_va617a_vibrate", protocol.status.id)
        assertEquals(ToyControlStyle.IndependentFunctions, protocol.status.controlStyle)
        assertEquals(listOf("震动"), protocol.status.channelNames)
        assertEquals(9, protocol.status.modeMax)
        assertEquals(10, protocol.status.features.first { it.type == "vibrate" }.max)
        assertEquals(listOf(BleProtocolOperation.SubscribeNotify(NOTIFY_UUID)), protocol.initialize(fingerprint))

        val vibrate = protocol.commandsFor(ToyControlAction.Combined(mode = 2 * 100 + 9, intensity = 10))
        assertEquals(1, vibrate.size)
        assertEquals("55030000090A00", assertIs<BleProtocolOperation.Write>(vibrate[0]).bytes.hexUpper())
    }

    @Test
    fun va617aWithSvakomManufacturerDoesNotFallbackToSistalkMonsterParty() {
        val protocol = BleProtocolRegistry.resolveNative(
            va617aSuckFingerprint(
                serviceUuids = setOf(SISTALK_SERVICE_UUID),
                characteristicUuids = setOf(SISTALK_OP_UUID, SISTALK_FUNCTION_UUID),
            )
        )

        assertEquals(null, protocol)
    }

    @Test
    fun st462aStopSendsAllThreeChannelStopsAndGlobalFallback() {
        val protocol = BleProtocolRegistry.resolveNative(st462aFingerprint())
            ?: error("ST462A protocol not resolved")

        val stop = protocol.commandsFor(ToyControlAction.Stop)
        // 舔、吮吸、震动三路停止帧 + 全局兜底。
        assertEquals("55140000000000", assertIs<BleProtocolOperation.Write>(stop[0]).bytes.hexUpper())
        assertEquals("55090000000000", assertIs<BleProtocolOperation.Write>(stop[1]).bytes.hexUpper())
        assertEquals("55030000000000", assertIs<BleProtocolOperation.Write>(stop[2]).bytes.hexUpper())
        assertEquals("550400000000AA", assertIs<BleProtocolOperation.Write>(stop[3]).bytes.hexUpper())
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
    fun pairedDualWritesSuckThenVibrateWithASettlingInterval() {
        val protocol = BleProtocolRegistry.resolveNative(qhSx007eFingerprint())
            ?: error("QH-SX007E protocol not resolved")

        // `dual` 的内侧对应吮吸、外侧对应震动；两个独立帧之间必须留出间隔。
        val operations = protocol.commandsFor(
            ToyControlAction.DualMotor(mode = 3, internalIntensity = 5, externalIntensity = 6),
        )
        assertEquals(3, operations.size)
        assertEquals("55090000030500", assertIs<BleProtocolOperation.Write>(operations[0]).bytes.hexUpper())
        assertEquals(BleProtocolOperation.Sleep(500L), operations[1])
        assertEquals("55030000030600", assertIs<BleProtocolOperation.Write>(operations[2]).bytes.hexUpper())
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

    // 真实广播（线上 trace 2026-07-09）：name=SX589A manufacturer=0x3500:53 56 41 02 FF 35 02 C9 1D 6C DE FF 35 00 00 3B 18 0C 01 FF
    // V2 产品码 payload[13..15]=00 00 3B=0x3B=59。
    private fun sx589aFingerprint(
        name: String = "SX589A",
        manufacturerData: String = "0x3500:53 56 41 02 FF 35 02 C9 1D 6C DE FF 35 00 00 3B 18 0C 01 FF",
    ) = BleGattFingerprint(
        name = name,
        address = "EA:02:C9:1D:6C:DE",
        manufacturerData = manufacturerData,
        scanRecordHex = "",
        serviceUuids = setOf(SERVICE_UUID),
        characteristicUuids = setOf(WRITE_UUID, NOTIFY_UUID),
    )

    // 真实广播（线上 trace 2026-07-09）：name=ST462A manufacturer=0x32:53 56 41 02 FF 32 26 02 4F 61 37 FF 32 00 01 39
    // V2 产品码 payload[13..15]=00 01 39=0x139=313。
    private fun st462aFingerprint(
        name: String = "ST462A",
        manufacturerData: String = "0x32:53 56 41 02 FF 32 26 02 4F 61 37 FF 32 00 01 39",
    ) = BleGattFingerprint(
        name = name,
        address = "FF:26:02:4F:61:37",
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

    // 真实广播（线上 trace 2026-07-14）：name=大人铃/VA617A-4 manufacturer=0x32:... 00 00 3F ...
    // 官方 ProductCodeMapper：产品码 63 -> VA617A_S，ComboOldVersionMapper 配对 63 <-> 62。
    private fun va617aSuckFingerprint(
        name: String = "大人铃",
        manufacturerData: String = "0x32:53 56 41 02 32 FF 25 06 B7 61 D5 FF 32 00 00 3F 00 00 BB",
        serviceUuids: Set<Uuid> = setOf(SERVICE_UUID),
        characteristicUuids: Set<Uuid> = setOf(WRITE_UUID, NOTIFY_UUID),
    ) = BleGattFingerprint(
        name = name,
        address = "FF:25:06:B7:61:D5",
        manufacturerData = manufacturerData,
        scanRecordHex = "",
        serviceUuids = serviceUuids,
        characteristicUuids = characteristicUuids,
    )

    // 真实广播（线上 trace 2026-07-14）：name=VA617A-3 manufacturer=0x32:... 00 00 3E ...
    // 官方 ProductCodeMapper：产品码 62 -> VA617A_V。
    private fun va617aVibrateFingerprint(
        name: String = "VA617A-3",
        manufacturerData: String = "0x32:53 56 41 02 FF 32 25 05 C3 6C 8F FF 32 00 00 3E 00 00 AA",
    ) = BleGattFingerprint(
        name = name,
        address = "FF:25:05:C3:6C:8F",
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
        val SISTALK_SERVICE_UUID: Uuid = Uuid.parse("00009000-0000-1000-8000-00805f9b34fb")
        val SISTALK_OP_UUID: Uuid = Uuid.parse("00009001-0000-1000-8000-00805f9b34fb")
        val SISTALK_FUNCTION_UUID: Uuid = Uuid.parse("00009002-0000-1000-8000-00805f9b34fb")
    }
}
