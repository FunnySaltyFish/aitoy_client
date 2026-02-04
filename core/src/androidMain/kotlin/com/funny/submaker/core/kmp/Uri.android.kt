package com.funny.submaker.core.kmp

import android.content.res.AssetFileDescriptor
import com.eygraber.uri.Uri
import com.eygraber.uri.toAndroidUri
import java.io.BufferedReader
import java.io.FileWriter
import java.io.InputStreamReader

actual fun Uri.readText(): String {
    val androidUri = toAndroidUri()
    return runCatching {
        val sb = StringBuilder()
        appCtx.contentResolver.openInputStream(androidUri).use { inputStream ->
            BufferedReader(InputStreamReader(inputStream)).use { reader ->
                while (true) {
                    val line = reader.readLine() ?: break
                    sb.append(line).append('\n')
                }
            }
        }
        sb.toString()
    }.getOrElse {
        it.printStackTrace()
        ""
    }
}

actual fun Uri.readBytes(): ByteArray {
    val androidUri = toAndroidUri()
    return runCatching {
        appCtx.contentResolver.openInputStream(androidUri).use { inputStream ->
            inputStream?.readBytes() ?: byteArrayOf()
        }
    }.getOrElse {
        it.printStackTrace()
        byteArrayOf()
    }
}

actual fun Uri.writeText(text: String) {
    val androidUri = toAndroidUri()
    runCatching {
        val pfd: AssetFileDescriptor? = appCtx.contentResolver.openAssetFileDescriptor(androidUri, "w")
        if (pfd != null) {
            FileWriter(pfd.fileDescriptor).use { it.write(text) }
            pfd.close()
        }
    }.onFailure { it.printStackTrace() }
}

