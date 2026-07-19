package com.funny.aitoy

import android.app.Application
import com.funny.aitoy.core.platform.AndroidPlatformInit
import com.funny.aitoy.core.prefs.AiToyPrefs
import com.funny.aitoy.database.AiToyDatabaseFactory
import com.funny.aitoy.diagnostics.AiToyCrashReporter
import com.funny.aitoy.diagnostics.AiToyTraceUploader
import com.funny.aitoy.network.ClientRequestContext

class AiToyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AndroidPlatformInit.init(this)
        AiToyPrefs.ensureInitialized()
        ClientRequestContext.configure(
            appVersionCode = BridgePlatform.appVersionCode,
            appVersionName = BridgePlatform.appVersionName,
            platform = "android",
            packageName = packageName,
        )
        AiToyDatabaseFactory.init(this)
        AiToyTraceUploader.install()
        AiToyCrashReporter.install()
    }
}
