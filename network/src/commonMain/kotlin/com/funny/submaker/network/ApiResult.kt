package com.funny.submaker.network

sealed interface ApiResult<out T> {
    data class Ok<T>(val data: T) : ApiResult<T>
    data class Err(val message: String, val throwable: Throwable? = null) : ApiResult<Nothing>
}

