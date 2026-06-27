package com.funny.aitoy.buttplug

data class ButtplugDeviceFingerprint(
    val name: String,
    val serviceUuids: Set<String>,
    val characteristicUuids: Set<String>,
)

data class ButtplugEndpoint(
    val serviceUuid: String,
    val characteristics: Map<String, String>,
) {
    val txUuid: String? get() = characteristics["tx"]
    val rxUuid: String? get() = characteristics["rx"]
}

data class ButtplugOutputFeature(
    val type: String,
    val min: Int,
    val max: Int,
    val featureIndex: Int,
    val description: String,
)

data class ButtplugDeviceDefinition(
    val identifiers: List<String>,
    val name: String,
    val features: List<ButtplugOutputFeature>,
)

data class ButtplugProtocolDefinition(
    val id: String,
    val displayName: String,
    val communicationTypes: Set<String>,
    val communicationNames: List<String>,
    val endpoints: List<ButtplugEndpoint>,
    val defaultDevice: ButtplugDeviceDefinition,
    val configurations: List<ButtplugDeviceDefinition>,
) {
    fun match(fingerprint: ButtplugDeviceFingerprint): ButtplugDeviceMatch? {
        val matchedEndpoint = endpoints.firstOrNull { endpoint ->
            fingerprint.serviceUuids.contains(endpoint.serviceUuid) &&
                endpoint.characteristics.values.any(fingerprint.characteristicUuids::contains)
        } ?: return null
        val nameMatched = communicationNames.isEmpty() ||
            communicationNames.any { alias -> fingerprint.name.matchesButtplugAlias(alias) }
        if (!nameMatched) return null
        val device = configurations.firstOrNull { config ->
            config.identifiers.any { identifier -> fingerprint.name.matchesButtplugAlias(identifier) }
        } ?: defaultDevice
        return ButtplugDeviceMatch(
            protocol = this,
            device = device,
            endpoint = matchedEndpoint,
        )
    }
}

data class ButtplugDeviceMatch(
    val protocol: ButtplugProtocolDefinition,
    val device: ButtplugDeviceDefinition,
    val endpoint: ButtplugEndpoint,
) {
    val outputTypes: Set<String>
        get() = device.features.map { it.type }.toSet()

    val vibrateOutputs: List<ButtplugOutputFeature>
        get() = device.features.filter { it.type == OUTPUT_VIBRATE || it.type == OUTPUT_OSCILLATE }

    val scalarOutputs: List<ButtplugOutputFeature>
        get() = device.features.filter {
            it.type == OUTPUT_VIBRATE ||
                it.type == OUTPUT_OSCILLATE ||
                it.type == OUTPUT_ROTATE ||
                it.type == OUTPUT_CONSTRICT ||
                it.type == OUTPUT_INFLATE
        }

    val linearOutputs: List<ButtplugOutputFeature>
        get() = device.features.filter { it.type == OUTPUT_HW_POSITION_WITH_DURATION }

    val hasOnlyVibrateOutputs: Boolean
        get() = device.features.isNotEmpty() && device.features.all {
            it.type == OUTPUT_VIBRATE || it.type == OUTPUT_OSCILLATE
        }
}

const val OUTPUT_VIBRATE = "vibrate"
const val OUTPUT_OSCILLATE = "oscillate"
const val OUTPUT_ROTATE = "rotate"
const val OUTPUT_CONSTRICT = "constrict"
const val OUTPUT_INFLATE = "inflate"
const val OUTPUT_POSITION = "position"
const val OUTPUT_HW_POSITION_WITH_DURATION = "hw_position_with_duration"
const val OUTPUT_HEATER = "heater"
const val OUTPUT_LED = "led"

const val COMMUNICATION_BTLE = "btle"
const val COMMUNICATION_HID = "hid"
const val COMMUNICATION_SERIAL = "serial"

fun String.normalizedButtplugName(): String =
    lowercase().replace("_", "").replace("-", "").replace(" ", "")

fun String.normalizedUuidText(): String = lowercase()

private fun String.matchesButtplugAlias(alias: String): Boolean {
    val normalizedName = normalizedButtplugName()
    val normalizedAlias = alias.normalizedButtplugName()
    return normalizedAlias.isNotBlank() && normalizedName.contains(normalizedAlias)
}
