package com.funny.aitoy.network.api.service

import com.funny.aitoy.network.api.ApiResp
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface ProtocolShareService {
    @POST("protocol-shares")
    suspend fun create(
        @Body request: CreateProtocolShareRequest,
    ): ApiResp<CreateProtocolSharePayload>

    @GET("protocol-shares/{shareId}")
    suspend fun get(
        @Path("shareId") shareId: String,
    ): ApiResp<ProtocolSharePayload>
}

@Serializable
data class CreateProtocolShareRequest(
    val title: String,
    val baseUrl: String,
    val payload: JsonObject,
)

@Serializable
data class CreateProtocolSharePayload(
    val shareId: String = "",
    val title: String = "",
    val url: String = "",
    val importCode: String = "",
    val createdAt: Long = 0,
)

@Serializable
data class ProtocolSharePayload(
    val shareId: String = "",
    val title: String = "",
    val payload: JsonObject = JsonObject(emptyMap()),
    val createdAt: Long = 0,
)
