package com.funny.submaker

import android.app.Application
import com.funny.submaker.core.platform.AndroidPlatformInit
import com.funny.submaker.database.SubMakerDatabaseFactory

class SubMakerApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AndroidPlatformInit.init(this)
        SubMakerDatabaseFactory.init(this)
    }
}
