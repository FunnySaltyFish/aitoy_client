package com.funny.submaker.network

import okhttp3.Request

interface TimeoutParamExtractor {
    fun getBaseReadTimeout(request: Request): Int?
    fun getPerCharTimeoutMillis(request: Request): Int?
    fun getTextLength(request: Request): Int?
}

class DefaultTimeoutParamExtractor : TimeoutParamExtractor {
    companion object {
        const val HEADER_TEXT_LENGTH = "X-App-Text-Length"
        const val HEADER_BASE_READ_TIMEOUT = "X-App-Base-Read-Timeout"
        const val HEADER_PER_CHAR_TIMEOUT = "X-App-Per-Char-Timeout"
    }

    override fun getBaseReadTimeout(request: Request): Int? {
        return request.header(HEADER_BASE_READ_TIMEOUT)?.toIntOrNull()
    }

    override fun getPerCharTimeoutMillis(request: Request): Int? {
        return request.header(HEADER_PER_CHAR_TIMEOUT)?.toIntOrNull()
    }

    override fun getTextLength(request: Request): Int? {
        return request.header(HEADER_TEXT_LENGTH)?.toIntOrNull()
    }
}
