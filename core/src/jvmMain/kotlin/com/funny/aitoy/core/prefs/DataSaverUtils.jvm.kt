package com.funny.aitoy.core.prefs

import com.funny.data_saver.core.DataSaverEncryptedProperties
import com.funny.data_saver.core.DataSaverInterface
import com.funny.aitoy.core.cache.CacheManager
import com.funny.aitoy.core.cache.fileSubDir

actual val DataSaverUtils: DataSaverInterface by lazy(LazyThreadSafetyMode.PUBLICATION) {
    DataSaverEncryptedProperties(
        filePath = defaultDataSaverFilePath(),
        encryptionKey = DATA_SAVER_ENC_KEY
    )
}

private fun defaultDataSaverFilePath(): String {
    val baseDir = CacheManager.fileSubDir("data")
    return baseDir.resolve("data_saver.properties").absolutePath
}

const val DATA_SAVER_ENC_KEY = "AiToyBridgeMagicKey"