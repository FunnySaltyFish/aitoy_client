package com.funny.aitoy.relay

const val UnlimitedSequenceDurationSec = -1
const val MaxSequenceDurationSec = 600

sealed interface RelaySequenceStep {
    data class Set(
        val mode: Int,
        val intensity: Int,
        val durationSec: Int?,
    ) : RelaySequenceStep

    data class Dual(
        val mode: Int,
        val internalIntensity: Int,
        val externalIntensity: Int,
        val durationSec: Int?,
    ) : RelaySequenceStep

    data class Pattern(val mode: Int, val durationSec: Int?) : RelaySequenceStep

    data class Intensity(val value: Int, val durationSec: Int?) : RelaySequenceStep

    data class Scalar(
        val featureIndex: Int,
        val mode: Int,
        val value: Int,
        val durationSec: Int?,
    ) : RelaySequenceStep

    data class Sleep(val durationSec: Int) : RelaySequenceStep

    data object Stop : RelaySequenceStep
}

private class SequenceScriptFormatException : IllegalArgumentException("序列脚本格式错误")

fun parseRelaySequenceScript(script: String): List<RelaySequenceStep> {
    val parts = script.split(';').map { it.trim() }.filter { it.isNotBlank() }
    if (parts.isEmpty()) throw SequenceScriptFormatException()
    return parts.map { part ->
        when {
            part == "stop()" -> RelaySequenceStep.Stop
            part.startsWith("sleep(") && part.endsWith(")") -> {
                val seconds = part.removePrefix("sleep(")
                    .removeSuffix(")")
                    .trim()
                    .toIntOrNull()
                    ?: throw SequenceScriptFormatException()
                RelaySequenceStep.Sleep(seconds.coerceIn(0, 300))
            }
            part.startsWith("set(") && part.endsWith(")") -> {
                val args = parseSequenceArguments(part.removePrefix("set(").removeSuffix(")"))
                val mode = args["mode"]?.coerceAtLeast(1) ?: throw SequenceScriptFormatException()
                val intensity = args["intensity"]?.coerceIn(0, 100) ?: throw SequenceScriptFormatException()
                RelaySequenceStep.Set(
                    mode = mode,
                    intensity = intensity,
                    durationSec = parseSequenceDuration(args),
                )
            }
            part.startsWith("dual(") && part.endsWith(")") -> {
                val args = parseSequenceArguments(part.removePrefix("dual(").removeSuffix(")"))
                RelaySequenceStep.Dual(
                    mode = args["mode"]?.coerceAtLeast(1) ?: throw SequenceScriptFormatException(),
                    internalIntensity = args["internal"]?.coerceIn(0, 100)
                        ?: throw SequenceScriptFormatException(),
                    externalIntensity = args["external"]?.coerceIn(0, 100)
                        ?: throw SequenceScriptFormatException(),
                    durationSec = parseSequenceDuration(args),
                )
            }
            part.startsWith("pattern(") && part.endsWith(")") -> {
                val args = parseSequenceArguments(part.removePrefix("pattern(").removeSuffix(")"))
                RelaySequenceStep.Pattern(
                    mode = args["mode"]?.coerceAtLeast(1) ?: throw SequenceScriptFormatException(),
                    durationSec = parseSequenceDuration(args),
                )
            }
            part.startsWith("intensity(") && part.endsWith(")") -> {
                val args = parseSequenceArguments(part.removePrefix("intensity(").removeSuffix(")"))
                RelaySequenceStep.Intensity(
                    value = args["value"]?.coerceIn(0, 100)
                        ?: args["intensity"]?.coerceIn(0, 100)
                        ?: throw SequenceScriptFormatException(),
                    durationSec = parseSequenceDuration(args),
                )
            }
            part.startsWith("scalar(") && part.endsWith(")") -> {
                val args = parseSequenceArguments(part.removePrefix("scalar(").removeSuffix(")"))
                RelaySequenceStep.Scalar(
                    featureIndex = (args["feature"] ?: 0).coerceAtLeast(0),
                    mode = (args["mode"] ?: 1).coerceAtLeast(0),
                    value = args["value"]?.coerceIn(0, 100)
                        ?: args["intensity"]?.coerceIn(0, 100)
                        ?: throw SequenceScriptFormatException(),
                    durationSec = parseSequenceDuration(args),
                )
            }
            else -> throw SequenceScriptFormatException()
        }
    }
}

private fun parseSequenceDuration(args: Map<String, Int>): Int? {
    val duration = args["duration"] ?: return null
    return if (duration == UnlimitedSequenceDurationSec) {
        UnlimitedSequenceDurationSec
    } else {
        duration.coerceIn(1, MaxSequenceDurationSec)
    }
}

private fun parseSequenceArguments(text: String): Map<String, Int> {
    if (text.isBlank()) throw SequenceScriptFormatException()
    return text.split(',')
        .associate { entry ->
            val key = entry.substringBefore('=', missingDelimiterValue = "").trim()
            val value = entry.substringAfter('=', missingDelimiterValue = "").trim().toIntOrNull()
            if (key.isBlank() || value == null) throw SequenceScriptFormatException()
            key to value
        }
}
