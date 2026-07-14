package com.funny.aitoy.network.api.service

import com.funny.aitoy.network.api.ApiResp
import com.funny.aitoy.network.GetCache
import kotlinx.serialization.Serializable
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface PayService {
    @GET("pay/products")
    @GetCache(refreshAt4Am = true, minSeconds = 300)
    suspend fun products(): ApiResp<ProductsPayload>

    @POST("pay/create_order")
    @FormUrlEncoded
    suspend fun createOrder(
        @Field("productId") productId: String,
        @Field("payType") payType: String = "alipay",
        @Field("months") months: Int = 1,
        @Field("quantity") quantity: Int = 1,
    ): ApiResp<CreateOrderPayload>

    @GET("pay/query_order")
    suspend fun queryOrder(
        @Query("orderNo") orderNo: String,
    ): ApiResp<QueryOrderPayload>

    @POST("pay/redeem")
    @FormUrlEncoded
    suspend fun redeemCode(
        @Field("code") code: String,
    ): ApiResp<RedeemPayload>
}

@Serializable
data class Product(
    val id: String,
    val type: String = "monthly",
    val level: String = "",
    val name: String,
    val priceCents: Int,
    val originalPriceCents: Int = priceCents,
    val monthlyPriceCents: Int = priceCents,
    val discountLabel: String = "",
    val campaignTitle: String = "",
    val campaignEndsAtMs: Long = 0,
    val durationDays: Int = 31,
    val aiControlSeconds: Int = 0,
    val validDays: Int = 0,
    val maxQuantity: Int = 1,
    val maxHoldSeconds: Int = 0,
    val oneTimeLimit: Boolean = false,
    val purchasable: Boolean = true,
    val description: String = "",
    val tagline: String = "",
    val upgradeHint: String = "",
    val highlight: Boolean = false,
)

@Serializable
data class ProductsPayload(
    val products: List<Product> = emptyList(),
    val agreementUrl: String = "",
)

@Serializable
data class CreateOrderPayload(
    val orderNo: String,
    val payUrl: String,
)

@Serializable
data class QueryOrderPayload(
    val orderNo: String,
    val status: String,
    val productId: String,
    val months: Int = 1,
    val quantity: Int = 1,
    val amountCents: Int = 0,
    val paidAtMs: Long = 0,
    val user: com.funny.aitoy.core.model.UserProfile? = null,
)

@Serializable
data class RedeemPayload(
    val code: String = "",
    val message: String = "",
    val user: com.funny.aitoy.core.model.UserProfile? = null,
)
