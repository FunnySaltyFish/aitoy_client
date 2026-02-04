package com.funny.submaker.core.subtitle

object VttWriter {
    fun write(segments: List<SubtitleSegment>): String {
        val normalized = segments
            .asSequence()
            .map { it.normalize() }
            .filter { it.endMs > it.startMs && it.text.isNotBlank() }
            .sortedWith(compareBy<SubtitleSegment> { it.startMs }.thenBy { it.endMs })
            .toList()

        return buildString {
            append("WEBVTT\n\n")
            normalized.forEach { seg ->
                append(formatTimestamp(seg.startMs, separator = '.'))
                append(" --> ")
                append(formatTimestamp(seg.endMs, separator = '.'))
                append('\n')
                append(seg.text.normalizeLineEndings().trim())
                append("\n\n")
            }
        }.trimEnd() + "\n"
    }
}

