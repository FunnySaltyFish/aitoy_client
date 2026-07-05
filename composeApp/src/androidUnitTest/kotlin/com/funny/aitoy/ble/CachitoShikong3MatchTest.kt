package com.funny.aitoy.ble

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 失控 3.0 识别对齐官方 Cachito `BleManufacturerDataScanner` / `ControlShiKongFragment` 的 ScanFilter：
 * company id `0x0071` + 厂商数据首字节 `0x0B`。
 *
 * 覆盖：命中命名设备、命中厂商数据匿名广播、拒绝微软 Swift Pair(`0x0006`) 噪声、
 * 拒绝同样使用 company `0x0071` 但类型位是 `0x17` 的司康沃 HJ-002。
 */
class CachitoShikong3MatchTest {
    private val protocol = CachitoShikong3BroadcastProtocol

    @Test
    fun matchesNamedShikong3Device() {
        val device = ScannedBleDevice(
            name = "失控3.0",
            address = "27:44:66:5F:31:F7",
            rssi = -50,
            connectable = true,
        )
        assertTrue(protocol.matches(device))
    }

    @Test
    fun matchesAnonymousBroadcastByManufacturerType() {
        // 官方 company 0x0071，数据首字节 0x0B 即失控 3.0 类型位。
        val device = ScannedBleDevice(
            name = "未命名设备",
            address = "AA:BB:CC:DD:EE:FF",
            rssi = -60,
            connectable = false,
            manufacturerData = "0x71:0B 12 34 56",
        )
        assertTrue(protocol.matches(device))
    }

    @Test
    fun rejectsMicrosoftSwiftPairNoise() {
        // company 0x0006 是微软 Swift Pair(Windows 电脑) 广播，不是玩具。
        val device = ScannedBleDevice(
            name = "未命名设备",
            address = "17:75:34:41:74:3A",
            rssi = -70,
            connectable = false,
            manufacturerData = "0x6:01 09 20 22 74 F1 D6 D6 1A 19 FC 0F 32",
        )
        assertFalse(protocol.matches(device))
    }

    @Test
    fun rejectsSvakomHj002SharingCompanyId() {
        // HJ-002 也用 company 0x0071，但类型位是 0x17（失控 4.0 语义），不能误判成失控 3.0。
        val device = ScannedBleDevice(
            name = "HJ-002",
            address = "F5:0C:00:00:03:83",
            rssi = -55,
            connectable = true,
            manufacturerData = "0x27:53 56 41 02 FF, 0x71:17 00 00 00 00 00",
        )
        assertFalse(protocol.matches(device))
    }
}
