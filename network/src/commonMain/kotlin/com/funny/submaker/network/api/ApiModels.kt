package com.funny.submaker.network.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ApiResp<T>(
    val code: Int = 0,
    val message: String = "ok",
    val data: T? = null,
)

@Serializable
data class SendCodeReq(
    val email: String,
    val purpose: String,
)

@Serializable
data class RegisterReq(
    val email: String,
    val password: String,
    val code: String,
    val username: String? = null,
    @SerialName("deviceId") val deviceId: String? = null,
)

@Serializable
data class LoginPasswordReq(
    val email: String,
    val password: String,
    @SerialName("deviceId") val deviceId: String? = null,
)

@Serializable
data class FindUsernameReq(
    val email: String,
    val code: String,
)

@Serializable
data class FindUsernamePayload(
    val username: String = "",
)

@Serializable
data class ResetPasswordReq(
    val email: String,
    val code: String,
    val newPassword: String,
)

@Serializable
data class TokenUserPayload(
    val token: String,
    val user: com.funny.submaker.core.model.UserProfile,
)

@Serializable
data class UserMePayload(
    val user: com.funny.submaker.core.model.UserProfile,
)

@Serializable
data class DeviceStatusReq(
    @SerialName("deviceId") val deviceId: String,
)

@Serializable
data class DevicePayload(
    val device: com.funny.submaker.core.model.DeviceProfile,
)

@Serializable
data class SyncDevicePayload(
    val user: com.funny.submaker.core.model.UserProfile,
    val device: com.funny.submaker.core.model.DeviceProfile,
)

@Serializable
data class ConsumeTrialReq(
    val seconds: Int,
    @SerialName("deviceId") val deviceId: String? = null,
)

@Serializable
data class ConsumeTrialPayload(
    val user: com.funny.submaker.core.model.UserProfile? = null,
    val device: com.funny.submaker.core.model.DeviceProfile? = null,
)

@Serializable
data class Product(
    val id: String,
    val name: String,
    val priceCents: Int,
    val proDays: Int,
)

@Serializable
data class ProductsPayload(
    val products: List<Product> = emptyList(),
)

@Serializable
data class CreateOrderReq(
    val productId: String,
)

@Serializable
data class CreateOrderPayload(
    val orderNo: String,
    val payUrl: String,
)

@Serializable
data class QueryOrderReq(
    val orderNo: String,
)

@Serializable
data class QueryOrderPayload(
    val orderNo: String,
    val status: String,
    val productId: String,
    val paidAtMs: Long = 0,
)

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
