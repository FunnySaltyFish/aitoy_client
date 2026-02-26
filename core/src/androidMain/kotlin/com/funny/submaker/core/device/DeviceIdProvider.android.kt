package com.funny.submaker.core.device

import android.provider.Settings
import com.funny.submaker.core.kmp.appCtx

actual fun platformDeviceIdOrNull(): String? {
    return try {
        Settings.Secure.getString(appCtx.contentResolver, Settings.Secure.ANDROID_ID)
    } catch (_: Throwable) {
        null
    }
}

