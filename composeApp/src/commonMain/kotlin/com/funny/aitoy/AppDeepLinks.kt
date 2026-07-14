package com.funny.aitoy

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

internal sealed interface AppDeepLinkTarget {
    data object Pay : AppDeepLinkTarget
    data class Redeem(val code: String) : AppDeepLinkTarget
}

internal object AppDeepLinks {
    private val _events = MutableSharedFlow<AppDeepLinkTarget>(extraBufferCapacity = 1)
    val events = _events.asSharedFlow()

    fun dispatch(raw: String?) {
        val value = raw?.trim().orEmpty()
        if (value.isBlank()) return
        parse(value)?.let { _events.tryEmit(it) }
    }

    private fun parse(raw: String): AppDeepLinkTarget? {
        val lower = raw.lowercase()
        return when {
            lower.startsWith("aitoy://redeem") -> {
                val code = queryParam(raw, "code")
                if (code.isNullOrBlank()) null else AppDeepLinkTarget.Redeem(code)
            }
            lower.startsWith("aitoy://pay") -> AppDeepLinkTarget.Pay
            lower.contains("/pay/open") && lower.contains("target=redeem") -> {
                val code = queryParam(raw, "code")
                if (code.isNullOrBlank()) AppDeepLinkTarget.Pay else AppDeepLinkTarget.Redeem(code)
            }
            lower.contains("/pay/open") -> AppDeepLinkTarget.Pay
            else -> null
        }
    }

    private fun queryParam(raw: String, name: String): String? {
        val query = raw.substringAfter('?', "")
        if (query.isBlank()) return null
        return query.split('&')
            .mapNotNull {
                val key = it.substringBefore('=', "")
                val value = it.substringAfter('=', "")
                if (key == name) value else null
            }
            .firstOrNull()
            ?.replace("+", " ")
            ?.let(::percentDecode)
    }

    private fun percentDecode(value: String): String =
        value.replace(Regex("%[0-9A-Fa-f]{2}")) {
            it.value.substring(1).toInt(16).toChar().toString()
        }
}
