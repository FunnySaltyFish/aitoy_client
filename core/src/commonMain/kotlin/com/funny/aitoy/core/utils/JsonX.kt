package com.funny.aitoy.core.utils

import com.funny.aitoy.core.log.Log
import kotlinx.serialization.json.Json

object JsonX {
    val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
        encodeDefaults = true
    }
}

inline fun <reified T> String.fromJson(): T = JsonX.json.decodeFromString(this)
inline fun <reified T> String.safeFromJson(default: T): T = runCatching {
    JsonX.json.decodeFromString<T>(this)
}.getOrElse {
    Log.e("JsonX") { "Failed to parse JSON: $this" }
    default
}

inline fun <reified T> T.toJson(): String = JsonX.json.encodeToString(this)