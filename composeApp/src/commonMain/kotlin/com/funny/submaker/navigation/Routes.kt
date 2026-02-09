package com.funny.submaker.navigation

import androidx.compose.runtime.Stable
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
@Stable
sealed interface AppRoute : NavKey

object Routes {
    @Serializable
    data object Workspace : AppRoute

    @Serializable
    data class Asr(
        val autoStart: Boolean = false,
    ) : AppRoute

    @Serializable
    data class Auth(
        val from: String? = null,
    ) : AppRoute

    @Serializable
    data class AuthLogin(
        val from: String? = null,
    ) : AppRoute

    @Serializable
    data class AuthVerify(
        val from: String? = null,
    ) : AppRoute

    @Serializable
    data class AuthBetaWelcome(
        val from: String? = null,
    ) : AppRoute

    @Serializable
    data class AuthAccount(
        val from: String? = null,
    ) : AppRoute
}

@Serializable
data class AuthResult(
    val email: String,
    val proActive: Boolean,
)
