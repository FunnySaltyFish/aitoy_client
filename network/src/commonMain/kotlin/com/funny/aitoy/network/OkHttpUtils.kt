package com.funny.aitoy.network

import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.Request
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
        val base = File(System.getProperty("java.io.tmpdir"), "aitoy_okhttp_cache")
        if (!base.exists()) {
            base.mkdirs()
        }
        return base
    }

    fun download(
        url: String,
        outputFile: File,
        onProgress: (Int) -> Unit = {},
    ) {
        val request = Request.Builder().url(url).get().build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("下载失败：${response.code}")
            val body = response.body ?: error("下载失败")
            val total = body.contentLength().coerceAtLeast(1L)
            outputFile.parentFile?.mkdirs()
            body.byteStream().use { input ->
                outputFile.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var readTotal = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        readTotal += read
                        onProgress(((readTotal * 100) / total).toInt().coerceIn(0, 100))
                    }
                }
            }
        }
    }
}
