package com.funny.submaker.network

import com.funny.submaker.core.prefs.SubMakerPrefs
import com.funny.submaker.core.utils.JsonX
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody
import retrofit2.Converter
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.lang.reflect.Type
import java.util.concurrent.ConcurrentHashMap

object ServiceCreator {
    private val retrofitCache = ConcurrentHashMap<String, Retrofit>()

    fun normalizeApiBaseUrl(baseUrlOverride: String? = null): String {
        val base = (baseUrlOverride?.trim()?.trimEnd('/').takeUnless { it.isNullOrBlank() })
            ?: SubMakerPrefs.serverBaseUrl.trim().trimEnd('/')
        val prefix = SubMakerPrefs.apiPrefix.trim().trim('/').trim()
        return if (prefix.isBlank()) "$base/" else "$base/$prefix/"
    }

    fun <T> create(service: Class<T>, baseUrlOverride: String? = null): T {
        return retrofit(baseUrlOverride).create(service)
    }

    private fun retrofit(baseUrlOverride: String?): Retrofit {
        val baseUrl = normalizeApiBaseUrl(baseUrlOverride)
        return retrofitCache.getOrPut(baseUrl) {
            Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(OkHttpUtils.okHttpClient)
                .addConverterFactory(NullOnEmptyConverterFactory())
                .addConverterFactory(JsonX.json.asConverterFactory("application/json; charset=utf-8".toMediaType()))
                .build()
        }
    }
}

private class NullOnEmptyConverterFactory : Converter.Factory() {
    override fun responseBodyConverter(
        type: Type,
        annotations: Array<Annotation>,
        retrofit: Retrofit,
    ): Converter<ResponseBody, *> {
        val delegate: Converter<ResponseBody, *> =
            retrofit.nextResponseBodyConverter<Any>(this, type, annotations)
        return Converter { body ->
            if (body.contentLength() == 0L) null else delegate.convert(body)
        }
    }
}
