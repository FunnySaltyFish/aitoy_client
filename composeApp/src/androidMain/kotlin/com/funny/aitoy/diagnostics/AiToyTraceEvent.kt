package com.funny.aitoy.diagnostics

enum class AiToyTraceUploadPolicy {
    Drop,
    Always,
    SessionOnce,
    RateLimited,
}

data class AiToyTraceEvent(
    val message: String,
    val error: Boolean = false,
    val type: String = "",
    val uploadPolicy: AiToyTraceUploadPolicy = if (error) {
        AiToyTraceUploadPolicy.Always
    } else {
        AiToyTraceUploadPolicy.Drop
    },
    val key: String = "",
    val intervalMs: Long = 0L,
)
