package com.funny.aitoy

expect object BridgePlatform {
    val appVersionCode: Int
    val appVersionName: String

    fun consumeLastCrashNotice(): String
    fun startForegroundService()
    fun stopForegroundService()
    fun openUrl(url: String)
    fun openBatteryConnectionSettings()
    fun openNotificationSettings()
    fun stableDeviceId(address: String): String

    suspend fun downloadAndInstallUpdate(
        url: String,
        versionName: String,
        onProgress: (Int) -> Unit,
    )
}
