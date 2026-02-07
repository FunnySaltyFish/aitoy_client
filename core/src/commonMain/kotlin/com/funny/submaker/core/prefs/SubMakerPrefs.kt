package com.funny.submaker.core.prefs

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import com.funny.data_saver.core.mutableDataSaverStateOf
import com.funny.submaker.core.device.DeviceIdProvider
import com.funny.submaker.core.model.UserProfile

object SubMakerPrefs {
    var serverBaseUrl: String by mutableDataSaverStateOf(
        DataSaverUtils,
        key = "SERVER_BASE_URL",
        initialValue = "http://192.168.1.118:5002",
    )

    var apiPrefix: String by mutableDataSaverStateOf(
        DataSaverUtils,
        key = "API_PREFIX",
        initialValue = "/api/v1",
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
