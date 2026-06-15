package com.funny.aitoy.core.kmp

import android.content.Context
import android.content.Intent
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import com.funny.aitoy.core.platform.AndroidPlatformInit

actual typealias KMPContext = Context

actual val LocalKMPContext = LocalContext

actual val appCtx: KMPContext
    get() = AndroidPlatformInit.appContext

actual fun KMPContext.openUrl(url: String) {
    val intent = Intent(Intent.ACTION_VIEW).apply {
        data = url.toUri()
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { startActivity(intent) }
        .recoverCatching { startActivity(Intent.createChooser(intent, null)) }
        .onFailure { it.printStackTrace() }
}

