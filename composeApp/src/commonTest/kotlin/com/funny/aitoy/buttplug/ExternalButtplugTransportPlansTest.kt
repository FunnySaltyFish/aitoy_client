package com.funny.aitoy.buttplug

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

class ExternalButtplugTransportPlansTest {
    @Test
    fun requestServerInfoUsesButtplugV4MessageVersion() {
        val body = firstBody(ExternalButtplugTransportPlans.requestServerInfo(7), "RequestServerInfo")

        assertEquals("7", body["Id"]?.jsonPrimitive?.content)
        assertEquals("AI Toy Bridge", body["ClientName"]?.jsonPrimitive?.content)
        assertEquals("4", body["MessageVersion"]?.jsonPrimitive?.content)
    }

    @Test
    fun scalarCommandUsesNormalizedValueAndActuatorType() {
        val feature = ButtplugOutputFeature(OUTPUT_ROTATE, -20, 20, 1, "")
        val command = ExternalButtplugTransportPlans.scalarCmd(
            id = 8,
            deviceIndex = 3,
            scalars = listOf(ExternalButtplugTransportPlans.scalarForFeature(feature, 0)),
        )
        val body = firstBody(command, "ScalarCmd")
        val scalar = body["Scalars"]!!.jsonArray[0].jsonObject

        assertEquals("8", body["Id"]?.jsonPrimitive?.content)
        assertEquals("3", body["DeviceIndex"]?.jsonPrimitive?.content)
        assertEquals("1", scalar["Index"]?.jsonPrimitive?.content)
        assertEquals("0.5", scalar["Scalar"]?.jsonPrimitive?.content)
        assertEquals("Rotate", scalar["ActuatorType"]?.jsonPrimitive?.content)
    }

    @Test
    fun linearCommandUsesNormalizedPositionAndDuration() {
        val feature = ButtplugOutputFeature(OUTPUT_HW_POSITION_WITH_DURATION, 0, 100, 2, "")
        val command = ExternalButtplugTransportPlans.linearCmd(
            id = 9,
            deviceIndex = 4,
            vectors = listOf(ExternalButtplugTransportPlans.linearForFeature(feature, 25, durationMs = 1200)),
        )
        val body = firstBody(command, "LinearCmd")
        val vector = body["Vectors"]!!.jsonArray[0].jsonObject

        assertEquals("9", body["Id"]?.jsonPrimitive?.content)
        assertEquals("4", body["DeviceIndex"]?.jsonPrimitive?.content)
        assertEquals("2", vector["Index"]?.jsonPrimitive?.content)
        assertEquals("1200", vector["Duration"]?.jsonPrimitive?.content)
        assertEquals("0.25", vector["Position"]?.jsonPrimitive?.content)
    }

    @Test
    fun stopDeviceUsesDeviceIndex() {
        val body = firstBody(ExternalButtplugTransportPlans.stopDevice(10, 5), "StopDeviceCmd")

        assertEquals("10", body["Id"]?.jsonPrimitive?.content)
        assertEquals("5", body["DeviceIndex"]?.jsonPrimitive?.content)
    }

    private fun firstBody(message: String, type: String) =
        Json.parseToJsonElement(message)
            .jsonArray[0]
            .jsonObject[type]!!
            .jsonObject
}
