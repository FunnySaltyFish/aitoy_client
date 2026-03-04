package com.funny.submaker.core.prefs

import com.funny.data_saver.core.DataSaverEncryptedProperties
import com.funny.data_saver.core.DataSaverInterface
import com.funny.submaker.core.cache.CacheManager
import com.funny.submaker.core.cache.fileSubDir

private val userScopedStoreCache = mutableMapOf<String, DataSaverInterface>()
private val userScopedStoreCacheLock = Any()

actual fun getUserDataSaverByOwner(ownerId: String): DataSaverInterface {
    val safeOwner = ownerId.toSafeStoreId()
    return synchronized(userScopedStoreCacheLock) {
        userScopedStoreCache.getOrPut(safeOwner) {
            val baseDir = CacheManager.fileSubDir("data")
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
