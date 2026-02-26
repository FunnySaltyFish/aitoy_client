package com.funny.submaker.core.kmp

internal actual fun showPlatformToast(
    context: KMPContext,
    msg: String,
    type: ToastType,
) {
    GlobalToastCenter.enqueue(msg, type)
}
