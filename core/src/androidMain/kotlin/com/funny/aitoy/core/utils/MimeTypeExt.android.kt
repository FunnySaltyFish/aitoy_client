package com.funny.aitoy.core.utils

import android.graphics.BitmapFactory
import android.webkit.MimeTypeMap
import androidx.core.net.toUri
import com.funny.aitoy.core.kmp.appCtx

actual fun String.resolveMimeType(): String? {
    val uri = runCatching { toUri() }.getOrNull() ?: return null
    val resolver = appCtx.contentResolver

    val mimeFromStream = runCatching {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, options)
        }
        options.outMimeType
    }.getOrNull()
    if (!mimeFromStream.isNullOrBlank()) return mimeFromStream

    val typeFromResolver = resolver.getType(uri)
    if (!typeFromResolver.isNullOrBlank()) return typeFromResolver

    val extension = MimeTypeMap.getFileExtensionFromUrl(this).orEmpty().lowercase()
    if (extension.isBlank()) return null
    return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
}

