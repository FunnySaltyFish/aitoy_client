package com.funny.aitoy.core.kmp

import androidx.compose.runtime.mutableStateListOf

enum class ToastType {
    Default,
    Success,
    Warning,
    Error,
    Info,
}

data class ToastItem(
    val id: Long,
    val message: String,
    val type: ToastType,
)

object GlobalToastCenter {
    private val _items = mutableStateListOf<ToastItem>()
    private var nextToastId = 1L

    val items: List<ToastItem>
        get() = _items

    fun dismiss(id: Long) {
        _items.removeAll { it.id == id }
    }

    internal fun enqueue(message: String, type: ToastType) {
        _items.add(
            ToastItem(
                id = nextToastId++,
                message = message,
                type = type,
            ),
        )
    }
}

fun toast(
    msg: String,
    type: ToastType = ToastType.Default,
) {
    if (msg.isBlank()) return
    showPlatformToast(appCtx, msg, type)
}

fun toastError(
    msg: String,
) {
    if (msg.isBlank()) return
    showPlatformToast(appCtx, msg, ToastType.Error)
}

internal expect fun showPlatformToast(
    context: KMPContext,
    msg: String,
    type: ToastType,
)
