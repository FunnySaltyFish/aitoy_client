package com.funny.submaker.core.utils

import java.security.MessageDigest

val String.md5: String
    get() {
        val digest = MessageDigest.getInstance("MD5")
        val temp = digest.digest(toByteArray())
        return temp.joinToString("") { "%02x".format(it) }
    }

fun String.safeSubstring(start: Int, end: Int = length): String =
    substring(start, minOf(end, length))

fun String.extractSuffix(): String {
    val index = lastIndexOf(".")
    return if (index == -1) "" else safeSubstring(index + 1)
}

fun String.extractJsonFromCodeBlockOrRaw(fallback: String = "{}"): String {
    val fenced = """```(json)?([\s\S]*?)```""".toRegex().find(this)?.groupValues?.getOrNull(2)?.trim()
    if (!fenced.isNullOrBlank()) return fenced
    val raw = """\[[\s\S]*]|\{[\s\S]*\}""".toRegex().find(this)?.value
    return raw ?: fallback
}

fun String.extractJsonFromPartial(): String {
    val trimmed = trim()
    if (trimmed.startsWith("{") && trimmed.endsWith("}")) return trimmed
    return trimmed.substringAfter("```json").substringBeforeLast("```").ifEmpty { "{}" }
}

fun String.resolveImageSuffix(): String {
    val direct = extractSuffix().lowercase()
    val base = if (direct.isNotBlank()) {
        direct.substringBefore("+")
    } else {
        val mime = resolveMimeType()?.lowercase()?.substringBefore(";").orEmpty()
        if (mime.startsWith("image/")) mime.substringAfter("image/") else ""
    }
    if (base.isBlank()) return ""
    return when (base) {
        "jpeg", "pjpeg" -> "jpg"
        "svg+xml" -> "svg"
        "x-icon" -> "ico"
        else -> base
    }
}
