package com.funny.aitoy

import android.app.Application
import com.funny.aitoy.core.platform.AndroidPlatformInit
import com.funny.aitoy.database.AiToyDatabaseFactory

class AiToyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AndroidPlatformInit.init(this)
        AiToyDatabaseFactory.init(this)
    }
}
