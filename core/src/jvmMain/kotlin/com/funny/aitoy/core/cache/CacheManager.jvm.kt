package com.funny.aitoy.core.cache

import ca.gosyer.appdirs.AppDirs
import com.funny.aitoy.core.log.Log
import java.io.File

actual object CacheManager {
    private const val TAG = "CacheManager"
    private const val APP_NAME = "AIToyBridge"
    private const val APP_AUTHOR = "FunnySaltyFish"

    private val appDirs by lazy(LazyThreadSafetyMode.PUBLICATION) {
        AppDirs {
            appName = APP_NAME
            appAuthor = APP_AUTHOR
        }
    }

    private val baseDir: File by lazy(LazyThreadSafetyMode.PUBLICATION) {
        File(appDirs.getUserDataDir()).apply { mkdirs() }
    }

    actual val cacheDir: File by lazy(LazyThreadSafetyMode.PUBLICATION) {
        baseDir.resolve("cache").apply { mkdirs() }
    }

    actual val fileDir: File by lazy(LazyThreadSafetyMode.PUBLICATION) {
        baseDir.resolve("files").apply { mkdirs() }
    }

    init {
        Log.d(TAG) { "baseDir=$baseDir, cacheDir=$cacheDir, fileDir=$fileDir" }
    }
}
