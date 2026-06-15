package com.funny.aitoy.core.utils

import java.net.URI
import java.net.URLConnection
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

actual fun String.resolveMimeType(): String? {
    val byName = URLConnection.guessContentTypeFromName(this)
    if (!byName.isNullOrBlank()) return byName

    val path: Path = runCatching {
        val raw = trim()
        when {
            raw.startsWith("file:", ignoreCase = true) -> Paths.get(URI(raw))
            else -> Paths.get(raw)
        }
    }.getOrNull() ?: return null

    return runCatching { Files.probeContentType(path) }.getOrNull()
}

