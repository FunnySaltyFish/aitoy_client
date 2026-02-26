package com.funny.submaker.core.utils

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

inline fun <reified T> T.toJson(): String = JsonX.json.encodeToString(this)
