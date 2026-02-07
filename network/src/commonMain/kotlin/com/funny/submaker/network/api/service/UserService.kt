package com.funny.submaker.network.api.service

import com.funny.submaker.network.api.ApiResp
import kotlinx.serialization.Serializable
import retrofit2.http.GET

interface UserService {
    @GET("user/me")
    suspend fun me(): ApiResp<UserMePayload>
}

@Serializable
data class UserMePayload(
    val user: com.funny.submaker.core.model.UserProfile,
)
