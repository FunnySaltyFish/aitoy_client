package com.funny.submaker.network.api.service

import com.funny.submaker.network.api.ApiResp
import com.funny.submaker.network.DynamicTimeout
import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface AsrService {
    @POST("asr/sessions")
    @DynamicTimeout(readTimeout = 120, writeTimeout = 120)
    suspend fun createSession(
        @Body req: AsrSessionReq,
    ): ApiResp<AsrSessionPayload>

    @POST("asr/upload-ticket")
    @DynamicTimeout(readTimeout = 120, writeTimeout = 120)
    suspend fun createUploadTicket(
        @Body req: AsrUploadTicketReq,
    ): ApiResp<AsrUploadTicketPayload>

    @POST("asr/jobs")
    @DynamicTimeout(readTimeout = 120, writeTimeout = 120)
    suspend fun createJob(
        @Body req: AsrCreateJobReq,
    ): ApiResp<AsrCreateJobPayload>

    @GET("asr/jobs/{jobId}")
    @DynamicTimeout(readTimeout = 120)
    suspend fun pollJob(
        @Path("jobId") jobId: String,
    ): ApiResp<AsrJobPayload>

    @GET("asr/jobs/{jobId}/result")
    @DynamicTimeout(readTimeout = 120)
    suspend fun getResult(
        @Path("jobId") jobId: String,
    ): ApiResp<AsrResultPayload>
}

@Serializable
data class AsrSessionReq(
    val provider: String = "dashscope",
    val region: String = "cn",
    val apiKey: String,
    val keyHint: String = "",
)

@Serializable
data class AsrSessionPayload(
    val sessionId: String,
    val expireAtMs: Long,
)

@Serializable
data class AsrUploadTicketReq(
    val provider: String = "dashscope",
    val model: String = "qwen3-asr-flash-filetrans",
    val fileName: String,
    val contentType: String? = null,
    val sessionId: String? = null,
)

@Serializable
data class AsrUploadTicketPayload(
    val uploadHost: String,
    val formFields: Map<String, String> = emptyMap(),
    val resolvedFileUrl: String,
    val expireAtMs: Long,
)

@Serializable
data class AsrOptionsReq(
    val language: String? = null,
    val enableItn: Boolean = false,
    val enableWords: Boolean = false,
    val channelId: List<Int> = listOf(0),
)

@Serializable
data class AsrCreateJobReq(
    val provider: String = "dashscope",
    val model: String = "qwen3-asr-flash-filetrans",
    val fileUrl: String,
    val sessionId: String? = null,
    val options: AsrOptionsReq = AsrOptionsReq(),
)

@Serializable
data class AsrCreateJobPayload(
    val jobId: String,
    val status: String,
    val providerTaskId: String = "",
)

@Serializable
data class AsrProgressPayload(
    val total: Int = 0,
    val succeeded: Int = 0,
    val failed: Int = 0,
)

@Serializable
data class AsrErrorPayload(
    val code: String = "",
    val message: String = "",
)

@Serializable
data class AsrJobPayload(
    val jobId: String,
    val status: String,
    val providerTaskId: String = "",
    val progress: AsrProgressPayload = AsrProgressPayload(),
    val error: AsrErrorPayload? = null,
)

@Serializable
data class AsrSegmentPayload(
    val startMs: Long,
    val endMs: Long,
    val text: String,
)

@Serializable
data class AsrMetaPayload(
    val provider: String = "",
    val model: String = "",
    val usageSeconds: Int = 0,
)

@Serializable
data class AsrResultPayload(
    val jobId: String,
    val status: String,
    val segments: List<AsrSegmentPayload> = emptyList(),
    val meta: AsrMetaPayload = AsrMetaPayload(),
    val error: AsrErrorPayload? = null,
)
