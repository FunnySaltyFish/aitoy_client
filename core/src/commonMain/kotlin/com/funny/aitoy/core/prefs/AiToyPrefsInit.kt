package com.funny.aitoy.core.prefs

import com.funny.data_saver.core.DataSaverConverter
import com.funny.aitoy.core.model.DeviceProfile
import com.funny.aitoy.core.model.Entitlement
import com.funny.aitoy.core.model.UserProfile
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString

object AiToyPrefsInit {
    private var inited = false

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }

    fun init() {
        if (inited) return
        inited = true

        DataSaverConverter.registerTypeConverters<Entitlement>(
            save = { json.encodeToString(it) },
            restore = { json.decodeFromString(it) },
        )
        DataSaverConverter.registerTypeConverters<UserProfile>(
            save = { json.encodeToString(it) },
            restore = { json.decodeFromString(it) },
        )
        DataSaverConverter.registerTypeConverters<DeviceProfile>(
            save = { json.encodeToString(it) },
            restore = { json.decodeFromString(it) },
        )
    }
}
