package com.funny.submaker.feature.auth.components

data class AuthPerk(
    val title: String,
    val desc: String,
    val badge: String,
)

val DefaultAuthPerks = listOf(
    AuthPerk(
        title = "自动保存",
        desc = "字幕修改实时同步，意外退出也能恢复。",
        badge = "云",
    ),
    AuthPerk(
        title = "专业导出",
        desc = "解锁 SRT/ASS 等专业级导出能力。",
        badge = "导",
    ),
    AuthPerk(
        title = "内测特权",
        desc = "锁定 Founding Beta 身份与后续优惠。",
        badge = "享",
    ),
)
