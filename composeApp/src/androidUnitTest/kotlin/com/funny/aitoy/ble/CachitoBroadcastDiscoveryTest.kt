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
    fun snTouhuanMiniTraceUsesBroadcastRouteInsteadOfGattOtaRoute() {
        val matches = BleBroadcastProtocolRegistry.resolveAll(
            ScannedBleDevice(
                name = "SN-9AQ1TR02789",
                address = "C1:19:32:00:03:D0",
                rssi = -55,
                connectable = true,
                serviceUuids = listOf("0000ffb0-0000-1000-8000-00805f9b34fb"),
                manufacturerData = "0x504:C1 19 32 00 03 D0",
                scanRecordHex = "02 01 05 03 02 B0 FF 09 FF 04 05 C1 19 32 00 03 D0 " +
                    "0F 09 53 4E 2D 39 41 51 31 54 52 30 32 37 38 39 02 0A 00",
            ),
        )

        val protocol = matches.single()
        assertEquals("cachito_touhuan_mini_advertise", protocol.status.id)
        assertFalse(protocol.preferGattWhenConnectable)

        val run = protocol.commandsFor(ToyControlAction.Intensity(70)).single().serviceUuid
        val stop = protocol.commandsFor(ToyControlAction.Stop).single().serviceUuid
        assertTrue(run.matchesUuidBody("-8200-", "-0100-6400005502"))
        assertTrue(stop.matchesUuidBody("-0F00-", "-0100-0000000000"))
    }

    @Test
    fun recognizesDaxiuAndShikong3FromOfficialProductTypeBytes() {
        val daxiuMatches = BleBroadcastProtocolRegistry.resolveAll(
            ScannedBleDevice(
                name = "未命名设备",
                address = "AA:BB:CC:DD:EE:01",
                rssi = -60,
                connectable = false,
                manufacturerData = "0x71:0E 00",
            ),
        )
        val shikong3Matches = BleBroadcastProtocolRegistry.resolveAll(
            ScannedBleDevice(
                name = "未命名设备",
                address = "AA:BB:CC:DD:EE:02",
                rssi = -60,
                connectable = false,
                manufacturerData = "113:23 00",
            ),
        )

        assertTrue(daxiuMatches.any { it.status.id == "cachito_daxiu_advertise" })
        assertTrue(shikong3Matches.any { it.status.id == "cachito_shikong3_advertise" })
    }

    @Test
    fun recognizesOfficialParsedManufacturerFrameText() {
        val matches = BleBroadcastProtocolRegistry.resolveAll(
            ScannedBleDevice(
                name = "未命名设备",
                address = "AA:BB:CC:DD:EE:03",
                rssi = -60,
                connectable = false,
                manufacturerData = "71000B0000",
            ),
        )

        assertTrue(matches.any { it.status.id == "cachito_shikong3_advertise" })
    }

    @Test
    fun doesNotTreatCachitoControlServiceUuidAsScannedDevice() {
        val controlAdvertisement = ScannedBleDevice(
            name = "未命名设备",
            address = "4A:7D:03:DD:85:68",
            rssi = -58,
            connectable = true,
            serviceUuids = listOf("710002ef-0400-dd82-0302-0000000000ca"),
            scanRecordHex = "02 01 1A 11 07 CA 00 00 00 00 00 02 03 82 DD 00 EF 02 00 71",
        )
        val matches = BleBroadcastProtocolRegistry.resolveAll(
            controlAdvertisement,
        )

        assertEquals(emptyList(), matches.map { it.status.id })
        assertTrue(controlAdvertisement.isLikelyCachitoControlAdvertisement)
        assertFalse(controlAdvertisement.controllable)
    }

    @Test
    fun cachitoQuickConnectDevicesResolveDirectlyToBroadcastProtocols() {
        val quickDevices = BleBroadcastProtocolRegistry.cachitoQuickConnectDevices()
        val shikong2 = quickDevices.first { it.broadcastProtocolName == "Cachito 失控 2.0" }
        val daxiu = quickDevices.first { it.broadcastProtocolName == "Cachito 大秀 3.0" }

        assertTrue(quickDevices.size >= 5)
        assertEquals(
            "cachito_shikong2_advertise",
            BleBroadcastProtocolRegistry.resolveAll(shikong2).first().status.id,
        )
        assertEquals(
            "cachito_daxiu_advertise",
            BleBroadcastProtocolRegistry.resolveAll(daxiu).single().status.id,
        )
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
    fun matchesShikong4ForHj002WhenSelectedRecordOnlyKeepsSvakomManufacturer() {
        // 线上 Trace 出现过同一台 HJ-002 的扫描帧带 0x71:17，但点击连接时仅保留 SVA(0x27) 厂商数据。
        // 这种情况下仍应走失控 4.0 广播，避免被 Svakom Iker GATT 路由抢走。
        val matches = BleBroadcastProtocolRegistry.resolveAll(
            ScannedBleDevice(
                name = "HJ-002",
                address = "F5:0C:00:00:09:47",
                rssi = -49,
                connectable = true,
                manufacturerData = "0x27:53 56 41 02 FF 50 0C 00 00 09 47 FF 50 00 00 0D 19 30 BB 00 01",
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
    fun shikongSeriesTreatZeroIntensityAsStop() {
        val shikong2StopFrames = CachitoShikong2BroadcastProtocol
            .commandsFor(ToyControlAction.Combined(mode = 1, intensity = 0))
            .map { it.serviceUuid }
        val shikong3StopFrames = CachitoShikong3BroadcastProtocol
            .commandsFor(ToyControlAction.Combined(mode = 1, intensity = 0))
            .map { it.serviceUuid }
        val shikong25StopFrames = CachitoShikong25BroadcastProtocol
            .commandsFor(ToyControlAction.Combined(mode = 1, intensity = 0))
            .map { it.serviceUuid }

        assertTrue(shikong2StopFrames.any { it.matchesUuidBody("-0400-", "-0302-0000000000") })
        assertTrue(shikong2StopFrames.any { it.matchesUuidBody("-0400-", "-0601-0000000000") })
        assertFalse(shikong2StopFrames.any { it.contains("-050A-", ignoreCase = true) })

        assertTrue(shikong3StopFrames.any { it.matchesUuidBody("-0200-", "-0100-6400000002") })
        assertTrue(shikong3StopFrames.any { it.matchesUuidBody("-0100-", "-0100-6400000002") })
        assertTrue(shikong3StopFrames.any { it.matchesUuidBody("-0F00-", "-0100-6400000002") })
        assertFalse(shikong3StopFrames.any { it.contains("-8200-", ignoreCase = true) })

        assertTrue(shikong25StopFrames.any { it.matchesUuidBody("-0200-", "-0100-6400000002") })
        assertTrue(shikong25StopFrames.any { it.matchesUuidBody("-0100-", "-0100-6400000002") })
        assertTrue(shikong25StopFrames.any { it.matchesUuidBody("-0F00-", "-0100-6400000002") })
        assertFalse(shikong25StopFrames.any { it.contains("-8200-", ignoreCase = true) })
    }

    @Test
    fun daxiuUsesOfficialMotionRtdHeatAndStopFrames() {
        val protocol = CachitoDaxiuBroadcastProtocol

        assertEquals(ToyControlStyle.IndependentFunctions, protocol.status.controlStyle)
        assertEquals(listOf("推拉深度", "伸出速度", "缩回速度", "入体端强度", "加热温度"), protocol.status.features.map { it.label })

        val thrust = protocol.commandsFor(ToyControlAction.Combined(mode = 100, intensity = 50)).single().serviceUuid
        assertTrue(thrust.matchesUuidBody("-8800-", "-0000-2B02020000"))

        val extendSpeed = protocol.commandsFor(ToyControlAction.Combined(mode = 200, intensity = 30)).single().serviceUuid
        assertTrue(extendSpeed.matchesUuidBody("-8800-", "-0000-2B05020000"))

        val enterStrength = protocol.commandsFor(ToyControlAction.Combined(mode = 400, intensity = 40)).single().serviceUuid
        assertTrue(enterStrength.matchesUuidBody("-8200-", "-0100-6400003102"))

        val heat = protocol.commandsFor(ToyControlAction.Combined(mode = 500, intensity = 50)).single().serviceUuid
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
        assertTrue(device.controllable)
    }

    @Test
    fun doesNotTreatMicrosoftSwiftPairAsCachitoCandidate() {
        val device = ScannedBleDevice(
            name = "未命名设备",
            address = "17:75:34:41:74:3A",
            rssi = -70,
            connectable = false,
            manufacturerData = "0x6:01 09 20 22 74 F1 D6 D6 1A 19 FC 0F 32",
            scanRecordHex = "1A FF 06 00 01 09 20 22 74 F1 D6 D6 1A 19 FC 0F 32",
        )

        assertFalse(BleBroadcastProtocolRegistry.isUnconfirmedCachitoBroadcastDevice(device))
        assertFalse(device.controllable)
    }

    @Test
    fun allowsUnrecognizedNonConnectableBroadcastToBeTriedWithoutSystemPeripherals() {
        val rawOnlyBroadcast = ScannedBleDevice(
            name = "未命名设备",
            address = "AA:BB:CC:DD:EE:FF",
            rssi = -70,
            connectable = false,
            scanRecordHex = "17 2B 01 00 30 D7 4C B9",
        )
        val appleBroadcast = ScannedBleDevice(
            name = "未命名设备",
            address = "11:22:33:44:55:66",
            rssi = -50,
            connectable = false,
            manufacturerData = "0x4c:12 02 00 01",
            scanRecordHex = "07 FF 4C 00 12 02 00 01",
        )

        assertTrue(rawOnlyBroadcast.controllable)
        assertFalse(appleBroadcast.controllable)
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
