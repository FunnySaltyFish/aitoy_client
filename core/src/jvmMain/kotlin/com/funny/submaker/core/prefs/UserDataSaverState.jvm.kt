package com.funny.submaker.core.prefs

import com.funny.data_saver.core.DataSaverEncryptedProperties
import com.funny.data_saver.core.DataSaverInterface

private val userScopedStoreCache = mutableMapOf<String, DataSaverInterface>()
private val userScopedStoreCacheLock = Any()

actual fun getUserDataSaverByOwner(ownerId: String): DataSaverInterface {
    val safeOwner = ownerId.toSafeStoreId()
    return synchronized(userScopedStoreCacheLock) {
        userScopedStoreCache.getOrPut(safeOwner) {
            val baseDir = java.io.File(System.getProperty("user.home"))
                .resolve(".submaker")
                .resolve("data")
                .resolve("user-scoped")
            baseDir.mkdirs()
            DataSaverEncryptedProperties(
                filePath = baseDir.resolve("$safeOwner.properties").absolutePath,
                encryptionKey = DATA_SAVER_ENC_KEY,
            )
        }
    }
}

private fun String.toSafeStoreId(): String {
    val mapped = buildString(length) {
        for (ch in this@toSafeStoreId) {
            append(if (ch.isLetterOrDigit() || ch == '_' || ch == '-') ch else '_')
        }
    }
    return mapped.ifBlank { "default" }
}
