package com.funny.aitoy.network

import com.funny.aitoy.core.prefs.AiToyPrefs
import okhttp3.Interceptor
import okhttp3.Response

object ClientRequestContext {
    @Volatile
    private var appVersionCode: Int = 0

    @Volatile
    private var appVersionName: String = ""

    @Volatile
    private var platform: String = ""

    @Volatile
    private var channel: String = "common"

    @Volatile
    private var packageName: String = ""

    fun configure(
        appVersionCode: Int,
        appVersionName: String,
        platform: String,
        channel: String = "common",
        packageName: String = "",
    ) {
        this.appVersionCode = appVersionCode
        this.appVersionName = appVersionName.trim()
        this.platform = platform.trim()
        this.channel = channel.trim().ifBlank { "common" }
        this.packageName = packageName.trim()
    }

    fun headers(): Map<String, String> {
        val userToken = AiToyPrefs.userToken.trim()
        val deviceId = runCatching { AiToyPrefs.deviceId.trim() }.getOrDefault("")
        return buildMap {
            putIfNotBlank("X-User-Token", userToken)
            putIfNotBlank("X-AI-Toy-Device-Id", deviceId)
            putIfNotBlank("X-AI-Toy-App-Version-Code", appVersionCode.takeIf { it > 0 }?.toString().orEmpty())
            putIfNotBlank("X-AI-Toy-App-Version-Name", appVersionName)
            putIfNotBlank("X-AI-Toy-Platform", platform)
            putIfNotBlank("X-AI-Toy-Channel", channel)
            putIfNotBlank("X-AI-Toy-Package-Name", packageName)
            putIfNotBlank("User-Agent", userAgent())
        }
    }

    private fun userAgent(): String {
        val name = appVersionName.ifBlank { "unknown" }
        val code = appVersionCode.takeIf { it > 0 }?.toString() ?: "unknown"
        val clientPlatform = platform.ifBlank { "unknown" }
        return "AI Toy/$name ($clientPlatform; vc=$code)"
    }

    private fun MutableMap<String, String>.putIfNotBlank(name: String, value: String) {
        if (value.isNotBlank()) {
            put(name, value)
        }
    }
}

object ClientRequestContextInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val builder = request.newBuilder()
        ClientRequestContext.headers().forEach { (name, value) ->
            if (request.header(name) == null) {
                builder.header(name, value)
            }
        }
        return chain.proceed(builder.build())
    }
}
