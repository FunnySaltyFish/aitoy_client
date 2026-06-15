package com.funny.aitoy.core.kmp

import com.eygraber.uri.Uri
import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

actual fun Uri.readText(): String {
    val path = toNioPath() ?: return ""
    return runCatching { Files.readString(path) }
        .getOrElse {
            it.printStackTrace()
            ""
        }
}

actual fun Uri.readBytes(): ByteArray {
    val path = toNioPath() ?: return byteArrayOf()
    return runCatching { Files.readAllBytes(path) }
        .getOrElse {
            it.printStackTrace()
            byteArrayOf()
        }
}

actual fun Uri.writeText(text: String) {
    val path = toNioPath() ?: return
    runCatching {
        val parent = path.parent
        if (parent != null) {
            Files.createDirectories(parent)
        }
        Files.write(path, text.toByteArray(StandardCharsets.UTF_8))
    }.onFailure { it.printStackTrace() }
}

fun Uri.toNioPath(): Path? {
    return runCatching {
        val raw = toString()
        if (raw.startsWith("file:", ignoreCase = true)) {
            Paths.get(URI(raw))
        } else {
            Paths.get(raw)
        }
    }.getOrNull()
}
