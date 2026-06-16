package com.funny.aitoy.network.api.service

import com.funny.aitoy.network.api.ApiResp
import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.POST

interface DiagnosticsService {
    @POST("diagnostics/crashes")
    suspend fun uploadCrash(
        @Body request: CrashReportRequest,
    ): ApiResp<CrashReportPayload>
}

@Serializable
data class CrashReportRequest(
    val reportId: String,
    val platform: String = "android",
    val versionCode: Int,
    val versionName: String,
    val deviceModel: String,
    val androidVersion: String,
    val exceptionTrace: String,
    val recentLogs: List<String> = emptyList(),
)

@Serializable
data class CrashReportPayload(
    val reportId: String = "",
)
