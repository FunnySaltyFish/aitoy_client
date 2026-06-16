package com.funny.aitoy

import android.app.Application
import com.funny.aitoy.core.platform.AndroidPlatformInit
import com.funny.aitoy.database.AiToyDatabaseFactory
import com.funny.aitoy.diagnostics.AiToyCrashReporter

class AiToyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AndroidPlatformInit.init(this)
        AiToyDatabaseFactory.init(this)
        AiToyCrashReporter.install()
    }
}
