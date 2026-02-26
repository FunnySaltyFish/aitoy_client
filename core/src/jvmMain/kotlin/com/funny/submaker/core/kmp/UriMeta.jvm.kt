package com.funny.submaker.core.kmp

import com.eygraber.uri.Uri
import java.nio.file.Files

actual fun Uri.displayName(): String? = toNioPath()?.fileName?.toString()

actual fun Uri.sizeBytes(): Long? {
    val path = toNioPath() ?: return null
    return runCatching { Files.size(path) }.getOrNull()
}

actual fun Uri.mimeType(): String? {
    val path = toNioPath() ?: return null
    return runCatching { Files.probeContentType(path) }.getOrNull()
}