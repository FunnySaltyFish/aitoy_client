package com.funny.submaker.network

import com.funny.submaker.core.prefs.SubMakerPrefs
import okhttp3.Interceptor

object AuthTokenInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
        val request = chain.request()
        if (request.header("Authorization") != null) {
            return chain.proceed(request)
        }

        val token = SubMakerPrefs.authToken.trim()
        if (token.isBlank()) {
            return chain.proceed(request)
        }

        val authed = request.newBuilder()
            .header("Authorization", "Bearer $token")
            .build()
        return chain.proceed(authed)
    }
}
