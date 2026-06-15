package com.funny.aitoy.network.api.service

import com.funny.aitoy.network.api.ApiResp
import kotlinx.serialization.Serializable
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST

interface AuthService {
    @POST("auth/send_code")
    @FormUrlEncoded
    suspend fun sendAuthCode(
        @Field("email") email: String,
        @Field("purpose") purpose: String,
    ): ApiResp<String?>

    @POST("auth/register")
    @FormUrlEncoded
    suspend fun register(
        @Field("email") email: String,
        @Field("password") password: String,
        @Field("code") code: String,
        @Field("username") username: String? = null,
        @Field("deviceId") deviceId: String? = null,
    ): ApiResp<TokenUserPayload>

    @POST("auth/login_password")
    @FormUrlEncoded
    suspend fun loginPassword(
        @Field("email") email: String,
        @Field("password") password: String,
        @Field("deviceId") deviceId: String? = null,
    ): ApiResp<TokenUserPayload>

    @POST("auth/login_code")
    @FormUrlEncoded
    suspend fun loginCode(
        @Field("email") email: String,
        @Field("code") code: String,
        @Field("deviceId") deviceId: String? = null,
    ): ApiResp<TokenUserPayload>

    @POST("auth/find_username")
    @FormUrlEncoded
    suspend fun findUsername(
        @Field("email") email: String,
        @Field("code") code: String,
    ): ApiResp<FindUsernamePayload>

    @POST("auth/reset_password")
    @FormUrlEncoded
    suspend fun resetPassword(
        @Field("email") email: String,
        @Field("code") code: String,
        @Field("newPassword") newPassword: String,
    ): ApiResp<String?>
}

@Serializable
data class FindUsernamePayload(
    val username: String = "",
)

@Serializable
data class TokenUserPayload(
    val token: String,
    val user: com.funny.aitoy.core.model.UserProfile,
)
