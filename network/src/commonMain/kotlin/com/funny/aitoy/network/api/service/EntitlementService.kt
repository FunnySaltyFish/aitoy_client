package com.funny.aitoy.network.api.service

import com.funny.aitoy.network.api.ApiResp
import com.funny.aitoy.network.GetCache
import kotlinx.serialization.Serializable
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface EntitlementService {
    @GET("entitlement/device_status")
    @GetCache(maxAgeSeconds = 15)
    suspend fun deviceStatus(
        @Query("deviceId") deviceId: String,
    ): ApiResp<DevicePayload>

    @POST("entitlement/sync_device")
    @FormUrlEncoded
    suspend fun syncDevice(
        @Field("deviceId") deviceId: String,
    ): ApiResp<SyncDevicePayload>

    @POST("entitlement/consume_trial")
    @FormUrlEncoded
    suspend fun consumeTrial(
        @Field("seconds") seconds: Int,
        @Field("deviceId") deviceId: String? = null,
    ): ApiResp<ConsumeTrialPayload>
}

@Serializable
data class DevicePayload(
    val device: com.funny.aitoy.core.model.DeviceProfile,
)

@Serializable
data class SyncDevicePayload(
    val user: com.funny.aitoy.core.model.UserProfile,
    val device: com.funny.aitoy.core.model.DeviceProfile,
)

@Serializable
data class ConsumeTrialPayload(
    val user: com.funny.aitoy.core.model.UserProfile? = null,
    val device: com.funny.aitoy.core.model.DeviceProfile? = null,
)
