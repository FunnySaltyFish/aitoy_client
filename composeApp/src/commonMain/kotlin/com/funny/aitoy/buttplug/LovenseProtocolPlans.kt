package com.funny.aitoy.buttplug

data class LovenseProtocolWrite(
    val endpointRole: String = "tx",
    val bytes: ByteArray,
    val withResponse: Boolean = false,
)

data class LovenseProfile(
    val deviceType: String,
    val outputCount: Int,
    val vibratorCount: Int,
    val hasRotate: Boolean,
    val hasConstrict: Boolean,
    val isStroker: Boolean,
) {
    val needRangeZero: Boolean get() = deviceType == "H"
    val isVibratorRotator: Boolean get() = outputCount == 2 && vibratorCount == 1 && hasRotate
    val isMax: Boolean get() = outputCount == 2 && vibratorCount == 1 && hasConstrict
    val usesMply: Boolean get() = (vibratorCount == 2 && outputCount > 2) || vibratorCount > 2
}

object LovenseProtocolPlans {
    const val protocolId: String = "lovense"
    const val keepaliveIntervalMs: Long = 5_000L

    val supportedKinds: Set<ToyOutputKind> =
        setOf(ToyOutputKind.Vibrate, ToyOutputKind.Oscillate, ToyOutputKind.Rotate, ToyOutputKind.Constrict, ToyOutputKind.Linear)

    fun deviceTypeFromResponse(response: String): String {
        val parts = response.trim().split(':')
        if (parts.size < 2) return protocolId
        val identifier = parts[0]
        val version = parts[1].takeWhile(Char::isDigit).toIntOrNull() ?: 0
        return if (identifier == "EI" && version >= 3) "EI-FW3" else identifier
    }

    fun deviceTypeFromBleName(name: String): String? {
        val match = Regex("""LVS-([A-Z]+)\d+""").find(name)
            ?: Regex("""LOVE-([A-Z]+)\d+""").find(name)
        return match?.groupValues?.getOrNull(1)
    }

    fun profile(deviceType: String, features: List<ButtplugOutputFeature>): LovenseProfile {
        val outputCount = features.count()
        val vibratorCount = features.count { it.type == OUTPUT_VIBRATE || it.type == OUTPUT_OSCILLATE }
        return LovenseProfile(
            deviceType = deviceType,
            outputCount = outputCount,
            vibratorCount = vibratorCount,
            hasRotate = features.any { it.type == OUTPUT_ROTATE },
            hasConstrict = features.any { it.type == OUTPUT_CONSTRICT },
            isStroker = deviceType == "BA" || deviceType == "H",
        )
    }

    fun keepaliveWrite(): LovenseProtocolWrite =
        command("DeviceType;")

    fun scalarWrites(
        profile: LovenseProfile,
        state: IntArray,
        feature: ButtplugOutputFeature,
        value: Int,
    ): List<LovenseProtocolWrite> {
        val kind = feature.type.toToyOutputKind()
        val speed = normalizeValue(kind, feature, value)
        return when {
            profile.isStroker && kind == ToyOutputKind.Oscillate ->
                listOf(command("Mply:$speed:${if (speed == 0 && profile.needRangeZero) 0 else 20};"))
            profile.usesMply && (kind == ToyOutputKind.Vibrate || kind == ToyOutputKind.Oscillate || kind == ToyOutputKind.Rotate) -> {
                state[feature.featureIndex.coerceIn(state.indices)] = kotlin.math.abs(speed)
                listOf(command("Mply:${state.joinToString(":")};"))
            }
            profile.isVibratorRotator && kind == ToyOutputKind.Rotate -> rotateWrites(speed)
            profile.isMax && kind == ToyOutputKind.Constrict -> listOf(command("Air:Level:${speed.coerceIn(0, 3)};"))
            kind == ToyOutputKind.Rotate -> listOf(command("Rotate:${kotlin.math.abs(speed)};"))
            kind == ToyOutputKind.Constrict -> listOf(command("Air:Level:${speed.coerceIn(0, 3)};"))
            kind == ToyOutputKind.Vibrate || kind == ToyOutputKind.Oscillate -> {
                if (profile.outputCount > 1 && !profile.isMax && !profile.isVibratorRotator) {
                    listOf(command("Vibrate${feature.featureIndex + 1}:$speed;"))
                } else {
                    listOf(command("Vibrate:$speed;"))
                }
            }
            else -> emptyList()
        }
    }

    fun linearWrites(
        profile: LovenseProfile,
        position: Int,
    ): List<LovenseProtocolWrite> =
        if (profile.isStroker) {
            listOf(command("FSetSite:${position.coerceIn(0, 100)};"))
        } else {
            emptyList()
        }

    private fun rotateWrites(speed: Int): List<LovenseProtocolWrite> =
        buildList {
            if (speed < 0) add(command("RotateChange;"))
            add(command("Rotate:${kotlin.math.abs(speed)};"))
        }

    private fun normalizeValue(kind: ToyOutputKind, feature: ButtplugOutputFeature, value: Int): Int =
        if (kind == ToyOutputKind.Rotate && feature.min < 0) {
            value.coerceIn(feature.min, feature.max)
        } else {
            value.coerceIn(feature.min, feature.max)
        }

    private fun command(value: String): LovenseProtocolWrite =
        LovenseProtocolWrite(bytes = value.encodeToByteArray())
}
