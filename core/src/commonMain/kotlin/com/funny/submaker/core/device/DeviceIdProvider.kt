package com.funny.submaker.core.device

import com.funny.data_saver.core.mutableDataSaverStateOf
import com.funny.submaker.core.prefs.DataSaverUtils
import java.util.UUID

expect fun platformDeviceIdOrNull(): String?

object DeviceIdProvider {
    private var cached: String? = null
    var deviceId: String by mutableDataSaverStateOf(DataSaverUtils, "DEVICE_ID", "")

    fun get(): String {
        cached?.let { return it }
        val value = deviceId.ifBlank {
            val id = platformDeviceIdOrNull()?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
            deviceId = id
            id
        }
        cached = value
        return value
    }
}

