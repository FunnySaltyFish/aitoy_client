package com.funny.submaker.core.utils

import java.io.File
import java.security.MessageDigest

fun File.createFileIfNotExist(): Boolean {
    if (exists()) return true
    parentFile?.mkdirs()
    return createNewFile()
}

fun File.createParentDirIfNotExist(): Boolean {
    val parent = parentFile ?: return false
    return parent.mkdirs()
}

fun File.fileMD5(): String? {
    return runCatching {
        if (!exists()) return ""
        val digest = MessageDigest.getInstance("MD5")
        digest.update(readBytes())
        digest.digest().joinToString("") { "%02x".format(it) }
    }.getOrElse {
        it.printStackTrace()
        null
    }
}

