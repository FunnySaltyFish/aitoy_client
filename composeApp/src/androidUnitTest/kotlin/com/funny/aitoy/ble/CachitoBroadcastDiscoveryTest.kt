package com.funny.aitoy.ble

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CachitoBroadcastDiscoveryTest {
    @Test
    fun recognizesAnonymousDaxiuFromSpacedRawManufacturerRecord() {
        val matches = BleBroadcastProtocolRegistry.resolveAll(
            scanRecordDevice("02 01 06 05 FF 71 00 03"),
        )

        assertTrue(matches.any { it.status.id == "cachito_daxiu_advertise" })
    }

    @Test
    fun recognizesKnownPrefixesFromRawRecordForShikongAndTemplates() {
        val shikongMatches = BleBroadcastProtocolRegistry.resolveAll(
            scanRecordDevice("02 01 06 05 FF 71 00 0B"),
        )
        val touhuanProMatches = BleBroadcastProtocolRegistry.resolveAll(
            scanRecordDevice("02 01 06 05 FF 71 00 0C"),
        )

        assertTrue(shikongMatches.any { it.status.id == "cachito_shikong3_advertise" })
        assertTrue(touhuanProMatches.any { it.status.id == "cachito_touhuan_pro_advertise" })
    }

    @Test
    fun matchesShikong4ForHj002CarryingBothSvakomAndCachitoMarkers() {
        // HJ-002 是司康沃/失控双品牌 OEM 设备，广播同时带 SVA(0x27) 与 Cachito(0x71:17) 标记，
        // 但控制通道是失控 4.0 广播；官方仅按 company 0x0071 + 首字节 0x17 识别，不能因 SVA 标记排除。
        val matches = BleBroadcastProtocolRegistry.resolveAll(
            ScannedBleDevice(
                name = "HJ-002",
                address = "F5:0C:00:00:03:83",
                rssi = -55,
                connectable = true,
                manufacturerData = "0x27:53 56 41 02 FF, 0x71:17 00 00 00 00 00",
            ),
        )

        assertTrue(matches.any { it.status.id == "cachito_shikong4_advertise" })
    }

    @Test
    fun shikong4UsesOfficialBaseAndPulseBroadcastFrames() {
        val protocol = CachitoShikong4BroadcastProtocol

        assertEquals(7, protocol.status.modeMax)
        assertEquals("基础强度", protocol.status.modeNames.first())

        val base = protocol.commandsFor(ToyControlAction.Combined(mode = 1, intensity = 40)).single().serviceUuid
        assertTrue(base.matchesUuidBody("-5100-", "-0100-6400004602"))

        val pulse = protocol.commandsFor(ToyControlAction.Combined(mode = 2, intensity = 40)).single().serviceUuid
        assertTrue(pulse.matchesUuidBody("-5200-", "-0100-2D3E150002"))

        val pulseStop = protocol.commandsFor(ToyControlAction.Combined(mode = 2, intensity = 0)).single().serviceUuid
        assertTrue(pulseStop.matchesUuidBody("-0200-", "-0100-6400000002"))

        val stopFrames = protocol.commandsFor(ToyControlAction.Stop).map { it.serviceUuid }
        assertTrue(stopFrames.any { it.matchesUuidBody("-0100-", "-0100-6400000002") })
        assertTrue(stopFrames.any { it.matchesUuidBody("-0F00-", "-0100-6400000002") })
        assertTrue(stopFrames.any { it.matchesUuidBody("-0200-", "-0100-6400000002") })
    }

    @Test
    fun daxiuUsesOfficialMotionRtdHeatAndStopFrames() {
        val protocol = CachitoDaxiuBroadcastProtocol

        val thrust = protocol.commandsFor(ToyControlAction.Combined(mode = 1, intensity = 50)).single().serviceUuid
        assertTrue(thrust.matchesUuidBody("-8800-", "-0000-2B08080000"))

        val enterStrength = protocol.commandsFor(ToyControlAction.Combined(mode = 4, intensity = 40)).single().serviceUuid
        assertTrue(enterStrength.matchesUuidBody("-8200-", "-0100-6400003102"))

        val heat = protocol.commandsFor(ToyControlAction.Combined(mode = 5, intensity = 50)).single().serviceUuid
        assertTrue(heat.matchesUuidBody("-9000-", "-0000-2D00000000"))

        val stopFrames = protocol.commandsFor(ToyControlAction.Stop).map { it.serviceUuid }
        assertTrue(stopFrames.any { it.matchesUuidBody("-8800-", "-0000-0000000000") })
        assertTrue(stopFrames.any { it.matchesUuidBody("-0200-", "-0100-0000000002") })
        assertTrue(stopFrames.any { it.matchesUuidBody("-1F00-", "-0000-0000000000") })
    }

    @Test
    fun keepsUnknownCachitoManufacturerVisibleWithoutEnablingControl() {
        val device = ScannedBleDevice(
            name = "未命名设备",
            address = "AA:BB:CC:DD:EE:FF",
            rssi = -60,
            connectable = false,
            manufacturerData = "0x71:7F 12 34 56",
        )

        assertTrue(BleBroadcastProtocolRegistry.resolveAll(device).isEmpty())
        assertTrue(BleBroadcastProtocolRegistry.isUnconfirmedCachitoBroadcastDevice(device))
        assertFalse(device.controllable)
    }

    @Test
    fun doesNotTreatMicrosoftSwiftPairAsCachitoCandidate() {
        val device = ScannedBleDevice(
            name = "未命名设备",
            address = "17:75:34:41:74:3A",
            rssi = -70,
            connectable = false,
            manufacturerData = "0x6:01 09 20 22 74 F1 D6 D6 1A 19 FC 0F 32",
        )

        assertFalse(BleBroadcastProtocolRegistry.isUnconfirmedCachitoBroadcastDevice(device))
    }

    private fun scanRecordDevice(scanRecordHex: String) = ScannedBleDevice(
        name = "未命名设备",
        address = "AA:BB:CC:DD:EE:FF",
        rssi = -60,
        connectable = false,
        scanRecordHex = scanRecordHex,
    )

    private fun String.matchesUuidBody(commandPart: String, payloadPart: String): Boolean {
        val upper = uppercase()
        return upper.startsWith("7100") &&
            upper.contains(commandPart) &&
            upper.contains(payloadPart) &&
            upper.length == 36
    }
}
