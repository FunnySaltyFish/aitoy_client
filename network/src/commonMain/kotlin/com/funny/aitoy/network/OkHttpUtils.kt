package com.funny.aitoy.network

import com.funny.aitoy.core.prefs.AiToyPrefs
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import java.net.URI
import java.io.File
import java.util.concurrent.TimeUnit

object OkHttpUtils {
    const val PRODUCTION_BASE_URL = "https://aitoy.funnysaltyfish.fun"
    const val LOCAL_BASE_URL = "http://192.168.1.118:8601"
    const val DEFAULT_API_PREFIX = "api"

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

    val currentBaseUrl: String
        get() = normalizeBaseUrl(AiToyPrefs.serverBaseUrl.ifBlank { PRODUCTION_BASE_URL })

    val currentApiBaseUrl: String
        get() = apiBaseUrl()

    val currentMcpUrl: String
        get() = externalUrl("mcp")

    fun useProductionBaseUrl() {
        AiToyPrefs.serverBaseUrl = PRODUCTION_BASE_URL
        AiToyPrefs.apiPrefix = DEFAULT_API_PREFIX
    }

    fun useLocalBaseUrl() {
        AiToyPrefs.serverBaseUrl = LOCAL_BASE_URL
        AiToyPrefs.apiPrefix = DEFAULT_API_PREFIX
    }

    fun saveBaseUrl(value: String): Boolean {
        val normalized = normalizeBaseUrl(value)
        if (!isHttpUrl(normalized)) return false
        AiToyPrefs.serverBaseUrl = normalized
        if (AiToyPrefs.apiPrefix.isBlank()) AiToyPrefs.apiPrefix = DEFAULT_API_PREFIX
        return true
    }

    fun apiBaseUrl(baseUrlOverride: String? = null): String {
        val base = normalizeBaseUrl(baseUrlOverride ?: currentBaseUrl)
        val prefix = AiToyPrefs.apiPrefix.trim().trim('/').ifBlank { DEFAULT_API_PREFIX }
        return "$base/$prefix/"
    }

    fun externalUrl(path: String = "", baseUrl: String = currentBaseUrl): String {
        val normalizedPath = path.trim().trimStart('/')
        val base = normalizeBaseUrl(baseUrl)
        return if (normalizedPath.isBlank()) base else "$base/$normalizedPath"
    }

    fun normalizeBaseUrl(value: String): String {
        val trimmed = value.trim().trimEnd('/')
        return when {
            trimmed.isBlank() -> PRODUCTION_BASE_URL
            trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
            else -> "http://$trimmed"
        }
    }

    fun isHttpUrl(value: String): Boolean {
        return runCatching {
            val uri = URI(value)
            (uri.scheme == "http" || uri.scheme == "https") && !uri.host.isNullOrBlank()
        }.getOrDefault(false)
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
