package com.funny.aitoy.core.subtitle

internal fun SubtitleSegment.normalize(): SubtitleSegment {
    val start = startMs.coerceAtLeast(0)
    val end = endMs.coerceAtLeast(0)
    return copy(
        startMs = minOf(start, end),
        endMs = maxOf(start, end),
        text = text,
    )
}

internal fun String.normalizeLineEndings(): String =
    replace("\r\n", "\n").replace("\r", "\n")

internal fun formatTimestamp(ms: Long, separator: Char): String {
    val clamped = ms.coerceAtLeast(0)
    val totalSeconds = clamped / 1000
    val milli = (clamped % 1000).toInt()
    val seconds = (totalSeconds % 60).toInt()
    val totalMinutes = totalSeconds / 60
    val minutes = (totalMinutes % 60).toInt()
    val hours = (totalMinutes / 60).toInt()

    return buildString(12) {
        append(hours.toString().padStart(2, '0'))
        append(':')
        append(minutes.toString().padStart(2, '0'))
        append(':')
        append(seconds.toString().padStart(2, '0'))
        append(separator)
        append(milli.toString().padStart(3, '0'))
    }
}

