package com.funny.aitoy.network.api.service

import com.funny.aitoy.network.api.ApiResp
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Query

interface AppService {
    @GET("app/config")
    suspend fun config(
        @Query("versionCode") versionCode: Int,
        @Query("platform") platform: String = "android",
        @Query("channel") channel: String = "common",
    ): ApiResp<AppConfigPayload>
}

@Serializable
data class AppConfigPayload(
    val baseUrl: String = "",
    val mcpUrl: String = "",
    val communityCode: String = "",
    val update: AppUpdatePayload = AppUpdatePayload(),
)

@Serializable
data class AppUpdatePayload(
    val latestVersionCode: Int = 0,
    val latestVersionName: String = "",
    val minSupportedVersionCode: Int = 0,
    val forceUpdate: Boolean = false,
    val updateAvailable: Boolean = false,
    val apkUrl: String = "",
    val downloadUrl: String = "",
    val downloadPageUrl: String = "",
    val fileSizeBytes: Long = 0,
    val msg: String = "",
    val updateLog: String = "",
)
