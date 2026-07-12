package com.funny.aitoy.model

data class AppUpdateState(
    val checked: Boolean = false,
    val updateAvailable: Boolean = false,
    val forceUpdate: Boolean = false,
    val latestVersionName: String = "",
    val message: String = "",
    val apkUrl: String = "",
    val downloadPageUrl: String = "",
    val fileSizeBytes: Long = 0,
    val updateLog: String = "",
)

