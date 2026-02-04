package com.funny.submaker.core.prefs

import com.funny.data_saver.core.DataSaverInterface

actual val DataSaverUtils: DataSaverInterface by lazy(LazyThreadSafetyMode.PUBLICATION) {
    DataSaverProperties(
        filePath = defaultDataSaverFilePath(),
    )
}

private fun defaultDataSaverFilePath(): String {
    val baseDir = java.io.File(System.getProperty("user.home"))
        .resolve(".submaker")
        .resolve("data")
    baseDir.mkdirs()
    return baseDir.resolve("data_saver.properties").absolutePath
}

