package com.funny.submaker.network.api

import com.funny.submaker.core.prefs.SubMakerPrefs
import com.funny.submaker.network.HttpClientProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class ApiException(
    val code: Int,
    override val message: String,
) : Exception(message)

object SubMakerApi {
    private val json: Json get() = HttpClientProvider.json
    private val mediaJson = "application/json; charset=utf-8".toMediaType()

    private fun baseUrl(): String = SubMakerPrefs.serverBaseUrl.trimEnd('/')
    private fun apiPrefix(): String = "/" + SubMakerPrefs.apiPrefix.trim('/').trim()

    private fun url(path: String): String = baseUrl() + apiPrefix() + "/" + path.trimStart('/')

    private fun authTokenOrNull(): String? = SubMakerPrefs.authToken.takeIf { it.isNotBlank() }
    private fun requireAuthToken(): String = authTokenOrNull() ?: throw ApiException(2001, "请先登录")

    private suspend fun <T> postJsonOrNull(
        path: String,
        body: String,
        respSerializer: KSerializer<ApiResp<T>>,
        authToken: String?,
    ): T? = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url(url(path))
            .post(body.toRequestBody(mediaJson))
            .apply {
                authToken?.let { header("Authorization", "Bearer $it") }
            }
            .build()

        HttpClientProvider.client.newCall(req).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            val parsed = runCatching { json.decodeFromString(respSerializer, raw) }
                .getOrElse { throw ApiException(9001, "响应解析失败：$raw") }
            if (parsed.code != 0) throw ApiException(parsed.code, parsed.message)
            parsed.data
        }
    }

    private suspend fun <T> getJsonOrNull(
        path: String,
        respSerializer: KSerializer<ApiResp<T>>,
        authToken: String?,
    ): T? = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url(url(path))
            .get()
            .apply {
                authToken?.let { header("Authorization", "Bearer $it") }
            }
            .build()

        HttpClientProvider.client.newCall(req).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            val parsed = runCatching { json.decodeFromString(respSerializer, raw) }
                .getOrElse { throw ApiException(9001, "响应解析失败：$raw") }
            if (parsed.code != 0) throw ApiException(parsed.code, parsed.message)
            parsed.data
        }
    }

    suspend fun sendAuthCode(email: String, purpose: String) {
        val req = SendCodeReq(email = email, purpose = purpose)
        postJsonOrNull(
            "auth/send_code",
            body = json.encodeToString(SendCodeReq.serializer(), req),
            respSerializer = ApiResp.serializer(kotlinx.serialization.json.JsonElement.serializer()),
            authToken = null,
        )
    }

    suspend fun register(email: String, password: String, code: String, username: String?, deviceId: String?): TokenUserPayload {
        val req = RegisterReq(email = email, password = password, code = code, username = username, deviceId = deviceId)
        return postJsonOrNull(
            "auth/register",
            body = json.encodeToString(RegisterReq.serializer(), req),
            respSerializer = ApiResp.serializer(TokenUserPayload.serializer()),
            authToken = null,
        ) ?: throw ApiException(9002, "空响应")
    }

    suspend fun loginPassword(email: String, password: String, deviceId: String?): TokenUserPayload {
        val req = LoginPasswordReq(email = email, password = password, deviceId = deviceId)
        return postJsonOrNull(
            "auth/login_password",
            body = json.encodeToString(LoginPasswordReq.serializer(), req),
            respSerializer = ApiResp.serializer(TokenUserPayload.serializer()),
            authToken = null,
        ) ?: throw ApiException(9002, "空响应")
    }

    suspend fun findUsername(email: String, code: String): String {
        val req = FindUsernameReq(email = email, code = code)
        val payload = postJsonOrNull(
            "auth/find_username",
            body = json.encodeToString(FindUsernameReq.serializer(), req),
            respSerializer = ApiResp.serializer(FindUsernamePayload.serializer()),
            authToken = null,
        ) ?: throw ApiException(9002, "空响应")
        return payload.username
    }

    suspend fun resetPassword(email: String, code: String, newPassword: String) {
        val req = ResetPasswordReq(email = email, code = code, newPassword = newPassword)
        postJsonOrNull(
            "auth/reset_password",
            body = json.encodeToString(ResetPasswordReq.serializer(), req),
            respSerializer = ApiResp.serializer(kotlinx.serialization.json.JsonElement.serializer()),
            authToken = null,
        )
    }

    suspend fun me(): UserMePayload =
        getJsonOrNull(
            "user/me",
            respSerializer = ApiResp.serializer(UserMePayload.serializer()),
            authToken = requireAuthToken(),
        ) ?: throw ApiException(9002, "空响应")

    suspend fun deviceStatus(deviceId: String): DevicePayload {
        val req = DeviceStatusReq(deviceId = deviceId)
        return postJsonOrNull(
            "entitlement/device_status",
            body = json.encodeToString(DeviceStatusReq.serializer(), req),
            respSerializer = ApiResp.serializer(DevicePayload.serializer()),
            authToken = null,
        ) ?: throw ApiException(9002, "空响应")
    }

    suspend fun syncDevice(deviceId: String): SyncDevicePayload {
        val req = DeviceStatusReq(deviceId = deviceId)
        return postJsonOrNull(
            "entitlement/sync_device",
            body = json.encodeToString(DeviceStatusReq.serializer(), req),
            respSerializer = ApiResp.serializer(SyncDevicePayload.serializer()),
            authToken = requireAuthToken(),
        ) ?: throw ApiException(9002, "空响应")
    }

    suspend fun consumeTrial(seconds: Int, deviceId: String?): ConsumeTrialPayload {
        val req = ConsumeTrialReq(seconds = seconds, deviceId = deviceId)
        return postJsonOrNull(
            "entitlement/consume_trial",
            body = json.encodeToString(ConsumeTrialReq.serializer(), req),
            respSerializer = ApiResp.serializer(ConsumeTrialPayload.serializer()),
            authToken = authTokenOrNull(),
        ) ?: throw ApiException(9002, "空响应")
    }

    suspend fun products(): ProductsPayload =
        getJsonOrNull(
            "pay/products",
            respSerializer = ApiResp.serializer(ProductsPayload.serializer()),
            authToken = null,
        ) ?: throw ApiException(9002, "空响应")

    suspend fun createOrder(productId: String): CreateOrderPayload {
        val req = CreateOrderReq(productId = productId)
        return postJsonOrNull(
            "pay/create_order",
            body = json.encodeToString(CreateOrderReq.serializer(), req),
            respSerializer = ApiResp.serializer(CreateOrderPayload.serializer()),
            authToken = requireAuthToken(),
        ) ?: throw ApiException(9002, "空响应")
    }

    suspend fun queryOrder(orderNo: String): QueryOrderPayload {
        val req = QueryOrderReq(orderNo = orderNo)
        return postJsonOrNull(
            "pay/query_order",
            body = json.encodeToString(QueryOrderReq.serializer(), req),
            respSerializer = ApiResp.serializer(QueryOrderPayload.serializer()),
            authToken = requireAuthToken(),
        ) ?: throw ApiException(9002, "空响应")
    }
}
