package com.funny.submaker.network

import okhttp3.Interceptor
import retrofit2.Invocation
import java.time.Duration
import java.time.ZonedDateTime

object CacheControlInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
        val request = chain.request()
        if (request.method != "GET") {
            val noStoreReq = request.newBuilder()
                .header("Cache-Control", "no-store")
                .build()
            val response = chain.proceed(noStoreReq)
            return response.newBuilder()
                .header("Cache-Control", "no-store")
                .build()
        }

        val getCache = request.tag(Invocation::class.java)
            ?.method()
            ?.getAnnotation(GetCache::class.java)

        val maxAgeSeconds = resolveGetMaxAge(getCache)
        val staleIfErrorSeconds = getCache?.staleIfErrorSeconds ?: 86_400
        val response = chain.proceed(request)
        return response.newBuilder()
            .header("Cache-Control", "public, max-age=$maxAgeSeconds, stale-if-error=$staleIfErrorSeconds")
            .build()
    }

    private fun resolveGetMaxAge(cache: GetCache?): Int {
        if (cache == null) return 30
        if (!cache.refreshAt4Am) return cache.maxAgeSeconds
        return secondsUntilNextRefreshAt4Am().coerceAtLeast(cache.minSeconds)
    }

    private fun secondsUntilNextRefreshAt4Am(): Int {
        val now = ZonedDateTime.now()
        var next = now
            .withHour(4)
            .withMinute(0)
            .withSecond(0)
            .withNano(0)
        if (!now.isBefore(next)) {
            next = next.plusDays(1)
        }
        return Duration.between(now, next).seconds.toInt().coerceIn(60, 86_400)
    }
}
