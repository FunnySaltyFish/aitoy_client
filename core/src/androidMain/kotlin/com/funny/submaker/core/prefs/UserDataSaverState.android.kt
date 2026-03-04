package com.funny.submaker.core.prefs

import com.funny.data_saver.core.DataSaverInterface
import com.funny.data_saver_mmkv.DataSaverMMKV
import com.tencent.mmkv.MMKV

private val userScopedStoreCache = mutableMapOf<String, DataSaverInterface>()
private val userScopedStoreCacheLock = Any()

actual fun getUserDataSaverByOwner(ownerId: String): DataSaverInterface {
    val safeOwner = ownerId.toSafeStoreId()
    return synchronized(userScopedStoreCacheLock) {
        userScopedStoreCache.getOrPut(safeOwner) {
            val id = "user_scope_$safeOwner"
            val mmkv = MMKV.mmkvWithID(id)
            DataSaverMMKV(mmkv)
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
