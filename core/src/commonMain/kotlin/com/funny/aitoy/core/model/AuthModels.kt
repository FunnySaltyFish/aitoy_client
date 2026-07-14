package com.funny.aitoy.core.model

import kotlinx.serialization.Serializable

@Serializable
data class Entitlement(
    val trialSecondsTotal: Int = 0,
    val trialSecondsUsed: Int = 0,
    val trialSecondsRemaining: Int = 0,
    val proExpireAtMs: Long = 0,
    val proActive: Boolean = false,
    val membershipLevel: String = "free",
    val membershipName: String = "免费版",
    val membershipExpireAtMs: Long = 0,
    val aiQuotaSecondsMonthly: Int = 0,
    val aiQuotaSecondsUsed: Int = 0,
    val aiQuotaSecondsRemaining: Int = 0,
    val aiQuotaMonth: String = "",
    val freeQuotaSecondsMonthly: Int = 0,
    val freeQuotaSecondsUsed: Int = 0,
    val freeQuotaSecondsRemaining: Int = 0,
    val membershipQuotaSecondsMonthly: Int = 0,
    val membershipQuotaSecondsUsed: Int = 0,
    val membershipQuotaSecondsRemaining: Int = 0,
    val aiAddonSecondsRemaining: Int = 0,
    val aiAddonExpireAtMs: Long = 0,
    val aiTotalSecondsRemaining: Int = aiQuotaSecondsRemaining,
)

@Serializable
data class UserProfile(
    val uid: String = "",
    val email: String = "",
    val username: String = "",
    val displayName: String = "",
    val avatarUrl: String = "",
    val userToken: String = "",
    val entitlement: Entitlement = Entitlement(),
)

@Serializable
data class DeviceProfile(
    val deviceId: String = "",
    val linkedUid: String? = null,
    val entitlement: Entitlement = Entitlement(),
    val trialSyncedUid: String? = null,
)
