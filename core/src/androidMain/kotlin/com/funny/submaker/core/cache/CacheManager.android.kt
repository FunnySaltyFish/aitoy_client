package com.funny.submaker.core.cache

import com.funny.submaker.core.kmp.appCtx
import java.io.File

actual object CacheManager {
    private val ctx get() = appCtx

    actual val cacheDir: File by lazy(LazyThreadSafetyMode.PUBLICATION) {
        (ctx.externalCacheDir ?: ctx.cacheDir).apply { mkdirs() }
    }

    actual val fileDir: File by lazy(LazyThreadSafetyMode.PUBLICATION) {
        (ctx.getExternalFilesDir(null) ?: ctx.filesDir).apply { mkdirs() }
    }
}
