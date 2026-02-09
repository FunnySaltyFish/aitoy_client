package com.funny.submaker.feature.auth.components

data class AuthPerk(
    val title: String,
    val desc: String,
    val badge: String,
)

val DefaultAuthPerks = listOf(
    AuthPerk(
        title = "永久优先体验权",
        desc = "Permanent Priority Access",
        badge = "钥",
    ),
    AuthPerk(
        title = "50GB 云存储空间",
        desc = "High-speed Cloud Storage",
        badge = "云",
    ),
    AuthPerk(
        title = "专属反馈通道",
        desc = "Direct Feedback Channel",
        badge = "讯",
    ),
)
