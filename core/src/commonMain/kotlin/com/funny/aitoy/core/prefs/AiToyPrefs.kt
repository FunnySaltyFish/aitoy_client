package com.funny.aitoy.core.prefs

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import com.funny.data_saver.core.mutableDataSaverStateOf
import com.funny.aitoy.core.device.DeviceIdProvider
import com.funny.aitoy.core.model.UserProfile

object AiToyPrefs {
    init {
        AiToyPrefsInit.init()
    }

    var serverBaseUrl: String by mutableDataSaverStateOf(
        DataSaverUtils,
        key = "SERVER_BASE_URL",
        initialValue = "",
    )

    var apiPrefix: String by mutableDataSaverStateOf(
        DataSaverUtils,
        key = "API_PREFIX",
        initialValue = "api",
    )

    var authToken: String by mutableDataSaverStateOf(
        DataSaverUtils,
        key = "AUTH_TOKEN",
        initialValue = "",
    )

    var user: UserProfile by mutableDataSaverStateOf(
        DataSaverUtils,
        key = "USER_PROFILE",
        initialValue = UserProfile(),
    )

    val isLoggedIn by derivedStateOf { authToken.isNotBlank() && user.uid.isNotBlank() }

    val deviceId: String get() = DeviceIdProvider.get()

    fun logout() {
        authToken = ""
        user = UserProfile()
    }
}
