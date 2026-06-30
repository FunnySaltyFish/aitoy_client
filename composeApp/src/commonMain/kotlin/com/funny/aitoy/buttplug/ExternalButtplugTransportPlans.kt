package com.funny.aitoy.buttplug

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

data class ExternalButtplugScalar(
    val index: Int,
    val kind: ToyOutputKind,
    val value: Double,
)

data class ExternalButtplugLinear(
    val index: Int,
    val position: Double,
    val durationMs: Int,
)

object ExternalButtplugTransportPlans {
    val protocolIds: Set<String> = setOf(
        "kizuna",
        "lovense-connect-service",
        "nextlevelracing",
        "nintendo-joycon",
        "realtouch",
        "rez-trancevibrator",
        "simulated",
        "tcode-v03",
        "vorze-cyclone-x",
        "xinput",
    )

    val supportedKinds: Set<ToyOutputKind> =
        setOf(ToyOutputKind.Vibrate, ToyOutputKind.Oscillate, ToyOutputKind.Rotate, ToyOutputKind.Constrict, ToyOutputKind.Linear, ToyOutputKind.Position)

    fun requestServerInfo(id: Int, clientName: String = "AI Toy Bridge"): String =
        envelope(
            "RequestServerInfo",
            buildJsonObject {
                put("Id", id)
                put("ClientName", clientName)
                put("MessageVersion", 4)
            },
        )

    fun requestDeviceList(id: Int): String =
        simpleMessage("RequestDeviceList", id)

    fun startScanning(id: Int): String =
        simpleMessage("StartScanning", id)

    fun stopScanning(id: Int): String =
        simpleMessage("StopScanning", id)

    fun stopDevice(id: Int, deviceIndex: Int): String =
        envelope(
            "StopDeviceCmd",
            buildJsonObject {
                put("Id", id)
                put("DeviceIndex", deviceIndex)
            },
        )

    fun scalarCmd(id: Int, deviceIndex: Int, scalars: List<ExternalButtplugScalar>): String =
        envelope(
            "ScalarCmd",
            buildJsonObject {
                put("Id", id)
                put("DeviceIndex", deviceIndex)
                put(
                    "Scalars",
                    JsonArray(
                        scalars.map { scalar ->
                            buildJsonObject {
                                put("Index", scalar.index)
                                put("Scalar", scalar.value.coerceIn(0.0, 1.0))
                                put("ActuatorType", scalar.kind.toButtplugActuatorType())
                            }
                        },
                    ),
                )
            },
        )

    fun linearCmd(id: Int, deviceIndex: Int, vectors: List<ExternalButtplugLinear>): String =
        envelope(
            "LinearCmd",
            buildJsonObject {
                put("Id", id)
                put("DeviceIndex", deviceIndex)
                put(
                    "Vectors",
                    JsonArray(
                        vectors.map { vector ->
                            buildJsonObject {
                                put("Index", vector.index)
                                put("Duration", vector.durationMs.coerceAtLeast(0))
                                put("Position", vector.position.coerceIn(0.0, 1.0))
                            }
                        },
                    ),
                )
            },
        )

    fun scalarForFeature(feature: ButtplugOutputFeature, value: Int): ExternalButtplugScalar =
        ExternalButtplugScalar(
            index = feature.featureIndex,
            kind = feature.type.toToyOutputKind(),
            value = normalizedScalar(feature, value),
        )

    fun linearForFeature(feature: ButtplugOutputFeature, position: Int, durationMs: Int): ExternalButtplugLinear =
        ExternalButtplugLinear(
            index = feature.featureIndex,
            position = normalizedScalar(feature, position),
            durationMs = durationMs,
        )

    private fun simpleMessage(type: String, id: Int): String =
        envelope(type, buildJsonObject { put("Id", id) })

    private fun envelope(type: String, body: JsonObject): String =
        buildJsonArray {
            add(buildJsonObject { put(type, body) })
        }.toString()

    private fun normalizedScalar(feature: ButtplugOutputFeature, value: Int): Double {
        val min = feature.min
        val max = feature.max
        if (max <= min) return 0.0
        return ((value.coerceIn(min, max) - min).toDouble() / (max - min).toDouble()).coerceIn(0.0, 1.0)
    }

    private fun ToyOutputKind.toButtplugActuatorType(): String =
        when (this) {
            ToyOutputKind.Vibrate -> "Vibrate"
            ToyOutputKind.Oscillate -> "Oscillate"
            ToyOutputKind.Rotate -> "Rotate"
            ToyOutputKind.Constrict -> "Constrict"
            ToyOutputKind.Inflate -> "Inflate"
            ToyOutputKind.Linear -> "Position"
            ToyOutputKind.Position -> "Position"
            ToyOutputKind.Heater -> "Heater"
            ToyOutputKind.Led -> "Led"
            ToyOutputKind.Spray -> "Spray"
            ToyOutputKind.Temperature -> "Temperature"
            ToyOutputKind.Unknown -> "Unknown"
        }
}
