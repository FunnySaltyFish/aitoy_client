package com.funny.aitoy.network.api.service

import com.funny.aitoy.network.api.ApiResp
import kotlinx.serialization.Serializable
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST

interface UserService {
    @GET("user/me")
    suspend fun me(): ApiResp<UserMePayload>

    @POST("user/sync_status")
    @FormUrlEncoded
    suspend fun syncStatus(
        @Field("clientSyncVersionMs") clientSyncVersionMs: Long,
    ): ApiResp<UserSyncStatusPayload>

    @POST("user/profile")
    @FormUrlEncoded
    suspend fun updateProfile(
        @Field("displayName") displayName: String,
        @Field("avatarUrl") avatarUrl: String? = null,
    ): ApiResp<UserMePayload>
}

@Serializable
data class UserMePayload(
    val user: com.funny.aitoy.core.model.UserProfile,
)

@Serializable
data class UserSyncStatusPayload(
    val clientSyncVersionMs: Long = 0,
    val serverSyncVersionMs: Long = 0,
    val needSync: Boolean = false,
)
