package com.funny.aitoy.ble

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ScannedBleDeviceTest {
    @Test
    fun appleContinuityDeviceIsNotShownAsControllable() {
        val device = ScannedBleDevice(
            name = "未命名设备",
            address = "51:58:D8:97:FE:60",
            rssi = -50,
            connectable = true,
            manufacturerData = "0x4c:10 05 19 1C 05 AA D9",
        )

        assertTrue(device.isLikelyAppleContinuityDevice)
        assertFalse(device.controllable)
    }

    @Test
    fun chokerBtDeviceCanStillBeAttempted() {
        val device = ScannedBleDevice(
            name = "BT003250",
            address = "14:DC:51:01:BF:BC",
            rssi = -84,
            connectable = true,
        )

        assertFalse(device.isLikelyAppleContinuityDevice)
        assertTrue(device.controllable)
    }
}
