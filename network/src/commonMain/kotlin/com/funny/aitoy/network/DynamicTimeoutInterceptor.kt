package com.funny.aitoy.network

import okhttp3.Interceptor
import okhttp3.Response
import retrofit2.Invocation
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.roundToInt

object DynamicTimeoutInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val invocation = request.tag(Invocation::class.java)
        val method = invocation?.method()
        val timeout = method?.getAnnotation(DynamicTimeout::class.java) ?: return chain.proceed(request)

        var readTimeout = timeout.readTimeout
        runCatching {
            val extractor = timeout.timeoutParamExtractor.java.getDeclaredConstructor().newInstance()
            val textLength = extractor.getTextLength(request) ?: 0
            val baseReadTimeout = extractor.getBaseReadTimeout(request) ?: -1
            val perCharTimeoutMillis = extractor.getPerCharTimeoutMillis(request) ?: -1
            if (baseReadTimeout > 0 && perCharTimeoutMillis > 0 && textLength > 0) {
                val calculatedTimeout = baseReadTimeout + ((textLength * perCharTimeoutMillis).toFloat() / 1000).roundToInt()
                readTimeout = if (readTimeout <= 0) calculatedTimeout else max(readTimeout, calculatedTimeout)
            }
        }

        var newChain = chain
        if (timeout.connectTimeout > 0) {
            newChain = newChain.withConnectTimeout(timeout.connectTimeout, TimeUnit.SECONDS)
        }
        if (readTimeout > 0) {
            newChain = newChain.withReadTimeout(readTimeout, TimeUnit.SECONDS)
        }
        if (timeout.writeTimeout > 0) {
            newChain = newChain.withWriteTimeout(timeout.writeTimeout, TimeUnit.SECONDS)
        }
        return newChain.proceed(request)
    }
}
