package com.funny.aitoy.buttplug

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class SpecializedProtocolPlansTest {
    @Test
    fun cachitoPayloadUsesFeatureIndexedPrefix() {
        assertContentEquals(byteArrayOf(0x02, 0x01, 0x05, 0x00), SpecializedProtocolPlans.cachitoPayload(0, 5))
        assertContentEquals(byteArrayOf(0x03, 0x02, 0x64, 0x00), SpecializedProtocolPlans.cachitoPayload(1, 100))
    }

    @Test
    fun jejouePayloadSelectsPatternFromActiveMotor() {
        assertContentEquals(byteArrayOf(0x02, 0x04), SpecializedProtocolPlans.jejouePayload(firstSpeed = 4, secondSpeed = 0))
        assertContentEquals(byteArrayOf(0x03, 0x05), SpecializedProtocolPlans.jejouePayload(firstSpeed = 0, secondSpeed = 5))
        assertContentEquals(byteArrayOf(0x01, 0x04), SpecializedProtocolPlans.jejouePayload(firstSpeed = 4, secondSpeed = 5))
    }

    @Test
    fun satisfyerPayloadRepeatsEachFeatureSpeedFourTimes() {
        assertContentEquals(
            byteArrayOf(7, 7, 7, 7, 20, 20, 20, 20),
            SpecializedProtocolPlans.satisfyerPayload(intArrayOf(7, 20), featureCount = 2),
        )
    }

    @Test
    fun monsterPubPrefersTxVibrateOverTx() {
        assertEquals(
            "txvibrate",
            SpecializedProtocolPlans.monsterPubEndpointRole(setOf("tx", "txmode", "txvibrate"), intArrayOf(0, 0)),
        )
    }

    @Test
    fun monsterPubUsesTxModeOnlyWhenTxDeviceStops() {
        assertEquals(
            "tx",
            SpecializedProtocolPlans.monsterPubEndpointRole(setOf("tx", "txmode"), intArrayOf(4, 0)),
        )
        assertEquals(
            "txmode",
            SpecializedProtocolPlans.monsterPubEndpointRole(setOf("tx", "txmode"), intArrayOf(0, 0)),
        )
    }

    @Test
    fun monsterPubGenericPayloadUsesProtocolPrefix() {
        assertContentEquals(
            byteArrayOf(0x03, 10, 20),
            SpecializedProtocolPlans.monsterPubPayload("generic0", intArrayOf(10, 20), featureCount = 2),
        )
    }
}
