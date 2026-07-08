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
    fun appleManufacturerWithRandomNameIsNotShownAsControllable() {
        val device = ScannedBleDevice(
            name = "NGNXKCJI501zwdMQMt661rTLA",
            address = "62:CD:63:20:E1:F4",
            rssi = -60,
            connectable = true,
            manufacturerData = "0x4c:0C 0E 00 65 88 F1 A9",
        )

        assertTrue(device.isLikelyAppleContinuityDevice)
        assertFalse(device.controllable)
    }

    @Test
    fun windowsDeviceIsNotShownAsControllable() {
        val device = ScannedBleDevice(
            name = "Windows Device",
            address = "56:DE:A1:66:06:63",
            rssi = -70,
            connectable = true,
        )

        assertTrue(device.isLikelySystemPeripheral)
        assertFalse(device.controllable)
    }

    @Test
    fun genericGattOnlyConnectableDeviceCanBeAttempted() {
        val device = ScannedBleDevice(
            name = "未命名设备",
            address = "01:45:C3:8B:08:6E",
            rssi = -90,
            connectable = true,
            serviceUuids = listOf("00001801-0000-1000-8000-00805f9b34fb"),
        )

        assertFalse(device.isLikelySystemPeripheral)
        assertTrue(device.controllable)
    }

    @Test
    fun unknownConnectableDeviceCanBeAttempted() {
        val device = ScannedBleDevice(
            name = "未命名设备",
            address = "43:31:06:44:A5:AB",
            rssi = -85,
            connectable = true,
        )

        assertTrue(device.controllable)
    }

    @Test
    fun chokerBtDeviceCanBeAttempted() {
        val device = ScannedBleDevice(
            name = "BT003250",
            address = "14:DC:51:01:BF:BC",
            rssi = -84,
            connectable = true,
        )

        assertFalse(device.isLikelyAppleContinuityDevice)
        assertTrue(device.controllable)
    }

    @Test
    fun chokerBtDeviceWithCustomGattServiceCanStillBeAttempted() {
        val device = ScannedBleDevice(
            name = "BT003250",
            address = "14:DC:51:01:BF:BC",
            rssi = -84,
            connectable = true,
            serviceUuids = listOf("0000fff0-0000-1000-8000-00805f9b34fb"),
        )

        assertFalse(device.isLikelyAppleContinuityDevice)
        assertTrue(device.controllable)
    }

    @Test
    fun customGattServiceDeviceCanStillBeAttempted() {
        val device = ScannedBleDevice(
            name = "Custom Toy",
            address = "22:33:44:55:66:77",
            rssi = -64,
            connectable = true,
            serviceUuids = listOf("0000fff0-0000-1000-8000-00805f9b34fb"),
        )

        assertFalse(device.isLikelySystemPeripheral)
        assertTrue(device.controllable)
    }
}
