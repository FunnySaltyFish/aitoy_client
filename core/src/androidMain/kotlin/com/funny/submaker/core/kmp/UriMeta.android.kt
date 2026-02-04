package com.funny.submaker.core.kmp

import android.database.Cursor
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import com.eygraber.uri.Uri
import com.eygraber.uri.toAndroidUri

actual fun Uri.displayName(): String? {
    val androidUri = toAndroidUri()
    val projection = arrayOf(OpenableColumns.DISPLAY_NAME)
    val cursor: Cursor = runCatching {
        appCtx.contentResolver.query(androidUri, projection, null, null, null)
    }.getOrNull() ?: return androidUri.lastPathSegment

    cursor.use {
        if (!it.moveToFirst()) return androidUri.lastPathSegment
        val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (index < 0) return androidUri.lastPathSegment
        return it.getString(index)
    }
}

actual fun Uri.sizeBytes(): Long? {
    val androidUri = toAndroidUri()
    val projection = arrayOf(OpenableColumns.SIZE)
    val cursor: Cursor = runCatching {
        appCtx.contentResolver.query(androidUri, projection, null, null, null)
    }.getOrNull() ?: return runCatching {
        appCtx.contentResolver.openAssetFileDescriptor(androidUri, "r")?.use { it.length }
    }.getOrNull()

    cursor.use {
        if (!it.moveToFirst()) return null
        val index = it.getColumnIndex(OpenableColumns.SIZE)
        if (index < 0) return null
        val value = it.getLong(index)
        return if (value >= 0) value else null
    }
}

actual fun Uri.mimeType(): String? {
    val androidUri = toAndroidUri()
    val resolverType = appCtx.contentResolver.getType(androidUri)
    if (!resolverType.isNullOrBlank()) return resolverType

    val name = displayName().orEmpty()
    val ext = name.substringAfterLast('.', missingDelimiterValue = "").lowercase()
    if (ext.isBlank()) return null
    return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
}

