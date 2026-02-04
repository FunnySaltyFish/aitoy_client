package com.funny.submaker.core.kmp

import com.eygraber.uri.Uri
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

actual fun Uri.displayName(): String? = toNioPath()?.fileName?.toString()

actual fun Uri.sizeBytes(): Long? {
    val path = toNioPath() ?: return null
    return runCatching { Files.size(path) }.getOrNull()
}

actual fun Uri.mimeType(): String? {
    val path = toNioPath() ?: return null
    return runCatching { Files.probeContentType(path) }.getOrNull()
}

private fun Uri.toNioPath(): Path? {
    return runCatching {
        val raw = toString()
        if (raw.startsWith("file:", ignoreCase = true)) {
            Paths.get(URI(raw))
        } else {
            Paths.get(raw)
        }
    }.getOrNull()
}

