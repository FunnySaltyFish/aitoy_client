package com.funny.aitoy.core.model

import kotlinx.serialization.Serializable

@Serializable
data class Entitlement(
    val trialSecondsTotal: Int = 0,
    val trialSecondsUsed: Int = 0,
    val trialSecondsRemaining: Int = 0,
    val proExpireAtMs: Long = 0,
    val proActive: Boolean = false,
)

@Serializable
data class UserProfile(
    val uid: String = "",
    val email: String = "",
    val username: String = "",
    val entitlement: Entitlement = Entitlement(),
)

@Serializable
data class DeviceProfile(
    val deviceId: String = "",
    val linkedUid: String? = null,
    val entitlement: Entitlement = Entitlement(),
    val trialSyncedUid: String? = null,
)

