package com.funny.aitoy.model

import kotlinx.serialization.Serializable

@Serializable
data class RememberedToy(
    val name: String,
    val address: String,
    val protocolName: String,
    val lastSeenAt: Long,
    val manufacturerData: String = "",
    val scanRecordHex: String = "",
)

enum class ToyRuntimeState(val label: String) {
    Offline("未连接"),
    Connecting("正在连接"),
    Connected("已连接"),
    Failed("连接不上"),
}

data class ManagedToy(
    val name: String,
    val address: String,
    val protocolName: String,
    val runtimeState: ToyRuntimeState,
    val selected: Boolean,
    val current: Boolean,
    val saved: Boolean,
    val batteryPercent: Int? = null,
)

fun ManagedToy.lastSeenKey(saved: List<RememberedToy>): Long =
    saved.firstOrNull { it.address == address }?.lastSeenAt ?: 0L

