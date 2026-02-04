package com.funny.submaker.core.platform

import android.content.Context
import com.tencent.mmkv.MMKV

object AndroidPlatformInit {
    lateinit var appContext: Context
        private set

    fun init(context: Context) {
        appContext = context.applicationContext
        MMKV.initialize(appContext)
    }
}
