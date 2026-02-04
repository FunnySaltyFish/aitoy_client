package com.funny.submaker.core.kmp

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import java.awt.Desktop
import java.net.URI

actual abstract class KMPContext

actual val appCtx: KMPContext = object : KMPContext() {}

actual val LocalKMPContext: ProvidableCompositionLocal<KMPContext> = staticCompositionLocalOf { appCtx }

actual fun KMPContext.openUrl(url: String) {
    runCatching {
        if (Desktop.isDesktopSupported()) {
            Desktop.getDesktop().browse(URI(url))
        }
    }.onFailure { it.printStackTrace() }
}
