package com.funny.aitoy.network.api

import kotlinx.serialization.Serializable

@Serializable
data class ApiResp<T>(
    val code: Int = 0,
    val msg: String? = null,
    val data: T? = null,
) {
    val isSuccess: Boolean get() = code == CODE_SUCCESS
    val displayErrorMsg: String get() = msg?.takeIf { it.isNotBlank() } ?: "加载失败"

    fun getOrDefault(default: T): T = if (isSuccess) data ?: default else default

    companion object {
        const val CODE_SUCCESS = 0
    }
}

class ApiException(
    val code: Int,
    override val message: String,
    override val cause: Throwable? = null,
) : Exception(message, cause)

suspend inline fun <T> apiRequest(crossinline request: suspend () -> ApiResp<T>): T {
    val resp = try {
        request()
    } catch (e: ApiException) {
        throw e
    } catch (e: Throwable) {
        throw ApiException(
            code = 9003,
            message = e.message ?: "网络请求失败",
            cause = e,
        )
    }
    if (resp.code != 0) {
        throw ApiException(resp.code, resp.displayErrorMsg)
    }
    return resp.data ?: throw ApiException(9002, "空响应")
}

suspend inline fun apiRequestUnit(crossinline request: suspend () -> ApiResp<*>?) {
    val resp = try {
        request()
    } catch (e: ApiException) {
        throw e
    } catch (e: Throwable) {
        throw ApiException(
            code = 9003,
            message = e.message ?: "网络请求失败",
            cause = e,
        )
    }
    if (resp != null && resp.code != 0) {
        throw ApiException(resp.code, resp.displayErrorMsg)
    }
}

suspend inline fun <T> apiRequestResult(crossinline request: suspend () -> ApiResp<T>): Result<T> {
    return runCatching { apiRequest(request) }
}
