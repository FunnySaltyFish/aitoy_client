package com.funny.submaker.network

import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.io.File
import java.util.concurrent.TimeUnit

object OkHttpUtils {
    val okHttpClient: OkHttpClient by lazy(LazyThreadSafetyMode.PUBLICATION) {
        val logger = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
            redactHeader("Authorization")
            redactHeader("X-Api-Key")
            redactHeader("api-key")
        }

        OkHttpClient.Builder()
            .cache(Cache(cacheDir(), 50L * 1024 * 1024))
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .addInterceptor(AuthTokenInterceptor)
            .addInterceptor(DynamicTimeoutInterceptor)
            .addNetworkInterceptor(CacheControlInterceptor)
            .addInterceptor(logger)
            .build()
    }

    private fun cacheDir(): File {
        val base = File(System.getProperty("java.io.tmpdir"), "submaker_okhttp_cache")
        if (!base.exists()) {
            base.mkdirs()
        }
        return base
    }
}
