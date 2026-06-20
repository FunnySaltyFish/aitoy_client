package com.funny.aitoy.network.api.service

import com.funny.aitoy.network.api.ApiResp
import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface DiagnosticsService {
    @POST("diagnostics/crashes")
    suspend fun uploadCrash(
        @Body request: CrashReportRequest,
    ): ApiResp<CrashReportPayload>

    @POST("diagnostics/traces")
    suspend fun uploadTrace(
        @Body request: TraceUploadRequest,
    ): ApiResp<TraceUploadPayload>

    @GET("diagnostics/traces")
    suspend fun listTraces(
        @Query("token") token: String? = null,
        @Query("session") session: String? = null,
        @Query("regex") regex: String? = null,
        @Query("startMs") startMs: Long? = null,
        @Query("endMs") endMs: Long? = null,
        @Query("limit") limit: Int? = null,
    ): ApiResp<TraceListPayload>
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

@Serializable
data class TraceUploadRequest(
    val sessionId: String,
    val userToken: String,
    val deviceId: String,
    val versionCode: Int,
    val versionName: String,
    val deviceModel: String,
    val androidVersion: String,
    val selectedDeviceName: String = "",
    val selectedDeviceAddress: String = "",
    val connectionState: String = "",
    val protocolId: String = "",
    val protocolName: String = "",
    val logs: List<TraceLogItem>,
)

@Serializable
data class TraceLogItem(
    val tsMs: Long,
    val level: String = "INFO",
    val tag: String = "AiToyTrace",
    val message: String,
)

@Serializable
data class TraceUploadPayload(
    val accepted: Int = 0,
)

@Serializable
data class TraceListPayload(
    val items: List<TraceLogRecord> = emptyList(),
    val total: Int = 0,
    val limit: Int = 0,
)

@Serializable
data class TraceLogRecord(
    val traceId: String = "",
    val sessionId: String = "",
    val userToken: String = "",
    val deviceId: String = "",
    val versionCode: Int = 0,
    val versionName: String = "",
    val deviceModel: String = "",
    val androidVersion: String = "",
    val selectedDeviceName: String = "",
    val selectedDeviceAddress: String = "",
    val connectionState: String = "",
    val protocolId: String = "",
    val protocolName: String = "",
    val level: String = "",
    val tag: String = "",
    val message: String = "",
    val clientTsMs: Long = 0,
    val receivedAtMs: Long = 0,
)
