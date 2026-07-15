package com.funny.aitoy

import com.funny.aitoy.ble.BleProtocolFeature
import com.funny.aitoy.ble.BleProtocolStatus
import com.funny.aitoy.ble.independentFunctionModeMax
import kotlin.test.Test
import kotlin.test.assertEquals

class RelayScalarFeatureValueTest {
    @Test
    fun independentScalarUsesFeatureRangeDirectly() {
        val flap = BleProtocolFeature(type = "flap", min = 0, max = 1, index = 0, label = "拍打")

        assertEquals(0, mapScalarFeatureValue(0, flap))
        assertEquals(1, mapScalarFeatureValue(1, flap))
        assertEquals(1, mapScalarFeatureValue(35, flap))
    }

    @Test
    fun independentScalarClampsToFeatureMax() {
        val vibrate = BleProtocolFeature(type = "vibrate", min = 0, max = 10, index = 1, label = "震动")

        assertEquals(6, mapScalarFeatureValue(6, vibrate))
        assertEquals(10, mapScalarFeatureValue(100, vibrate))
    }

    @Test
    fun independentFunctionModeUsesFeatureSpecificModeMax() {
        val status = BleProtocolStatus(id = "beyourlover_va617a_vibrate", modeMax = 9)
        val vibrate = BleProtocolFeature(type = "vibrate", min = 0, max = 10, index = 0, label = "震动", modeMax = 9)

        assertEquals(9, status.independentFunctionModeMax(vibrate))
    }
}
