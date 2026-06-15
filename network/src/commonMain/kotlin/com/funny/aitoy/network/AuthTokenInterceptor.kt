package com.funny.aitoy.network

import com.funny.aitoy.core.prefs.AiToyPrefs
import okhttp3.Interceptor

object AuthTokenInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
        val request = chain.request()
        if (request.header("Authorization") != null) {
            return chain.proceed(request)
        }

        val token = AiToyPrefs.authToken.trim()
        if (token.isBlank()) {
            return chain.proceed(request)
        }

        val authed = request.newBuilder()
            .header("Authorization", "Bearer $token")
            .build()
        return chain.proceed(authed)
    }
}
