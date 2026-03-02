package com.funny.submaker.core.prefs

import com.funny.data_saver.core.DataSaverEncryptedProperties
import com.funny.data_saver.core.DataSaverInterface

actual val DataSaverUtils: DataSaverInterface by lazy(LazyThreadSafetyMode.PUBLICATION) {
    DataSaverEncryptedProperties(
        filePath = defaultDataSaverFilePath(),
        encryptionKey = DATA_SAVER_ENC_KEY
    )
}

private fun defaultDataSaverFilePath(): String {
    val baseDir = java.io.File(System.getProperty("user.home"))
        .resolve(".submaker")
        .resolve("data")
    baseDir.mkdirs()
    return baseDir.resolve("data_saver.properties").absolutePath
}

const val DATA_SAVER_ENC_KEY = "SubMakerMagicKey"