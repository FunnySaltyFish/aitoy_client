package com.funny.submaker.core.device

import android.provider.Settings
import com.funny.submaker.core.platform.AndroidPlatformInit

actual fun platformDeviceIdOrNull(): String? {
    return try {
        Settings.Secure.getString(AndroidPlatformInit.appContext.contentResolver, Settings.Secure.ANDROID_ID)
    } catch (_: Throwable) {
        null
    }
}

